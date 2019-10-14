// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.formatter.java;

import com.intellij.formatting.*;
import com.intellij.formatting.alignment.AlignmentStrategy;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

  @NotNull
  private List<TextRange> extractLinesRanges() {
    PsiLiteralExpressionImpl literal = ObjectUtils.tryCast(myNode.getPsi(), PsiLiteralExpressionImpl.class);
    if (literal == null || literal.getLiteralElementType() != JavaTokenType.TEXT_BLOCK_LITERAL) return Collections.emptyList();
    int indent = literal.getTextBlockIndent();
    if (indent == -1) return Collections.emptyList();
    String text = myNode.getText();

    List<TextRange> linesRanges = new ArrayList<>();
    // open quotes
    int start = StringUtil.indexOf(text, '\n', 3);
    if (start == -1) return Collections.emptyList();
    linesRanges.add(new TextRange(0, start));
    start += 1;

    while (start < text.length()) {
      int end = StringUtil.indexOf(text, '\n', start);
      if (end == -1) end = text.length();
      if (start + indent < end) start += indent;
      if (start != end) linesRanges.add(new TextRange(start, end));
      start = end + 1;
    }

    return linesRanges;
  }

  @Nullable
  @Override
  public Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
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
