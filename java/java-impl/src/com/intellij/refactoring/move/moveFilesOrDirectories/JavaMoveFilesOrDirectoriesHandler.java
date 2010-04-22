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
package com.intellij.refactoring.move.moveFilesOrDirectories;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.copy.JavaCopyFilesOrDirectoriesHandler;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil;

public class JavaMoveFilesOrDirectoriesHandler extends MoveFilesOrDirectoriesHandler {
  @Override
  public boolean canMove(PsiElement[] elements, PsiElement targetContainer) {
    final PsiElement[] srcElements = adjustForMove(null, elements, targetContainer);
    assert srcElements != null;

    boolean allJava = true;
    for (PsiElement element : srcElements) {
      if (element instanceof PsiDirectory) {
        allJava &= JavaCopyFilesOrDirectoriesHandler.hasPackages((PsiDirectory)element);
      }
      else if (element instanceof PsiFile) {
        allJava &= element instanceof PsiJavaFile && !JspPsiUtil.isInJspFile(element) &&
                   ((PsiJavaFile)element).getClasses().length > 0 && !CollectHighlightsUtil.isOutsideSourceRootJavaFile((PsiJavaFile) element);
      }
      else {
        return false;
      }
    }
    if (allJava) return false;

    return super.canMove(srcElements, targetContainer);
  }

  @Override
  public PsiElement[] adjustForMove(Project project, PsiElement[] sourceElements, PsiElement targetElement) {
    PsiElement[] result = new PsiElement[sourceElements.length];
    for(int i = 0; i < sourceElements.length; i++) {
      result[i] = sourceElements[i] instanceof PsiClass ? sourceElements[i].getContainingFile() : sourceElements[i];
    }
    return result;
  }

  @Override
  public void doMove(Project project, PsiElement[] elements, PsiElement targetContainer, MoveCallback callback) {
    super.doMove(project, elements, targetContainer, callback);
  }
}
