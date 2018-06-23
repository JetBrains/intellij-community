// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.KeymapExtension;
import com.intellij.openapi.keymap.KeymapGroup;
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil;
import com.intellij.openapi.keymap.impl.ui.Group;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.util.ArrayUtil;

/**
 * @author yole
 */
public class DebuggerKeymapExtension implements KeymapExtension {
  public KeymapGroup createGroup(final Condition<AnAction> filtered, final Project project) {
    AnAction[] xDebuggerActions = ActionsTreeUtil.getActions("XDebugger.Actions");
    AnAction[] javaDebuggerActions = ActionsTreeUtil.getActions("JavaDebuggerActions");

    Group group = new Group(KeyMapBundle.message("debugger.actions.group.title"), AllIcons.Actions.StartDebugger);
    for (AnAction action : ArrayUtil.mergeArrays(xDebuggerActions, javaDebuggerActions)) {
      ActionsTreeUtil.addAction(group, action, filtered);
    }
    return group;
  }
}
