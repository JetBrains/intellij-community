package org.jetbrains.jps.incremental;

import org.jetbrains.jps.incremental.messages.BuildMessage;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/29/11
 */
public interface MessageHandler {
  void processMessage(BuildMessage msg);
}
