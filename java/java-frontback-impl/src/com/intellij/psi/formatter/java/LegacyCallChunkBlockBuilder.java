// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.java;

import com.intellij.formatting.*;
import com.intellij.formatting.alignment.AlignmentStrategy;
import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.psi.formatter.java.AbstractJavaBlock.newJavaBlock;

/**
 * The previous version of {@link CallChunkBlockBuilder} for compatibility with releases prior to 2021.2,
 * see <a href="https://youtrack.jetbrains.com/issue/IDEA-274778">IDEA-274778</a>.
 */
public class LegacyCallChunkBlockBuilder {

  private final CommonCodeStyleSettings mySettings;
  private final CommonCodeStyleSettings.IndentOptions myIndentSettings;
  private final JavaCodeStyleSettings myJavaSettings;
  private final FormattingMode myFormattingMode;

  public LegacyCallChunkBlockBuilder(@NotNull CommonCodeStyleSettings settings, @NotNull JavaCodeStyleSettings javaSettings,
                               @NotNull FormattingMode formattingMode) {
    mySettings = settings;
    myIndentSettings = settings.getIndentOptions();
    myJavaSettings = javaSettings;
    myFormattingMode = formattingMode;
  }

  public @NotNull Block create(final @NotNull List<? extends ASTNode> subNodes, final Wrap wrap, final @Nullable Alignment alignment) {
    final ArrayList<Block> subBlocks = new ArrayList<>();
    final ASTNode firstNode = subNodes.get(0);
    if (firstNode.getElementType() == JavaTokenType.DOT) {
      AlignmentStrategy strategy = AlignmentStrategy.getNullStrategy();
      Block block = newJavaBlock(firstNode, mySettings, myJavaSettings, Indent.getNoneIndent(), null, strategy, myFormattingMode);
      subBlocks.add(block);
      subNodes.remove(0);
      if (!subNodes.isEmpty()) {
        subBlocks.add(create(subNodes, wrap, null));
      }
      return new SyntheticCodeBlock(subBlocks, alignment, mySettings, myJavaSettings, Indent.getContinuationIndent(myIndentSettings.USE_RELATIVE_INDENTS), wrap);
    }
    return new SyntheticCodeBlock(createJavaBlocks(subNodes), alignment, mySettings, myJavaSettings, Indent.getContinuationWithoutFirstIndent(myIndentSettings.USE_RELATIVE_INDENTS), null);
  }

  private @NotNull List<Block> createJavaBlocks(final @NotNull List<? extends ASTNode> subNodes) {
    final ArrayList<Block> result = new ArrayList<>();
    for (ASTNode node : subNodes) {
      Indent indent = Indent.getContinuationWithoutFirstIndent(myIndentSettings.USE_RELATIVE_INDENTS);
      result.add(newJavaBlock(node, mySettings, myJavaSettings, indent, null, AlignmentStrategy.getNullStrategy(), myFormattingMode));
    }
    return result;
  }

}
