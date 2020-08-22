// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actionMacro.actions;

import com.intellij.ide.actionMacro.ActionMacro;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public final class MacrosGroup extends ActionGroup {
  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    ArrayList<AnAction> actions = new ArrayList<>();
    final ActionManagerEx actionManager = ((ActionManagerEx) ActionManager.getInstance());
    for (String id : actionManager.getActionIdList(ActionMacro.MACRO_ACTION_PREFIX)) {
      actions.add(actionManager.getAction(id));
    }
    return actions.toArray(AnAction.EMPTY_ARRAY);
  }
}
