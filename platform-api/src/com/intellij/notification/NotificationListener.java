package com.intellij.notification;

import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public interface NotificationListener {

  enum OnClose {
    REMOVE,
    LEAVE
  }

  @NotNull
  OnClose perform();

}
