// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages;

import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

public final class DefaultFindUsagesHandlerFactory extends FindUsagesHandlerFactory {

  @Internal
  public static final class DefaultFindUsagesHandler extends FindUsagesHandler {
    DefaultFindUsagesHandler(@NotNull PsiElement psiElement) {
      super(psiElement);
    }
  }

  @Override
  public boolean canFindUsages(final @NotNull PsiElement element) {
    if (!element.isValid()) {
      return false;
    }
    if (element instanceof PsiFileSystemItem) {
      return ((PsiFileSystemItem)element).getVirtualFile() != null;
    }
    return LanguageFindUsages.canFindUsagesFor(element);
  }

  @Override
  public FindUsagesHandler createFindUsagesHandler(final @NotNull PsiElement element, final boolean forHighlightUsages) {
    return new DefaultFindUsagesHandler(element);
  }
}
