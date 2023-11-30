// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util;

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.Nullable;

public final class PsiNavigateUtil {
  public static void navigate(final @Nullable PsiElement psiElement) {
    navigate(psiElement, true);
  }

  public static void navigate(final @Nullable PsiElement psiElement, boolean requestFocus) {
    if (psiElement != null && psiElement.isValid()) {
      final PsiElement navigationElement = psiElement.getNavigationElement();
      final int offset = navigationElement instanceof PsiFile ? -1 : navigationElement.getTextOffset();

      VirtualFile virtualFile = PsiUtilCore.getVirtualFile(psiElement);
      Navigatable navigatable;
      if (virtualFile != null && virtualFile.isValid()) {
        navigatable = PsiNavigationSupport.getInstance().createNavigatable(navigationElement.getProject(), virtualFile, offset);
      }
      else if (navigationElement instanceof Navigatable) {
        navigatable = (Navigatable)navigationElement;
      }
      else {
        return;
      }
      navigatable.navigate(requestFocus);
    }
  }
}