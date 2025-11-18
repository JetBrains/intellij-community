// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.modcompletion.ModCompletionItem;
import com.intellij.modcompletion.ModCompletionItemProvider;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A lightweight implementation of completion using ModCompletion providers 
 * @see ModCompletionItemProvider 
 */
@NotNullByDefault
@ApiStatus.Internal
public final class LightModCompletionServiceImpl {
  public static void getItems(PsiFile file, int caretOffset, int invocationCount, CompletionType type,
                              Consumer<ModCompletionItem> sink) {
    CharSequence sequence = file.getFileDocument().getCharsSequence();
    int start = caretOffset;
    while (start > 0 && StringUtil.isJavaIdentifierPart(sequence.charAt(start-1))) {
      start--;
    }
    getItems(file, start, caretOffset, invocationCount, type, sink);
  }

  public static void getItems(PsiFile file, int startOffset, int caretOffset, int invocationCount, CompletionType type,  
                              Consumer<ModCompletionItem> sink) {
    PsiElement element;
    if (startOffset == caretOffset) {
      PsiFile copy = (PsiFile)file.copy();
      Document document = copy.getFileDocument();
      document.insertString(caretOffset, CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED);
      PsiDocumentManager manager = PsiDocumentManager.getInstance(file.getProject());
      manager.commitDocument(document);
      element = Objects.requireNonNull(copy.findElementAt(caretOffset));
    } else {
      element = Objects.requireNonNull(file.findElementAt(startOffset));
    }
    List<ModCompletionItemProvider> providers = ModCompletionItemProvider.EP_NAME.allForLanguage(file.getLanguage());
    String prefix = file.getFileDocument().getText(TextRange.create(startOffset, caretOffset));
    ModCompletionItemProvider.CompletionContext context = new ModCompletionItemProvider.CompletionContext(
      file, caretOffset, element, new CamelHumpMatcher(prefix), invocationCount, type);
    for (ModCompletionItemProvider provider : providers) {
      provider.provideItems(context, sink);
    }
  }
}
