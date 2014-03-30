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

package com.intellij.ide.actions;

import com.intellij.ide.DataManager;
import com.intellij.ide.projectView.impl.ProjectViewImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;

/**
 * @author yole
 */
public class NewElementToolbarAction extends NewElementAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    if (e.getData(LangDataKeys.IDE_VIEW) == null) {
      final Project project = e.getData(CommonDataKeys.PROJECT);
      final PsiFileSystemItem psiFile = e.getData(CommonDataKeys.PSI_FILE).getParent();
      ProjectViewImpl.getInstance(project).selectCB(psiFile, psiFile.getVirtualFile(), true).doWhenDone(new Runnable() {
        @Override
        public void run() {
          showPopup(DataManager.getInstance().getDataContext());
        }
      });
    }
    else {
      super.actionPerformed(e);
    }
  }

  @Override
  public void update(AnActionEvent event) {
    super.update(event);
    if (event.getData(LangDataKeys.IDE_VIEW) == null) {
      Project project = event.getData(CommonDataKeys.PROJECT);
      PsiFile psiFile = event.getData(CommonDataKeys.PSI_FILE);
      if (project != null && psiFile != null) {
        final ToolWindow projectViewWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW);
        if (projectViewWindow.isVisible()) {
          event.getPresentation().setEnabled(true);
        }
      }
    }
  }
}
