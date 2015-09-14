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
package com.intellij.psi.codeStyle.autodetect;

import com.intellij.formatting.ASTBlock;
import com.intellij.formatting.Block;
import com.intellij.formatting.Indent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.formatter.common.NewLineBlocksIterator;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class FormatterBasedLineIndentInfoBuilder {
  private static final int MAX_NEW_LINE_BLOCKS_TO_PROCESS = 500;

  private final Document myDocument;
  private final CharSequence myText;
  private final Block myRootBlock;

  public FormatterBasedLineIndentInfoBuilder(@NotNull Document document, @NotNull Block rootBlock) {
    myDocument = document;
    myText = myDocument.getCharsSequence();
    myRootBlock = rootBlock;
  }

  public List<LineIndentInfo> build() {
    List<Block> newLineBlocks = getBlocksStartingNewLine();
    
    return ContainerUtil.map(newLineBlocks, new Function<Block, LineIndentInfo>() {
      @Override
      public LineIndentInfo fun(Block newLineBlock) {
        int blockStartOffset = newLineBlock.getTextRange().getStartOffset();
        int line = myDocument.getLineNumber(blockStartOffset);
        int lineStartOffset = myDocument.getLineStartOffset(line);

        if (rangeHasTabs(lineStartOffset, blockStartOffset)) {
          return LineIndentInfo.LINE_WITH_TABS;
        }
        
        if (hasTotallyNormalOrNoneIndent(newLineBlock)) {
          return LineIndentInfo.newNormalIndent(blockStartOffset - lineStartOffset);
        }
        else {
          return LineIndentInfo.LINE_WITH_NOT_COUNTABLE_INDENT; 
        }
      }
    });
  }
  
  private static boolean hasTotallyNormalOrNoneIndent(Block block) {
    final TextRange range = block.getTextRange();
    final int startOffset = range.getStartOffset();
    
    boolean startOffsetAlreadyHasNormalIndent = false;
    
    while (block != null && range.getStartOffset() == startOffset) {
      Indent.Type type = block.getIndent() != null ? block.getIndent().getType() : null;
      
      if (type == Indent.Type.NONE || type == Indent.Type.NORMAL && !startOffsetAlreadyHasNormalIndent) {
        startOffsetAlreadyHasNormalIndent = true;
      }
      else {
        return false;
      }

      if (block instanceof AbstractBlock) {
        ((AbstractBlock)block).setBuildIndentsOnly(true);
      }
      List<Block> subBlocks = block.getSubBlocks();
      block = subBlocks.isEmpty() ? null : subBlocks.get(0);
    }
    
    return true;
  }

  @NotNull
  private List<Block> getBlocksStartingNewLine() {
    NewLineBlocksIterator newLineBlocksIterator = new NewLineBlocksIterator(myRootBlock, myDocument);

    List<Block> newLineBlocks = new ArrayList<Block>();
    int currentLine = 0;
    while (newLineBlocksIterator.hasNext() && currentLine < MAX_NEW_LINE_BLOCKS_TO_PROCESS) {
      Block next = newLineBlocksIterator.next();
      if (next instanceof ASTBlock && ((ASTBlock)next).getNode() instanceof PsiComment) {
        continue;
      }
      newLineBlocks.add(next);
      currentLine++;
    }

    return newLineBlocks;
  }
  
  private boolean rangeHasTabs(int lineStartOffset, int textStartOffset) {
    return CharArrayUtil.indexOf(myText, "\t", lineStartOffset, textStartOffset) > 0;
  }
}
