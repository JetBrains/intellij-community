// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventField;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.internal.statistic.eventLog.events.VarargEventId;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class JavaContributorCollectors extends CounterUsagesCollector {
  public static final String TAG_TYPE = "tag";
  public static final String STATIC_QUALIFIER_TYPE = "static_qualifier";

  private static final EventLogGroup ourGroup = new EventLogGroup("java.completion.contributors", 2);

  private static final EventField<String>
    TYPE_CONTRIBUTOR_FIELD = EventFields.String("type_contributor", List.of(TAG_TYPE, STATIC_QUALIFIER_TYPE));
  private static final EventField<String> TYPE_COMPLETION_FIELD =
    EventFields.String("type_completion", List.of(CompletionType.SMART.name(), CompletionType.BASIC.name()));

  private static final VarargEventId INSERT_HANDLE_EVENT = ourGroup.registerVarargEvent("insert.handle",
                                                                                        TYPE_CONTRIBUTOR_FIELD,
                                                                                        TYPE_COMPLETION_FIELD);

  @Override
  public EventLogGroup getGroup() {
    return ourGroup;
  }

  public static void logInsertHandle(@Nullable Project project, @NotNull String contributorType, @NotNull CompletionType completionType) {
    final List<EventPair<?>> data = new ArrayList<>(2);

    data.add(TYPE_CONTRIBUTOR_FIELD.with(contributorType));
    data.add(TYPE_COMPLETION_FIELD.with(completionType.name()));

    INSERT_HANDLE_EVENT.log(project, data);
  }
}
