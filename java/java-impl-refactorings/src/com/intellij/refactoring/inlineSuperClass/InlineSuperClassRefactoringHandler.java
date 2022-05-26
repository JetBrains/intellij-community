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

package com.intellij.refactoring.inlineSuperClass;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.refactoring.inline.JavaInlineActionHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.Nullable;

public class InlineSuperClassRefactoringHandler extends JavaInlineActionHandler {

  @Override
  public boolean isEnabledOnElement(PsiElement element) {
    return element instanceof PsiClass;
  }

  @Override
  public boolean canInlineElement(PsiElement element) {
    if (!(element instanceof PsiClass)) return false;
    if (element.getLanguage() != JavaLanguage.INSTANCE) return false;
    return true;
  }

  @Override
  public void inlineElement(final Project project, final Editor editor, final PsiElement element) {
    if (DirectClassInheritorsSearch.search((PsiClass)element).findFirst() == null) {
      CommonRefactoringUtil.showErrorHint(project, editor, JavaRefactoringBundle.message("inline.super.no.inheritors.warning.message"),
                                          JavaRefactoringBundle.message("inline.super.class"), null);
      return;
    }
    PsiClass superClass = (PsiClass) element;
    if (!superClass.getManager().isInProject(superClass)) {
      CommonRefactoringUtil.showErrorHint(project, editor, JavaRefactoringBundle.message("inline.super.non.project.class.warning.message"),
                                          JavaRefactoringBundle.message("inline.super.class"), null);
      return;
    }

    PsiClass chosen = null;
    PsiReference reference = editor != null ? TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset()) : null;
    if (reference != null) {
      final PsiElement resolve = reference.resolve();
      if (resolve == superClass) {
        final PsiElement referenceElement = reference.getElement();
        final PsiElement parent = referenceElement.getParent();
        if (parent instanceof PsiReferenceList) {
          final PsiElement gParent = parent.getParent();
          if (gParent instanceof PsiClass) {
            chosen = (PsiClass)gParent;
          }
        }
      }
    }
    new InlineSuperClassRefactoringDialog(project, superClass, chosen).show();
  }

  @Nullable
  @Override
  public String getActionName(PsiElement element) {
    return JavaRefactoringBundle.message("inline.super.class.action.name");
  }
}