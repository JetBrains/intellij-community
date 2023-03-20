// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInsight.intention.impl.IntentionActionWithTextCaching;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId3;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public final class IntentionsCollector extends CounterUsagesCollector {
  private final static EventLogGroup GROUP = new EventLogGroup("intentions", 58);
  private final static EventId3<Class<?>, PluginInfo, Language> CALLED =
    GROUP.registerEvent("called", EventFields.Class("id"), EventFields.PluginInfo, EventFields.Language);
  private final static EventId3<Class<?>, PluginInfo, Language> SHOWN =
    GROUP.registerEvent("shown", EventFields.Class("id"), EventFields.PluginInfo, EventFields.Language);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static void record(@NotNull Project project, @NotNull IntentionAction action, @NotNull Language language) {
    recordIntentionEvent(project, action, language, CALLED);
  }

  private static void recordIntentionEvent(@NotNull Project project,
                                           @NotNull IntentionAction action,
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
  private static Class<?> getOriginalHandlerClass(@NotNull IntentionAction action) {
    Object handler = action;
    if (action instanceof IntentionActionDelegate) {
      IntentionAction delegate = ((IntentionActionDelegate)action).getDelegate();
      if (delegate != action) {
        return getOriginalHandlerClass(delegate);
      }
    }
    else if (action instanceof QuickFixWrapper) {
      handler = ((QuickFixWrapper)action).getFix();
    }
    return handler.getClass();
  }

  public static void reportShownIntentions(@NotNull Project project,
                                           ListPopup popup,
                                           @NotNull Language language) {
    @SuppressWarnings("unchecked") List<IntentionActionWithTextCaching> values = popup.getListStep().getValues();
    for (IntentionActionWithTextCaching value : values) {
      recordIntentionEvent(project, value.getAction(), language, SHOWN);
    }
  }
}

