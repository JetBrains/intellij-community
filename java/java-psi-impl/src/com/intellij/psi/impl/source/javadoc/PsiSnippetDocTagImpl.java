// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiSnippetDocTag;
import com.intellij.psi.javadoc.PsiSnippetDocTagBody;
import com.intellij.psi.javadoc.PsiSnippetDocTagValue;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PsiSnippetDocTagImpl extends CompositePsiElement implements PsiSnippetDocTag, PsiLanguageInjectionHost {
  public PsiSnippetDocTagImpl() {
    super(JavaDocElementType.DOC_SNIPPET_TAG);
  }

  @Override
  public @NotNull String getName() {
    return getNameElement().getText().substring(1);
  }

  @Override
  public PsiDocComment getContainingComment() {
    ASTNode scope = getTreeParent();
    while (scope.getElementType() != JavaDocElementType.DOC_COMMENT) {
      scope = scope.getTreeParent();
    }
    return (PsiDocComment)SourceTreeToPsiMap.treeElementToPsi(scope);
  }

  @Override
  public PsiElement getNameElement() {
    return findPsiChildByType(JavaDocTokenType.DOC_TAG_NAME);
  }

  @Override
  public PsiElement @NotNull [] getDataElements() {
    return getChildrenAsPsiElements(PsiInlineDocTagImpl.VALUE_BIT_SET, PsiElement.ARRAY_FACTORY);
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public @Nullable PsiSnippetDocTagValue getValueElement() {
    return (PsiSnippetDocTagValue)findPsiChildByType(JavaDocElementType.DOC_SNIPPET_TAG_VALUE);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitSnippetTag(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiSnippetDocTag";
  }

  @Override
  public boolean isValidHost() {
    return true;
  }

  @Contract(pure = true)
  public @NotNull List<@NotNull TextRange> getContentRanges() {
    boolean isMarkdown = PsiUtil.isInMarkdownDocComment(this);
    final PsiSnippetDocTagValue valueElement = getValueElement();
    if (valueElement == null) return Collections.emptyList();

    final PsiSnippetDocTagBody body = valueElement.getBody();
    if (body == null) return Collections.emptyList();

    final ASTNode colon = getColonElement(body);
    if (colon == null) return Collections.emptyList();

    final int startOffset = colon.getTextRange().getEndOffset();

    final TextRange snippetBodyRange = body.getTextRange();

    final TextRange range = TextRange.create(startOffset, snippetBodyRange.getEndOffset());
    final TextRange snippetBodyTextRangeRelativeToSnippetTag = range.shiftLeft(getStartOffset());

    final String[] lines = snippetBodyTextRangeRelativeToSnippetTag.substring(getText()).split("\n");
    if (lines.length == 0) return Collections.singletonList(snippetBodyTextRangeRelativeToSnippetTag);

    return getRanges(snippetBodyTextRangeRelativeToSnippetTag, lines, isMarkdown);
  }

  @Contract(pure = true)
  private static @NotNull List<@NotNull TextRange> getRanges(@NotNull TextRange snippetBodyTextRangeRelativeToSnippet, String@NotNull [] lines, boolean isMarkdown) {
    final int firstLine = getFirstNonEmptyLine(lines, isMarkdown);
    final int lastLine = getLastNonEmptyLine(lines, isMarkdown);

    int totalMinIndent = getIndent(lines, firstLine, lastLine, isMarkdown);

    int startOffset = getStartOffsetOfFirstNonEmptyLine(snippetBodyTextRangeRelativeToSnippet, lines, firstLine);

    final List<TextRange> ranges = new ArrayList<>();
    for (int i = firstLine; i < Math.min(lastLine, lines.length); i++) {
      final String line = lines[i];
      final int size = line.length() + 1;
      final int indentSize = getIndentSize(line, totalMinIndent);

      ranges.add(TextRange.create(0, size - indentSize).shiftRight(startOffset + indentSize));
      startOffset += size;
    }

    final String line = lines[lastLine];
    final int indentSize = getIndentSize(line, totalMinIndent);

    final int endOffset = snippetBodyTextRangeRelativeToSnippet.getEndOffset();
    final int lastLineStartOffset = Math.min(endOffset, startOffset + indentSize);
    final int lastLineEndOffset = startOffset + line.length();

    ranges.add(TextRange.create(lastLineStartOffset, Math.min(endOffset, lastLineEndOffset)));
    return ranges;
  }

  /**
   * Usually leading asterisks of a javadoc are aligned so the common indent for lines in snippet body is obvious,
   * but nevertheless javadoc can have multiple leading asterisks, and they don't have to be aligned.
   * This method either returns the passed indent or, if the passed indent is too short, which will result in leaving some leading
   * asterisks after stripping the indent from the line, the indent that goes after the last leading asterisk.
   * @param line a line to calculate the indent size for
   * @param indent an indent that is minimal across all the lines in the snippet body
   * @return the indent that is either the passed indent, or a new indent that goes after the last leading asterisk.
   */
  @Contract(pure = true)
  private static @Range(from = 0, to = Integer.MAX_VALUE) int getIndentSize(final @NotNull String line, int indent) {
    final int ownLineIndent = CharArrayUtil.shiftForward(line, 0, " *");

    final String maxPossibleIndent = line.substring(0, ownLineIndent);
    final int lastAsteriskInIndent = maxPossibleIndent.lastIndexOf('*', ownLineIndent);

    return lastAsteriskInIndent >= indent ? lastAsteriskInIndent + 1 : indent;
  }

  @Contract(pure = true)
  private static @Range(from = 0, to = Integer.MAX_VALUE) int getStartOffsetOfFirstNonEmptyLine(@NotNull TextRange snippetBodyTextRangeRelativeToSnippet, String@NotNull [] lines, int firstLine) {
    int start = snippetBodyTextRangeRelativeToSnippet.getStartOffset();
    for (int i = 0; i < Math.min(firstLine, lines.length); i++) {
      start += lines[i].length() + 1;
    }
    return start;
  }

  @Contract(pure = true)
  private static @Range(from = 0, to = Integer.MAX_VALUE) int getIndent(String@NotNull [] lines, int firstLine, int lastLine, boolean isMarkdown) {
    int minIndent = Integer.MAX_VALUE;
    for (int i = firstLine; i <= lastLine && i < lines.length; i++) {
      String line = lines[i];
      final int indentLength;
      if (isEmptyOrSpacesWithLeadingAsterisksOnly(line, isMarkdown)) {
        indentLength = line.length();
      }
      else {
        indentLength = calculateIndent(line, isMarkdown);
      }
      if (minIndent > indentLength) minIndent = indentLength;
    }
    if (minIndent == Integer.MAX_VALUE) minIndent = 0;
    return minIndent;
  }

  @Contract(pure = true)
  private static @Range(from = 0, to = Integer.MAX_VALUE) int getLastNonEmptyLine(String@NotNull[] lines, boolean isMarkdown) {
    int lastLine = lines.length - 1;
    while (lastLine > 0 && isEmptyOrSpacesWithLeadingAsterisksOnly(lines[lastLine], isMarkdown)) {
      lastLine --;
    }
    return lastLine;
  }

  @Contract(pure = true)
  private static @Range(from = 0, to = Integer.MAX_VALUE) int getFirstNonEmptyLine(String@NotNull[] lines, boolean isMarkdown) {
    int firstLine = 0;
    while (firstLine < lines.length && isEmptyOrSpacesWithLeadingAsterisksOnly(lines[firstLine], isMarkdown)) {
      firstLine ++;
    }
    return firstLine;
  }

  @Contract(pure = true)
  private static boolean isEmptyOrSpacesWithLeadingAsterisksOnly(@NotNull String lines, boolean isMarkdown) {
    if (lines.isEmpty()) return true;
    return lines.matches(isMarkdown ? "^\\s*///\\s*$" : "^\\s*\\**\\s*$");
  }

  @Contract(pure = true)
  private static @Range(from = 0, to = Integer.MAX_VALUE) int calculateIndent(@NotNull String content, boolean isMarkdown) {
    if (content.isEmpty()) return 0;
    final String noIndent = content.replaceAll(isMarkdown ? "^\\s*///\\s*" : "^\\s*\\*\\s*", "");
    return content.length() - noIndent.length();
  }

  @Contract(pure = true)
  private static @Nullable ASTNode getColonElement(@NotNull PsiSnippetDocTagBody snippetBodyBody) {
    final ASTNode[] colonElements = snippetBodyBody.getNode().getChildren(TokenSet.create(JavaDocTokenType.DOC_TAG_VALUE_COLON));
    return colonElements.length == 1 ? colonElements[0] : null;
  }

  @Override
  public PsiLanguageInjectionHost updateText(@NotNull String text) {
    return new SnippetDocTagManipulator().handleContentChange(this, text);
  }

  @Override
  public @NotNull LiteralTextEscaper<? extends PsiLanguageInjectionHost> createLiteralTextEscaper() {
    return new LiteralTextEscaper<PsiSnippetDocTagImpl>(this) {

      private int[] myOffsets;

      @Override
      public boolean decode(@NotNull TextRange rangeInsideHost, @NotNull StringBuilder outChars) {
        final List<TextRange> ranges = myHost.getContentRanges();

        final String content = rangeInsideHost.substring(myHost.getText());

        myOffsets = new int[content.length() + 1];
        Arrays.fill(myOffsets, -1);

        int i = 0;
        boolean decoded = false;
        for (TextRange range : ranges) {
          if (!rangeInsideHost.contains(range)) continue;

          for (int j = 0; j < range.getLength(); j++) {
            myOffsets[i ++] = range.getStartOffset() + j;
          }

          decoded = true;
          outChars.append(range.substring(myHost.getText()));
        }
        myOffsets[i] = rangeInsideHost.getEndOffset();

        return decoded;
      }

      @Override
      public int getOffsetInHost(int offsetInDecoded, @NotNull TextRange rangeInsideHost) {
        if (offsetInDecoded >= myOffsets.length) {
          return -1;
        }
        return myOffsets[offsetInDecoded];
      }

      @Override
      public boolean isOneLine() {
        return false;
      }
    };
  }

}
