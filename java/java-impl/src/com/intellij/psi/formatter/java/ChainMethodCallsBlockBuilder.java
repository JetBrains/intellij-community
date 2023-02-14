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

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.intellij.psi.formatter.java.JavaFormatterUtil.getWrapType;

class ChainMethodCallsBlockBuilder {
  private final CommonCodeStyleSettings mySettings;
  private final CommonCodeStyleSettings.IndentOptions myIndentSettings;
  private final JavaCodeStyleSettings myJavaSettings;

  private final Wrap myBlockWrap;
  private final Alignment myBlockAlignment;
  private final Indent myBlockIndent;

  private final FormattingMode myFormattingMode;

  ChainMethodCallsBlockBuilder(Alignment alignment,
                                      Wrap wrap,
                                      Indent indent,
                                      CommonCodeStyleSettings settings,
                                      JavaCodeStyleSettings javaSettings,
                                      @NotNull FormattingMode formattingMode)
  {
    myBlockWrap = wrap;
    myBlockAlignment = alignment;
    myBlockIndent = indent;
    mySettings = settings;
    myIndentSettings = settings.getIndentOptions();
    myJavaSettings = javaSettings;
    myFormattingMode = formattingMode;
  }

  public Block build(List<? extends ASTNode> nodes)  {
    List<Block> blocks = buildBlocksFrom(nodes);

    Indent indent = myBlockIndent != null ? myBlockIndent : Indent.getContinuationWithoutFirstIndent(myIndentSettings.USE_RELATIVE_INDENTS);
    return new SyntheticCodeBlock(blocks, myBlockAlignment, mySettings, myJavaSettings, indent, myBlockWrap);
  }

  private List<Block> buildBlocksFrom(List<? extends ASTNode> nodes) {
    List<ChainedCallChunk> methodCall = splitMethodCallOnChunksByDots(nodes);

    Wrap wrap = Wrap.createWrap(getWrapType(mySettings.METHOD_CALL_CHAIN_WRAP), true);
    Wrap builderMethodWrap = Wrap.createWrap(WrapType.ALWAYS, true);
    Alignment chainedCallsAlignment = null;

    List<Block> blocks = new ArrayList<>();

    int commonIndentSize = mySettings.KEEP_BUILDER_METHODS_INDENTS ? getCommonIndentSize(methodCall) : -1;

    CallChunkBlockBuilder builder = new CallChunkBlockBuilder(mySettings, myJavaSettings, myFormattingMode);
    for (int i = 0; i < methodCall.size(); i++) {
      ChainedCallChunk currentCallChunk = methodCall.get(i);
      if (isMethodCall(currentCallChunk) && !isBuilderMethod(currentCallChunk) || isComment(currentCallChunk)) {
        if (chainedCallsAlignment == null) {
          chainedCallsAlignment = createCallChunkAlignment(i, methodCall);
        }
      }
      else {
        chainedCallsAlignment = null;
      }

      Wrap currWrap = isMethodCall(currentCallChunk) && canWrap(i, methodCall)
                      ? isBuilderMethod(currentCallChunk) ? builderMethodWrap : wrap
                      : null;

      blocks.add(builder.create(currentCallChunk.nodes,
                                currWrap, chainedCallsAlignment,
                                getRelativeIndentSize(commonIndentSize, currentCallChunk)));
    }

    return blocks;
  }

  private int getCommonIndentSize(@NotNull List<ChainedCallChunk> chunks) {
    String commonIndent = null;
    for (ChainedCallChunk chunk : chunks) {
      if (isMethodCall(chunk) && isBuilderMethod(chunk)) {
        String currIndent = chunk.getIndentString();
        if (currIndent != null) {
          if (commonIndent == null) {
            commonIndent = currIndent;
          }
          else if (commonIndent.startsWith(currIndent)) {
            commonIndent = currIndent;
          }
        }
      }
    }
    return commonIndent != null ? commonIndent.length() : -1;
  }

