/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.formatter.common;

import com.intellij.formatting.*;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class InjectedLanguageBlockWrapper implements Block {
  private final Block myOriginal;
  private final int myOffset;
  private final TextRange myRange;
  @Nullable private final Indent myIndent;
  private List<Block> myBlocks;

  /**
   *  main code                  prefix    injected code        suffix
   *     |                         |            |                 |
   *     |                       xxx!!!!!!!!!!!!!!!!!!!!!!!!!!!!xxx
   * ...............................!!!!!!!!!!!!!!!!!!!!!!!!!!!!....................
   *                                ^
   *                              offset
   *
   * @param original block inside injected code
   * @param offset start offset of injected code inside the main document
   * @param range range of code inside injected document which is really placed in the main document
   * @param indent
   */
  public InjectedLanguageBlockWrapper(final @NotNull Block original, final int offset, @Nullable TextRange range, @Nullable Indent indent) {
    myOriginal = original;
    myOffset = offset;
    myRange = range;
    myIndent = indent;
  }

  public Indent getIndent() {
    return myIndent != null ? myIndent : myOriginal.getIndent();
  }

  @Nullable
  public Alignment getAlignment() {
    return myOriginal.getAlignment();
  }

  @NotNull
  public TextRange getTextRange() {
    TextRange range = myOriginal.getTextRange();
    if (myRange != null) {
      range = range.intersection(myRange);
    }

    int start = myOffset + range.getStartOffset() - (myRange != null ? myRange.getStartOffset() : 0);
    return TextRange.from(start, range.getLength());
  }

  @NotNull
  public List<Block> getSubBlocks() {
    if (myBlocks == null) {
      myBlocks = buildBlocks();
    }
    return myBlocks;
  }

  private List<Block> buildBlocks() {
    final List<Block> list = myOriginal.getSubBlocks();
    if (list.size() == 0) return AbstractBlock.EMPTY;
    if (myOffset == 0 && myRange == null) return list;

    final ArrayList<Block> result = new ArrayList<Block>(list.size());
    if (myRange == null) {
      for (Block block : list) {
        result.add(new InjectedLanguageBlockWrapper(block, myOffset, myRange, null));
      }
    }
    else {
      collectBlocksIntersectingRange(list, result, myRange);
    }
    return result;
  }

  private void collectBlocksIntersectingRange(final List<Block> list, final List<Block> result, final @NotNull TextRange range) {
    for (Block block : list) {
      final TextRange textRange = block.getTextRange();
      if (range.contains(textRange)) {
        result.add(new InjectedLanguageBlockWrapper(block, myOffset, range, null));
      }
      else if (textRange.intersectsStrict(range)) {
        collectBlocksIntersectingRange(block.getSubBlocks(), result, range);
      }
    }
  }

  @Nullable
  public Wrap getWrap() {
    return myOriginal.getWrap();
  }

  @Nullable public Spacing getSpacing(Block child1, Block child2) {
    if (child1 instanceof InjectedLanguageBlockWrapper) child1 = ((InjectedLanguageBlockWrapper)child1).myOriginal;
    if (child2 instanceof InjectedLanguageBlockWrapper) child2 = ((InjectedLanguageBlockWrapper)child2).myOriginal;
    return myOriginal.getSpacing(child1,  child2);
  }

  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    return myOriginal.getChildAttributes(newChildIndex);
  }

  public boolean isIncomplete() {
    return myOriginal.isIncomplete();
  }

  public boolean isLeaf() {
    return myOriginal.isLeaf();
  }
}
class A {
  private List<Integer> myResult;
  {
    m(10, myResult);
  }

  private void m(int i, List<Integer> result) {
    if (i > 0) {
      m(i - 1, result);
    }
    result.add(i);
  }
}