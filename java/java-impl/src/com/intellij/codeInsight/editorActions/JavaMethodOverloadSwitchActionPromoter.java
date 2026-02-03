// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.actionSystem.ActionPromoter;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public final class JavaMethodOverloadSwitchActionPromoter implements ActionPromoter {
  @Override
  public @Unmodifiable List<AnAction> promote(@NotNull @Unmodifiable List<? extends AnAction> actions, @NotNull DataContext context) {
    return ContainerUtil.findAll(actions, a -> a instanceof JavaMethodOverloadSwitchUpAction ||
                                               a instanceof JavaMethodOverloadSwitchDownAction);
  }
}
