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

import com.intellij.refactoring.move.MoveHandlerDelegate;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.openapi.project.Project;

import java.util.Arrays;

public abstract class MoveClassesOrPackagesHandlerBase extends MoveHandlerDelegate {
  protected static boolean isPackageOrDirectory(final PsiElement element) {
    if (element instanceof PsiPackage) return true;
    return element instanceof PsiDirectory && JavaDirectoryService.getInstance().getPackage((PsiDirectory)element) != null;
  }

  public PsiElement[] adjustForMove(final Project project, final PsiElement[] sourceElements, final PsiElement targetElement) {
    return MoveClassesOrPackagesImpl.adjustForMove(project,sourceElements, targetElement);
  }

  public void doMove(final Project project, final PsiElement[] elements, final PsiElement targetContainer, final MoveCallback callback) {
    if (tryDirectoryMove ( project, elements, targetContainer, callback)) {
      return;
    }
    MoveClassesOrPackagesImpl.doMove(project, elements, targetContainer, callback);
  }

  private static boolean tryDirectoryMove(Project project, final PsiElement[] sourceElements, final PsiElement targetElement, final MoveCallback callback) {
    if (targetElement instanceof PsiDirectory) {
      final PsiElement[] adjustedElements = MoveClassesOrPackagesImpl.adjustForMove(project, sourceElements, targetElement);
      if (adjustedElements != null) {
        if ( CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, Arrays.asList(adjustedElements),true) ) {
          new MoveClassesOrPackagesToNewDirectoryDialog((PsiDirectory)targetElement, adjustedElements, callback).show();
        }
      }
      return true;
    }
    return false;
  }
}
