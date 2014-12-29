/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

public class GoToLinkTargetAction extends DumbAwareAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = getEventProject(e);
    VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
    e.getPresentation().setEnabledAndVisible(project != null && file != null && file.is(VFileProperty.SYMLINK));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = getEventProject(e);
    VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
    if (project != null && file != null && file.is(VFileProperty.SYMLINK)) {
      VirtualFile target = file.getCanonicalFile();
      if (target != null) {
        PsiManager psiManager = PsiManager.getInstance(project);
        PsiFileSystemItem psiFile = target.isDirectory() ? psiManager.findDirectory(target) : psiManager.findFile(target);
        if (psiFile != null) {
          ProjectView.getInstance(project).select(psiFile, target, false);
        }
      }
    }
  }
}
