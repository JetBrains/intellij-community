// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.util;

import com.intellij.psi.*;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import org.jetbrains.annotations.NotNull;


class DefaultNonCodeSearchElementDescriptionProvider implements ElementDescriptionProvider {
  static final DefaultNonCodeSearchElementDescriptionProvider INSTANCE = new DefaultNonCodeSearchElementDescriptionProvider();

  @Override
  public String getElementDescription(@NotNull final PsiElement element, @NotNull final ElementDescriptionLocation location) {
    if (!(location instanceof NonCodeSearchDescriptionLocation ncdLocation)) return null;

    if (element instanceof PsiDirectory) {
      if (ncdLocation.isNonJava()) {
        final String qName = PsiDirectoryFactory.getInstance(element.getProject()).getQualifiedName((PsiDirectory)element, false);
        if (qName.length() > 0) return qName;
        return null;
      }
      return ((PsiDirectory) element).getName();
    }

    if (element instanceof PsiMetaOwner psiMetaOwner) {
      final PsiMetaData metaData = psiMetaOwner.getMetaData();
      if (metaData != null) {
        return metaData.getName();
      }
    }
    if (element instanceof PsiNamedElement) {
      return ((PsiNamedElement)element).getName();
    }
    return null;
  }
}
