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
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId2;
import com.intellij.internal.statistic.eventLog.events.EventId3;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public final class IntentionFUSCollector extends CounterUsagesCollector {
  private final static EventLogGroup GROUP = new EventLogGroup("intentions", 60);
  private final static EventId3<Class<?>, PluginInfo, Language> CALLED =
    GROUP.registerEvent("called", EventFields.Class("id"), EventFields.PluginInfo, EventFields.Language);
  private final static EventId3<Class<?>, PluginInfo, Language> SHOWN =
    GROUP.registerEvent("shown", EventFields.Class("id"), EventFields.PluginInfo, EventFields.Language);
  private static final EventId2<Long, FileType> POPUP_DELAY = GROUP.registerEvent("popup.delay", EventFields.DurationMs, EventFields.FileType);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static void record(@NotNull Project project, @NotNull CommonIntentionAction action, @NotNull Language language) {
    recordIntentionEvent(project, action, language, CALLED);
  }

  private static void recordIntentionEvent(@NotNull Project project,
                                           @NotNull CommonIntentionAction action,
                                           @NotNull Language language,
                                           EventId3<Class<?>, PluginInfo, Language> eventId) {
    final Class<?> clazz = getOriginalHandlerClass(action);
    final PluginInfo info = PluginInfoDetectorKt.getPluginInfo(clazz);
    eventId.log(project, clazz, info, language);
    if (eventId == CALLED) {
      FeatureUsageTracker.getInstance().triggerFeatureUsedByIntention(clazz);
    }
  }

  @NotNull
  private static Class<?> getOriginalHandlerClass(@NotNull CommonIntentionAction action) {
    if (action instanceof IntentionActionDelegate actionDelegate) {
      IntentionAction delegate = actionDelegate.getDelegate();
      if (delegate != action) {
        return getOriginalHandlerClass(delegate);
      }
    }
    LocalQuickFix quickFix = QuickFixWrapper.unwrap(action);
    if (quickFix != null) {
      return quickFix.getClass();
    }
    return action.getClass();
  }

  public static void reportShownIntentions(@NotNull Project project,
                                           ListPopup popup,
                                           @NotNull Language language) {
    @SuppressWarnings("unchecked") List<IntentionActionWithTextCaching> values = popup.getListStep().getValues();
    for (IntentionActionWithTextCaching value : values) {
      recordIntentionEvent(project, value.getAction(), language, SHOWN);
    }
  }

  /**
   * Report time between Alt-Enter invocation and the intention popup appearing onscreen
   */
  public static void reportPopupDelay(@NotNull Project project, long delayMs, @NotNull FileType fileType) {
    POPUP_DELAY.log(project, delayMs, fileType);
  }
}

