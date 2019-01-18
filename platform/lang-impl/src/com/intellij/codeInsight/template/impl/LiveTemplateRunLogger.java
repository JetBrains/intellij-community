// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.impl;

import com.intellij.internal.statistic.eventLog.FeatureUsageGroup;
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger;
import com.intellij.lang.Language;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

class LiveTemplateRunLogger {
  private static final FeatureUsageGroup GROUP = new FeatureUsageGroup("live.templates", 1);

  static void log(@NotNull TemplateImpl template, @NotNull Language language) {
    String key = template.getKey();
    String groupName = template.getGroupName();
    if (StringUtil.isNotEmpty(key) && StringUtil.isNotEmpty(groupName)) {
      Map<String, Object> data = ContainerUtil.newHashMap();
      data.put("fileLanguage", language.getID());
      data.put("groupName", groupName);
      FeatureUsageLogger.INSTANCE.log(GROUP, key, data);
    }
  }

}
