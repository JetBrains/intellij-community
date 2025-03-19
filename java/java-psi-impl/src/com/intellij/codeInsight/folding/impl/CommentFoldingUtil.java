// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.lang.Commenter;
import com.intellij.lang.LanguageCommenters;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.function.Predicate;

public final class CommentFoldingUtil {

  /**
   * Construct descriptor for comment folding.
   *
   * @param comment            comment to fold
   * @param document           document with comment
   * @param isCollapse         is comment collapsed by default or not
   * @param processedComments  already processed comments
   * @param isCustomRegionFunc determines whether element contains custom region tag
   */
  public static @Nullable FoldingDescriptor getCommentDescriptor(@NotNull PsiComment comment,
                                                       @NotNull Document document,
                                                       @NotNull Set<? super PsiElement> processedComments,
                                                       @NotNull Predicate<? super PsiElement> isCustomRegionFunc,
                                                       boolean isCollapse) {
    if (!processedComments.add(comment)) return null;

    final Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(comment.getLanguage());
    if (!(commenter instanceof CodeDocumentationAwareCommenter)) return null;

    final CodeDocumentationAwareCommenter docCommenter = (CodeDocumentationAwareCommenter)commenter;
    final IElementType commentType = comment.getTokenType();

    final TextRange commentRange = getCommentRange(comment, processedComments, isCustomRegionFunc, docCommenter);
    if (commentRange == null) return null;

    final String placeholder = getCommentPlaceholder(document, commentType, commentRange);
    if (placeholder == null) return null;

    return new FoldingDescriptor(comment.getNode(), commentRange, null, placeholder, isCollapse, Collections.emptySet());
  }

  private static @Nullable TextRange getCommentRange(@NotNull PsiComment comment,
                                                     @NotNull Set<? super PsiElement> processedComments,
                                                     @NotNull Predicate<? super PsiElement> isCustomRegionFunc,
                                                     @NotNull CodeDocumentationAwareCommenter docCommenter) {
    final IElementType commentType = comment.getTokenType();
    if (commentType == docCommenter.getDocumentationCommentTokenType() || commentType == docCommenter.getBlockCommentTokenType()) {
      return comment.getTextRange();
    }

    if (commentType != docCommenter.getLineCommentTokenType()) return null;

    return getOneLineCommentRange(comment, processedComments, isCustomRegionFunc, docCommenter);
  }

  /**
   * We want to allow to fold subsequent single line comments like
   * <pre>
   *     // this is comment line 1
   *     // this is comment line 2
   * </pre>
   *
   * @param startComment      comment to check
   * @param processedComments set that contains already processed elements. It is necessary because we process all elements of
   *                          the PSI tree, hence, this method may be called for both comments from the example above. However,
   *                          we want to create fold region during the first comment processing, put second comment to it and
   *                          skip processing when current method is called for the second element
   */
  private static @Nullable TextRange getOneLineCommentRange(@NotNull PsiComment startComment,
                                                  @NotNull Set<? super PsiElement> processedComments,
                                                  @NotNull Predicate<? super PsiElement> isCustomRegionFunc,
                                                  @NotNull CodeDocumentationAwareCommenter docCommenter) {
    if (isCustomRegionFunc.test(startComment)) return null;

    PsiElement end = null;
    for (PsiElement current = startComment.getNextSibling(); current != null; current = current.getNextSibling()) {
      ASTNode node = current.getNode();
      if (node == null) {
        break;
      }
      final IElementType elementType = node.getElementType();
      if (elementType == docCommenter.getLineCommentTokenType() &&
          !isCustomRegionFunc.test(current) &&
          !processedComments.contains(current)) {
        end = current;
        // We don't want to process, say, the second comment in case of three subsequent comments when it's being examined
        // during all elements traversal. I.e. we expect to start from the first comment and grab as many subsequent
        // comments as possible during the single iteration.
        processedComments.add(current);
        continue;
      }
      if (elementType == TokenType.WHITE_SPACE) {
        continue;
      }
      break;
    }

    if (end == null) return null;

    return new TextRange(startComment.getTextRange().getStartOffset(), end.getTextRange().getEndOffset());
  }

