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
package com.intellij.formatting.templateLanguages;

import com.intellij.formatting.Block;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Spacing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Alexey Chmutov
 */
public abstract class AbstractBlockUtil<TDataBlock extends IDataBlock, TTemplateBlock extends ITemplateBlock> {

  protected AbstractBlockUtil() {
  }

  public final List<TDataBlock> buildChildWrappers(@NotNull final Block parent) {
    return buildChildWrappers(parent, null);
  }

  public final List<TDataBlock> buildChildWrappers(@NotNull final Block parent, @Nullable Indent indent) {
    List<Block> children = parent.getSubBlocks();
    if (children.size() == 0) return Collections.emptyList();
    ArrayList<TDataBlock> result = new ArrayList<>(children.size());
    TDataBlock prevWrapper = null;
    for (Block child : children) {
      TDataBlock currWrapper = createBlockWrapper(child, indent);
      ContainerUtil.addIfNotNull(result, currWrapper);
      if(currWrapper != null && prevWrapper != null) {
        Spacing spacing = parent.getSpacing(prevWrapper.getOriginal(), currWrapper.getOriginal());
        prevWrapper.setRightHandSpacing(currWrapper, spacing);
      }
      prevWrapper = currWrapper;
    }
    return result;
  }

  private Pair<List<TDataBlock>, List<TDataBlock>> splitBlocksByRightBound(@NotNull Block parent, @NotNull TextRange bounds) {
    final List<Block> subBlocks = parent.getSubBlocks();
    if (subBlocks.size() == 0) return Pair
      .create(Collections.emptyList(), Collections.emptyList());
    final ArrayList<TDataBlock> before = new ArrayList<>(subBlocks.size() / 2);
    final ArrayList<TDataBlock> after = new ArrayList<>(subBlocks.size() / 2);
    splitByRightBoundAndCollectBlocks(subBlocks, before, after, bounds);
    return new Pair<>(before, after);
  }

  private void splitByRightBoundAndCollectBlocks(@NotNull List<Block> blocks,
                                                 @NotNull List<TDataBlock> before,
                                                 @NotNull List<TDataBlock> after,
                                                 @NotNull TextRange bounds) {
    for (Block block : blocks) {
      final TextRange textRange = block.getTextRange();
      if (bounds.contains(textRange)) {
        ContainerUtil.addIfNotNull(before, createBlockWrapper(block, null));
      }
      else if (bounds.getEndOffset() <= textRange.getStartOffset()) {
        ContainerUtil.addIfNotNull(after, createBlockWrapper(block, null));
      }
      else {
        //assert block.getSubBlocks().size() != 0 : "Block " + block.getTextRange() + " doesn't contain subblocks!";
        splitByRightBoundAndCollectBlocks(block.getSubBlocks(), before, after, bounds);
      }
    }
  }

  @Nullable
  protected abstract TDataBlock createBlockWrapper(@NotNull Block block, Indent indent);

  @NotNull
  protected abstract Block createBlockFragmentWrapper(@NotNull Block block, @NotNull TextRange dataBlockTextRange);

