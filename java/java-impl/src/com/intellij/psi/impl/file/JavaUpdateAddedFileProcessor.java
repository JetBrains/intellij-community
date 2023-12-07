// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file;

import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.psi.*;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
 */
public class JavaUpdateAddedFileProcessor extends UpdateAddedFileProcessor {
  @Override
  public boolean canProcessElement(@NotNull final PsiFile file) {
    return file instanceof PsiClassOwner && !JavaProjectRootsUtil.isOutsideJavaSourceRoot(file);
  }

  @Override
  public void update(final PsiFile element, PsiFile originalElement) throws IncorrectOperationException {
    if (element.getViewProvider() instanceof TemplateLanguageFileViewProvider || PsiUtil.isModuleFile(element)) {
      return;
    }

    PsiDirectory dir = element.getContainingDirectory();
    if (dir != null) {
      PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(dir);
      if (aPackage != null && isNotImplicitClass(element)) {
        String packageName = aPackage.getQualifiedName();
        ((PsiClassOwner)element).setPackageName(packageName);
      }
    }
  }

  private static boolean isNotImplicitClass(@NotNull PsiFile file) {
    if (file instanceof PsiJavaFile javaFile) {
      PsiClass[] classes = javaFile.getClasses();
      if (ContainerUtil.or(classes, cl -> cl instanceof PsiImplicitClass)) {
        return false;
      }
    }
    return true;
  }
}