// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.java;

import com.intellij.formatting.*;
import com.intellij.formatting.alignment.AlignmentStrategy;
import com.intellij.lang.ASTNode;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.psi.formatter.java.AbstractJavaBlock.newJavaBlock;

public class CallChunkBlockBuilder {

  public static final String FRAGMENT_DEBUG_NAME = "chainFragment";
  public static final String CHAINED_CALL_DEBUG_NAME = "chainedCall";

  private final CommonCodeStyleSettings mySettings;
  private final CommonCodeStyleSettings.IndentOptions myIndentSettings;
  private final JavaCodeStyleSettings myJavaSettings;
  private final FormattingMode myFormattingMode;
  private final Indent mySmartIndent;
  private final boolean myUseRelativeIndents;

  private boolean isFirst = true;

  public CallChunkBlockBuilder(@NotNull CommonCodeStyleSettings settings, @NotNull JavaCodeStyleSettings javaSettings,
                               @NotNull FormattingMode formattingMode) {
    mySettings = settings;
    myIndentSettings = settings.getIndentOptions();
    myJavaSettings = javaSettings;
    myFormattingMode = formattingMode;
    myUseRelativeIndents = myIndentSettings != null && myIndentSettings.USE_RELATIVE_INDENTS;
    mySmartIndent = Indent.getSmartIndent(Indent.Type.CONTINUATION, myUseRelativeIndents);
  }

  public @NotNull Block create(final @NotNull List<? extends ASTNode> subNodes,
                               final Wrap wrap,
                               final @Nullable Alignment alignment,
                               int relativeIndentSize) {
    final ArrayList<Block> subBlocks = new ArrayList<>();
    final ASTNode firstNode = subNodes.get(0);
    if (JavaFormatterUtil.isStartOfCallChunk(mySettings, firstNode)) {
      AlignmentStrategy strategy = AlignmentStrategy.getNullStrategy();
      Indent indent = relativeIndentSize > 0 ? Indent.getSpaceIndent(relativeIndentSize) : Indent.getNoneIndent();
      Block block = newJavaBlock(firstNode, mySettings, myJavaSettings, indent, null, strategy, myFormattingMode);
      subBlocks.add(block);
      if (subNodes.size() > 1) {
        subBlocks.addAll(createJavaBlocks(subNodes.subList(1, subNodes.size())));
      }
      return createSyntheticBlock(subBlocks, getChainedBlockIndent(true), alignment, wrap, CHAINED_CALL_DEBUG_NAME);
    }
    else {
      return createSyntheticBlock(
        createJavaBlocks(subNodes), getChainedBlockIndent(false), alignment, null, FRAGMENT_DEBUG_NAME);
    }
  }

  private Block createSyntheticBlock(@NotNull List<Block> subBlocks,
                                     @NotNull Indent chainedBlockIndent,
                                     @Nullable Alignment alignment,
                                     @Nullable Wrap wrap,
                                     final @NotNull String debugName) {
    return new SyntheticCodeBlock(subBlocks, alignment, mySettings, myJavaSettings, chainedBlockIndent, wrap) {
      @Override
      public String getDebugName() {
        return debugName + ": " + SyntheticCodeBlock.class.getSimpleName();
      }
    };
  }

  private Indent getChainedBlockIndent(boolean isChainedCall) {
    if (isFirst) {
      isFirst = false;
      return Indent.getNoneIndent();
    }
    return isChainedCall ? mySmartIndent : Indent.getContinuationIndent(myUseRelativeIndents);
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