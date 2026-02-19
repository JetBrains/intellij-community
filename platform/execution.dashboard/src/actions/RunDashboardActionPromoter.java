// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.actions;

import com.intellij.openapi.actionSystem.ActionPromoter;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.List;

public final class RunDashboardActionPromoter implements ActionPromoter, ActionRemoteBehaviorSpecification.Frontend {
  @Override
  public List<AnAction> promote(@NotNull @Unmodifiable List<? extends AnAction> actions, @NotNull DataContext context) {
    for (AnAction action : actions) {
      if (action instanceof StopAction) {
        return new SmartList<>(action);
      }
    }
    return Collections.emptyList();
  }
}
