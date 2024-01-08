// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic;

import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInsight.intention.impl.IntentionActionWithTextCaching;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.ReportingClassSubstitutor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public final class IntentionFUSCollector extends CounterUsagesCollector {
  private static final ClassEventField ID_FIELD = EventFields.Class("id");
  private static final IntEventField POSITION_FIELD = EventFields.Int("position");
  private static final StringEventField INSPECTION_ID_FIELD =
    EventFields.StringValidatedByCustomRule("inspection_id", InspectionUsageFUSCollector.InspectionToolValidator.class);

  private final static EventLogGroup GROUP = new EventLogGroup("intentions", 61);
  private final static EventId3<Class<?>, PluginInfo, Language> CALLED =
    GROUP.registerEvent("called", ID_FIELD, EventFields.PluginInfo, EventFields.Language);
  private final static VarargEventId SHOWN =
    GROUP.registerVarargEvent("shown", ID_FIELD, EventFields.PluginInfo, EventFields.Language, POSITION_FIELD, INSPECTION_ID_FIELD);
  private static final EventId2<Long, FileType> POPUP_DELAY =
    GROUP.registerEvent("popup.delay", EventFields.DurationMs, EventFields.FileType);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static void record(@NotNull Project project, @NotNull CommonIntentionAction action, @NotNull Language language) {
    final Class<?> clazz = getOriginalHandlerClass(action);
    final PluginInfo info = PluginInfoDetectorKt.getPluginInfo(clazz);
    CALLED.log(project, clazz, info, language);
    FeatureUsageTracker.getInstance().triggerFeatureUsedByIntention(clazz);
  }

  @NotNull
  private static Class<?> getOriginalHandlerClass(@NotNull CommonIntentionAction action) {
    return ReportingClassSubstitutor.getClassToReport(action);
  }

  public static void reportShownIntentions(@NotNull Project project,
                                           @NotNull ListPopup popup,
                                           @NotNull Language language) {
    @SuppressWarnings("unchecked") List<IntentionActionWithTextCaching> values = popup.getListStep().getValues();
    for (int i = 0; i < values.size(); i++) {
      IntentionActionWithTextCaching intention = values.get(i);
      final Class<?> clazz = getOriginalHandlerClass(intention.getAction());
      final PluginInfo info = PluginInfoDetectorKt.getPluginInfo(clazz);
      SHOWN.log(
        project,
        EventFields.PluginInfo.with(info),
        ID_FIELD.with(clazz),
        EventFields.Language.with(language),
        POSITION_FIELD.with(i),
        INSPECTION_ID_FIELD.with(intention.getToolId())
      );
    }
  }

  /**
   * Report time between Alt-Enter invocation and the intention popup appearing onscreen
   */
  public static void reportPopupDelay(@NotNull Project project, long delayMs, @NotNull FileType fileType) {
    POPUP_DELAY.log(project, delayMs, fileType);
  }
}

