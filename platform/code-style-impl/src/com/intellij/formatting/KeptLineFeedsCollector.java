// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.util.Segment;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiFileRange;
import com.intellij.psi.codeStyle.CodeStyleSettingsService;
import com.intellij.psi.codeStyle.LanguageCodeStyleProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class KeptLineFeedsCollector {

  private static final ThreadLocal<KeptLineFeedsCollector> THREAD_LOCAL = new ThreadLocal<>();
  private final PsiFile myPsiFile;
  private final List<SmartPsiFileRange> myBreaks = new ArrayList<>();

  private KeptLineFeedsCollector(@NotNull PsiFile psiFile) {
    myPsiFile = psiFile;
  }

  public static void setup(@NotNull PsiFile psiFile) {
    LanguageCodeStyleProvider provider = CodeStyleSettingsService.getLanguageCodeStyleProvider(psiFile.getLanguage());
    if (provider != null && provider.usesCommonKeepLineBreaks() && CodeStyle.getLanguageSettings(psiFile).KEEP_LINE_BREAKS) {
      THREAD_LOCAL.set(new KeptLineFeedsCollector(psiFile));
    }
  }

  public static List<Segment> getLineFeedsAndCleanup() {
    KeptLineFeedsCollector collector = THREAD_LOCAL.get();
    if (collector == null) return Collections.emptyList();
    THREAD_LOCAL.remove();
    List<Segment> segments = ContainerUtil.mapNotNull(collector.myBreaks, range -> range.getRange());
    for (SmartPsiFileRange psiFileRange : collector.myBreaks) {
      SmartPointerManager.getInstance(collector.myPsiFile.getProject()).removePointer(psiFileRange);
    }
    return segments;
  }

  public static void registerLineFeed(WhiteSpace whiteSpace) {
    KeptLineFeedsCollector collector = THREAD_LOCAL.get();
    if (collector == null) return;
    SmartPsiFileRange pointer =
      SmartPointerManager.getInstance(collector.myPsiFile.getProject()).createSmartPsiFileRangePointer(collector.myPsiFile, whiteSpace.getTextRange());
    collector.myBreaks.add(pointer);
  }
}
