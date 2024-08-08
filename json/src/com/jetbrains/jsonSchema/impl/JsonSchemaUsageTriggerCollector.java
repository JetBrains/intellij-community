// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;

import java.util.Arrays;

public final class JsonSchemaUsageTriggerCollector extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("json.schema", 3);
  private static final EventId1<String> COMPLETION_BY_SCHEMA_INVOKED =
    GROUP.registerEvent("completion.by.schema.invoked", EventFields.String("schemaKind",
                                                                           Arrays.asList("builtin", "schema", "user", "remote")));

  public static void trigger(String feature) {
    COMPLETION_BY_SCHEMA_INVOKED.log(feature);
  }

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }
}
