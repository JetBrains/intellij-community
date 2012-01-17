package org.jetbrains.jps.incremental.messages;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/29/11
 */
public class UptoDateFilesSavedEvent extends BuildMessage {
  public static UptoDateFilesSavedEvent INSTANCE = new UptoDateFilesSavedEvent();

  private UptoDateFilesSavedEvent() {
    super("", Kind.INFO);
  }
}
