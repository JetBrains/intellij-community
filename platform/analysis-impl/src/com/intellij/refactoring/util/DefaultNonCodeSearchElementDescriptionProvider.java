// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util;

import com.intellij.psi.*;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import org.jetbrains.annotations.NotNull;


class DefaultNonCodeSearchElementDescriptionProvider implements ElementDescriptionProvider {
  static final DefaultNonCodeSearchElementDescriptionProvider INSTANCE = new DefaultNonCodeSearchElementDescriptionProvider();

  @Override
  public String getElementDescription(final @NotNull PsiElement element, final @NotNull ElementDescriptionLocation location) {
    if (!(location instanceof NonCodeSearchDescriptionLocation ncdLocation)) return null;

    if (element instanceof PsiDirectory psiDirectory) {
      if (ncdLocation.isNonJava()) {
        final String qName = PsiDirectoryFactory.getInstance(element.getProject()).getQualifiedName(psiDirectory, false);
        if (!qName.isEmpty()) return qName;
        return null;
      }
      return psiDirectory.getName();
    }

    if (element instanceof PsiMetaOwner psiMetaOwner) {
      final PsiMetaData metaData = psiMetaOwner.getMetaData();
      if (metaData != null) {
        return metaData.getName();
      }
    }
    if (element instanceof PsiNamedElement namedElement) {
      return namedElement.getName();
    }
    return null;
  }
}
