// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.service;

import com.intellij.application.options.CodeStyle;
import com.intellij.formatting.FormatTextRanges;
import com.intellij.formatting.FormattingRangesInfo;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ImportOptimizer;
import com.intellij.lang.LanguageImportStatements;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade;
import com.intellij.psi.impl.source.codeStyle.CodeFormattingData;
import com.intellij.psi.impl.source.codeStyle.CoreCodeStyleUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class CoreFormattingService implements FormattingService {

  private static final Set<Feature> FEATURES = EnumSet.of(Feature.AD_HOC_FORMATTING,
                                                          Feature.FORMAT_FRAGMENTS,
                                                          Feature.OPTIMIZE_IMPORTS);

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
    return CoreCodeStyleUtil.postProcessElement(file, formatted, canChangeWhiteSpacesOnly);
  }

  @Override
  public @NotNull PsiElement formatElement(@NotNull PsiElement element,
                                           @NotNull TextRange range,
                                           boolean canChangeWhiteSpacesOnly) {
    ASTNode treeElement = element.getNode();
    PsiFile file = element.getContainingFile();
    final CodeFormatterFacade codeFormatter = new CodeFormatterFacade(getSettings(file), element.getLanguage());
    final PsiElement formatted = codeFormatter.processRange(treeElement, range.getStartOffset(), range.getEndOffset()).getPsi();
    return CoreCodeStyleUtil.postProcessElement(file, formatted, canChangeWhiteSpacesOnly);
  }

  public void asyncFormatElement(@NotNull PsiElement element, @NotNull TextRange range, boolean canChangeWhitespaceOnly) {
    if (ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isHeadlessEnvironment()) {
      formatElement(element, range, canChangeWhitespaceOnly);
    }
    PsiFile file = element.getContainingFile();
    Project project = file.getProject();
    ReadAction
      .nonBlocking(
        () -> CodeFormattingData.prepare(file, Collections.singletonList(range)))
      .expireWhen(() -> project.isDisposed() || !file.isValid())
      .finishOnUiThread(ModalityState.nonModal(), data -> {
        CommandProcessor.getInstance().runUndoTransparentAction(() -> {
          WriteAction.run(() -> {
            formatElement(element, range, canChangeWhitespaceOnly);
          });
        });
      })
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  @Override
  public void formatRanges(@NotNull PsiFile file, FormattingRangesInfo rangesInfo, boolean canChangeWhiteSpaceOnly, boolean quickFormat) {
    List<CoreCodeStyleUtil.RangeFormatInfo> infos = CoreCodeStyleUtil.getRangeFormatInfoList(file, rangesInfo);
    final CodeFormatterFacade codeFormatter = new CodeFormatterFacade(getSettings(file), file.getLanguage());
    codeFormatter.processText(file, (FormatTextRanges)rangesInfo, !canChangeWhiteSpaceOnly);
    CoreCodeStyleUtil.postProcessRanges(infos, range -> CoreCodeStyleUtil.postProcessText(file, range, canChangeWhiteSpaceOnly));
  }

  @Override
  public @NotNull Set<ImportOptimizer> getImportOptimizers(@NotNull PsiFile file) {
    return LanguageImportStatements.INSTANCE.forFile(file);
  }

  private static CodeStyleSettings getSettings(@NotNull PsiFile file) {
    return CodeStyle.getSettings(file);
  }
}
