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
package com.intellij.refactoring.removemiddleman;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class RemoveMiddlemanHandler implements RefactoringActionHandler {
  @NonNls static final String REMOVE_METHODS = "refactoring.removemiddleman.remove.methods";

  protected static String getRefactoringName() {
    return getRefactoringNameText();
  }

  protected static String getHelpID() {
    return HelpID.RemoveMiddleman;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    final ScrollingModel scrollingModel = editor.getScrollingModel();
    scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE);
    final PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    if (!(element instanceof PsiField)) {
      CommonRefactoringUtil.showErrorHint(project, editor, RefactorJBundle.message("cannot.perform.the.refactoring") + RefactorJBundle.message(
          "the.caret.should.be.positioned.at.the.name.of.the.field.to.be.refactored"), getRefactoringNameText(), getHelpID());
      return;
    }
    invoke((PsiField)element, editor);
  }

  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    if (elements.length != 1) {
      return;
    }
    if (elements[0] instanceof PsiField) {
      Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
      invoke((PsiField)elements[0], editor);
    }
  }

  private static void invoke(final PsiField field, Editor editor) {
    final Project project = field.getProject();
    final Set<PsiMethod> delegating = DelegationUtils.getDelegatingMethodsForField(field);
    if (delegating.isEmpty()) {
      final String message =
        RefactorJBundle.message("cannot.perform.the.refactoring") + RefactorJBundle.message("field.selected.is.not.used.as.a.delegate");
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringNameText(), getHelpID());
      return;
    }

    MemberInfo[] infos = new MemberInfo[delegating.size()];
    int i = 0;
    for (PsiMethod method : delegating) {
      final MemberInfo memberInfo = new MemberInfo(method);
      memberInfo.setChecked(true);
      memberInfo.setToAbstract(method.findDeepestSuperMethods().length == 0);
      infos[i++] = memberInfo;
    }
    new RemoveMiddlemanDialog(field, infos).show();
  }

  private static String getRefactoringNameText() {
    return RefactorJBundle.message("remove.middleman");
  }
}
