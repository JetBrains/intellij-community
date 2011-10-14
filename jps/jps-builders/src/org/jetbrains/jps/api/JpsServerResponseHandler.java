package org.jetbrains.jps.api;

/**
 * @author Eugene Zhuravlev
 *         Date: 8/15/11
 */
public interface JpsServerResponseHandler {
  void handleCompileMessage(JpsRemoteProto.Message.Response.CompileMessage compileResponse);

  /**
   * @param event
   * @return true if session should be terminated, false otherwise
   */
  boolean handleBuildEvent(JpsRemoteProto.Message.Response.BuildEvent event);

  void handleFailure(JpsRemoteProto.Message.Failure failure);

  void sessionTerminated();
}
