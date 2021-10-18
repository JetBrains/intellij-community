// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting;

import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Character.isWhitespace;
import static java.util.Comparator.reverseOrder;
import static java.util.stream.Collectors.toList;


public class LineCommentAddSpacePostFormatProcessor implements PostFormatProcessor {

  @Override
  public @NotNull PsiElement processElement(@NotNull PsiElement source,
                                            @NotNull CodeStyleSettings settings) {
    processText(source.getContainingFile(), source.getTextRange(), settings);
    return source;
  }

  @Override
  public @NotNull TextRange processText(@NotNull PsiFile source,
                                        @NotNull TextRange rangeToReformat,
                                        @NotNull CodeStyleSettings settings) {

    Language language = source.getLanguage();
    if (!settings.getCommonSettings(language).LINE_COMMENT_ADD_SPACE) {
      // return rangeToReformat;  // Option is disabled
    }

    Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(language);
    SingleLineCommentFinder commentFinder = new SingleLineCommentFinder(commenter);
    source.accept(commentFinder);

    List<Integer> commentOffsets = commentFinder.commentOffsets
      .stream()
      .filter(rangeToReformat::contains)
      // Will go backwards to protect earlier offsets from modifications in latter ones
      .sorted(reverseOrder())
      .collect(toList());

    if (commentOffsets.isEmpty()) {
      return rangeToReformat;  // Nothing useful found
    }

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(source.getProject());
    Document document = documentManager.getDocument(source);

    if (document == null) {
      return rangeToReformat;  // Failed to get document
    }

    commentOffsets.forEach(offset -> document.insertString(offset, " "));

    documentManager.commitDocument(document);

    return rangeToReformat.grown(commentOffsets.size());
  }


  private static class SingleLineCommentFinder extends PsiElementVisitor {
    private final List<String> lineCommentPrefixes;
    private final List<Integer> commentOffsets = new ArrayList<>();

    SingleLineCommentFinder(Commenter commenter) {
      lineCommentPrefixes = ContainerUtil.map(commenter.getLineCommentPrefixes(), String::trim);
    }

    @Override
    public void visitFile(@NotNull PsiFile file) {
      file.acceptChildren(this);
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      element.acceptChildren(this);
    }

    @Override
    public void visitComment(@NotNull PsiComment comment) {
      String commentText = comment.getText();

      // Find the line comment prefix
      String commentPrefix = ContainerUtil.find(lineCommentPrefixes, prefix -> commentText.startsWith(prefix));

      if (commentPrefix == null) return;  // Not found -> Not a line comment

      if (commentPrefix.equals(commentText)) return;  // Empty comment, no need to add a trailing space

      if (isWhitespace(commentText.charAt(commentPrefix.length()))) return;  // Space is already there

      commentOffsets.add(comment.getTextRange().getStartOffset() + commentPrefix.length());
    }
  }
}
