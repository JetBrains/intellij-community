// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.service;

import com.intellij.formatting.FormattingRangesInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.ExternalFormatProcessor;
import com.intellij.psi.impl.source.codeStyle.CoreCodeStyleUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * To support legacy API
 */
@ApiStatus.Internal
public final class ExternalFormatProcessorAdapter implements FormattingService {
  @Override
  public boolean canFormat(@NotNull PsiFile file, boolean isExplicit) {
    return ExternalFormatProcessor.useExternalFormatter(file);
  }

  @Override
  public PsiElement formatElement(@NotNull PsiElement element, boolean canChangeWhiteSpacesOnly) {
    return ExternalFormatProcessor.formatElement(element, element.getTextRange(), canChangeWhiteSpacesOnly);
  }

  @Override
  public PsiElement formatElement(@NotNull PsiElement element,
                                  @NotNull TextRange range,
                                  boolean canChangeWhiteSpacesOnly) {
    return ExternalFormatProcessor.formatElement(element, range, canChangeWhiteSpacesOnly);
  }

  @Override
  public void formatRanges(@NotNull PsiFile file, FormattingRangesInfo rangesInfo, boolean canChangeWhiteSpaceOnly) {
    List<CoreCodeStyleUtil.RangeFormatInfo> infos = CoreCodeStyleUtil.getRangeFormatInfoList(file, rangesInfo);
    CoreCodeStyleUtil.postProcessRanges(
      file, infos, range -> ExternalFormatProcessor.formatRangeInFile(file, range, false, false));
  }
}