  /**
   * Construct placeholder for comment based on its type.
   *
   * @param document     document with comment
   * @param commentType  type of comment
   * @param commentRange text range of comment
   */
  public static @Nullable String getCommentPlaceholder(@NotNull Document document,
                                             @NotNull IElementType commentType,
                                             @NotNull TextRange commentRange) {
    return getCommentPlaceholder(document, commentType, commentRange, "...");
  }


  /**
   * Construct placeholder for comment based on its type.
   *
   * @param document     document with comment
   * @param commentType  type of comment
   * @param commentRange text range of comment
   * @param replacement  replacement for comment content. included in placeholder
   */
  public static @Nullable String getCommentPlaceholder(@NotNull Document document,
                                             @NotNull IElementType commentType,
                                             @NotNull TextRange commentRange,
                                             @NotNull String replacement) {
    final Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(commentType.getLanguage());
    if (!(commenter instanceof CodeDocumentationAwareCommenter)) return null;

    final CodeDocumentationAwareCommenter docCommenter = (CodeDocumentationAwareCommenter)commenter;

    final String placeholder;
    if (commentType == docCommenter.getLineCommentTokenType()) {
      placeholder = getLineCommentPlaceholderText(commenter, replacement);
    }
    else if (commentType == docCommenter.getBlockCommentTokenType()) {
      placeholder = getMultilineCommentPlaceholderText(commenter, replacement);
    }
    else if (commentType == docCommenter.getDocumentationCommentTokenType()) {
      placeholder = getDocCommentPlaceholderText(document, docCommenter, commentRange, replacement);
    }
    else {
      placeholder = null;
    }

    return placeholder;
  }

  private static @Nullable String getDocCommentPlaceholderText(@NotNull Document document,
                                                               @NotNull CodeDocumentationAwareCommenter commenter,
                                                               @NotNull TextRange commentRange,
                                                               @NotNull String replacement) {
    final String prefix = commenter.getDocumentationCommentPrefix();
    final String suffix = commenter.getDocumentationCommentSuffix();
    final String linePrefix = commenter.getDocumentationCommentLinePrefix();

    if (prefix == null || suffix == null || linePrefix == null) return null;

    final String header = getCommentHeader(document, suffix, prefix, linePrefix, commentRange);
    final String fullText = getCommentText(document, suffix, prefix, linePrefix, commentRange);
    if (StringUtil.equalsIgnoreWhitespaces(header, fullText)) replacement = "";

    return getCommentPlaceholder(prefix, suffix, header, replacement);
  }

  private static @Nullable String getMultilineCommentPlaceholderText(@NotNull Commenter commenter, @NotNull String replacement) {
    final String prefix = commenter.getBlockCommentPrefix();
    final String suffix = commenter.getBlockCommentSuffix();

    if (prefix == null || suffix == null) return null;

    return getCommentPlaceholder(prefix, suffix, null, replacement);
  }

  private static @Nullable String getLineCommentPlaceholderText(@NotNull Commenter commenter, @NotNull String replacement) {
    final String prefix = commenter.getLineCommentPrefix();

    if (prefix == null) return null;

    return getCommentPlaceholder(prefix, null, null, replacement);
  }

  /**
   * Construct comment placeholder based on rule placeholder ::= prefix[text ]replacement[suffix] .
   *
   * @param text        part of comment content to include in placeholder
   * @param replacement replacement for the rest of comment content
   */
  public static @NotNull String getCommentPlaceholder(@NotNull String prefix,
                                             @Nullable String suffix,
                                             @Nullable String text,
                                             @NotNull String replacement) {
    final StringBuilder sb = new StringBuilder();
    sb.append(prefix);

    if (text != null && !text.isEmpty()) {
      sb.append(text);
      sb.append(" ");
    }

    sb.append(replacement);

    if (suffix != null) sb.append(suffix);

    return sb.toString();
  }

