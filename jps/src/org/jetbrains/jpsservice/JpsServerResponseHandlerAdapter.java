package org.jetbrains.jpsservice;

/**
 * @author Eugene Zhuravlev
 *         Date: 8/15/11
 */
public class JpsServerResponseHandlerAdapter implements JpsServerResponseHandler {

  public void handleCompileMessage(JpsRemoteProto.Message.Response.CompileMessage compileResponse) {
  }

  public void handleCommandResponse(JpsRemoteProto.Message.Response.CommandResponse response) {
  }

  public void handleFailure(JpsRemoteProto.Message.Failure failure) {
  }

  public void sessionTerminated() {
  }
}
