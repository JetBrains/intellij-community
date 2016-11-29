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

import com.intellij.ide.SelectInContext;
import com.intellij.ide.impl.ProjectPaneSelectInTarget;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class SelectInProjectViewAction extends DumbAwareAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final ProjectPaneSelectInTarget target = new ProjectPaneSelectInTarget(e.getProject());
    final SelectInContext context = SelectInContextImpl.createContext(e);
    if (context != null) {
      target.selectIn(context, true);
    }
  }

  @Override
  public void beforeActionPerformedUpdate(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      PsiDocumentManager.getInstance(project).commitAllDocuments();
    }
    super.beforeActionPerformedUpdate(e);
  }

  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getProject();
    if (project != null) {
      final ProjectPaneSelectInTarget target = new ProjectPaneSelectInTarget(project);
      final SelectInContext context = SelectInContextImpl.createContext(e);
      if (context != null && target.canSelect(context)) {
        e.getPresentation().setEnabled(true);
        return;
      }
    }
    e.getPresentation().setEnabled(false);
  }
}
