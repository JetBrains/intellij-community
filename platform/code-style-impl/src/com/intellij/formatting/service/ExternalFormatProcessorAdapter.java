// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.service;

import com.intellij.formatting.FormatTextRanges;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.ExternalFormatProcessor;
import com.intellij.psi.impl.source.codeStyle.CoreCodeStyleUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * To support legacy API
 */
@ApiStatus.Internal
public final class ExternalFormatProcessorAdapter implements FormattingService {
  @Override
  public boolean canFormat(@NotNull PsiFile file) {
    return ExternalFormatProcessor.useExternalFormatter(file);
  }

  public void formatCollectedRanges(@NotNull PsiFile file, @NotNull FormatTextRanges ranges) {
    CoreCodeStyleUtil.formatRanges(file, ranges);
  }
}
