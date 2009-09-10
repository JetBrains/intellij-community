package com.intellij.notification;

/**
 * @author spleaner
 */
public enum NotificationDisplayType {

  NONE("Ignore"),
  BALLOON("Balloon"),
  STICKY_BALLOON("Sticky balloon");

  private String myTitle;

  private NotificationDisplayType(final String humanTitle) {
    myTitle = humanTitle;
  }

  public String getTitle() {
    return myTitle;
  }

}
