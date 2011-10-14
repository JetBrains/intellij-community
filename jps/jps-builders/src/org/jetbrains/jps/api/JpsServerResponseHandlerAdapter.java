package org.jetbrains.jps.api;

/**
 * @author Eugene Zhuravlev
 *         Date: 8/15/11
 */
public class JpsServerResponseHandlerAdapter implements JpsServerResponseHandler {

  public void handleCompileMessage(JpsRemoteProto.Message.Response.CompileMessage compileResponse) {
  }

  public boolean handleBuildEvent(JpsRemoteProto.Message.Response.BuildEvent response) {
    return false;
  }

  public void handleFailure(JpsRemoteProto.Message.Failure failure) {
  }

  public void sessionTerminated() {
  }
}
