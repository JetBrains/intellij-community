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
package com.intellij.formatting.alignment;

import com.intellij.formatting.Alignment;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static java.util.Arrays.asList;

/**
 * <code>GoF 'Strategy'</code> for {@link Alignment} retrieval.
 */
public abstract  class AlignmentStrategy {

  private static final AlignmentStrategy NULL_STRATEGY = new SharedAlignmentStrategy(null);

  /**
   * @return    shared strategy instance that returns <code>null</code> all the time
   */
  public static AlignmentStrategy getNullStrategy() {
    return NULL_STRATEGY;
  }

  /**
   * Constructs strategy that returns given alignment for all elements except those which types are delivered as a trailing argument.
   *
   * @param alignment   target alignment to wrap
   * @param typesToIgnore   types of the elements for which <code>null</code> should be returned on subsequent calls
   *                        to {@link #getAlignment(IElementType)}
   * @return                strategy that returns given alignment all the time for elements which types are not defined
   *                        as <code>'types to ignore'</code>; <code>null</code> is returned for them
   */
  public static AlignmentStrategy wrap(Alignment alignment, IElementType ... typesToIgnore) {
    return new SharedAlignmentStrategy(alignment, typesToIgnore);
  }

  /**
   * Creates strategy that creates and caches one alignment per given type internally and returns it on subsequent calls
   * to {@link #getAlignment(IElementType)} for elements which type is listed at the given collection. <code>null</code>
   * is returned from {@link #getAlignment(IElementType)} for elements which types are not listed at the given collection.
   * <p/>
   * This strategy is assumed to be used at following situations - suppose we want to align code blocks that doesn't belong
   * to the same parent but have similar structure, e.g. variable declaration assignments like the one below:
   * <pre>
   *     int start  = 1;
   *     int finish = 2;
   * </pre>
   * We can provide parent blocks of that target blocks with the same instance of this alignment strategy and let them eventually
   * reuse the same alignment objects for target sub-blocks of the same type.
   *
   * @param targetTypes                   target types for which cached alignment should be returned
   * @param allowBackwardShift            flag that specifies if former aligned element may be shifted to right in order to align
   *                                      to subsequent element (e.g. <code>'='</code> block of <code>'int start  = 1'</code> statement
   *                                      below is shifted one symbol right in order to align to the <code>'='</code> block
   *                                      of <code>'int finish  = 1'</code> statement)
   * @return                              alignment retrieval strategy that follows the rules described above
   */
  public static AlignmentStrategy createAlignmentPerTypeStrategy(Collection<IElementType> targetTypes, boolean allowBackwardShift) {
    return new AlignmentPerTypeStrategy(targetTypes, allowBackwardShift);
  }

  @Nullable
  public abstract Alignment getAlignment(IElementType elementType);

  /**
   * Stands for {@link AlignmentStrategy} implementation that is configured to return single pre-configured {@link Alignment} object
   * or <code>null</code> for all calls to {@link #getAlignment(IElementType)}.
   */
  private static class SharedAlignmentStrategy extends AlignmentStrategy {

    private final Set<IElementType> myDisableElementTypes = new HashSet<IElementType>();
    private final Alignment myAlignment;

    SharedAlignmentStrategy(Alignment alignment, IElementType ... disabledElementTypes) {
      myAlignment = alignment;
      myDisableElementTypes.addAll(asList(disabledElementTypes));
    }

    @Nullable
    public Alignment getAlignment(IElementType elementType) {
      return myDisableElementTypes.contains(elementType) ? null : myAlignment;
    }
  }

  /**
   * Alignment strategy that creates and caches alignments for target element types and returns them for elements with the
   * same types.
   */
  private static class AlignmentPerTypeStrategy extends AlignmentStrategy {

    private final Map<IElementType, Alignment> myAlignments = new HashMap<IElementType, Alignment>();

    AlignmentPerTypeStrategy(Collection<IElementType> targetElementTypes, boolean allowBackwardShift) {
      for (IElementType elementType : targetElementTypes) {
        myAlignments.put(elementType, Alignment.createAlignment(allowBackwardShift));
      }
    }

    @Override
    public Alignment getAlignment(IElementType elementType) {
      return myAlignments.get(elementType);
    }
  }
}