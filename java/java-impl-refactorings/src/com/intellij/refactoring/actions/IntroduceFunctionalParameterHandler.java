// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
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
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    if (dataContext != null) {
      final PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
      final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
      if (file != null && editor != null && !introduceStrategy(project, editor, file, elements)) {
        showErrorMessage(project, editor);
      }
    }
  }

  @Override
  public void invoke(final @NotNull Project project, final Editor editor, final PsiFile file, DataContext dataContext) {
    ExtractMethodHandler.selectAndPass(project, editor, file, elements-> {
        if (!introduceStrategy(project, editor, file, elements)) {
          showErrorMessage(project, editor);
        }
      }
    );
  }

  private static void showErrorMessage(@NotNull Project project, Editor editor) {
    final String message = RefactoringBundle
      .getCannotRefactorMessage(RefactoringBundle.message("is.not.supported.in.the.current.context",
                                                          IntroduceFunctionalParameterAction.getRefactoringName()));
    CommonRefactoringUtil.showErrorHint(project, editor, message, IntroduceFunctionalParameterAction.getRefactoringName(), HelpID.INTRODUCE_PARAMETER);
  }
}
