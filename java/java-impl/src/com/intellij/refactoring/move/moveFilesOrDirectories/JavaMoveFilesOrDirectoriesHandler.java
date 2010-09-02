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

import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.JavaDirectoryServiceImpl;
import com.intellij.refactoring.copy.JavaCopyFilesOrDirectoriesHandler;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
  public void doMove(final Project project, PsiElement[] elements, PsiElement targetContainer, MoveCallback callback) {

    MoveFilesOrDirectoriesUtil
      .doMove(project, elements, new PsiElement[]{targetContainer}, callback, new Function<PsiElement[], PsiElement[]>() {
        @Override
        public PsiElement[] fun(final PsiElement[] elements) {
          return new WriteCommandAction<PsiElement[]>(project, "Regrouping ...") {
            @Override
            protected void run(Result<PsiElement[]> result) throws Throwable {
              final List<PsiElement> adjustedElements = new ArrayList<PsiElement>();
              for (PsiElement element : elements) {
                if (element instanceof PsiClass) {
                  final PsiFile containingFile = obtainContainingFile(element, elements);
                  if (containingFile != null && !adjustedElements.contains(containingFile)) {
                    adjustedElements.add(containingFile);
                  }
                }
                else {
                  adjustedElements.add(element);
                }
              }
              result.setResult(adjustedElements.toArray(new PsiElement[adjustedElements.size()]));
            }
          }.execute().getResultObject();
        }
      });
  }

  @Nullable
  private static PsiFile obtainContainingFile(PsiElement element, PsiElement[] elements) {
    final PsiClass[] classes = ((PsiClassOwner)element.getParent()).getClasses();
    final Set<PsiClass> nonMovedClasses = new HashSet<PsiClass>();
    for (PsiClass aClass : classes) {
      if (ArrayUtil.find(elements, aClass) < 0) {
        nonMovedClasses.add(aClass);
      }
    }
    final PsiFile containingFile = element.getContainingFile();
    if (nonMovedClasses.isEmpty()) {
      return containingFile;
    }
    else {
      final PsiDirectory containingDirectory = containingFile.getContainingDirectory();
      if (containingDirectory != null) {
        try {
          JavaDirectoryServiceImpl.checkCreateClassOrInterface(containingDirectory, ((PsiClass)element).getName());
          final PsiElement createdClass = containingDirectory.add(element);
          element.delete();
          return createdClass.getContainingFile();
        }
        catch (IncorrectOperationException e) {
          final Iterator<PsiClass> iterator = nonMovedClasses.iterator();
          final PsiClass nonMovedClass = iterator.next();
          final PsiElement createdFile = containingDirectory.add(nonMovedClass).getContainingFile();
          nonMovedClass.delete();
          while (iterator.hasNext()) {
            final PsiClass currentClass = iterator.next();
            createdFile.add(currentClass);
            currentClass.delete();
          }
          return containingFile;
        }
      }
    }
    return null;
  }
}
