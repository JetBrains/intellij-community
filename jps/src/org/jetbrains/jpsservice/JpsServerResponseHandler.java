package org.jetbrains.jpsservice;

/**
 * @author Eugene Zhuravlev
 *         Date: 8/15/11
 */
public interface JpsServerResponseHandler {
  void handleCompileResponse(JpsRemoteProto.Message.Response.CompileResponse compileResponse);

  void handleStatusResponse(JpsRemoteProto.Message.Response.StatusResponse response);

  void handleFailure(JpsRemoteProto.Message.Failure failure);
}
