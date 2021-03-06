// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.services;

import com.intellij.openapi.actionSystem.ActionPromoter;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class ServiceViewActionPromoter implements ActionPromoter {
  @Override
  public List<AnAction> promote(@NotNull List<? extends AnAction> actions, @NotNull DataContext context) {
    for (AnAction action : actions) {
      if (action instanceof JumpToServicesAction) {
        return new SmartList<>(action);
      }
    }
    return Collections.emptyList();
  }
}
