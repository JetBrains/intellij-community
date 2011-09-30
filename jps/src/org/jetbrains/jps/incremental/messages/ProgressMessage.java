package org.jetbrains.jps.incremental.messages;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/29/11
 */
public class ProgressMessage extends BuildMessage {
  public ProgressMessage(String messageText) {
    super(messageText);
  }
}
