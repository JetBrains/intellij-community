package com.intellij.notification;

import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public interface NotificationListener {
  NotificationListener REMOVE = new NotificationListener() {
    @NotNull
    public Continue perform() {
      return Continue.REMOVE;
    }

    public Continue onRemove() {
      return Continue.REMOVE;
    }
  };

  enum Continue {
    REMOVE,
    LEAVE
  }

  @NotNull
  Continue perform();

  Continue onRemove();  
}
