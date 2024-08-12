// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic;

import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.impl.IntentionActionWithTextCaching;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.ReportingClassSubstitutor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public final class IntentionFUSCollector extends CounterUsagesCollector {
  private static final ClassEventField ID_FIELD = EventFields.Class("id");
  private static final BooleanEventField DUMB_MODE_FIELD = EventFields.Boolean("dumb_mode");
  private static final IntEventField POSITION_FIELD = EventFields.Int("position");
  private static final IntEventField DISTANCE_FIELD = EventFields.Int("distance");
  private static final StringEventField INSPECTION_ID_FIELD =
    EventFields.StringValidatedByCustomRule("inspection_id", InspectionUsageFUSCollector.InspectionToolValidator.class);

  private static final EventLogGroup GROUP = new EventLogGroup("intentions", 63);

  private static final VarargEventId CALLED =
    GROUP.registerVarargEvent("called",
                              ID_FIELD,
                              EventFields.PluginInfo,
                              EventFields.Language,
                              DISTANCE_FIELD,
                              DUMB_MODE_FIELD);

  private static final VarargEventId SHOWN =
    GROUP.registerVarargEvent("shown",
                              ID_FIELD,
                              EventFields.PluginInfo,
                              EventFields.Language,
                              POSITION_FIELD,
                              INSPECTION_ID_FIELD,
                              DISTANCE_FIELD,
                              DUMB_MODE_FIELD);

  private static final EventId3<Long, FileType, Boolean> POPUP_DELAY =
    GROUP.registerEvent("popup.delay",
                        EventFields.DurationMs,
                        EventFields.FileType,
                        DUMB_MODE_FIELD);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static void record(@NotNull Project project,
                            @NotNull CommonIntentionAction action,
                            @NotNull Language language,
                            @Nullable Editor hostEditor,
                            int fixOffset) {
    final Class<?> clazz = getOriginalHandlerClass(action);
    final PluginInfo info = PluginInfoDetectorKt.getPluginInfo(clazz);
    boolean dumb = DumbService.isDumb(project);
    CALLED.log(project,
               EventFields.PluginInfo.with(info),
               ID_FIELD.with(clazz),
               EventFields.Language.with(language),
               DISTANCE_FIELD.with(getDistance(hostEditor, fixOffset)),
               DUMB_MODE_FIELD.with(dumb));
    FeatureUsageTracker.getInstance().triggerFeatureUsedByIntention(clazz);
  }

  private static @NotNull Class<?> getOriginalHandlerClass(@NotNull CommonIntentionAction action) {
    return ReportingClassSubstitutor.getClassToReport(action);
  }

  public static void reportShownIntentions(@NotNull Project project,
                                           @NotNull ListPopup popup,
                                           @NotNull Language language,
                                           @NotNull Editor editor) {
    @SuppressWarnings("unchecked") List<IntentionActionWithTextCaching> values = popup.getListStep().getValues();
    boolean dumb = DumbService.isDumb(project);
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
        INSPECTION_ID_FIELD.with(intention.getToolId()),
        DISTANCE_FIELD.with(getDistance(editor, intention.getFixOffset())),
        DUMB_MODE_FIELD.with(dumb)
      );
    }
  }

  private static int getDistance(@Nullable Editor editor, int fixOffset) {
    return fixOffset == -1 || editor == null ? 0 : editor.getCaretModel().getOffset() - fixOffset;
  }

  /**
   * Report time between Alt-Enter invocation and the intention popup appearing onscreen
   */
  public static void reportPopupDelay(@NotNull Project project, long delayMs, @NotNull FileType fileType) {
    boolean dumb = DumbService.isDumb(project);
    POPUP_DELAY.log(project, delayMs, fileType, dumb);
  }
}

