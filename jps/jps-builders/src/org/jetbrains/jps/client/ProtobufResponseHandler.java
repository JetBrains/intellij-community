package org.jetbrains.jps.client;

import com.google.protobuf.MessageLite;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/22/12
 */
public interface ProtobufResponseHandler {

  /**
   * @param message
   * @return true if session should be terminated, false otherwise
   * @throws Exception
   */
  boolean handleMessage(MessageLite message) throws Exception;

  void sessionTerminated();
}
