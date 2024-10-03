// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil;
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider;
import com.intellij.internal.statistic.eventLog.uploader.EventLogExternalUploader;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

final class EventLogApplicationLifecycleListener implements AppLifecycleListener {
  @Override
  public void appWillBeClosed(boolean isRestart) {
    if (!isRestart && !PluginManagerCore.isRunningFromSources() && isSendingOnExitEnabled()) {
      List<StatisticsEventLoggerProvider> enabledLoggerProviders =
        ContainerUtil.filter(StatisticsEventLogProviderUtil.getEventLogProviders(), p -> p.isSendEnabled() && p.getSendLogsOnIdeClose());
      if (!enabledLoggerProviders.isEmpty() && !isUpdateInProgress()) {
        ProgressManager.getInstance().run(new Task.Modal(null, "Starting External Log Uploader", false) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            EventLogExternalUploader.INSTANCE.startExternalUpload(
              enabledLoggerProviders,
              StatisticsUploadAssistant.isUseTestStatisticsConfig(),
              StatisticsUploadAssistant.isUseTestStatisticsSendEndpoint());
          }
        });
      }
    }
  }

  private static boolean isSendingOnExitEnabled() {
    // the default value is true, but if a registry is yet not loaded on appWillBeClosed, it means that something bad happened
    return Registry.is("feature.usage.event.log.send.on.ide.close", false);
  }

  private static boolean isUpdateInProgress() {
    return ApplicationInfo.getInstance().getBuild().asString().
      equals(PropertiesComponent.getInstance().getValue("ide.self.update.started.for.build"));
  }
}
