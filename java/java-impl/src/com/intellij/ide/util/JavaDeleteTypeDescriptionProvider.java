// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.java.JavaBundle;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;


public final class JavaDeleteTypeDescriptionProvider implements ElementDescriptionProvider {
  @Override
  public String getElementDescription(@NotNull final PsiElement element, @NotNull final ElementDescriptionLocation location) {
    if (location instanceof DeleteTypeDescriptionLocation && ((DeleteTypeDescriptionLocation) location).isPlural()) {
      if (element instanceof PsiMethod) {
        return JavaBundle.message("prompt.delete.method", 2);
      }
      else if (element instanceof PsiField) {
        return JavaBundle.message("prompt.delete.field", 2);
      }
      else if (element instanceof PsiClass) {
        if (((PsiClass)element).isInterface()) {
          return JavaBundle.message("prompt.delete.interface", 2);
        }
        return element instanceof PsiTypeParameter
               ? JavaBundle.message("prompt.delete.type.parameter", 2)
               : JavaBundle.message("prompt.delete.class", 2);
      }
      else if (element instanceof PsiPackage) {
        return JavaBundle.message("prompt.delete.package", 2);
      }
    }
    return null;
  }
}
