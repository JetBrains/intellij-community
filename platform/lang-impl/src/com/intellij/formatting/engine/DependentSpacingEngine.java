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
package com.intellij.formatting.engine;

import com.intellij.formatting.DependantSpacingImpl;
import com.intellij.formatting.SpacingImpl;
import com.intellij.formatting.WhiteSpace;
import com.intellij.openapi.util.TextRange;

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
 * current block. E.g. consider example above - <code>'1'</code> block has dependent spacing that targets the whole
 * <code>'{1, 2, 3}'</code> block. So, it's not possible to answer whether line feed should be used during processing block
 * <code>'1'</code>.
 * <p/>
 * We store such 'forward dependencies' at the current collection where the key is the range of the target 'dependent forward
 * region' and value is dependent spacing object.
 * <p/>
 * Every time we detect that formatter changes 'has line feeds' status of such dependent region, we
 * {@link DependantSpacingImpl#setDependentRegionLinefeedStatusChanged() mark} the dependent spacing as changed and schedule one more
 * formatting iteration.
 */
public class DependentSpacingEngine {
  private final BlockRangesMap myBlockRangesMap;
  
  private SortedMap<TextRange, DependantSpacingImpl> myPreviousDependencies =
    new TreeMap<>((o1, o2) -> {
      int offsetsDelta = o1.getEndOffset() - o2.getEndOffset();

      if (offsetsDelta == 0) {
        offsetsDelta = o2.getStartOffset() - o1.getStartOffset();     // starting earlier is greater
      }
      return offsetsDelta;
    });

  public DependentSpacingEngine(BlockRangesMap helper) {
    myBlockRangesMap = helper;
  }

  public boolean shouldReformatPreviouslyLocatedDependentSpacing(WhiteSpace space) {
    final TextRange changed = space.getTextRange();
    final SortedMap<TextRange, DependantSpacingImpl> sortedHeadMap = myPreviousDependencies.tailMap(changed);

    for (final Map.Entry<TextRange, DependantSpacingImpl> entry : sortedHeadMap.entrySet()) {
      final TextRange textRange = entry.getKey();

      if (textRange.contains(changed)) {
        final DependantSpacingImpl spacing = entry.getValue();
        if (spacing.isDependentRegionLinefeedStatusChanged()) {
          continue;
        }

        final boolean containedLineFeeds = spacing.getMinLineFeeds() > 0;
        final boolean containsLineFeeds = myBlockRangesMap.containsLineFeeds(textRange);

        if (containedLineFeeds != containsLineFeeds) {
          spacing.setDependentRegionLinefeedStatusChanged();
          return true;
        }
      }
    }

    return false;
  }
  
  public void registerUnresolvedDependentSpacingRanges(final SpacingImpl spaceProperty, List<TextRange> unprocessedRanges) {
    final DependantSpacingImpl dependantSpaceProperty = (DependantSpacingImpl)spaceProperty;
    if (dependantSpaceProperty.isDependentRegionLinefeedStatusChanged()) return;

    for (TextRange range: unprocessedRanges) {
      myPreviousDependencies.put(range, dependantSpaceProperty);
    }
  }
  
  public void clear() {
    myPreviousDependencies.clear();
  }
}