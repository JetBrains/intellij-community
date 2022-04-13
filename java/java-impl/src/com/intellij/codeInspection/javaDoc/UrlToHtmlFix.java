// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.javaDoc;

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.javadoc.PsiDocComment;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class UrlToHtmlFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private final int myStartOffsetInDocComment;

  // if you want to use "..." as the HTML link text, use the end URL offset in a JavaDoc comment as `myEndOffsetInDocComment`
  // if you want to use a substring after the URL as the HTML link text, use the end of that substring
  private final int myEndOffsetInDocComment;

  UrlToHtmlFix(@Nullable PsiDocComment element, int startOffsetInDocComment, int endOffsetInDocComment) {
    super(element);
    myStartOffsetInDocComment = startOffsetInDocComment;
    myEndOffsetInDocComment = endOffsetInDocComment;
  }

  @Override
  public @NotNull String getText() {
    return JavaBundle.message("quickfix.text.replace.url.with.html");
  }

  @Override
  public @NotNull String getFamilyName() {
    return getText();
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    String commentText = startElement.getText();
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
    String wrappedLink = "<a href=\"" + urlAndMaybeText + "\">" + text + "</a>";
    CommentTracker ct = new CommentTracker();
    PsiElement replacement = ct.replace(startElement, prefix + wrappedLink + suffix);
    if (editor != null) {
      int start = replacement.getTextRange().getStartOffset() + prefix.length() + urlAndMaybeText.length() + 11;
      int end = start + text.length();
      editor.getCaretModel().moveToOffset(start);
      editor.getSelectionModel().setSelection(start, end);
    }
  }
}
