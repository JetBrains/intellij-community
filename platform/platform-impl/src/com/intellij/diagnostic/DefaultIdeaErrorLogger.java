/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.diagnostic.VMOptions.MemoryKind;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.ErrorLogger;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.io.MappingFailedException;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;

/**
 * @author kir
 */
@SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
public class DefaultIdeaErrorLogger implements ErrorLogger {
  private static boolean ourOomOccurred = false;
  private static boolean ourLoggerBroken = false;
  private static boolean ourMappingFailedNotificationPosted = false;

  private static final String FATAL_ERROR_NOTIFICATION_PROPERTY = "idea.fatal.error.notification";
  private static final String DISABLED_VALUE = "disabled";
  private static final String ENABLED_VALUE = "enabled";

  @Override
  public boolean canHandle(IdeaLoggingEvent event) {
    if (ourLoggerBroken) return false;

    try {
      UpdateChecker.checkForUpdate(event);

      boolean notificationEnabled = !DISABLED_VALUE.equals(System.getProperty(FATAL_ERROR_NOTIFICATION_PROPERTY, ENABLED_VALUE));

      ErrorReportSubmitter submitter = IdeErrorsDialog.getSubmitter(event.getThrowable());
      boolean showPluginError = !(submitter instanceof ITNReporter) || ((ITNReporter)submitter).showErrorInRelease(event);

      //noinspection ThrowableResultOfMethodCallIgnored
      return notificationEnabled ||
             showPluginError ||
             ApplicationManagerEx.getApplicationEx().isInternal() ||
             getOOMErrorKind(event.getThrowable()) != null ||
             event.getThrowable() instanceof MappingFailedException;
    }
    catch (LinkageError e) {
      if (e.getMessage().contains("Could not initialize class com.intellij.diagnostic.IdeErrorsDialog")) {
        ourLoggerBroken = true;
      }
      throw e;
    }
  }

  @Override
  public void handle(IdeaLoggingEvent event) {
    if (ourLoggerBroken) return;

    try {
      Throwable throwable = event.getThrowable();
      final MemoryKind kind = getOOMErrorKind(throwable);
      if (kind != null) {
        ourOomOccurred = true;
        SwingUtilities.invokeAndWait(new Runnable() {
          public void run() {
            new OutOfMemoryDialog(kind).show();
          }
        });
      }
      else if (throwable instanceof MappingFailedException) {
        processMappingFailed(event);
      }
      else if (!ourOomOccurred) {
        MessagePool messagePool = MessagePool.getInstance();
        LogMessage message = messagePool.addIdeFatalMessage(event);
        if (message != null && ApplicationManager.getApplication() != null) {
          ErrorNotifier.notifyUi(message, messagePool);
        }
      }
    }
    catch (Throwable e) {
      String message = e.getMessage();
      //noinspection InstanceofCatchParameter
      if (message != null && message.contains("Could not initialize class com.intellij.diagnostic.MessagePool") ||
          e instanceof NullPointerException && ApplicationManager.getApplication() == null) {
        ourLoggerBroken = true;
      }
    }
  }

  @Nullable
  private static MemoryKind getOOMErrorKind(Throwable t) {
    String message = t.getMessage();

    if (t instanceof OutOfMemoryError) {
      if (message != null && message.contains("unable to create new native thread")) return null;
      if (message != null && message.contains("PermGen")) return MemoryKind.PERM_GEN;
      return MemoryKind.HEAP;
    }

    if (t instanceof VirtualMachineError && message != null && message.contains("CodeCache")) {
      return MemoryKind.CODE_CACHE;
    }

    return null;
  }

  private static void processMappingFailed(IdeaLoggingEvent event) throws InterruptedException, InvocationTargetException {
    if (!ourMappingFailedNotificationPosted && SystemInfo.isWindows && SystemInfo.is32Bit) {
      ourMappingFailedNotificationPosted = true;
      @SuppressWarnings("ThrowableResultOfMethodCallIgnored") String exceptionMessage = event.getThrowable().getMessage();
      String text = exceptionMessage +
        "<br>Possible cause: unable to allocate continuous memory chunk of necessary size.<br>" +
        "Reducing JVM maximum heap size (-Xmx) may help.";
      Notifications.Bus.notify(new Notification("Memory", "Memory Mapping Failed", text, NotificationType.WARNING), null);
    }
  }
}