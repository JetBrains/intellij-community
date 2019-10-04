// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.findUsages;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The default empty implementation of the {@link FindUsagesProvider} interface.
 * @author max
 */
public class EmptyFindUsagesProvider implements FindUsagesProvider {

  @Override
  public boolean canFindUsagesFor(@NotNull PsiElement psiElement) {
    return false;
  }

  @Override
  @Nullable
  public String getHelpId(@NotNull PsiElement psiElement) {
    return null;
  }

  @Override
  @NotNull
  public String getType(@NotNull PsiElement element) {
    return "";
  }

  @Override
  @NotNull
  public String getDescriptiveName(@NotNull PsiElement element) {
    return getNodeText(element, true);
  }

  @Override
  @NotNull
  public String getNodeText(@NotNull PsiElement element, boolean useFullName) {
    if (element instanceof PsiNamedElement) {
      final String name = ((PsiNamedElement)element).getName();
      if (name != null) {
        return name;
      }
    }
    return "";
  }
}
