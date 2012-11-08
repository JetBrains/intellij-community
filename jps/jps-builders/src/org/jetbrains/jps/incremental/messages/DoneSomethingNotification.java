package org.jetbrains.jps.incremental.messages;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/29/11
 */
public class DoneSomethingNotification extends BuildMessage {
  public static DoneSomethingNotification INSTANCE = new DoneSomethingNotification();

  private DoneSomethingNotification() {
    super("", Kind.INFO);
  }
}
