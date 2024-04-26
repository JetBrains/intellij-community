// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.smartPointers;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dennis.Ushakov
 */
public final class JavaAnchorProvider extends SmartPointerAnchorProvider {
  @Override
  public PsiElement getAnchor(@NotNull PsiElement element) {
    if (!element.getLanguage().isKindOf(JavaLanguage.INSTANCE) || !element.isPhysical()) {
      return null;
    }

    if (element instanceof PsiAnonymousClass) {
      return ((PsiAnonymousClass)element).getBaseClassReference().getReferenceNameElement();
    }
    if (element instanceof PsiClass || element instanceof PsiMethod || 
        (element instanceof PsiVariable && !(element instanceof PsiLocalVariable))) {
      return ((PsiNameIdentifierOwner)element).getNameIdentifier();
    }
    if (element instanceof PsiImportList) {
      return element.getContainingFile();
    }
    return null;
  }

  @Override
  public @Nullable PsiElement restoreElement(@NotNull PsiElement anchor) {
    if (anchor instanceof PsiIdentifier) {
      PsiElement parent = anchor.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement) { // anonymous class, type
        parent = parent.getParent();
      }

      return parent;
    }
    if (anchor instanceof PsiJavaFile) {
      return ((PsiJavaFile)anchor).getImportList();
    }
    return null;
  }
}
