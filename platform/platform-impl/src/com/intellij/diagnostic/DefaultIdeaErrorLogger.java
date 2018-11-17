// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.android.tools.analytics.AnalyticsSettings;
import com.intellij.diagnostic.VMOptions.MemoryKind;
import com.intellij.featureStatistics.fusCollectors.AppLifecycleUsageTriggerCollector;
import com.intellij.ide.AndroidStudioSystemHealthMonitorAdapter;
import com.intellij.ide.ExceptionRegistry;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.StackTrace;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.ErrorLogger;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.io.MappingFailedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

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
      if (app.isDisposed() || app.isDisposeInProgress()) {
        return false;
      }

      UpdateChecker.checkForUpdate(event);

      boolean notificationEnabled = !DISABLED_VALUE.equals(System.getProperty(FATAL_ERROR_NOTIFICATION_PROPERTY, ENABLED_VALUE));

      ErrorReportSubmitter submitter = IdeErrorsDialog.getSubmitter(event.getThrowable());
      boolean showPluginError = !(submitter instanceof ITNReporter) || ((ITNReporter)submitter).showErrorInRelease(event);

      boolean isOOM = getOOMErrorKind(event.getThrowable()) != null;
      boolean isMappingFailed = !isOOM && event.getThrowable() instanceof MappingFailedException;
      AppLifecycleUsageTriggerCollector.onError(isOOM, isMappingFailed);

      return notificationEnabled ||
             showPluginError ||
             ApplicationManagerEx.getApplicationEx().isInternal() ||
             isOOM ||
             isMappingFailed;
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

    // Android Studio: track exception count
    if (AnalyticsSettings.getOptedIn()) {
      Throwable t = event.getThrowable();
      if (t != null) {
        if (isReportableCrash(t)) {
          StackTrace stackTrace = ExceptionRegistry.INSTANCE.register(t);
          incrementAndSaveExceptionCount(t);
          AndroidStudioSystemHealthMonitorAdapter.reportException(t, stackTrace);
        }
      }
    }

    try {
      Throwable throwable = event.getThrowable();
      MemoryKind kind = getOOMErrorKind(throwable);
      if (kind != null) {
        ourOomOccurred = true;
        SwingUtilities.invokeAndWait(() -> new OutOfMemoryDialog(kind).show());
      }
      else if (throwable instanceof MappingFailedException) {
        processMappingFailed(event);
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

  public static boolean isReportableCrash(@NotNull Throwable t) {
    if (t instanceof ClassNotFoundException) {
      String cls = t.getMessage();
      if (cls != null && cls.startsWith("com.sun.jdi.")) {
        // Android Studio:
        // Running on a JRE. We're already warning about that in the System Health Monitor.
        // https://code.google.com/p/android/issues/detail?id=225130
        return false;
      }
    }

    return !(t instanceof Logger.EmptyThrowable);
  }

  private static void incrementAndSaveExceptionCount(@NotNull Throwable t) {
    AndroidStudioSystemHealthMonitorAdapter.incrementAndSaveExceptionCount();
    PluginId pluginId = IdeErrorsDialog.findPluginId(t);
    if (pluginId != null) {
      IdeaPluginDescriptor plugin = PluginManager.getPlugin(pluginId);
      if (plugin != null && plugin.isBundled()) {
        AndroidStudioSystemHealthMonitorAdapter.incrementAndSaveBundledPluginsExceptionCount();
      }
      else {
        AndroidStudioSystemHealthMonitorAdapter.incrementAndSaveNonBundledPluginsExceptionCount();
      }
    }
  }

  @Nullable
  private static MemoryKind getOOMErrorKind(Throwable t) {
    String message = t.getMessage();

    if (t instanceof OutOfMemoryError) {
      if (message != null && message.contains("unable to create new native thread")) return null;
      if (message != null && message.contains("Metaspace")) return MemoryKind.METASPACE;
      return MemoryKind.HEAP;
    }

    if (t instanceof VirtualMachineError && message != null && message.contains("CodeCache")) {
      return MemoryKind.CODE_CACHE;
    }

    return null;
  }

  private static void processMappingFailed(IdeaLoggingEvent event) {
    if (!ourMappingFailedNotificationPosted && SystemInfo.isWindows && SystemInfo.is32Bit) {
      ourMappingFailedNotificationPosted = true;
      String exceptionMessage = event.getThrowable().getMessage();
      String text = exceptionMessage +
        "<br>Possible cause: unable to allocate continuous memory chunk of necessary size.<br>" +
        "Reducing JVM maximum heap size (-Xmx) may help.";
      Notifications.Bus.notify(new Notification("Memory", "Memory Mapping Failed", text, NotificationType.WARNING), null);
    }
  }
}