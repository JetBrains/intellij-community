// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;

public class JsonSchemaUsageTriggerCollector {
  private static final String GROUP = "statistics.json.schema";

  public static void trigger(String feature) {
    FUCounterUsageLogger.getInstance().logEvent(GROUP, feature);
  }
}
