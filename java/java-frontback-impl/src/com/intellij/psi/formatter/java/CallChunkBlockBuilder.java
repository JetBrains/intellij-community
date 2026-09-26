// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.java;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.FormattingMode;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Wrap;
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
    this(settings, javaSettings, formattingMode, false);
  }

  public CallChunkBlockBuilder(@NotNull CommonCodeStyleSettings settings, @NotNull JavaCodeStyleSettings javaSettings,
                               @NotNull FormattingMode formattingMode,
                               boolean enforceUseSpaceIndent) {
    mySettings = settings;
    myIndentSettings = settings.getIndentOptions();
    myJavaSettings = javaSettings;
    myFormattingMode = formattingMode;
    myUseRelativeIndents = myIndentSettings != null && myIndentSettings.USE_RELATIVE_INDENTS;
    mySmartIndent = enforceUseSpaceIndent ? Indent.getSmartIndent(Indent.Type.SPACES, true)
                                          : Indent.getSmartIndent(Indent.Type.CONTINUATION, myUseRelativeIndents);
  }

  /**
   * @deprecated use {@link #create(List, Wrap, Alignment, int, boolean)}
   */
  @Deprecated
  public @NotNull Block create(final @NotNull List<? extends ASTNode> subNodes,
                               final Wrap wrap,
                               final @Nullable Alignment alignment,
                               int relativeIndentSize) {
    return create(subNodes, wrap, alignment, relativeIndentSize, true);
  }

  /**
   * Creates a code block {@link Block} for one call in a call chain.
   * <ul>
   *   <li>Each call chain contains a set of calls.
   *   Example: {@code foo.bar().baz()} is a call chain with calls {@code foo}, {@code .bar()} and {@code .baz()}</li>
   *   <li>Each call contains a set of {@code subNodes}.
   *   Example: {@code bar()} with {@code .}, {@code bar}, {@code (}, {@code )} nodes.</li>
   * </ul>
   *
   * @param subNodes nodes of the call from which the block should be created.
   * @param wrap wrap to use for the created block.
   * @param alignment alignment to use for the created block.
   * @param relativeIndentSize indent size used for the first children of the block.
   * @param isFirstCallOfCallChain whether the block is created for the first call in the call chain.
   */
  public @NotNull Block create(@NotNull List<? extends ASTNode> subNodes,
                                Wrap wrap,
                                @Nullable Alignment alignment,
                                int relativeIndentSize,
                                boolean isFirstCallOfCallChain) {
    final ArrayList<Block> subBlocks = new ArrayList<>();
    final ASTNode firstNode = subNodes.getFirst();
    Indent firstIndent = relativeIndentSize > 0 ? Indent.getSpaceIndent(relativeIndentSize) : Indent.getNoneIndent();
    if (JavaFormatterUtil.isStartOfCallChunk(mySettings, firstNode)) {
      AlignmentStrategy strategy = AlignmentStrategy.getNullStrategy();
      Block block = newJavaBlock(firstNode, mySettings, myJavaSettings, firstIndent, null, strategy, myFormattingMode);
      subBlocks.add(block);
      if (subNodes.size() > 1) {
        subBlocks.addAll(createJavaBlocks(subNodes.subList(1, subNodes.size()), firstIndent, isFirstCallOfCallChain));
      }
      return createSyntheticBlock(subBlocks, getChainedBlockIndent(true), alignment, wrap, CHAINED_CALL_DEBUG_NAME);
    }
    else {
      return createSyntheticBlock(
        createJavaBlocks(subNodes, firstIndent, isFirstCallOfCallChain), getChainedBlockIndent(false), alignment, null, FRAGMENT_DEBUG_NAME);
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

  private @NotNull List<Block> createJavaBlocks(final @NotNull List<? extends ASTNode> subNodes, Indent firstIndent, boolean isFirstCallOfCallChain) {
    final ArrayList<Block> result = new ArrayList<>();
    for (int i = 0; i < subNodes.size(); i++) {
      ASTNode node = subNodes.get(i);
      boolean isLastNode = i + 1 == subNodes.size();
      Indent indent = !isFirstCallOfCallChain && isLastNode && node.getElementType() == JavaTokenType.DOT ? firstIndent : Indent.getContinuationWithoutFirstIndent(myIndentSettings.USE_RELATIVE_INDENTS);
      result.add(newJavaBlock(node, mySettings, myJavaSettings, indent, null, AlignmentStrategy.getNullStrategy(), myFormattingMode));
    }
    return result;
  }
}