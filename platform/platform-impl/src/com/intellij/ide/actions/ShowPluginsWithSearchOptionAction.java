// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexander Lobas
 */
public class ShowPluginsWithSearchOptionAction extends DumbAwareAction {
  private final String mySearchOption;

  public ShowPluginsWithSearchOptionAction(@NotNull @Nls String text, @NotNull String searchOption) {
    super(text);
    mySearchOption = searchOption;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ShowSettingsUtil.getInstance().showSettingsDialog(e.getProject(), PluginManagerConfigurable.class, c -> c.enableSearch(mySearchOption));
  }
}