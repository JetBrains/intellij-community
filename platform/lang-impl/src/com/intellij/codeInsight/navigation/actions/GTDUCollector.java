// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation.actions;

import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsEventLogGroup;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

final class GTDUCollector extends CounterUsagesCollector {

  enum GTDUChoice {
    GTD,
    SU
  }

  private static final EnumEventField<GTDUChoice> CHOICE = EventFields.Enum("choice", GTDUChoice.class);
  private static final ClassEventField NAVIGATION_PROVIDER_CLASS = EventFields.Class("navigation_provider_class");
  private static final EventLogGroup GROUP = new EventLogGroup("actions.gtdu", 58);

  private static final VarargEventId PERFORMED = registerGTDUEvent("performed");
  private static final VarargEventId NAVIGATED = registerGTDUEvent("navigated");

  @NotNull
  private static VarargEventId registerGTDUEvent(String eventId) {
    return GROUP.registerVarargEvent(
      eventId,
      EventFields.InputEvent,
      EventFields.ActionPlace,
      ActionsEventLogGroup.CONTEXT_MENU,
      EventFields.CurrentFile,
      CHOICE,
      NAVIGATION_PROVIDER_CLASS
    );
  }

  static void recordPerformed(@NotNull GTDUChoice choice) {
    PERFORMED.log(ContainerUtil.append(GotoDeclarationAction.getCurrentEventData(), CHOICE.with(choice)).toArray(new EventPair[0]));
  }

  static void recordNavigated(@NotNull List<@NotNull EventPair<?>> eventData, @NotNull Class<?> navigationProviderClass) {
    NAVIGATED.log(ContainerUtil.append(eventData, NAVIGATION_PROVIDER_CLASS.with(navigationProviderClass)).toArray(new EventPair[0]));
  }

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }
}
