// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.diagnostic.VMOptions.MemoryKind;
import com.intellij.ide.plugins.PluginUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.ErrorLogger;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.system.CpuArch;
import org.jetbrains.annotations.Nullable;

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
      final Application app = ApplicationManager.getApplication();
      if (app.isDisposed()) {
        return false;
      }

      UpdateChecker.checkForUpdate(event);

      boolean notificationEnabled = !DISABLED_VALUE.equals(System.getProperty(FATAL_ERROR_NOTIFICATION_PROPERTY, ENABLED_VALUE));

      Throwable t = event.getThrowable();
      PluginId pluginId = PluginUtil.getInstance().findPluginId(t);

      ErrorReportSubmitter submitter = IdeErrorsDialog.getSubmitter(t, pluginId);
      boolean showPluginError = !(submitter instanceof ITNReporter) || ((ITNReporter)submitter).showErrorInRelease(event);

      final MemoryKind kind = getOOMErrorKind(event.getThrowable());
      boolean isOOM = kind != null;

      return notificationEnabled ||
             showPluginError ||
             ApplicationManager.getApplication().isInternal() ||
             isOOM;
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
      MemoryKind kind = getOOMErrorKind(throwable);
      if (kind != null) {
        ourOomOccurred = true;
        LowMemoryNotifier.showNotification(kind, true);
      }
      else if (!ourOomOccurred) {
        MessagePool.getInstance().addIdeFatalMessage(event);
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

  public static @Nullable MemoryKind getOOMErrorKind(Throwable t) {
    String message = t.getMessage();

    if (t instanceof OutOfMemoryError) {
      if (message != null && message.contains("unable to create") && message.contains("native thread")) return null;
      if (message != null && message.contains("Metaspace")) return MemoryKind.METASPACE;
      return MemoryKind.HEAP;
    }

    if (t instanceof VirtualMachineError && message != null && message.contains("CodeCache")) {
      return MemoryKind.CODE_CACHE;
    }

    return null;
  }
}
