// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics.fusCollectors;

import com.intellij.diagnostic.IdePerformanceListener;
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger;
import com.intellij.internal.statistic.service.fus.collectors.FUSApplicationUsageTrigger;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PreloadingActivity;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class UsageTriggerCollectionActivity extends PreloadingActivity {
  @Override
  public void preload(@NotNull ProgressIndicator indicator) {
    IdePerformanceListener.Adapter handler = new IdePerformanceListener.Adapter() {
      @Override
      public void uiFreezeFinished(int lengthInSeconds) {
        FUSApplicationUsageTrigger.getInstance().trigger(AppLifecycleUsageTriggerCollector.class,
                                                         "ide.freeze." + getLength(lengthInSeconds));
        FeatureUsageLogger.INSTANCE.log("lifecycle",
                                        "ide.freeze", Collections.singletonMap("durationSeconds", lengthInSeconds));
      }

      private String getLength(int seconds) {
        if (seconds <= 1) {
          return "0_1";
        }
        if (seconds <= 2) {
          return "1_2";
        }
        if (seconds <= 5) {
          return "2_5";
        }
        return "5_more";
      }
    };
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(IdePerformanceListener.TOPIC, handler);
  }
}
