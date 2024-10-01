// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public final class ToolWindowKeymapExtension implements KeymapExtension {
  @Override
  public @Nullable KeymapGroup createGroup(Condition<? super AnAction> filtered, Project project) {
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
