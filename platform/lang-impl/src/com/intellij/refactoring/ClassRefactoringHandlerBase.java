// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.openapi.util.NlsContexts.DialogTitle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.lang.ElementsHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.PsiNavigateUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dennis.Ushakov
 */
public abstract class ClassRefactoringHandlerBase implements RefactoringActionHandler, ElementsHandler {
  @Override
  public boolean isEnabledOnElements(PsiElement[] elements) {
    return elements.length == 1 && acceptsElement(elements[0]);
  }

  protected static void navigate(final PsiElement element) {
    PsiNavigateUtil.navigate(element);
  }
  
  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    int offset = editor.getCaretModel().getOffset();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    final PsiElement position = file.findElementAt(offset);
    PsiElement element = position;

    while (true) {
      if (element == null || element instanceof PsiFile) {
        String message = RefactoringBundle
          .getCannotRefactorMessage(getInvalidPositionMessage());
        CommonRefactoringUtil.showErrorHint(project, editor, message, getTitle(), getHelpId());
        return;
      }

      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, element)) return;

      if (acceptsElement(element)) {
        invoke(project, new PsiElement[]{position}, dataContext);
        return;
      }
      element = element.getParent();
    }
  }

  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    final PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    showDialog(project, elements[0], editor, file, dataContext);
  }

  protected abstract boolean acceptsElement(PsiElement element);

  protected abstract void showDialog(Project project, PsiElement element, Editor editor, PsiFile file, DataContext dataContext);

  protected abstract String getHelpId();

  protected abstract @DialogTitle String getTitle();

  protected abstract @DialogMessage String getInvalidPositionMessage();
}
