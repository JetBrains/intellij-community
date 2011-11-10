/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diagnostic;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.ErrorLogger;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.io.MappingFailedException;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;

/**
 * @author kir
 */
public class DefaultIdeaErrorLogger implements ErrorLogger {
  private static boolean ourOomOccured = false;
  private static boolean mappingFailedNotificationPosted = false;
  @NonNls private static final String FATAL_ERROR_NOTIFICATION_PROPERTY = "idea.fatal.error.notification";
  @NonNls private static final String DISABLED_VALUE = "disabled";
  @NonNls private static final String ENABLED_VALUE = "enabled";
  @NonNls private static final String PARAM_PERMGEN = "PermGen";

  public boolean canHandle(IdeaLoggingEvent event) {
    boolean notificationEnabled = !DISABLED_VALUE.equals(System.getProperty(FATAL_ERROR_NOTIFICATION_PROPERTY, ENABLED_VALUE));

    return notificationEnabled ||
           !(IdeErrorsDialog.getSubmitter(event.getThrowable()) instanceof ITNReporter) ||
           ApplicationManagerEx.getApplicationEx().isInternal() ||
           isOOMError(event.getThrowable())     ||
           event.getThrowable() instanceof MappingFailedException;
  }

  /**
   * @noinspection CallToPrintStackTrace
   */
  public void handle(IdeaLoggingEvent event) {
    try {
      Throwable throwable = event.getThrowable();
      if (isOOMError(throwable)) {
        processOOMError(throwable);
      }
      else if (throwable instanceof MappingFailedException) {
        processMappingFailed(event);
      }
      else if (!ourOomOccured) {
        MessagePool.getInstance().addIdeFatalMessage(event);
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  private boolean isOOMError(Throwable throwable) {
    return throwable instanceof OutOfMemoryError ||
        throwable instanceof VirtualMachineError && throwable.getMessage().contains("CodeCache");
  }

  /**
   * @noinspection CallToPrintStackTrace
   */
  private static void processOOMError(final Throwable throwable) throws InterruptedException, InvocationTargetException {
    ourOomOccured = true;
    throwable.printStackTrace();

    SwingUtilities.invokeAndWait(new Runnable() {
      public void run() {
        String message = throwable.getMessage();
        OutOfMemoryDialog.MemoryKind k = message != null && message.contains(PARAM_PERMGEN)
                                         ? OutOfMemoryDialog.MemoryKind.PERM_GEN
                                         : message != null && message.contains("CodeCache")
                                           ? OutOfMemoryDialog.MemoryKind.CODE_CACHE
                                           : OutOfMemoryDialog.MemoryKind.HEAP;
        new OutOfMemoryDialog(k).show();
      }
    });
  }

  private static void processMappingFailed(final IdeaLoggingEvent event) throws InterruptedException, InvocationTargetException {
    if (!mappingFailedNotificationPosted && SystemInfo.isWindows && SystemInfo.is32Bit) {
      mappingFailedNotificationPosted = true;
      final String exceptionMessage = event.getThrowable().getMessage();
      final String text = exceptionMessage + 
        "<br>Possible cause: unable to allocate continuous memory chunk of necessary size.<br>Reducing JVM's maximum heap size (-Xmx) may help."; 
      Notifications.Bus.notify(new Notification("Memory", "Memory Mapping Failed", text, NotificationType.WARNING), NotificationDisplayType.BALLOON, null);      
    }
  }
  
}
