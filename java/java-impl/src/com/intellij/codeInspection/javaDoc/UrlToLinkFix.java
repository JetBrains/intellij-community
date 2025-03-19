// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.javaDoc;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

class UrlToLinkFix extends PsiUpdateModCommandAction<PsiDocComment> {
  private final int myStartOffsetInDocComment;

  private final int myEndOffsetInDocComment;

  /**
   * @param comment                 target Javadoc comment to fix
   * @param startOffsetInDocComment the start offset of the URL in Javadoc comment
   * @param endOffsetInDocComment   if you want to use "..." as the link text, use the end URL offset in a JavaDoc comment;
   *                                if you want to use a substring after the URL as the link text, use the end of that substring
   */
  UrlToLinkFix(@NotNull PsiDocComment comment, int startOffsetInDocComment, int endOffsetInDocComment) {
    super(comment);
    myStartOffsetInDocComment = startOffsetInDocComment;
    myEndOffsetInDocComment = endOffsetInDocComment;
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("quickfix.text.replace.url.with.link");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiDocComment element, @NotNull ModPsiUpdater updater) {
    String commentText = element.getText();
    String prefix = commentText.substring(0, myStartOffsetInDocComment);
    String urlAndMaybeText = commentText.substring(myStartOffsetInDocComment, myEndOffsetInDocComment);
    String suffix = commentText.substring(myEndOffsetInDocComment);
    int urlEnd = urlAndMaybeText.indexOf(' ');
    String text;
    if (urlEnd > 0) {
      text = urlAndMaybeText.substring(urlEnd).trim();
      urlAndMaybeText = urlAndMaybeText.substring(0, urlEnd);
    }
    else {
      text = "...";
    }

    CommentTracker ct = new CommentTracker();
    String wrappedLink = element.isMarkdownComment()
                         ? "[%s](%s)".formatted(text, urlAndMaybeText)
                         : "<a href=\"" + urlAndMaybeText + "\">" + text + "</a>";

    PsiElement replacement = ct.replace(element, prefix + wrappedLink + suffix);
    int start = replacement.getTextRange().getStartOffset() + prefix.length();
    start += element.isMarkdownComment() ? 1 : urlAndMaybeText.length() + 11;

    updater.select(TextRange.from(start, text.length()));
  }
}
