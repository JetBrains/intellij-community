// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.impl;

import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

class LiveTemplateRunLogger {
  private static final String GROUP = "live.templates";

  static void log(@NotNull TemplateImpl template, @NotNull Language language) {
    String key = template.getKey();
    String groupName = template.getGroupName();
    if (TemplateSettings.getInstance().isStatisticsSafeTemplate(key, groupName)) {
      final FeatureUsageData data = new FeatureUsageData().
        addLanguage(language).
        addData("group", groupName);
      FUCounterUsageLogger.getInstance().logEvent(GROUP, key, data);
    }
  }
}
