// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.common;

import com.intellij.formatting.*;
import com.intellij.lang.Language;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class InjectedLanguageBlockWrapper implements BlockEx {
  private final           Block       myOriginal;
  private final           int         myOffset;
  private final           TextRange   myRange;
  private final @Nullable Indent      myIndent;
  private final @Nullable Language    myLanguage;
  private                 List<Block> myBlocks;

  /**
   * <pre>
   *  main code     prefix    injected code        suffix
   *     |            |            |                 |
   *     |          xxx!!!!!!!!!!!!!!!!!!!!!!!!!!!!xxx
   * ..................!!!!!!!!!!!!!!!!!!!!!!!!!!!!..........
   *                   ^
   *                 offset
   * </pre>
   * @param original block inside injected code
   * @param offset start offset of injected code inside the main document
   * @param range range of code inside injected document which is really placed in the main document
   */
  public InjectedLanguageBlockWrapper(final @NotNull Block original, final int offset, @Nullable TextRange range, @Nullable Indent indent) {
    this(original, offset, range, indent, null);
  }

  public InjectedLanguageBlockWrapper(final @NotNull Block original,
                                      final int offset,
                                      @Nullable TextRange range,
                                      @Nullable Indent indent,
                                      @Nullable Language language) {
    myOriginal = original;
    myOffset = offset;
    myRange = range;
    myIndent = indent;
    myLanguage = language;
  }

  @Override
  public Indent getIndent() {
    return myIndent != null ? myIndent : myOriginal.getIndent();
  }

  @Override
  public @Nullable Alignment getAlignment() {
    return myOriginal.getAlignment();
  }

  @Override
  public @NotNull TextRange getTextRange() {
    TextRange range = myOriginal.getTextRange();
    if (myRange != null) {
      range = range.intersection(myRange);
    }

    int start = myOffset + range.getStartOffset() - (myRange != null ? myRange.getStartOffset() : 0);
    return TextRange.from(start, range.getLength());
  }

  @Override
  public @Nullable Language getLanguage() {
    return myLanguage;
  }

  @Override
  public @NotNull List<Block> getSubBlocks() {
    if (myBlocks == null) {
      myBlocks = buildBlocks();
    }
    return myBlocks;
  }

  private List<Block> buildBlocks() {
    final List<Block> list = myOriginal.getSubBlocks();
    if (list.isEmpty()) return AbstractBlock.EMPTY;
    if (myOffset == 0 && myRange == null) return list;

    final ArrayList<Block> result = new ArrayList<>(list.size());
    if (myRange == null) {
      for (Block block : list) {
        result.add(new InjectedLanguageBlockWrapper(block, myOffset, null, null, myLanguage));
      }
    }
    else {
      collectBlocksIntersectingRange(list, result, myRange);
    }
    return result;
  }

  private void collectBlocksIntersectingRange(final List<? extends Block> list, final List<? super Block> result, final @NotNull TextRange range) {
    for (Block block : list) {
      final TextRange textRange = block.getTextRange();
      if (block instanceof InjectedLanguageBlockWrapper && block.getTextRange().equals(range)) {
        continue;
      }
      if (range.contains(textRange)) {
        result.add(new InjectedLanguageBlockWrapper(block, myOffset, range, null, myLanguage));
      }
      else if (textRange.intersectsStrict(range)) {
        collectBlocksIntersectingRange(block.getSubBlocks(), result, range);
      }
    }
  }

  @Override
  public @Nullable Wrap getWrap() {
    return myOriginal.getWrap();
  }

  @Override
  public @Nullable Spacing getSpacing(final Block child1, final @NotNull Block child2) {
    int shift = 0;
    Block child1ToUse = child1;
    Block child2ToUse = child2;
    if (child1 instanceof InjectedLanguageBlockWrapper) {
      child1ToUse = ((InjectedLanguageBlockWrapper)child1).myOriginal;
      shift = child1.getTextRange().getStartOffset() - child1ToUse.getTextRange().getStartOffset();
    }
    if (child2 instanceof InjectedLanguageBlockWrapper) child2ToUse = ((InjectedLanguageBlockWrapper)child2).myOriginal;
    Spacing spacing = myOriginal.getSpacing(child1ToUse, child2ToUse);
    if (spacing instanceof DependantSpacingImpl hostSpacing && shift != 0) {
      final int finalShift = shift;
      List<TextRange> shiftedRanges = ContainerUtil.map(hostSpacing.getDependentRegionRanges(), range -> range.shiftRight(finalShift));
      return new DependantSpacingImpl(
        hostSpacing.getMinSpaces(), hostSpacing.getMaxSpaces(), shiftedRanges,
        hostSpacing.shouldKeepLineFeeds(), hostSpacing.getKeepBlankLines(), DependentSpacingRule.DEFAULT
      );
    }
    return spacing;
  }

  @Override
  public @NotNull ChildAttributes getChildAttributes(final int newChildIndex) {
    return myOriginal.getChildAttributes(newChildIndex);
  }

  @Override
  public boolean isIncomplete() {
    return myOriginal.isIncomplete();
  }

  @Override
  public boolean isLeaf() {
    return myOriginal.isLeaf();
  }

  @Override
  public String toString() {
    return myOriginal.toString();
  }

  public Block getOriginal() {
    return myOriginal;
  }

  @Override
  public @Nullable String getDebugName() {
    if (myOriginal != null) {
      String originalDebugName = myOriginal.getDebugName();
      if (originalDebugName == null) originalDebugName = myOriginal.getClass().getSimpleName();
      return "wrapped " + originalDebugName;
    }
    else {
      return null;
    }
  }

}