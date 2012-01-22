package org.jetbrains.jps.javac;

import com.google.protobuf.MessageLite;
import org.jetbrains.jps.client.ProtobufResponseHandler;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/22/12
 */
public class JavacServerResponseHandler implements ProtobufResponseHandler{

  public boolean handleMessage(MessageLite message) throws Exception {
    return false;
  }

  public void sessionTerminated() {
  }
}
