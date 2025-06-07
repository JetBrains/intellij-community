// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.java;

import com.intellij.formatting.*;
import com.intellij.formatting.alignment.AlignmentStrategy;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.formatter.FormatterUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class LabeledJavaBlock extends AbstractJavaBlock{
  public LabeledJavaBlock(ASTNode node,
                          Wrap wrap,
                          Alignment alignment,
                          Indent indent,
                          CommonCodeStyleSettings settings,
                          JavaCodeStyleSettings javaSettings,
                          @NotNull FormattingMode formattingMode) {
    super(node, wrap, alignment, indent, settings, javaSettings, formattingMode);
  }

  @Override
  protected List<Block> buildChildren() {
    final ArrayList<Block> result = new ArrayList<>();
    ASTNode child = myNode.getFirstChildNode();
    Indent currentIndent = getLabelIndent();
    Wrap currentWrap = null;
    while (child != null) {
      if (!FormatterUtil.containsWhiteSpacesOnly(child) && child.getTextLength() > 0){
        result.add(createJavaBlock(child, mySettings, myJavaSettings, currentIndent, currentWrap, AlignmentStrategy.getNullStrategy(), getFormattingMode()));
        if (child.getElementType() == JavaTokenType.COLON) {
          currentIndent = Indent.getNoneIndent();
          currentWrap =Wrap.createWrap(WrapType.ALWAYS, true);
        }
      }
      child = child.getTreeNext();
    }
    return result;
  }

  private Indent getLabelIndent() {
    if (mySettings.getRootSettings().getIndentOptions(JavaFileType.INSTANCE).LABEL_INDENT_ABSOLUTE) {
      return Indent.getAbsoluteLabelIndent();
    } else {
      return Indent.getLabelIndent();
    }
  }

  @Override
  public @NotNull ChildAttributes getChildAttributes(final int newChildIndex) {
    return new ChildAttributes(Indent.getNoneIndent(), null);
  }
}
