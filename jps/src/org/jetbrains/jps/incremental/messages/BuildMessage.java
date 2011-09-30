package org.jetbrains.jps.incremental.messages;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/29/11
 */
public abstract class BuildMessage {

  private final String myMessageText;

  public BuildMessage(String messageText) {
    myMessageText = messageText;
  }

  public String getMessageText() {
    return myMessageText;
  }
}
