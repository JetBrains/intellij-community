// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.internal;

import com.intellij.build.events.ProgressBuildEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemStatusEvent;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemTaskExecutionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public class ExternalSystemTaskProgressIndicatorUpdater {

  private ExternalSystemTaskProgressIndicatorUpdater() {
  }

  public static void updateProgressIndicator(@NotNull ExternalSystemTaskNotificationEvent event,
                                             @NotNull ProgressIndicator indicator,
                                             Function<String, @NlsContexts.ProgressText String> textWrapper) {
    long total;
    long progress;
    String unit;
    if (event instanceof ExternalSystemBuildEvent &&
        ((ExternalSystemBuildEvent)event).getBuildEvent() instanceof ProgressBuildEvent) {
      ProgressBuildEvent progressEvent = (ProgressBuildEvent)((ExternalSystemBuildEvent)event).getBuildEvent();
      total = progressEvent.getTotal();
      progress = progressEvent.getProgress();
      unit = progressEvent.getUnit();
    }
    else if (event instanceof ExternalSystemTaskExecutionEvent &&
             ((ExternalSystemTaskExecutionEvent)event).getProgressEvent() instanceof ExternalSystemStatusEvent) {
      ExternalSystemStatusEvent<?> progressEvent = (ExternalSystemStatusEvent<?>)((ExternalSystemTaskExecutionEvent)event).getProgressEvent();
      total = progressEvent.getTotal();
      progress = progressEvent.getProgress();
      unit = progressEvent.getUnit();
    } else {
      return;
    }

    String sizeInfo = getSizeInfo(progress, total, unit);
    if (total <= 0) {
      indicator.setIndeterminate(true);
    }
    else {
      indicator.setIndeterminate(false);
      indicator.setFraction((double)progress / total);
    }
    String description = event.getDescription();
    indicator.setText(textWrapper.apply(description) + (sizeInfo.isEmpty() ? "" : "  (" + sizeInfo + ')'));
  }

  private static String getSizeInfo(long progress, long total, String unit) {
    if ("bytes".equals(unit)) {
      return total <= 0
             ? StringUtil.formatFileSize(progress) + " / ?"
             : StringUtil.formatFileSize(progress) + " / " + StringUtil.formatFileSize(total);
    } else if ("items".equals(unit)) {
      return total <= 0
             ? progress + " / ?"
             : progress + " / " + total;
    } else {
      return "";
    }
  }
}
