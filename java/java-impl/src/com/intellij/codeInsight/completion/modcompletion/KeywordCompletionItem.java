// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.modcompletion;

import com.intellij.codeInsight.ModNavigatorTailType;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcompletion.CompletionItemPresentation;
import com.intellij.modcompletion.PsiUpdateCompletionItem;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.MarkupText;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.NotNullByDefault;

/**
 * A completion item for a Java keyword.
 */
@NotNullByDefault
final class KeywordCompletionItem extends PsiUpdateCompletionItem {
  private final @NlsSafe String myKeyword;
  private final ModNavigatorTailType myTail;
  private final boolean myAdjustLineOffset;

  KeywordCompletionItem(@NlsSafe String keyword, ModNavigatorTailType tail) {
    this(keyword, tail, false);
  }

  KeywordCompletionItem(@NlsSafe String keyword, ModNavigatorTailType tail, boolean adjustLineOffset) {
    myKeyword = keyword;
    myTail = tail;
    myAdjustLineOffset = adjustLineOffset;
  }

  @Override
  public String mainLookupString() {
    return myKeyword;
  }

  @Override
  public KeywordInfo contextObject() {
    return new KeywordInfo(myKeyword);
  }

  @Override
  public CompletionItemPresentation presentation() {
    return new CompletionItemPresentation(MarkupText.plainText(myKeyword).highlightAll(MarkupText.Kind.STRONG));
  }

  @Override
  public void update(ActionContext actionContext, InsertionContext insertionContext, PsiFile file, ModPsiUpdater updater) {
    myTail.processTail(actionContext.project(), updater, actionContext.offset());
    if (myAdjustLineOffset) {
      PsiDocumentManager.getInstance(actionContext.project()).commitDocument(file.getFileDocument());
      CodeStyleManager.getInstance(actionContext.project()).adjustLineIndent(file, actionContext.selection().getStartOffset());
    }
  }

  public record KeywordInfo(String keyword) {
  }
}
