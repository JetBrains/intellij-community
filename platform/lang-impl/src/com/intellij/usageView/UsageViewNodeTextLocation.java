// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.usageView;

import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiPresentableMetaData;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public final class UsageViewNodeTextLocation extends ElementDescriptionLocation {
  private UsageViewNodeTextLocation() { }

  public static final UsageViewNodeTextLocation INSTANCE = new UsageViewNodeTextLocation();

  @NotNull
  @Override
  public ElementDescriptionProvider getDefaultProvider() {
    return DEFAULT_PROVIDER;
  }

  private static final ElementDescriptionProvider DEFAULT_PROVIDER = (element, location) -> {
    if (!(location instanceof UsageViewNodeTextLocation)) return null;

    if (element instanceof PsiMetaOwner) {
      final PsiMetaData metaData = ((PsiMetaOwner)element).getMetaData();
      if (metaData instanceof PsiPresentableMetaData) {
        return ((PsiPresentableMetaData)metaData).getTypeName() + " " + DescriptiveNameUtil.getMetaDataName(metaData);
      }
    }

    if (element instanceof PsiFile) {
      return ((PsiFile)element).getName();
    }

    return LanguageFindUsages.getNodeText(element, true);
  };
}