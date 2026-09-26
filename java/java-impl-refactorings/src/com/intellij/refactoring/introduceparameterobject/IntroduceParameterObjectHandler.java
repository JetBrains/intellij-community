// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceparameterobject;

import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IntroduceParameterObjectHandler implements RefactoringActionHandler, ContextAwareActionHandler {
  @Override
  public boolean isAvailableForQuickList(@NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext dataContext) {
    final PsiMethod selectedMethod = getSelectedMethod(editor, file);
    if (selectedMethod != null) {
      final PsiMethod[] deepestSuperMethods = selectedMethod.findDeepestSuperMethods();
      return deepestSuperMethods.length > 0 || getErrorMessage(selectedMethod) == null;
    }
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    List<PsiMember> elements =
      CommonRefactoringUtil.findElementsFromCaretsAndSelections(editor, file, null,
                                                                e -> e instanceof PsiParameter || e instanceof PsiMethod);
    PsiMethod selectedMethod = PsiTreeUtil.getParentOfType(PsiTreeUtil.findCommonParent(elements), PsiMethod.class, false);
    if (selectedMethod == null) {
      final String message = RefactorJBundle.message("cannot.perform.the.refactoring") +
                             RefactorJBundle.message("the.caret.should.be.positioned.within.a.method.declaration.to.be.refactored");
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.IntroduceParameterObject);
      return;
    }
    invoke(project, editor, selectedMethod, new HashSet<>(ContainerUtil.filterIsInstance(elements, PsiParameter.class)));
  }

  private static PsiMethod getSelectedMethod(Editor editor, PsiFile file) {
    final int caret = editor.getCaretModel().getOffset();
    final PsiElement elementAt = file.findElementAt(caret);
    return MethodUtils.getJavaMethodFromHeader(elementAt);
  }

  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    if (elements.length != 1) {
      return;
    }
    final PsiMethod method = PsiTreeUtil.getParentOfType(elements[0], PsiMethod.class, false);
    if (method == null) {
      return;
    }
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    invoke(project, editor, method, Set.of());
  }

  private static void invoke(Project project, Editor editor, PsiMethod selectedMethod, Set<PsiParameter> selectedParameters) {
    PsiMethod newMethod = SuperMethodWarningUtil.checkSuperMethod(selectedMethod);
    if (newMethod == null) return;
    final String message = getErrorMessage(newMethod);
    if (message != null) {
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.IntroduceParameterObject);
      return;
    }
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, newMethod)) return;

    new IntroduceParameterObjectDialog(newMethod, selectedParameters).show();
  }

  private static @NlsContexts.DialogMessage String getErrorMessage(PsiMethod newMethod) {
    final PsiParameter[] parameters = newMethod.getParameterList().getParameters();
    if (parameters.length == 0) {
     return RefactorJBundle.message("cannot.perform.the.refactoring") +
            RefactorJBundle.message("method.selected.has.no.parameters");
    }
    if (newMethod instanceof PsiCompiledElement) {
      return RefactorJBundle.message("cannot.perform.the.refactoring") +
             RefactorJBundle.message("the.selected.method.cannot.be.wrapped.because.it.is.defined.in.a.non.project.class");
    }
    return null;
  }

  private static @NlsContexts.DialogTitle String getRefactoringName() {
    return RefactorJBundle.message("introduce.parameter.object");
  }
}
