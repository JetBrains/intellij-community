package org.jetbrains.jpsservice;

/**
 * @author Eugene Zhuravlev
 *         Date: 8/15/11
 */
public class JpsServerResponseHandlerAdapter implements JpsServerResponseHandler {

  public void handleCompileResponse(JpsRemoteProto.Message.Response.CompileResponse compileResponse) {
  }

  public void handleStatusResponse(JpsRemoteProto.Message.Response.StatusResponse response) {
  }

  public void handleFailure(JpsRemoteProto.Message.Failure failure) {
  }
}
