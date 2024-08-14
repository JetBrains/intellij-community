// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.java;

import com.intellij.formatting.*;
import com.intellij.formatting.alignment.AlignmentStrategy;
import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DocCommentBlock extends AbstractJavaBlock{
  public DocCommentBlock(ASTNode node,
                         Wrap wrap,
                         Alignment alignment,
                         Indent indent,
                         CommonCodeStyleSettings settings,
                         JavaCodeStyleSettings javaSettings,
                         @NotNull FormattingMode formattingMode)
  {
    super(node, wrap, alignment, indent, settings, javaSettings, formattingMode);
  }

  @Override
  protected List<Block> buildChildren() {
    final ArrayList<Block> result = new ArrayList<>();

    ASTNode child = myNode.getFirstChildNode();
    boolean isMarkdown = PsiUtil.isInMarkdownDocComment(child.getPsi());
    while (child != null) {
      IElementType type = child.getElementType();
      if (type == JavaDocTokenType.DOC_COMMENT_START || (type == JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS && isMarkdown)) {
        result.add(createJavaBlock(child, mySettings, myJavaSettings, Indent.getNoneIndent(), null, AlignmentStrategy.getNullStrategy(), getFormattingMode()));
      } else if (!FormatterUtil.containsWhiteSpacesOnly(child) && !child.getText().trim().isEmpty()){
        result.add(createJavaBlock(child, mySettings, myJavaSettings, Indent.getSpaceIndent(1), null, AlignmentStrategy.getNullStrategy(), getFormattingMode()));
      }
      child = child.getTreeNext();
    }
    return result;

  }

  @Override
  public @NotNull ChildAttributes getChildAttributes(final int newChildIndex) {
    return new ChildAttributes(Indent.getSpaceIndent(1), null);
  }
}
