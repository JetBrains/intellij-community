package org.jetbrains.jps.incremental.messages;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/29/11
 */
public class ProgressMessage extends BuildMessage {
  private volatile float myDone;

  public ProgressMessage(String messageText) {
    this(messageText, -1.0f);
  }

  public ProgressMessage(String messageText, float done) {
    super(messageText, Kind.PROGRESS);
    myDone = done;
  }

  public float getDone() {
    return myDone;
  }

  public void setDone(float done) {
    myDone = done;
  }
}
