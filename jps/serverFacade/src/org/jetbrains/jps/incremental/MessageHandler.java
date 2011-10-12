package org.jetbrains.jps.incremental;

import org.jetbrains.jps.incremental.messages.BuildMessage;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/29/11
 */
public interface MessageHandler {
  MessageHandler DEAF = new MessageHandler() {
    public void processMessage(BuildMessage msg) {
    }
  };

  void processMessage(BuildMessage msg);
}
