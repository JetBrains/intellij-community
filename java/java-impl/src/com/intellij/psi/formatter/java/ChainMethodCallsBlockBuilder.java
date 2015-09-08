/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.formatter.java;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Wrap;
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

class ChainMethodCallsBlockBuilder {
  private final CommonCodeStyleSettings mySettings;
  private final CommonCodeStyleSettings.IndentOptions myIndentSettings;
  private final JavaCodeStyleSettings myJavaSettings;

  private final Wrap myBlockWrap;
  private final Alignment myBlockAlignment;
  private final Indent myBlockIndent;

  private Wrap myWrap;
  private Alignment myChainedCallsAlignment;

  public ChainMethodCallsBlockBuilder(Alignment alignment,
                                      Wrap wrap,
                                      Indent indent,
                                      CommonCodeStyleSettings settings,
                                      JavaCodeStyleSettings javaSettings)
  {
    myBlockWrap = wrap;
    myBlockAlignment = alignment;
    myBlockIndent = indent;
    mySettings = settings;
    myIndentSettings = settings.getIndentOptions();
    myJavaSettings = javaSettings;
  }

  public Block build(List<ASTNode> nodes)  {
    List<Block> blocks = buildBlocksFrom(nodes);

    Indent indent = myBlockIndent != null ? myBlockIndent : Indent.getContinuationWithoutFirstIndent(myIndentSettings.USE_RELATIVE_INDENTS);
    return new SyntheticCodeBlock(blocks, myBlockAlignment, mySettings, myJavaSettings, indent, myBlockWrap);
  }

  private List<Block> buildBlocksFrom(List<ASTNode> nodes) {
    List<ChainedCallChunk> methodCall = splitMethodCallOnChunksByDots(nodes);

    myWrap = null;
    myChainedCallsAlignment = null;

    List<Block> blocks = new ArrayList<Block>();

    for (int i = 0; i < methodCall.size(); i++) {
      ChainedCallChunk currentCallChunk = methodCall.get(i);
      if (isMethodCall(currentCallChunk) || isComment(currentCallChunk)) {
        if (myWrap == null)
          myWrap = createCallChunkWrap(i, methodCall);
        if (myChainedCallsAlignment == null)
          myChainedCallsAlignment = createCallChunkAlignment(i, methodCall);
      }
      else {
        myWrap = null;
        myChainedCallsAlignment = null;
      }

      CallChunkBlockBuilder builder = new CallChunkBlockBuilder(mySettings, myJavaSettings);
      blocks.add(builder.create(currentCallChunk.nodes, myWrap, myChainedCallsAlignment));
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

  private Wrap createCallChunkWrap(int chunkIndex, @NotNull List<ChainedCallChunk> methodCall) {
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

  private boolean chunkIsFirstInChainMethodCall(@NotNull ChainedCallChunk callChunk, @NotNull List<ChainedCallChunk> methodCall) {
    return !methodCall.isEmpty() && callChunk == methodCall.get(0);
  }

  @NotNull
  private List<ChainedCallChunk> splitMethodCallOnChunksByDots(@NotNull List<ASTNode> nodes) {
    List<ChainedCallChunk> result = new ArrayList<ChainedCallChunk>();

    List<ASTNode> current = new ArrayList<ASTNode>();
    for (ASTNode node : nodes) {
      if (node.getElementType() == JavaTokenType.DOT || node.getPsi() instanceof PsiComment) {
        result.add(new ChainedCallChunk(current));
        current = new ArrayList<ASTNode>();
      }
      current.add(node);
    }

    result.add(new ChainedCallChunk(current));
    return result;
  }

  private Alignment createCallChunkAlignment(int chunkIndex, @NotNull List<ChainedCallChunk> methodCall) {
    ChainedCallChunk current = methodCall.get(chunkIndex);
    return shouldAlignMethod(current, methodCall)
           ? AbstractJavaBlock.createAlignment(mySettings.ALIGN_MULTILINE_CHAINED_METHODS, null)
           : null;
  }

  private boolean isMethodCall(@NotNull ChainedCallChunk callChunk) {
    List<ASTNode> nodes = callChunk.nodes;
    return nodes.size() >= 3 && nodes.get(2).getElementType() == JavaElementType.EXPRESSION_LIST;
  }
}

class ChainedCallChunk {
  @NotNull final List<ASTNode> nodes;

  ChainedCallChunk(@NotNull List<ASTNode> nodes) {
    this.nodes = nodes;
  }

  boolean isEmpty() {
    return nodes.isEmpty();
  }
}