  private static int getRelativeIndentSize(int commonIndentSize, @NotNull ChainedCallChunk chunk) {
    if (commonIndentSize >= 0) {
      String indentString = chunk.getIndentString();
      if (indentString != null) {
        return Math.max(indentString.length() - commonIndentSize, 0);
      }
    }
    return -1;
  }

  private static boolean isComment(ChainedCallChunk chunk) {
    List<ASTNode> nodes = chunk.nodes;
    if (nodes.size() == 1) {
      return nodes.get(0).getPsi() instanceof PsiComment;
    }
    return false;
  }

  private boolean isBuilderMethod(@NotNull ChainedCallChunk chunk) {
    String identifier = chunk.getIdentifier();
    return identifier != null && mySettings.isBuilderMethod(identifier);
  }

  private boolean canWrap(int chunkIndex, @NotNull List<? extends ChainedCallChunk> methodCall) {
    if (chunkIndex <= 0) return false;
    ChainedCallChunk prev = methodCall.get(chunkIndex - 1);
    boolean isFirst = !isMethodCall(prev) || chunkIndex == 1;
    if (isFirst) {
      if (mySettings.WRAP_FIRST_METHOD_IN_CALL_CHAIN) {
        ChainedCallChunk next = chunkIndex + 1 < methodCall.size() ? methodCall.get(chunkIndex + 1) : null;
        return next != null && isMethodCall(next);
      }
      return false;
    }
    return true;
  }

  private boolean shouldAlignMethod(ChainedCallChunk currentMethodChunk, List<ChainedCallChunk> methodCall) {
    return mySettings.ALIGN_MULTILINE_CHAINED_METHODS
           && !currentMethodChunk.isEmpty()
           && !chunkIsFirstInChainMethodCall(currentMethodChunk, methodCall);
  }

  private static boolean chunkIsFirstInChainMethodCall(@NotNull ChainedCallChunk callChunk, @NotNull List<ChainedCallChunk> methodCall) {
    return !methodCall.isEmpty() && callChunk == methodCall.get(0);
  }

  @NotNull
  private List<ChainedCallChunk> splitMethodCallOnChunksByDots(@NotNull List<? extends ASTNode> nodes) {
    List<ChainedCallChunk> result = new ArrayList<>();

    List<ASTNode> current = new ArrayList<>();
    for (ASTNode node : nodes) {
      if (JavaFormatterUtil.isStartOfCallChunk(mySettings, node) || node.getPsi() instanceof PsiComment) {
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
    for (Iterator<ASTNode> iter = callChunk.nodes.iterator(); iter.hasNext();) {
      ASTNode node = iter.next();
      if (node.getElementType() == JavaTokenType.IDENTIFIER) {
        node = iter.hasNext() ? iter.next() : null;
        return node != null && node.getElementType() == JavaElementType.EXPRESSION_LIST;
      }
    }
    return false;
  }


  private record ChainedCallChunk(@NotNull List<ASTNode> nodes) {
    boolean isEmpty() {
      return nodes.isEmpty();
    }

    @Nullable
    private String getIdentifier() {
      return ObjectUtils.doIfNotNull(
        ContainerUtil.find(nodes, node -> node.getElementType() == JavaTokenType.IDENTIFIER),
        node -> node.getText());
    }

    @Nullable
    private String getIndentString() {
      if (nodes.size() > 0) {
        ASTNode prev = nodes.get(0).getTreePrev();
        if (prev != null && prev.getPsi() instanceof PsiWhiteSpace && prev.textContains('\n')) {
          CharSequence whitespace = prev.getChars();
          int lineStart = CharArrayUtil.lastIndexOf(whitespace, "\n", whitespace.length() - 1);
          if (lineStart >= 0) {
            return whitespace.subSequence(lineStart + 1, whitespace.length()).toString();
          }
        }
      }
      return null;
    }
  }
}

