// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.service;

import com.intellij.formatting.FormattingRangesInfo;
import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.ExternalFormatProcessor;
import com.intellij.psi.impl.source.codeStyle.CoreCodeStyleUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * To support legacy API
 */
@ApiStatus.Internal
public final class ExternalFormatProcessorAdapter implements FormattingService {


  private static final Set<Feature> FEATURES = EnumSet.of(Feature.AD_HOC_FORMATTING,
                                                          Feature.FORMAT_FRAGMENTS);

  @Override
  public boolean canFormat(@NotNull PsiFile file) {
    return ExternalFormatProcessor.useExternalFormatter(file);
  }

  @Override
  public @NotNull Set<Feature> getFeatures() {
    return FEATURES;
  }

  @Override
  public @NotNull PsiElement formatElement(@NotNull PsiElement element, boolean canChangeWhiteSpacesOnly) {
    return ExternalFormatProcessor.formatElement(element, element.getTextRange(), canChangeWhiteSpacesOnly);
  }

  @Override
  public @NotNull PsiElement formatElement(@NotNull PsiElement element,
                                           @NotNull TextRange range,
                                           boolean canChangeWhiteSpacesOnly) {
    return ExternalFormatProcessor.formatElement(element, range, canChangeWhiteSpacesOnly);
  }

  @Override
  public void formatRanges(@NotNull PsiFile file, FormattingRangesInfo rangesInfo, boolean canChangeWhiteSpaceOnly, boolean quickFormat) {
    List<CoreCodeStyleUtil.RangeFormatInfo> infos = CoreCodeStyleUtil.getRangeFormatInfoList(file, rangesInfo);
    // IMPORTANT: Don't use canChangeWhiteSpaceOnly from parameters because we always want it to be 'false' for formatRanges called here.
    CoreCodeStyleUtil.postProcessRanges(
      file, infos, range -> ExternalFormatProcessor.formatRangeInFile(file, range, false, false));
  }

  @Override
  public @NotNull Set<ImportOptimizer> getImportOptimizers(@NotNull PsiFile file) {
    return Collections.emptySet();
  }
}
