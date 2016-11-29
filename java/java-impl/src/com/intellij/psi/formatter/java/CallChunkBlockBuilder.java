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

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
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

  private final CommonCodeStyleSettings mySettings;
  private final CommonCodeStyleSettings.IndentOptions myIndentSettings;
  private final JavaCodeStyleSettings myJavaSettings;

  public CallChunkBlockBuilder(@NotNull CommonCodeStyleSettings settings, @NotNull JavaCodeStyleSettings javaSettings) {
    mySettings = settings;
    myIndentSettings = settings.getIndentOptions();
    myJavaSettings = javaSettings;
  }

  @NotNull
  public Block create(@NotNull final List<ASTNode> subNodes, final Wrap wrap, @Nullable final Alignment alignment) {
    final ArrayList<Block> subBlocks = new ArrayList<>();
    final ASTNode firstNode = subNodes.get(0);
    if (firstNode.getElementType() == JavaTokenType.DOT) {
      AlignmentStrategy strategy = AlignmentStrategy.getNullStrategy();
      Block block = newJavaBlock(firstNode, mySettings, myJavaSettings, Indent.getNoneIndent(), null, strategy);
      subBlocks.add(block);
      subNodes.remove(0);
      if (!subNodes.isEmpty()) {
        subBlocks.add(create(subNodes, wrap, null));
      }
      return new SyntheticCodeBlock(subBlocks, alignment, mySettings, myJavaSettings, Indent.getContinuationIndent(myIndentSettings.USE_RELATIVE_INDENTS), wrap);
    }
    return new SyntheticCodeBlock(createJavaBlocks(subNodes), alignment, mySettings, myJavaSettings, Indent.getContinuationWithoutFirstIndent(myIndentSettings.USE_RELATIVE_INDENTS), null);
  }

  @NotNull
  private List<Block> createJavaBlocks(@NotNull final List<ASTNode> subNodes) {
    final ArrayList<Block> result = new ArrayList<>();
    for (ASTNode node : subNodes) {
      Indent indent = Indent.getContinuationWithoutFirstIndent(myIndentSettings.USE_RELATIVE_INDENTS);
      result.add(newJavaBlock(node, mySettings, myJavaSettings, indent, null, AlignmentStrategy.getNullStrategy()));
    }
    return result;
  }

}