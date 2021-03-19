// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics;

import com.intellij.internal.statistic.eventLog.EventLogNotificationService;
import com.intellij.internal.statistic.eventLog.EventLogSystemLogger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

public class FeatureUsageEventSubscribeActivity implements StartupActivity.DumbAware {
  private static final AtomicBoolean isSubscribed = new AtomicBoolean(false);

  @Override
  public void runActivity(@NotNull Project project) {
    ProductivityFeaturesRegistry registry = ProductivityFeaturesRegistry.getInstance();
    FeatureUsageTracker usageTracker = FeatureUsageTracker.getInstance();
    if (registry != null && usageTracker != null && !isSubscribed.getAndSet(true)) {
      EventLogNotificationService.INSTANCE.subscribe(logEvent -> {
        FeatureDescriptor feature = registry.getFeatureDescriptorByLogEvent(logEvent.getGroup().getId(),
                                                                            logEvent.getEvent().getId(),
                                                                            logEvent.getEvent().getData());
        if (feature != null) {
          usageTracker.triggerFeatureUsed(feature.getId());
        }
        return null;
      }, EventLogSystemLogger.DEFAULT_RECORDER);
    }
  }
}
