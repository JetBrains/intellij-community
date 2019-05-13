// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental;

import org.jetbrains.jps.incremental.messages.BuildMessage;

/**
 * @author Eugene Zhuravlev
 */
public interface MessageHandler {
  MessageHandler DEAF = new MessageHandler() {
    @Override
    public void processMessage(BuildMessage msg) {
    }
  };

  void processMessage(BuildMessage msg);
}
