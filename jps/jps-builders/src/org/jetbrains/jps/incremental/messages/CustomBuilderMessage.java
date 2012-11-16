package org.jetbrains.jps.incremental.messages;

/**
 * @author nik
 */
public class CustomBuilderMessage extends BuildMessage {
  private final String myBuilderId;
  private final String myMessageType;
  private final String myMessageText;

  public CustomBuilderMessage(String builderId, String messageType, String messageText) {
    super("", Kind.INFO);
    myBuilderId = builderId;
    myMessageType = messageType;
    myMessageText = messageText;
  }

  public String getBuilderId() {
    return myBuilderId;
  }

  public String getMessageType() {
    return myMessageType;
  }

  public String getMessageText() {
    return myMessageText;
  }
}
