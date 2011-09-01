package org.jetbrains.jpsservice;

/**
 * @author Eugene Zhuravlev
 *         Date: 8/15/11
 */
public interface JpsServerResponseHandler {
  void handleCompileMessage(JpsRemoteProto.Message.Response.CompileMessage compileResponse);

  /**
   *
   * @param response
   * @return false
   */
  void handleCommandResponse(JpsRemoteProto.Message.Response.CommandResponse response);

  void handleFailure(JpsRemoteProto.Message.Failure failure);

  void sessionTerminated();
}
