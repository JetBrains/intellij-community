// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.findUsages;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

public final class DescriptiveNameUtil {

  private DescriptiveNameUtil() {
  }

  public static @NotNull @NlsSafe String getMetaDataName(@NotNull PsiMetaData metaData) {
    String name = metaData.getName();
    return StringUtil.isEmpty(name) ? "''" : name;
  }

  public static @NotNull @NlsSafe String getDescriptiveName(@NotNull PsiElement psiElement) {
    PsiUtilCore.ensureValid(psiElement);

    if (psiElement instanceof PsiMetaOwner psiMetaOwner) {
      PsiMetaData metaData = psiMetaOwner.getMetaData();
      if (metaData != null) return getMetaDataName(metaData);
    }

    if (psiElement instanceof PsiFile) {
      return ((PsiFile)psiElement).getName();
    }

    return LanguageFindUsages.getDescriptiveName(psiElement);
  }
}
