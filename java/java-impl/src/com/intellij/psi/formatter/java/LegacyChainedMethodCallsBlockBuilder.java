// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.java;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiComment;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.source.tree.JavaElementType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.psi.formatter.java.JavaFormatterUtil.getWrapType;

/**
 * The previous version of {@link ChainMethodCallsBlockBuilder} for compatibility with releases prior to 2021.2,
 * see <a href="https://youtrack.jetbrains.com/issue/IDEA-274778">IDEA-274778</a>
 */
public class LegacyChainedMethodCallsBlockBuilder {
  public static final String COMPATIBILITY_KEY = "java.formatter.chained.calls.pre212.compatibility";

  private final CommonCodeStyleSettings               mySettings;
  private final CommonCodeStyleSettings.IndentOptions myIndentSettings;
  private final JavaCodeStyleSettings                 myJavaSettings;

  private final Wrap      myBlockWrap;
  private final Alignment myBlockAlignment;
  private final Indent    myBlockIndent;

  private final FormattingMode myFormattingMode;

  LegacyChainedMethodCallsBlockBuilder(Alignment alignment,
                                       Wrap wrap,
                                       Indent indent,
                                       CommonCodeStyleSettings settings,
                                       JavaCodeStyleSettings javaSettings,
                                       @NotNull FormattingMode formattingMode) {
    myBlockWrap = wrap;
    myBlockAlignment = alignment;
    myBlockIndent = indent;
    mySettings = settings;
    myIndentSettings = settings.getIndentOptions();
    myJavaSettings = javaSettings;
    myFormattingMode = formattingMode;
  }

  public Block build(List<? extends ASTNode> nodes) {
    List<Block> blocks = buildBlocksFrom(nodes);

    Indent indent = myBlockIndent != null ? myBlockIndent : Indent.getContinuationWithoutFirstIndent(myIndentSettings.USE_RELATIVE_INDENTS);
    return new SyntheticCodeBlock(blocks, myBlockAlignment, mySettings, myJavaSettings, indent, myBlockWrap);
  }

  private List<Block> buildBlocksFrom(List<? extends ASTNode> nodes) {
    List<ChainedCallChunk> methodCall = splitMethodCallOnChunksByDots(nodes);

    Wrap wrap = null;
    Alignment chainedCallsAlignment = null;

    List<Block> blocks = new ArrayList<>();

    for (int i = 0; i < methodCall.size(); i++) {
      ChainedCallChunk currentCallChunk = methodCall.get(i);
      if (isMethodCall(currentCallChunk) || isComment(currentCallChunk)) {
        if (wrap == null) {
          wrap = createCallChunkWrap(i, methodCall);
        }
        if (chainedCallsAlignment == null) {
          chainedCallsAlignment = createCallChunkAlignment(i, methodCall);
        }
      }
      else {
        wrap = null;
        chainedCallsAlignment = null;
      }

      LegacyCallChunkBlockBuilder builder = new LegacyCallChunkBlockBuilder(mySettings, myJavaSettings, myFormattingMode);
      blocks.add(builder.create(currentCallChunk.nodes, wrap, chainedCallsAlignment));
    }

    return blocks;
  }

  private static boolean isComment(ChainedCallChunk chunk) {
    List<ASTNode> nodes = chunk.nodes;
    if (nodes.size() == 1) {
      return nodes.get(0).getPsi() instanceof PsiComment;
    }
    return false;
  }

  private Wrap createCallChunkWrap(int chunkIndex, @NotNull List<? extends ChainedCallChunk> methodCall) {
    if (mySettings.WRAP_FIRST_METHOD_IN_CALL_CHAIN) {
      ChainedCallChunk next = chunkIndex + 1 < methodCall.size() ? methodCall.get(chunkIndex + 1) : null;
      if (next != null && isMethodCall(next)) {
        return Wrap.createWrap(getWrapType(mySettings.METHOD_CALL_CHAIN_WRAP), true);
      }
    }

    return Wrap.createWrap(getWrapType(mySettings.METHOD_CALL_CHAIN_WRAP), false);
  }

  private boolean shouldAlignMethod(ChainedCallChunk currentMethodChunk, List<ChainedCallChunk> methodCall) {
    return mySettings.ALIGN_MULTILINE_CHAINED_METHODS
           && !currentMethodChunk.isEmpty()
           && !chunkIsFirstInChainMethodCall(currentMethodChunk, methodCall);
  }

  private static boolean chunkIsFirstInChainMethodCall(@NotNull ChainedCallChunk callChunk, @NotNull List<ChainedCallChunk> methodCall) {
    return !methodCall.isEmpty() && callChunk == methodCall.get(0);
  }

  private static @NotNull List<ChainedCallChunk> splitMethodCallOnChunksByDots(@NotNull List<? extends ASTNode> nodes) {
    List<ChainedCallChunk> result = new ArrayList<>();

    List<ASTNode> current = new ArrayList<>();
    for (ASTNode node : nodes) {
      if (node.getElementType() == JavaTokenType.DOT || node.getPsi() instanceof PsiComment) {
        if (!current.isEmpty()) {
          result.add(new ChainedCallChunk(current));
        }
        current = new ArrayList<>();
      }
      current.add(node);
    }

    if (!current.isEmpty()) {
      result.add(new ChainedCallChunk(current));
    }

    return result;
  }

  private Alignment createCallChunkAlignment(int chunkIndex, @NotNull List<ChainedCallChunk> methodCall) {
    ChainedCallChunk current = methodCall.get(chunkIndex);
    return shouldAlignMethod(current, methodCall)
           ? AbstractJavaBlock.createAlignment(mySettings.ALIGN_MULTILINE_CHAINED_METHODS, null)
           : null;
  }

  private static boolean isMethodCall(@NotNull ChainedCallChunk callChunk) {
    List<ASTNode> nodes = callChunk.nodes;
    return nodes.size() >= 3 && nodes.get(2).getElementType() == JavaElementType.EXPRESSION_LIST;
  }

  private record ChainedCallChunk(@NotNull List<ASTNode> nodes) {
    boolean isEmpty() {
      return nodes.isEmpty();
    }
  }
}

