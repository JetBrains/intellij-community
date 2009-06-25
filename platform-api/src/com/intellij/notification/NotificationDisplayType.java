package com.intellij.notification;

/**
 * @author spleaner
 */
public enum NotificationDisplayType {

  BALOON {
    @Override
    public String toString() {
      return "Balloon";
    }},
  ICON

}
