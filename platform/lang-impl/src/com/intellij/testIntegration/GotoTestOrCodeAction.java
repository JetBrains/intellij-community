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

package com.intellij.testIntegration;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;

public class GotoTestOrCodeAction extends BaseCodeInsightAction {
  @Override
  @NotNull
  protected CodeInsightActionHandler getHandler(){
    return new GotoTestOrCodeHandler();
  }

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(false);
    if (TestFinderHelper.getFinders().length == 0) {
      return;
    }

    Project project = e.getProject();
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor == null || project == null) return;

    PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(editor, project);
    if (psiFile == null) return;

    PsiElement element = GotoTestOrCodeHandler.getSelectedElement(editor, psiFile);

    if (TestFinderHelper.findSourceElement(element) == null) return;

    presentation.setEnabledAndVisible(true);
    if (TestFinderHelper.isTest(element)) {
      presentation.setText(ActionsBundle.message("action.GotoTestSubject.text"));
      presentation.setDescription(ActionsBundle.message("action.GotoTestSubject.description"));
    } else {
      presentation.setText(ActionsBundle.message("action.GotoTest.text"));
      presentation.setDescription(ActionsBundle.message("action.GotoTest.description"));
    }
  }
}
