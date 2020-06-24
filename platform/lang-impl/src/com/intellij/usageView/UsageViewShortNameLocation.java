// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.usageView;

import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public final class UsageViewShortNameLocation extends ElementDescriptionLocation {
  private UsageViewShortNameLocation() {
  }

  public static final UsageViewShortNameLocation INSTANCE = new UsageViewShortNameLocation();

  @NotNull
  @Override
  public ElementDescriptionProvider getDefaultProvider() {
    return DEFAULT_PROVIDER;
  }

  private static final ElementDescriptionProvider DEFAULT_PROVIDER = new ElementDescriptionProvider() {
    @Override
    public String getElementDescription(@NotNull final PsiElement element, @NotNull final ElementDescriptionLocation location) {
      if (!(location instanceof UsageViewShortNameLocation)) return null;

      if (element instanceof PsiMetaOwner) {
        PsiMetaData metaData = ((PsiMetaOwner)element).getMetaData();
        if (metaData!=null) return DescriptiveNameUtil.getMetaDataName(metaData);
      }

      if (element instanceof PsiNamedElement) {
        return ((PsiNamedElement)element).getName();
      }
      return "";
    }
  };
}
