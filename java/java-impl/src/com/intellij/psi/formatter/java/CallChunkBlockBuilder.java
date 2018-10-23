/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

  private final CommonCodeStyleSettings mySettings;
  private final CommonCodeStyleSettings.IndentOptions myIndentSettings;
  private final JavaCodeStyleSettings myJavaSettings;
  private final FormattingMode myFormattingMode;

  public CallChunkBlockBuilder(@NotNull CommonCodeStyleSettings settings, @NotNull JavaCodeStyleSettings javaSettings,
                               @NotNull FormattingMode formattingMode) {
    mySettings = settings;
    myIndentSettings = settings.getIndentOptions();
    myJavaSettings = javaSettings;
    myFormattingMode = formattingMode;
  }

  @NotNull
  public Block create(@NotNull final List<? extends ASTNode> subNodes, final Wrap wrap, @Nullable final Alignment alignment) {
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

    // Support for groovy style dot placement
    if (!subNodes.isEmpty()) {
      final ASTNode lastNode = subNodes.get(subNodes.size() - 1);
      if (lastNode.getElementType() == JavaTokenType.DOT) {
        AlignmentStrategy strategy = AlignmentStrategy.getNullStrategy();
        subNodes.remove(subNodes.size() - 1);
        if (!subNodes.isEmpty()) {
          subBlocks.add(create(subNodes, wrap, null));
        }
        Block block =
          newJavaBlock(lastNode, mySettings, myJavaSettings, Indent.getNoneIndent(), Wrap.createWrap(WrapType.NONE, true), strategy,
                       myFormattingMode);
        subBlocks.add(block);
        return new SyntheticCodeBlock(subBlocks, alignment, mySettings, myJavaSettings,
                                      Indent.getContinuationWithoutFirstIndent(myIndentSettings.USE_RELATIVE_INDENTS), wrap);
      }
    }
    List<Block> blocks = createJavaBlocks(subNodes);

    // Last line not contains '.', but needs to be wrapped
    final Wrap finalWrap = myJavaSettings.PLACE_DOT_ON_NEXT_LINE ? null : wrap;
    return new SyntheticCodeBlock(blocks, alignment, mySettings, myJavaSettings,
                                  Indent.getContinuationWithoutFirstIndent(myIndentSettings.USE_RELATIVE_INDENTS),
                                  finalWrap);
  }

  @NotNull
  private List<Block> createJavaBlocks(@NotNull final List<? extends ASTNode> subNodes) {
    final ArrayList<Block> result = new ArrayList<>();
    for (ASTNode node : subNodes) {
      Indent indent = Indent.getContinuationWithoutFirstIndent(myIndentSettings.USE_RELATIVE_INDENTS);
      result.add(newJavaBlock(node, mySettings, myJavaSettings, indent, null, AlignmentStrategy.getNullStrategy(), myFormattingMode));
    }
    return result;
  }

}