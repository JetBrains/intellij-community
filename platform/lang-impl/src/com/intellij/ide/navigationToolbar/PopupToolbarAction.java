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
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;

/**
 * User: anna
 * Date: 19-Dec-2005
 */
public class PopupToolbarAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) return;
    if (UISettings.getInstance().SHOW_NAVIGATION_BAR){
      new SelectInNavBarTarget(project).select(null, false);
      return;
    }
    if (PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext) instanceof NavBarPanel) {
      return;
    }
    final Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    final NavBarPanel toolbarPanel = new NavBarPanel(project);
    toolbarPanel.showHint(editor, dataContext);
  }

  @Override
  public void update(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null);
  }
}
