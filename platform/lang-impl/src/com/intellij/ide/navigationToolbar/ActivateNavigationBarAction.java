/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.navigationToolbar;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.IdeRootPane;

/**
 * @author Anna Kozlova
 * @author Konstantin Bulenkov
 */
public class ActivateNavigationBarAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    if (project != null && UISettings.getInstance().SHOW_NAVIGATION_BAR){
      final IdeFrameImpl frame = WindowManagerEx.getInstanceEx().getFrame(project);
      final IdeRootPane ideRootPane = ((IdeRootPane)frame.getRootPane());
      final NavBarPanel navBarPanel = (NavBarPanel)ideRootPane.findByName(NavBarRootPaneExtension.NAV_BAR).getComponent();
      navBarPanel.rebuildAndSelectTail(true);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    final Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    UISettings settings = UISettings.getInstance();
    final boolean enabled = project != null && settings.SHOW_NAVIGATION_BAR && !settings.PRESENTATION_MODE;
    e.getPresentation().setEnabled(enabled);
  }
}
