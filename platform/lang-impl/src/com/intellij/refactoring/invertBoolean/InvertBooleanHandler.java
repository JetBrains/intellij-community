// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.invertBoolean;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;

public class InvertBooleanHandler implements RefactoringActionHandler {
  public static final String INVERT_BOOLEAN_HELP_ID = "refactoring.invertBoolean";
  private static final Logger LOG = Logger.getInstance(InvertBooleanHandler.class);

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    if (element == null) {
      element = file.findElementAt(editor.getCaretModel().getOffset());
    }
    final InvertBooleanDelegate delegate = InvertBooleanDelegate.findInvertBooleanDelegate(element);
    if (delegate == null) {
      CommonRefactoringUtil.showErrorHint(project, editor, RefactoringBundle.getCannotRefactorMessage(
        RefactoringBundle.message("error.wrong.caret.position.method.or.variable.name")), getRefactoringName(), INVERT_BOOLEAN_HELP_ID);
      return;
    }
    final PsiElement namedElement = delegate.adjustElement(element, project, editor);
    if (namedElement != null && PsiElementRenameHandler.canRename(project, editor, namedElement)) {
      new InvertBooleanDialog(namedElement).show();
    }
  }

  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    LOG.assertTrue(elements.length == 1);
    final InvertBooleanDelegate delegate = InvertBooleanDelegate.findInvertBooleanDelegate(elements[0]);
    if (delegate == null) {
      CommonRefactoringUtil.showErrorHint(project, null, RefactoringBundle.getCannotRefactorMessage(
        RefactoringBundle.message("error.wrong.caret.position.method.or.variable.name")), getRefactoringName(), INVERT_BOOLEAN_HELP_ID);
      return;
    }
    PsiElement element = delegate.adjustElement(elements[0], project, null);
    if (element != null && PsiElementRenameHandler.canRename(project, null, element)) {
      new InvertBooleanDialog(element).show();
    }
  }

  public static @NlsContexts.DialogTitle String getRefactoringName() {
    return RefactoringBundle.message("invert.boolean.title");
  }
}