  /**
   * Get first non-blank line from comment.
   * If line with comment prefix contains text then it will be used as header, otherwise second line will be used.
   * If both lines are blank or comment contains only one line then empty string is returned.
   *
   * @param document      document with comment
   * @param commentSuffix doc comment suffix
   * @param linePrefix    prefix for doc comment line
   * @param commentRange  comment text range in document
   */
  public static @NotNull String getCommentHeader(@NotNull Document document,
                                        @NotNull String commentSuffix,
                                        @NotNull String commentPrefix,
                                        @NotNull String linePrefix,
                                        @NotNull TextRange commentRange) {
    final int nFirstCommentLine = document.getLineNumber(commentRange.getStartOffset());
    for (int i = 0; i <= 1; i++) {
      final String line = getCommentLine(i, nFirstCommentLine, document, commentSuffix, commentPrefix, linePrefix, commentRange);
      if (line == null) return "";
      if (line.chars().anyMatch(c -> !StringUtil.isWhiteSpace((char)c))) return line;
    }
    return "";
  }

  /**
   * Get comment text excluding prefixes and suffixes.
   * If line contains whitespaces they will be included as well.
   *
   * @param document      document with comment
   * @param commentSuffix doc comment suffix
   * @param commentPrefix doc comment prefix
   * @param linePrefix    prefix for doc comment line
   * @param commentRange  comment text range in document
   */
  public static @NotNull String getCommentText(@NotNull Document document,
                                      @NotNull String commentSuffix,
                                      @NotNull String commentPrefix,
                                      @NotNull String linePrefix,
                                      @NotNull TextRange commentRange) {
    final StringBuilder sb = new StringBuilder();
    final int nFirstCommentLine = document.getLineNumber(commentRange.getStartOffset());
    for (int i = 0; ; i++) {
      final String line = getCommentLine(i, nFirstCommentLine, document, commentSuffix, commentPrefix, linePrefix, commentRange);
      if (line == null) break;
      sb.append(line);
    }
    return sb.toString();
  }

  @Contract("_, _ -> new")
  private static @NotNull TextRange getLineRange(@NotNull Document document, int nLine) {
    int startOffset = document.getLineStartOffset(nLine);
    int endOffset = document.getLineEndOffset(nLine);
    return new TextRange(startOffset, endOffset);
  }

  private static @Nullable String getCommentLine(int lineOffset,
                                                 int nFirstCommentLine,
                                                 @NotNull Document document,
                                                 @NotNull String commentSuffix,
                                                 @NotNull String commentPrefix,
                                                 @NotNull String linePrefix,
                                                 @NotNull TextRange commentRange) {
    if (lineOffset == 0) {
      final TextRange lineRange = getLineRange(document, nFirstCommentLine);
      return getCommentLine(document, lineRange, commentRange, commentPrefix, commentSuffix);
    }
    final int nCommentLine = nFirstCommentLine + lineOffset;
    if (nCommentLine >= document.getLineCount()) return null;

    final TextRange lineRange = getLineRange(document, nCommentLine);
    if (lineRange.getEndOffset() > commentRange.getEndOffset()) return null;

    return getCommentLine(document, lineRange, commentRange, linePrefix, commentSuffix);
  }

  private static @NotNull String getCommentLine(@NotNull Document document,
                                                @NotNull TextRange lineRange,
                                                @NotNull TextRange commentRange,
                                                @NotNull String prefix,
                                                @NotNull String suffix) {
    int startOffset = Math.max(lineRange.getStartOffset(), commentRange.getStartOffset());
    int endOffset = Math.min(lineRange.getEndOffset(), commentRange.getEndOffset());

    String commentPart = document.getText(new TextRange(startOffset, endOffset));

    int suffixIdx = commentPart.indexOf(suffix);
    if (suffixIdx != -1) commentPart = commentPart.substring(0, suffixIdx).trim();

    int prefixIdx = commentPart.indexOf(prefix);
    if (prefixIdx != -1) commentPart = commentPart.substring(prefixIdx + prefix.length());

    return commentPart;
  }
}
