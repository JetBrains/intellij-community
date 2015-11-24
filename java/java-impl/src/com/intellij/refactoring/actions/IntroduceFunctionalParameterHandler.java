/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.refactoring.introduceParameter.IntroduceParameterHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;

public class IntroduceFunctionalParameterHandler extends IntroduceParameterHandler {
  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    if (dataContext != null) {
      final PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
      final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
      if (file != null && editor != null && !introduceStrategy(project, editor, file, elements)) {
        showErrorMessage(project, editor);
      }
    }
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, DataContext dataContext) {
    ExtractMethodHandler.selectAndPass(project, editor, file, new Pass<PsiElement[]>() {
      @Override
      public void pass(PsiElement[] elements) {
        if (!introduceStrategy(project, editor, file, elements)) {
          showErrorMessage(project, editor);
        }
      }
    });
  }

  private static void showErrorMessage(@NotNull Project project, Editor editor) {
    final String message = RefactoringBundle
      .getCannotRefactorMessage(RefactoringBundle.message("is.not.supported.in.the.current.context", IntroduceFunctionalParameterAction.REFACTORING_NAME));
    CommonRefactoringUtil.showErrorHint(project, editor, message, IntroduceFunctionalParameterAction.REFACTORING_NAME, HelpID.INTRODUCE_PARAMETER);
  }
}