  public final List<Block> mergeBlocks(@NotNull List<TTemplateBlock> tlBlocks, @NotNull List<TDataBlock> foreignBlocks) {
    ArrayList<Block> result = new ArrayList<>(tlBlocks.size() + foreignBlocks.size());
    int vInd = 0;
    int fInd = 0;
    while (vInd < tlBlocks.size() && fInd < foreignBlocks.size()) {
      final TTemplateBlock v = tlBlocks.get(vInd);
      final TDataBlock f = foreignBlocks.get(fInd);
      final TextRange vRange = v.getTextRange();
      final TextRange fRange = f.getTextRange();
      if (vRange.getStartOffset() >= fRange.getEndOffset()) {
        // add leading foreign blocks
        result.add(f);
        fInd++;
      }
      else if (vRange.getEndOffset() <= fRange.getStartOffset()) {
        // add leading TL blocks
        result.add(v);
        vInd++;
      }
      else if (vRange.getStartOffset() < fRange.getStartOffset() ||
               vRange.getStartOffset() == fRange.getStartOffset() && vRange.getEndOffset() >= fRange.getEndOffset()) {
        // add including TL blocks and split intersecting foreign blocks
        result.add(v);
        while (fInd < foreignBlocks.size() && vRange.contains(foreignBlocks.get(fInd).getTextRange())) {
          v.addForeignChild(foreignBlocks.get(fInd++));
        }
        if (fInd < foreignBlocks.size()) {
          final TDataBlock notContainedF = foreignBlocks.get(fInd);
          if (vRange.intersectsStrict(notContainedF.getTextRange())) {
            Pair<List<TDataBlock>, List<TDataBlock>> splitBlocks = splitBlocksByRightBound(notContainedF.getOriginal(), vRange);
            for (TDataBlock blockWrapper : splitBlocks.getFirst()) {
              v.addForeignChild(blockWrapper);
            }
            foreignBlocks.remove(fInd);
            if (splitBlocks.getSecond().size() > 0) {
              foreignBlocks.addAll(fInd, splitBlocks.getSecond());
            }
          }
        }
        vInd++;
      }
      else if (vRange.getStartOffset() > fRange.getStartOffset() || vRange.getStartOffset() == fRange.getStartOffset()) {
        // add including foreign blocks or split them if needed
        int lastContainedTlInd = vInd;
        while (lastContainedTlInd < tlBlocks.size() && fRange.intersectsStrict(tlBlocks.get(lastContainedTlInd).getTextRange())) {
          lastContainedTlInd++;
        }
        if (fRange.contains(tlBlocks.get(lastContainedTlInd - 1).getTextRange())) {
          result.add(f);
          fInd++;
          while (vInd < lastContainedTlInd) {
            f.addTlChild(tlBlocks.get(vInd++));
          }
        }
        else {
          Block original = f.getOriginal();
          if (!original.getSubBlocks().isEmpty()) {
            foreignBlocks.remove(fInd);
            foreignBlocks.addAll(fInd, buildChildWrappers(original));
          } else {
            result.add(new ErrorLeafBlock(f.getTextRange().getStartOffset(), getEndOffset(tlBlocks, foreignBlocks)));
            return result;
          }
        }
      }
    }
    while (vInd < tlBlocks.size()) {
      result.add(tlBlocks.get(vInd++));
    }
    while (fInd < foreignBlocks.size()) {
      result.add(foreignBlocks.get(fInd++));
    }
    return result;
  }

  private int getEndOffset(@NotNull List<TTemplateBlock> tlBlocks, @NotNull List<TDataBlock> foreignBlocks) {
    return Math.max(foreignBlocks.get(foreignBlocks.size() - 1).getTextRange().getEndOffset(),
                    tlBlocks.get(tlBlocks.size() - 1).getTextRange().getEndOffset());
  }

  @NotNull
  public final List<TDataBlock> filterBlocksByRange(@NotNull List<TDataBlock> list, @NotNull TextRange textRange) {
    int i = 0;
    while (i < list.size()) {
      final TDataBlock wrapper = list.get(i);
      final TextRange range = wrapper.getTextRange();
      if (textRange.contains(range)) {
        i++;
      }
      else if (range.intersectsStrict(textRange)) {
        list.remove(i);
        list.addAll(i, buildChildWrappers(wrapper.getOriginal()));
      }
      else {
        list.remove(i);
      }
    }
    return list;
  }

  public final List<Block> splitBlockIntoFragments(@NotNull TDataBlock block, @NotNull List<TTemplateBlock> subBlocks) {
    final List<Block> children = new ArrayList<>(5);
    final TextRange range = block.getTextRange();
    int childStartOffset = range.getStartOffset();
    TTemplateBlock lastTLBlock = null;
    for (TTemplateBlock tlBlock : subBlocks) {
      final TextRange tlTextRange = tlBlock.getTextRange();
      if (tlTextRange.getStartOffset() > childStartOffset) {
        TextRange dataBlockTextRange = new TextRange(childStartOffset, tlTextRange.getStartOffset());
        if (tlBlock.isRequiredRange(dataBlockTextRange)) {
          children.add(createBlockFragmentWrapper(block.getOriginal(), dataBlockTextRange));
        }
      }
      children.add(tlBlock);
      lastTLBlock = tlBlock;
      childStartOffset = tlTextRange.getEndOffset();
    }
    if (range.getEndOffset() > childStartOffset) {
      TextRange dataBlockTextRange = new TextRange(childStartOffset, range.getEndOffset());
      if (lastTLBlock == null || lastTLBlock.isRequiredRange(dataBlockTextRange) ) {
        children.add(createBlockFragmentWrapper(block.getOriginal(), dataBlockTextRange));
      }
    }
    return children;
  }

  @NotNull
  public static List<Block> setParent(@NotNull List<Block> children, @NotNull BlockWithParent parent) {
    for (Block block : children) {
      if (block instanceof BlockWithParent) ((BlockWithParent)block).setParent(parent);
    }
    return children;
  }
}
