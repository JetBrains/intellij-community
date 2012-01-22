package org.jetbrains.jps.api;

import com.google.protobuf.MessageLite;
import org.jetbrains.jps.client.ProtobufResponseHandler;

/**
 * @author Eugene Zhuravlev
 *         Date: 8/15/11
 */
public class JpsServerResponseHandler implements ProtobufResponseHandler {

  public final boolean handleMessage(MessageLite msg) throws Exception {
    final JpsRemoteProto.Message message = (JpsRemoteProto.Message)msg;
    final JpsRemoteProto.Message.Type messageType = message.getMessageType();
    if (messageType == JpsRemoteProto.Message.Type.FAILURE) {
      handleFailure(message.getFailure());
      return true;
    }

    if (messageType == JpsRemoteProto.Message.Type.RESPONSE) {
      final JpsRemoteProto.Message.Response response = message.getResponse();
      final JpsRemoteProto.Message.Response.Type responseType = response.getResponseType();
      switch (responseType) {
        case BUILD_EVENT: {
          return handleBuildEvent(response.getBuildEvent());
        }
        case COMPILE_MESSAGE: {
          handleCompileMessage(response.getCompileMessage());
          return false;
        }

        default: throw new Exception("Unknown response: " + response);
      }
    }

    throw new Exception("Unknown message received: " + message);
  }

  /**
   * @param event
   * @return true if session should be terminated, false otherwise
   */
  public boolean handleBuildEvent(JpsRemoteProto.Message.Response.BuildEvent event) {
    return false;
  }

  public void handleCompileMessage(JpsRemoteProto.Message.Response.CompileMessage compileResponse) {
  }

  public void handleFailure(JpsRemoteProto.Message.Failure failure) {
  }

  public void sessionTerminated() {
  }
}
