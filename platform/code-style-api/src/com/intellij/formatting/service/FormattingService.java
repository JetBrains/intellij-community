// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.service;

import com.intellij.formatting.FormattingRangesInfo;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@ApiStatus.Experimental
public interface FormattingService {
  ExtensionPointName<FormattingService> EP_NAME = ExtensionPointName.create("com.intellij.formattingService");

  /**
   * A feature supported by the service.
   */
  enum Feature {
    /**
     * The service can provide fast formatting of multiple collected ranges, for example, upon refactoring. It must also support
     * {@link #FORMAT_FRAGMENTS} feature.
     */
    AD_HOC_FORMATTING,
    /**
     * The service can format multiple text ranges within the same document as opposed to complete file only.
     */
    FORMAT_FRAGMENTS
  }

  Set<Feature> getFeatures();

  boolean canFormat(@NotNull PsiFile file);

  @NotNull
  PsiElement formatElement(@NotNull PsiElement element, boolean canChangeWhiteSpaceOnly);

  @NotNull
  PsiElement formatElement(@NotNull PsiElement element, @NotNull TextRange range, boolean canChangeWhiteSpaceOnly);

  void formatRanges(@NotNull PsiFile file, FormattingRangesInfo rangesInfo, boolean canChangeWhiteSpaceOnly);
}
