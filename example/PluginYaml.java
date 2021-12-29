package example;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.WriteListener;
import javax.inject.Inject;
import javax.servlet.ServletException;
import oracle.dbtools.plugin.api.di.annotations.Provides;
import oracle.dbtools.plugin.api.di.annotations.Priority;
import oracle.dbtools.plugin.api.servlet.FilterOrder;
import oracle.dbtools.plugin.api.logging.Log;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.ByteArrayOutputStream;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

/**
 * This example plugin {@link Filter} demonstrates:
 * <ul>
 * <li>Converting JSON response to YAML</li>
 * </ul>
 *
 * <h4>Project Setup</h4>Copy the ORDS plugin-demo folder, replace the PluginDemp.java with this file. Add the following files to the new project lib directory
 * <ul>
 * <li>jackson-core-2.13.0.jar</li>
 * <li>jackson-databind-2.13.0.jar</li>
 * <li>jackson-dataformat-yaml-2.13.0.jar</li>
 * <li>snakeyaml-1.28.jar</li>
 * </ul>
 * Note that the above jar versions are based on the version of Jackson that ships with ORDS 21.4.0.
 *
 * <h4>Testing the Filter</h4> Invoke a REST service with Accepts: text/yaml as a header.
 *
 * Requires jackson-dataformat-yaml-2.13.0.jar and snakeyaml-1.28.jar to be added to the ords.war as a plugin.
 */
@Provides
@Priority(ring = FilterOrder.STREAMING, value = 50)
public class PluginYaml implements Filter {
  @Inject
  PluginYaml(Log log) {
    this.log = log;
  }

  public void init(FilterConfig fConfig) throws ServletException {
    // No init required
  }

	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		final HttpServletRequest req = (HttpServletRequest) request;
		final String acceptsHeader = req.getHeader("Accepts");

	    if (CONTENT_TYPE_YAML.equalsIgnoreCase(acceptsHeader)) {
	    	ServletResponseWrapperCopier capturingResponseWrapper = new ServletResponseWrapperCopier(
	                (HttpServletResponse) response, acceptsHeader);

	      log.fine("PluginYaml using ServletResponseWrapperCopier");
	      chain.doFilter(req, capturingResponseWrapper);
	    } else {
	      chain.doFilter(req, response);
	    }
	}

	public void destroy() {
		//close any resources here
	}

  private final Log log;
  private final String CONTENT_TYPE_YAML = "text/yaml";

  class ServletResponseWrapperCopier extends HttpServletResponseWrapper{

	  private final String acceptsHeader;
	  private final ByteArrayOutputStream capture;
	  private ServletOutputStream output;
	  private PrintWriter writer;

	  public ServletResponseWrapperCopier(HttpServletResponse response, String acceptsHeader) {
	      super(response);
	      capture = new ByteArrayOutputStream(response.getBufferSize());
	      this.acceptsHeader = acceptsHeader;
	  }

	  @Override
	  public ServletOutputStream getOutputStream() throws IOException {
	      if (writer != null) {
	          throw new IllegalStateException(
	                  "getWriter() has already been called on this response.");
	      }

	      if (!"application/json".equalsIgnoreCase(super.getContentType())) {
	    	  return super.getOutputStream();
	      }

	      if (output == null) {
		      log.fine("PluginYaml ServletResponseWrapperCopier getOutputStream() using capture ByteArrayOutputStream");
	          output = new ServletOutputStream() {
	              @Override
	              public void write(int b) throws IOException {
	                  capture.write(b);
	              }

	              @Override
	              public void flush() throws IOException {
	                  capture.flush();
	              }

	              @Override
	              public void close() throws IOException {
	                  capture.close();
	              }

				@Override
				public boolean isReady() {
					// TODO Auto-generated method stub
					return false;
				}

				@Override
				public void setWriteListener(WriteListener arg0) {
					// TODO Auto-generated method stub

				}
	          };
	      }

	      return output;
	  }

	  @Override
	  public void flushBuffer()
              throws java.io.IOException {
	      if ("application/json".equalsIgnoreCase(super.getContentType())) {
	    	  if (CONTENT_TYPE_YAML.equalsIgnoreCase(acceptsHeader)) {
		      log.fine("PluginYaml ServletResponseWrapperCopier flushBuffer() replacing response content");
	    	      JsonNode jsonNodeTree = new ObjectMapper().readTree(capture.toByteArray());
		    	  byte[] jsonAsYaml = new YAMLMapper().writeValueAsBytes(jsonNodeTree);
		    	  super.setContentType(CONTENT_TYPE_YAML);
		    	  super.getOutputStream().write(jsonAsYaml);
	    	  }
	      }

	      super.flushBuffer();
	  }
	  @Override
	  public PrintWriter getWriter() throws IOException {
	      if (output != null) {
	          throw new IllegalStateException(
	                  "getOutputStream() has already been called on this response.");
	      }

	      if (writer == null) {
	          writer = new PrintWriter(new OutputStreamWriter(capture,
	                  getCharacterEncoding()));
	      }

	      return writer;
	  }
  }
}
