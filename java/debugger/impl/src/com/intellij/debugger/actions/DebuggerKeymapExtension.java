// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.keymap.KeymapExtension;
import com.intellij.openapi.keymap.KeymapGroup;
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil;
import com.intellij.openapi.keymap.impl.ui.Group;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.util.ArrayUtil;

final class DebuggerKeymapExtension implements KeymapExtension {
  @Override
  public KeymapGroup createGroup(final Condition<? super AnAction> filtered, final Project project) {
    AnAction[] xDebuggerActions = ActionsTreeUtil.getActions("XDebugger.Actions");
    AnAction[] javaDebuggerActions = ActionsTreeUtil.getActions("JavaDebuggerActions");

    Group group = new Group(JavaDebuggerBundle.message("debugger.actions.group.title"), null, () -> AllIcons.Actions.StartDebugger);
    for (AnAction action : ArrayUtil.mergeArrays(xDebuggerActions, javaDebuggerActions)) {
      ActionsTreeUtil.addAction(group, action, filtered);
    }
    return group;
  }
}
