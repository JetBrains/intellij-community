// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.java;

import com.intellij.formatting.*;
import com.intellij.formatting.alignment.AlignmentStrategy;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.psi.formatter.java.JavaFormatterUtil.extractTextRangesFromLiteralText;

public class TextBlockBlock extends AbstractJavaBlock {

  private final Indent myIndent;

  public TextBlockBlock(ASTNode textBlock,
                           Wrap wrap,
                           AlignmentStrategy alignment,
                           Indent indent,
                           CommonCodeStyleSettings settings,
                           JavaCodeStyleSettings javaSettings,
                           @NotNull FormattingMode formattingMode) {
    super(textBlock, wrap, alignment, indent, settings, javaSettings, formattingMode);
    myIndent = indent;
  }

  @Override
  protected List<Block> buildChildren() {
    if (getFormattingMode() != FormattingMode.REFORMAT) return Collections.emptyList();
    int offset = myNode.getStartOffset();
    Alignment alignment = createChildAlignment();
    List<TextRange> textRanges = extractLinesRanges();
    List<Block> children = new ArrayList<>(textRanges.size());
    for (int i = 0; i < textRanges.size(); i++) {
      TextRange range = textRanges.get(i).shiftRight(offset);
      Indent indent = i == 0 ? Indent.getNoneIndent() : Indent.getContinuationIndent();
      children.add(new TextLineBlock(range, alignment, indent, null));
    }
    return children;
  }

  private @NotNull List<TextRange> extractLinesRanges() {
    PsiLiteralExpression literal = ObjectUtils.tryCast(myNode.getPsi(), PsiLiteralExpression.class);
    if (literal == null || !literal.isTextBlock()) return Collections.emptyList();
    int indent = PsiLiteralUtil.getTextBlockIndent(literal);
    if (indent == -1) return Collections.emptyList();
    String text = literal.getText();

    return extractTextRangesFromLiteralText(text, indent);
  }

  @Override
  public @Nullable Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
    return null;
  }

  @Override
  public Indent getIndent() {
    return myIndent;
  }

  @Override
  public boolean isLeaf() {
    return getFormattingMode() != FormattingMode.REFORMAT;
  }
}
