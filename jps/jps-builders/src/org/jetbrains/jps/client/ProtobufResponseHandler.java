// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.client;

import com.google.protobuf.MessageLite;

/**
 * @author Eugene Zhuravlev
 */
public interface ProtobufResponseHandler {

  /**
   * @return true if session should be terminated, false otherwise
   */
  boolean handleMessage(MessageLite message);

  void sessionTerminated();
}
