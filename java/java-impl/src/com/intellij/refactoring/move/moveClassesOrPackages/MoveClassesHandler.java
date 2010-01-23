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
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import org.jetbrains.annotations.Nullable;

public class MoveClassesHandler extends MoveClassesOrPackagesHandlerBase {
  public boolean canMove(final PsiElement[] elements, @Nullable final PsiElement targetContainer) {
    for(PsiElement element: elements) {
      PsiFile parentFile;
      if (element instanceof PsiJavaFile) {
        final PsiClass[] classes = ((PsiJavaFile)element).getClasses();
        if (classes.length == 0) return false;
        for (PsiClass aClass : classes) {
          if (aClass instanceof JspClass) return false;
        }
        parentFile = (PsiFile)element;
      } else {
        if (element instanceof JspClass) return false;
        if (!(element instanceof PsiClass)) return false;
        if (!(element.getParent() instanceof PsiFile)) return false;
        parentFile = (PsiFile)element.getParent();
      }
      if (CollectHighlightsUtil.isOutsideSourceRootJavaFile(parentFile)) return false;
    }
    return super.canMove(elements, targetContainer);
  }

  public boolean isValidTarget(final PsiElement psiElement) {
    return psiElement instanceof PsiClass ||
           MovePackagesHandler.isPackageOrDirectory(psiElement);
  }

  public boolean tryToMove(final PsiElement element, final Project project, final DataContext dataContext, final PsiReference reference,
                           final Editor editor) {
    if (CollectHighlightsUtil.isOutsideSourceRootJavaFile(element.getContainingFile())) return false;
    if (isReferenceInAnonymousClass(reference)) return false;

    if (element instanceof PsiClass && !(element instanceof PsiAnonymousClass) && element.getParent() instanceof PsiFile) {
      MoveClassesOrPackagesImpl.doMove(project, new PsiElement[]{element},
                                       LangDataKeys.TARGET_PSI_ELEMENT.getData(dataContext), null);
      return true;
    }
    return false;
  }

  public static boolean isReferenceInAnonymousClass(@Nullable final PsiReference reference) {
    if (reference instanceof PsiJavaCodeReferenceElement &&
       ((PsiJavaCodeReferenceElement)reference).getParent() instanceof PsiAnonymousClass) {
      return true;
    }
    return false;
  }
}
