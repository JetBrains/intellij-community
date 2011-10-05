package org.jetbrains.jps.incremental.messages;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/29/11
 */
public abstract class BuildMessage {
  public static enum Kind {
    ERROR, WARNING, INFO, PROGRESS
  }

  private final String myMessageText;
  private final Kind myKind;

  protected BuildMessage(String messageText, Kind kind) {
    myMessageText = messageText;
    myKind = kind;
  }

  public Kind getKind() {
    return myKind;
  }

  public String getMessageText() {
    return myMessageText;
  }

  public String toString() {
    return getMessageText();
  }
}
