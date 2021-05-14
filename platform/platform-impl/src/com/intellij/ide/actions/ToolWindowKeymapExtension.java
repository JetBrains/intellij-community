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

package com.intellij.ide.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.keymap.KeymapExtension;
import com.intellij.openapi.keymap.KeymapGroup;
import com.intellij.openapi.keymap.KeymapGroupFactory;
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class ToolWindowKeymapExtension implements KeymapExtension {
  @Nullable
  @Override
  public KeymapGroup createGroup(Condition<? super AnAction> filtered, Project project) {
    String title = UIUtil.removeMnemonic(ActionsBundle.message("group.ToolWindowsGroup.text"));
    List<ActivateToolWindowAction> windowActions = project != null ?
                                                   ToolWindowsGroup.getToolWindowActions(project, false) :
                                                   Collections.emptyList();

    KeymapGroup result = KeymapGroupFactory.getInstance().createGroup(title);
    for (AnAction action : windowActions) {
      ActionsTreeUtil.addAction(result, action, filtered);
    }
    return result;
  }
}
