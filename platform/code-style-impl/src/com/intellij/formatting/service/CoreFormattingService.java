// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.service;

import com.intellij.application.options.CodeStyle;
import com.intellij.formatting.FormatTextRanges;
import com.intellij.formatting.FormattingRangesInfo;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade;
import com.intellij.psi.impl.source.codeStyle.CoreCodeStyleUtil;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class CoreFormattingService implements FormattingService {

  private static final Set<Feature> FEATURES = EnumSet.of(Feature.AD_HOC_FORMATTING,
                                                          Feature.FORMAT_FRAGMENTS);

  @Override
  public boolean canFormat(@NotNull PsiFile file) {
    return true;
  }

  @Override
  public @NotNull Set<Feature> getFeatures() {
    return FEATURES;
  }

  @Override
  public @NotNull PsiElement formatElement(@NotNull PsiElement element, boolean canChangeWhiteSpacesOnly) {
    ASTNode treeElement = element.getNode();
    PsiFile file = element.getContainingFile();
    final PsiElement formatted =
      new CodeFormatterFacade(getSettings(file), element.getLanguage(), canChangeWhiteSpacesOnly)
        .processElement(treeElement).getPsi();
    if (!canChangeWhiteSpacesOnly) {
      return CoreCodeStyleUtil.postProcessElement(file, formatted);
    }
    return formatted;
  }

  @Override
  public @NotNull PsiElement formatElement(@NotNull PsiElement element,
                                           @NotNull TextRange range,
                                           boolean canChangeWhiteSpacesOnly) {
    ASTNode treeElement = element.getNode();
    PsiFile file = element.getContainingFile();
    final CodeFormatterFacade codeFormatter = new CodeFormatterFacade(getSettings(file), element.getLanguage());
    final PsiElement formatted = codeFormatter.processRange(treeElement, range.getStartOffset(), range.getEndOffset()).getPsi();
    return canChangeWhiteSpacesOnly ? formatted : CoreCodeStyleUtil.postProcessElement(file, formatted);
  }

  @Override
  public void formatRanges(@NotNull PsiFile file, FormattingRangesInfo rangesInfo, boolean canChangeWhiteSpaceOnly, boolean quickFormat) {
    List<CoreCodeStyleUtil.RangeFormatInfo> infos =
      canChangeWhiteSpaceOnly ? null : CoreCodeStyleUtil.getRangeFormatInfoList(file, rangesInfo);
    final CodeFormatterFacade codeFormatter = new CodeFormatterFacade(getSettings(file), file.getLanguage());
    codeFormatter.processText(file, (FormatTextRanges)rangesInfo, !canChangeWhiteSpaceOnly);
    if (infos != null) {
      CoreCodeStyleUtil.postProcessRanges(file, infos, range -> CoreCodeStyleUtil.postProcessText(file, range));
    }
  }

  private static CodeStyleSettings getSettings(@NotNull PsiFile file) {
    return CodeStyle.getSettings(file);
  }
}
