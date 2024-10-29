// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.engine;

import com.intellij.formatting.DependantSpacingImpl;
import com.intellij.formatting.LeafBlockWrapper;
import com.intellij.formatting.WhiteSpace;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.ApiStatus;

import java.util.*;

/**
 * Formatter provides a notion of {@link DependantSpacingImpl dependent spacing}, i.e. spacing that insist on line feed if target
 * dependent region contains line feed.
 * <p/>
 * Example:
 * <pre>
 *       int[] data = {1, 2, 3};
 * </pre>
 * We want to keep that in one line if possible but place curly braces on separate lines if the width is not enough:
 * <pre>
 *      int[] data = {    | &lt; right margin
 *          1, 2, 3       |
 *      }                 |
 * </pre>
 * There is a possible case that particular block has dependent spacing property that targets region that lays beyond the
 * current block. E.g. consider example above - {@code '1'} block has dependent spacing that targets the whole
 * {@code '{1, 2, 3}'} block. So, it's not possible to answer whether line feed should be used during processing block
 * {@code '1'}.
 * <p/>
 * We store such 'forward dependencies' at the current collection where the key is the range of the target 'dependent forward
 * region' and value is dependent spacing object.
 * <p/>
 * Every time we detect that formatter changes 'has line feeds' status of such dependent region, we
 * {@link DependantSpacingImpl#setDependentRegionLinefeedStatusChanged(int)} () mark} the dependent spacing as changed and schedule one more
 * formatting iteration.
 */
@ApiStatus.Internal
final class DependentSpacingEngine {
  /**
   * {@link AdjustWhiteSpacesState} can iterate tree of {@link LeafBlockWrapper} multiple times, because of dependent spacings.
   * This field represents current iteration number
   */
  private int myIteration = 1;

  private final BlockRangesMap myBlockRangesMap;

  private final Set<LeafBlockWrapper> myLeafBlocksToReformat = new HashSet<>();

  private final SortedMap<TextRange, LeafBlockWrapper> myPreviousDependencies =
    new TreeMap<>((o1, o2) -> {
      int offsetsDelta = o1.getEndOffset() - o2.getEndOffset();

      if (offsetsDelta == 0) {
        offsetsDelta = o2.getStartOffset() - o1.getStartOffset();     // starting earlier is greater
      }
      return offsetsDelta;
    });

  DependentSpacingEngine(BlockRangesMap helper) {
    myBlockRangesMap = helper;
  }

  /**
   * Reformat all dependent spaces, which have a dependency on a {@link TextRange} corresponding to the 'space'.
   * {@link AdjustWhiteSpacesState} iterates through all leafs again in case if there was a first change.
   * @param space - candidate who could potentially be located in one of the dependent regions
   * @return true if it was the first change, and false otherwise
   */
  boolean shouldReformatPreviouslyLocatedDependentSpacing(WhiteSpace space) {
    final TextRange changed = space.getTextRange();
    final SortedMap<TextRange, LeafBlockWrapper> sortedHeadMap = myPreviousDependencies.tailMap(changed);

    for (final Map.Entry<TextRange, LeafBlockWrapper> entry : sortedHeadMap.entrySet()) {
      final TextRange textRange = entry.getKey();

      if (textRange.contains(changed)) {
        final LeafBlockWrapper currentBlock = entry.getValue();
        if (!(currentBlock.getSpaceProperty() instanceof DependantSpacingImpl spacing) ||
            spacing.isDependentRegionLinefeedStatusChanged(myIteration)) {
          continue;
        }

        final boolean containedLineFeeds = spacing.getMinLineFeeds() > 0;
        final boolean containsLineFeeds = myBlockRangesMap.containsLineFeedsOrTooLong(textRange);

        if (containedLineFeeds != containsLineFeeds) {
          if (!spacing.isDependantSpacingChangedAtLeastOnce()) {
            spacing.setDependentRegionLinefeedStatusChanged(myIteration);
            return true;
          }
          else if (containsLineFeeds) {
            spacing.setDependentRegionLinefeedStatusChanged(myIteration);
            myLeafBlocksToReformat.add(currentBlock);
            return false;
          }
        }
      }
    }

    return false;
  }

  void incrementIteration() {
    myIteration++;
  }

  /**
   * Registers all dependant ranges for the current 'leafBlockWrapper'. It happens only once per iteration
   * @param leafBlockWrapper - block, from which dependent spacing is extracted
   * @param unprocessedRanges - list of dependent ranges
   * @see DependantSpacingImpl#isDependantSpacingChangedAtLeastOnce()
   */
  void registerUnresolvedDependentSpacingRanges(final LeafBlockWrapper leafBlockWrapper, List<? extends TextRange> unprocessedRanges) {
    if (!(leafBlockWrapper.getSpaceProperty() instanceof DependantSpacingImpl dependantSpaceProperty)) return;

    if (dependantSpaceProperty.isDependentRegionLinefeedStatusChanged(myIteration)) return;

    for (TextRange range: unprocessedRanges) {
      myPreviousDependencies.put(range, leafBlockWrapper);
    }
  }

  /**
   * Returns a list of blocks with dependant spaces which should be reformatted after {@link AdjustWhiteSpacesState} is finished.
   */
  Set<LeafBlockWrapper> getLeafBlocksToReformat() { return myLeafBlocksToReformat; }

  void clear() {
    myPreviousDependencies.clear();
  }
}