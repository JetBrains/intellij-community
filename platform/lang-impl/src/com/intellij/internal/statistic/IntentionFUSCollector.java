// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic;

import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.IntentionSource;
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public final class IntentionFUSCollector extends CounterUsagesCollector {
  private static final ClassEventField ID_FIELD = EventFields.Class("id");
  private static final IntEventField POSITION_FIELD = EventFields.Int("position");
  private static final IntEventField DISTANCE_FIELD = EventFields.Int("distance");
  private static final StringEventField INSPECTION_ID_FIELD =
    EventFields.StringValidatedByCustomRule("inspection_id", InspectionUsageFUSCollector.InspectionToolValidator.class);
  private static final EnumEventField<IntentionSource> SOURCE_FIELD = EventFields.Enum("source", IntentionSource.class);

  private static final EventLogGroup GROUP = new EventLogGroup("intentions", 66);

  private static final VarargEventId CALLED = GROUP.registerVarargEvent(
    "called",
    ID_FIELD,
    EventFields.PluginInfo,
    EventFields.Language,
    DISTANCE_FIELD,
    EventFields.Dumb,
    SOURCE_FIELD
  );

  private static final VarargEventId SHOWN = GROUP.registerVarargEvent(
    "shown",
    ID_FIELD,
    EventFields.PluginInfo,
    EventFields.Language,
    POSITION_FIELD,
    INSPECTION_ID_FIELD,
    DISTANCE_FIELD,
    EventFields.Dumb,
    SOURCE_FIELD
  );

  private static final EventId3<Long, FileType, Boolean> POPUP_DELAY =
    GROUP.registerEvent("popup.delay",
                        EventFields.DurationMs,
                        EventFields.FileType,
                        EventFields.Dumb);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  /**
   * Only for backward compatibility. Use overload with {@link IntentionSource}.
   */
  @Deprecated
  public static void record(@NotNull Project project,
                            @NotNull CommonIntentionAction action,
                            @NotNull Language language,
                            @Nullable Editor hostEditor,
                            int fixOffset) {
    record(project, action, language, hostEditor, fixOffset, IntentionSource.CONTEXT_ACTIONS);
  }

  public static void record(@NotNull Project project,
                            @NotNull CommonIntentionAction action,
                            @NotNull Language language,
                            @Nullable Editor hostEditor,
                            int fixOffset,
                            @NotNull IntentionSource source) {
    Class<?> clazz = getOriginalHandlerClass(action);
    PluginInfo info = PluginInfoDetectorKt.getPluginInfo(clazz);
    boolean dumb = DumbService.isDumb(project);
    CALLED.log(project,
               EventFields.PluginInfo.with(info),
               ID_FIELD.with(clazz),
               EventFields.Language.with(language),
               DISTANCE_FIELD.with(getDistance(hostEditor, fixOffset)),
               EventFields.Dumb.with(dumb),
               SOURCE_FIELD.with(source));
    FeatureUsageTracker.getInstance().triggerFeatureUsedByIntention(clazz);
  }

  private static @NotNull Class<?> getOriginalHandlerClass(@NotNull CommonIntentionAction action) {
    return ReportingClassSubstitutor.getClassToReport(action);
  }

  public static void reportShownIntentions(@NotNull Project project,
                                           @NotNull ListPopup popup,
                                           @NotNull Language language,
                                           @NotNull Editor editor,
                                           @NotNull IntentionSource source) {
    @SuppressWarnings("unchecked") List<IntentionActionWithTextCaching> values = popup.getListStep().getValues();
    boolean dumb = DumbService.isDumb(project);
    for (int i = 0; i < values.size(); i++) {
      IntentionActionWithTextCaching intention = values.get(i);
      Class<?> clazz = getOriginalHandlerClass(intention.getAction());
      PluginInfo info = PluginInfoDetectorKt.getPluginInfo(clazz);

      SHOWN.log(
        project,
        EventFields.PluginInfo.with(info),
        ID_FIELD.with(clazz),
        EventFields.Language.with(language),
        POSITION_FIELD.with(i),
        INSPECTION_ID_FIELD.with(intention.getToolId()),
        DISTANCE_FIELD.with(getDistance(editor, intention.getFixOffset())),
        EventFields.Dumb.with(dumb),
        SOURCE_FIELD.with(source)
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

