// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.formatter.java;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.FormattingMode;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Wrap;
import com.intellij.formatting.alignment.AlignmentStrategy;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

public class PartialWhitespaceBlock extends SimpleJavaBlock {
  private final TextRange myRange;

  public PartialWhitespaceBlock(ASTNode node,
                                TextRange range,
                                Wrap wrap,
                                Alignment alignment,
                                Indent indent,
                                CommonCodeStyleSettings settings,
                                JavaCodeStyleSettings javaSettings,
                                @NotNull FormattingMode formattingMode) {
    super(node, wrap, AlignmentStrategy.wrap(alignment), indent, settings, javaSettings, formattingMode);
    myRange = range;
  }

  @NotNull
  @Override
  public TextRange getTextRange() {
    return myRange;
  }
}
