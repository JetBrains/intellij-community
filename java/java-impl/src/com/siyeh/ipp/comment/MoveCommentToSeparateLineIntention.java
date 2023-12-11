/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ipp.comment;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public final class MoveCommentToSeparateLineIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("move.comment.to.separate.line.intention.family.name");
  }

  @Override
  public @IntentionName @NotNull String getTextForElement(@NotNull PsiElement element) {
    return IntentionPowerPackBundle.message("move.comment.to.separate.line.intention.name");
  }

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new CommentOnLineWithSourcePredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    final PsiComment comment = (PsiComment)element;
    final PsiWhiteSpace whitespace;
    while (true) {
      final PsiElement prevLeaf = PsiTreeUtil.prevLeaf(element);
      if (prevLeaf == null || prevLeaf instanceof PsiWhiteSpace && prevLeaf.getText().indexOf('\n') >= 0) {
        whitespace = (PsiWhiteSpace)prevLeaf;
        break;
      }
      element = prevLeaf;
    }
    final PsiElement anchor = element;

    final Document document = comment.getContainingFile().getViewProvider().getDocument();
    final String newline;
    if (whitespace == null) {
      newline = "\n";
    }
    else {
      final String text = whitespace.getText();
      newline = text.substring(text.lastIndexOf('\n'));
    }
    final PsiElement prev = PsiTreeUtil.prevLeaf(comment);
    final TextRange commentRange = comment.getTextRange();
    final int deleteOffset = prev instanceof PsiWhiteSpace ? prev.getTextRange().getStartOffset() : commentRange.getStartOffset();
    document.deleteString(deleteOffset, commentRange.getEndOffset());

    final int offset = anchor.getTextRange().getStartOffset();
    document.insertString(offset, newline);
    document.insertString(offset, comment.getText());
    updater.moveTo(offset);
  }
}
