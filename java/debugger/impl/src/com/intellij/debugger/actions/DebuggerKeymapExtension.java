/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    Group group = new Group(KeyMapBundle.message("debugger.actions.group.title"), AllIcons.General.Debug);
    for (AnAction action : ArrayUtil.mergeArrays(xDebuggerActions, javaDebuggerActions)) {
      ActionsTreeUtil.addAction(group, action, filtered);
    }
    return group;
  }
}
