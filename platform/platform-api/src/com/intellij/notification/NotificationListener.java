package com.intellij.notification;

import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;

/**
 * @author spleaner
 */
public interface NotificationListener {

  void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event);
  
}
