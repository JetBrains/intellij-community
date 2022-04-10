// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.move.moveFilesOrDirectories;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.JavaDirectoryServiceImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class JavaMoveFilesOrDirectoriesHandler extends MoveFilesOrDirectoriesHandler {
  @Override
  public PsiElement adjustTargetForMove(DataContext dataContext, PsiElement targetContainer) {
    if (targetContainer instanceof PsiPackage) {
      final Module module = LangDataKeys.TARGET_MODULE.getData(dataContext);
      if (module != null) {
        final PsiDirectory[] directories = ((PsiPackage)targetContainer).getDirectories(GlobalSearchScope.moduleScope(module));
        if (directories.length == 1) {
          return directories[0];
        }
      }
    }
    return super.adjustTargetForMove(dataContext, targetContainer);
  }

  @Override
  public boolean canMove(PsiElement[] elements, PsiElement targetContainer, @Nullable PsiReference reference) {
    if (reference != null) return false;
    if (elements.length > 1) {
      elements = preprocess(elements);
    }
    return super.canMove(elements, targetContainer, null);
  }

  @Override
  public PsiElement[] adjustForMove(Project project, PsiElement[] sourceElements, PsiElement targetElement) {
    sourceElements = preprocess(sourceElements);
    return super.adjustForMove(project, sourceElements, targetElement);
  }

  private static PsiElement[] preprocess(PsiElement[] sourceElements) {
    Set<PsiElement> result = new LinkedHashSet<>();
    for (PsiElement sourceElement : sourceElements) {
      result.add(sourceElement instanceof PsiClass ? sourceElement.getContainingFile() : sourceElement);
    }
    return PsiUtilCore.toPsiElementArray(result);
  }

  @Override
  public void doMove(final Project project, PsiElement[] elements, PsiElement targetContainer, MoveCallback callback) {

    elements = super.adjustForMove(project, elements, targetContainer);
    if (elements == null) {
      return;
    }
    MoveFilesOrDirectoriesUtil
      .doMove(project, elements, new PsiElement[]{targetContainer}, callback,
              elements1 -> WriteCommandAction.writeCommandAction(project).withName(
                JavaRefactoringBundle.message("move.files.regrouping.command.name")).compute(() -> {
                final List<PsiElement> adjustedElements = new ArrayList<>();
                for (int i = 0, length = elements1.length; i < length; i++) {
                  PsiElement element = elements1[i];
                  if (element instanceof PsiClass) {
                    final PsiClass topLevelClass = PsiUtil.getTopLevelClass(element);
                    if (topLevelClass != null) {
                      elements1[i] = topLevelClass;
                      final PsiFile containingFile = obtainContainingFile(topLevelClass, elements1);
                      if (containingFile != null) {
                        adjustedElements.add(containingFile);
                        continue;
                      }
                    }
                  }
                  adjustedElements.add(element);
                }
                return PsiUtilCore.toPsiElementArray(adjustedElements);
              }));
  }

  @Nullable
  private static PsiFile obtainContainingFile(@NotNull PsiElement element, PsiElement[] elements) {
    final PsiFile containingFile = element.getContainingFile();
    final PsiClass[] classes = ((PsiClassOwner)containingFile).getClasses();
    final Set<PsiClass> nonMovedClasses = new HashSet<>();
    for (PsiClass aClass : classes) {
      if (ArrayUtilRt.find(elements, aClass) < 0) {
        nonMovedClasses.add(aClass);
      }
    }
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
