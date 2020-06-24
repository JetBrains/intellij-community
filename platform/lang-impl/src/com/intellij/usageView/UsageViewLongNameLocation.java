// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.usageView;

import com.intellij.psi.*;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public final class UsageViewLongNameLocation extends ElementDescriptionLocation {
  private UsageViewLongNameLocation() {
  }

  public static final UsageViewLongNameLocation INSTANCE = new UsageViewLongNameLocation();

  @NotNull
  @Override
  public ElementDescriptionProvider getDefaultProvider() {
    return DEFAULT_PROVIDER;
  }

  private static final ElementDescriptionProvider DEFAULT_PROVIDER = new ElementDescriptionProvider() {
    @Override
    public String getElementDescription(@NotNull final PsiElement element, @NotNull final ElementDescriptionLocation location) {
      if (location instanceof UsageViewLongNameLocation) {
        if (element instanceof PsiDirectory) {
          return PsiDirectoryFactory.getInstance(element.getProject()).getQualifiedName((PsiDirectory)element, true);
        }
        if (element instanceof PsiQualifiedNamedElement) {
          return ((PsiQualifiedNamedElement)element).getQualifiedName();
        }
        return UsageViewShortNameLocation.INSTANCE.getDefaultProvider().getElementDescription(
          element, UsageViewShortNameLocation.INSTANCE);
      }
      return null;
    }
  };
}
