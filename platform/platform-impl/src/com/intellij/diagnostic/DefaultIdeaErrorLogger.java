// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.diagnostic.VMOptions.MemoryKind;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.PluginUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

@ApiStatus.Internal
public final class DefaultIdeaErrorLogger {
  private static volatile boolean ourOomOccurred;
  private static volatile boolean ourLoggerBroken;
  private static final AtomicBoolean ourPluginUpdateScheduled = new AtomicBoolean(false);

  private static final String FATAL_ERROR_NOTIFICATION_PROPERTY = "idea.fatal.error.notification";
  private static final String DISABLED_VALUE = "disabled";

  static boolean canHandle(@NotNull IdeaLoggingEvent event) {
    if (ourLoggerBroken) return false;

    try {
      var app = ApplicationManager.getApplication();
      if (app == null || app.isExitInProgress() || app.isDisposed()) return false;

      var t = event.getThrowable();
      if (getOOMErrorKind(t) != null) return true;

      var notificationEnabled = !DISABLED_VALUE.equals(System.getProperty(FATAL_ERROR_NOTIFICATION_PROPERTY));

      var plugin = PluginManagerCore.getPlugin(PluginUtil.getInstance().findPluginId(t));
      var submitter = IdeErrorsDialog.getSubmitter(t, plugin);
      var showPluginError = !(submitter instanceof ITNReporter itnReporter) || itnReporter.showErrorInRelease(event);

      scheduleUpdateCheck(plugin);

      return app.isInternal() || notificationEnabled || showPluginError;
    }
    catch (LinkageError e) {
      if (e.getMessage().contains("Could not initialize class com.intellij.diagnostic.IdeErrorsDialog")) {
        ourLoggerBroken = true;
      }
      throw e;
    }
  }

  private static void scheduleUpdateCheck(PluginDescriptor plugin) {
    if (plugin != null && !plugin.isBundled() && !ourPluginUpdateScheduled.getAndSet(true)) {
      var app = ApplicationManager.getApplication();
      app.executeOnPooledThread(() -> {
        if (!app.isExitInProgress() && !app.isDisposed() && UpdateSettings.getInstance().isPluginsCheckNeeded()) {
          UpdateChecker.updateAndShowResult();
        }
      });
    }
  }

  static void handle(@NotNull IdeaLoggingEvent event) {
    if (ourLoggerBroken) return;

    try {
      var kind = getOOMErrorKind(event.getThrowable());
      if (kind != null) {
        ourOomOccurred = true;
        LowMemoryNotifier.showNotification(kind, true);
      }
      else if (!ourOomOccurred) {
        MessagePool.getInstance().addIdeFatalMessage(event);
      }
    }
    catch (Throwable e) {
      var message = e.getMessage();
      //noinspection InstanceofCatchParameter
      if (message != null && message.contains("Could not initialize class com.intellij.diagnostic.MessagePool") ||
          e instanceof NullPointerException && ApplicationManager.getApplication() == null) {
        ourLoggerBroken = true;
      }
    }
  }

  public static @Nullable MemoryKind getOOMErrorKind(@NotNull Throwable t) {
    var message = t.getMessage();

    if (t instanceof OutOfMemoryError) {
      if (message != null) {
        if (message.contains("unable to create") && message.contains("native thread")) return null;
        if (message.contains("Metaspace")) return MemoryKind.METASPACE;
        if (message.contains("direct buffer memory")) return MemoryKind.DIRECT_BUFFERS;
      }
      return MemoryKind.HEAP;
    }

    if (t instanceof VirtualMachineError && message != null && message.contains("CodeCache")) {
      return MemoryKind.CODE_CACHE;
    }

    return null;
  }
}
