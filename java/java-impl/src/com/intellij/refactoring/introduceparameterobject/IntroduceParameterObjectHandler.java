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
package com.intellij.refactoring.introduceparameterobject;

import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.actions.RefactoringActionContextUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;

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
    final ScrollingModel scrollingModel = editor.getScrollingModel();
    scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiMethod selectedMethod = getSelectedMethod(editor, file);
    if (selectedMethod == null) {
      final String message = RefactorJBundle.message("cannot.perform.the.refactoring") +
                             RefactorJBundle.message("the.caret.should.be.positioned.within.a.method.declaration.to.be.refactored");
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.IntroduceParameterObject);
      return;
    }
    invoke(project, selectedMethod, editor);
  }

  private static PsiMethod getSelectedMethod(Editor editor, PsiFile file) {
    final int caret = editor.getCaretModel().getOffset();
    final PsiElement elementAt = file.findElementAt(caret);
    return RefactoringActionContextUtil.getJavaMethodHeader(elementAt);
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
    invoke(project, method, editor);
  }

  private static void invoke(final Project project, final PsiMethod selectedMethod, Editor editor) {
    PsiMethod newMethod = SuperMethodWarningUtil.checkSuperMethod(selectedMethod);
    if (newMethod == null) return;
    final String message = getErrorMessage(newMethod);
    if (message != null) {
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.IntroduceParameterObject);
      return;
    }
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, newMethod)) return;

    new IntroduceParameterObjectDialog(newMethod).show();
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
