package com.intellij.notification;

import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public interface NotificationListener {

  enum Continue {
    REMOVE,
    LEAVE
  }

  @NotNull
  Continue perform();

  Continue onRemove();  
}
