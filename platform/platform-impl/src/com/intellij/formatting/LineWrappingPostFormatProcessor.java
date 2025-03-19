// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

@ApiStatus.Internal
public final class LineWrappingPostFormatProcessor implements PostFormatProcessor {
  @Override
  public @NotNull PsiElement processElement(@NotNull PsiElement source, @NotNull CodeStyleSettings settings) {
    processText(source.getContainingFile(), source.getTextRange(), settings);
    return source;
  }

  @Override
  public @NotNull TextRange processText(@NotNull PsiFile source, @NotNull TextRange rangeToReformat, @NotNull CodeStyleSettings settings) {
    Document document = PsiDocumentManager.getInstance(source.getProject()).getDocument(source);
    if (document != null && settings.getCommonSettings(source.getLanguage()).WRAP_LONG_LINES &&
        !PostprocessReformattingAspect.getInstance(source.getProject()).isViewProviderLocked(source.getViewProvider())) {
      RangeMarker rangeMarker = document.createRangeMarker(rangeToReformat.getStartOffset(), rangeToReformat.getEndOffset());
      final int startOffset = rangeToReformat.getStartOffset();
      final int endOffset = rangeToReformat.getEndOffset();
      try {
        LineWrappingUtil.wrapLongLinesIfNecessary(source, document, startOffset, endOffset,
                                                  Collections.singletonList(new TextRange(startOffset, endOffset)),
                                                  settings.getRightMargin(source.getLanguage()));
        return rangeMarker.getTextRange();
      }
      finally {
        rangeMarker.dispose();
      }
    }
    return rangeToReformat;
  }
}
