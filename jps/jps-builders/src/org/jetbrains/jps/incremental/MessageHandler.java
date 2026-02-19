// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

import org.jetbrains.jps.incremental.messages.BuildMessage;

/**
 * @author Eugene Zhuravlev
 */
public interface MessageHandler {
  @SuppressWarnings("Convert2Lambda")
  MessageHandler DEAF = new MessageHandler() {
    @Override
    public void processMessage(BuildMessage msg) {
    }
  };

  void processMessage(BuildMessage msg);
}
