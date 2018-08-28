// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.ui.customization.CustomizationConfigurable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class CustomizeUIAction extends AnAction {
  public CustomizeUIAction() {
    super("Customize Menus and Toolbars...");
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final ShowSettingsUtil util = ShowSettingsUtil.getInstance();
    util.editConfigurable(project, new CustomizationConfigurable());
  }
}
