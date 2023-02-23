// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions;

import com.intellij.execution.runners.FakeRerunAction;
import com.intellij.openapi.actionSystem.ActionPromoter;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

class ExecutionActionsPromoter implements ActionPromoter {
  @Override
  public @Nullable List<AnAction> promote(@NotNull List<? extends AnAction> actions, @NotNull DataContext context) {
    List<AnAction> list = new ArrayList<>(actions);
    list.sort(Comparator.comparing(action -> action instanceof FakeRerunAction || action instanceof StopAction));
    return list;
  }
}
