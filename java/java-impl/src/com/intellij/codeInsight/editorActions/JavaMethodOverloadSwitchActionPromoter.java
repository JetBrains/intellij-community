// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @Unmodifiable
  public List<AnAction> promote(@NotNull @Unmodifiable List<? extends AnAction> actions, @NotNull DataContext context) {
    return ContainerUtil.findAll(actions, a -> a instanceof JavaMethodOverloadSwitchUpAction ||
                                               a instanceof JavaMethodOverloadSwitchDownAction);
  }
}
