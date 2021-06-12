// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.service;

import com.intellij.application.options.CodeStyle;
import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.FormattingMode;
import com.intellij.formatting.FormattingRangesInfo;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Base class for synchronous document formatting services.
 */
@ApiStatus.Experimental
public abstract class AbstractDocumentFormattingService implements FormattingService {

  @Override
  public final @NotNull PsiElement formatElement(@NotNull PsiElement element, boolean canChangeWhiteSpaceOnly) {
    return formatElement(element, element.getTextRange(), canChangeWhiteSpaceOnly);
  }

  @Override
  public final @NotNull PsiElement formatElement(@NotNull PsiElement element,
                                           @NotNull TextRange range,
                                           boolean canChangeWhiteSpaceOnly) {
    PsiFile file = element.getContainingFile();
    FormattingContext formattingContext = FormattingContext.create(file, range, CodeStyle.getSettings(file), FormattingMode.REFORMAT);
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(file.getProject());
    Document document = documentManager.getDocument(file);
    if (document != null) {
      int offset = element.getTextOffset();
      formatDocument(document, Collections.singletonList(range), formattingContext, canChangeWhiteSpaceOnly, false);
      documentManager.commitDocument(document);
      PsiElement resultingElement = file.findElementAt(offset);
      if (resultingElement != null) {
        return resultingElement;
      }
    }
    return element;
  }

  @Override
  public final void formatRanges(@NotNull PsiFile file,
                                 FormattingRangesInfo rangesInfo,
                                 boolean canChangeWhiteSpaceOnly,
                                 boolean quickFormat) {
    TextRange boundRange = ObjectUtils.notNull(rangesInfo.getBoundRange(), file.getTextRange());
    FormattingContext formattingContext = FormattingContext.create(file, boundRange, CodeStyle.getSettings(file), FormattingMode.REFORMAT);
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(file.getProject());
    Document document = documentManager.getDocument(file);
    if (document != null) {
      formatDocument(document, rangesInfo.getTextRanges(), formattingContext, canChangeWhiteSpaceOnly, quickFormat);
      documentManager.commitDocument(document);
    }
  }


  public abstract void formatDocument(@NotNull Document document,
                                      @NotNull List<TextRange> formattingRanges,
                                      @NotNull FormattingContext formattingContext,
                                      boolean canChangeWhiteSpaceOnly,
                                      boolean quickFormat);

}
