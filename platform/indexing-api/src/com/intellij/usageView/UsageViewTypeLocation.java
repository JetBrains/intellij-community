// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.usageView;

import com.intellij.ide.TypePresentationService;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.psi.*;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiPresentableMetaData;
import com.intellij.util.indexing.IndexingBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public final class UsageViewTypeLocation extends ElementDescriptionLocation {
  private UsageViewTypeLocation() {
  }

  public static final UsageViewTypeLocation INSTANCE = new UsageViewTypeLocation();

  @NotNull
  @Override
  public ElementDescriptionProvider getDefaultProvider() {
    return DEFAULT_PROVIDER;
  }

  private static final ElementDescriptionProvider DEFAULT_PROVIDER = new ElementDescriptionProvider() {
    @Override
    public String getElementDescription(@NotNull final PsiElement psiElement, @NotNull final ElementDescriptionLocation location) {
      if (!(location instanceof UsageViewTypeLocation)) return null;

      if (psiElement instanceof PsiMetaOwner) {
        final PsiMetaData metaData = ((PsiMetaOwner)psiElement).getMetaData();
        if (metaData instanceof PsiPresentableMetaData) {
          return ((PsiPresentableMetaData)metaData).getTypeName();
        }
      }

      if (psiElement instanceof PsiFile) {
        return IndexingBundle.message("terms.file");
      }
      if (psiElement instanceof PsiDirectory) {
        return IndexingBundle.message("terms.directory");
      }

      String type = LanguageFindUsages.getType(psiElement);
      if (!type.isEmpty()) {
        return type;
      }
      return TypePresentationService.getService().getTypePresentableName(psiElement.getClass());
    }
  };
}