// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.convertToInstanceMethod;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

public class ConvertToInstanceMethodHandler implements RefactoringActionHandler, ContextAwareActionHandler {
  private static final Logger LOG = Logger.getInstance(ConvertToInstanceMethodHandler.class);

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    if (element == null) {
      element = file.findElementAt(editor.getCaretModel().getOffset());
    }

    if (element == null) return;
    if (element instanceof PsiIdentifier) element = element.getParent();

    if(!(element instanceof PsiMethod)) {
      String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("error.wrong.caret.position.method"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.CONVERT_TO_INSTANCE_METHOD);
      return;
    }
    if(LOG.isDebugEnabled()) {
      LOG.debug("MakeMethodStaticHandler invoked");
    }
    invoke(project, new PsiElement[]{element}, dataContext);
  }

  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    if (elements.length != 1 || !(elements[0] instanceof PsiMethod method)) return;
    @NotNull PossibleInstanceQualifiers result = PossibleInstanceQualifiers.build(method);
    if (result instanceof PossibleInstanceQualifiers.Invalid error) {
      Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
      CommonRefactoringUtil.showErrorHint(project, editor, RefactoringBundle.getCannotRefactorMessage(error.message()),
                                          getRefactoringName(),
                                          HelpID.CONVERT_TO_INSTANCE_METHOD);
    }
    else if (result instanceof PossibleInstanceQualifiers.Valid targetQualifiers) {
      new ConvertToInstanceMethodDialog(method, targetQualifiers.toArray()).show();
    }
  }

  @Override
  public boolean isAvailableForQuickList(@NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext dataContext) {
    PsiElement caretElement = BaseRefactoringAction.getElementAtCaret(editor, file);
    return MethodUtils.getJavaMethodFromHeader(caretElement) != null;
  }

  static @NlsContexts.DialogTitle String getRefactoringName() {
    return JavaRefactoringBundle.message("convert.to.instance.method.title");
  }
}
