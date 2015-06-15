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

/*
 * User: anna
 * Date: 27-Aug-2008
 */
package com.intellij.refactoring.inlineSuperClass;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.inline.JavaInlineActionHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;

import java.util.Collection;

public class InlineSuperClassRefactoringHandler extends JavaInlineActionHandler {
  public static final String REFACTORING_NAME = "Inline Super Class";

  @Override
  public boolean isEnabledOnElement(PsiElement element) {
    return element instanceof PsiClass;
  }

  public boolean canInlineElement(PsiElement element) {
    if (!(element instanceof PsiClass)) return false;
    if (element.getLanguage() != StdLanguages.JAVA) return false;
    Collection<PsiClass> inheritors = DirectClassInheritorsSearch.search((PsiClass)element).findAll();
    return inheritors.size() > 0;
  }

  public void inlineElement(final Project project, final Editor editor, final PsiElement element) {
    PsiClass superClass = (PsiClass) element;
    Collection<PsiClass> inheritors = DirectClassInheritorsSearch.search((PsiClass)element).findAll();
    if (!superClass.getManager().isInProject(superClass)) {
      CommonRefactoringUtil.showErrorHint(project, editor, "Cannot inline non-project class", REFACTORING_NAME, null);
      return;
    }

    for (PsiClass inheritor : inheritors) {
      if (PsiTreeUtil.isAncestor(superClass, inheritor, false)) {
        CommonRefactoringUtil.showErrorHint(project, editor, "Cannot inline into the inner class. Move \'" + inheritor.getName() + "\' to upper level", REFACTORING_NAME, null);
        return;
      }
      if (inheritor instanceof PsiAnonymousClass) {
        CommonRefactoringUtil.showErrorHint(project, editor, "Cannot inline into anonymous class.", REFACTORING_NAME, null);
        return;
      }
    }

    PsiClass chosen = null;
    PsiReference reference = editor != null ? TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset()) : null;
    if (reference != null) {
      final PsiElement resolve = reference.resolve();
      if (resolve == superClass) {
        final PsiElement referenceElement = reference.getElement();
        if (referenceElement != null) {
          final PsiElement parent = referenceElement.getParent();
          if (parent instanceof PsiReferenceList) {
            final PsiElement gParent = parent.getParent();
            if (gParent instanceof PsiClass && inheritors.contains(gParent)) {
              chosen = (PsiClass)gParent;
            }
          }
        }
      }
    }
    new InlineSuperClassRefactoringDialog(project, superClass, chosen, inheritors.toArray(new PsiClass[inheritors.size()])).show();
  }
}