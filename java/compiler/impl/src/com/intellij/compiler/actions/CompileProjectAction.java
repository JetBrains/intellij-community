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
package com.intellij.compiler.actions;

import com.intellij.history.LocalHistory;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.project.Project;

public class CompileProjectAction extends CompileActionBase {
  protected void doAction(DataContext dataContext, final Project project) {
    CompilerManager.getInstance(project).rebuild(new CompileStatusNotification() {
      public void finished(boolean aborted, int errors, int warnings, final CompileContext compileContext) {
        if (aborted || project.isDisposed()) {
          return;
        }

        String text = getTemplatePresentation().getText();
        LocalHistory.getInstance().putSystemLabel(project, errors == 0
                                       ? CompilerBundle.message("rebuild.lvcs.label.no.errors", text)
                                       : CompilerBundle.message("rebuild.lvcs.label.with.errors", text));
      }
    });
  }

  public void update(AnActionEvent event) {
    super.update(event);
    Presentation presentation = event.getPresentation();
    if (!presentation.isEnabled()) {
      return;
    }
    Project project = CommonDataKeys.PROJECT.getData(event.getDataContext());
    presentation.setEnabled(project != null);
  }
}