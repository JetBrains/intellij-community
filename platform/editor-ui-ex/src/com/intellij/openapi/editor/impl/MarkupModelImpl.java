/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.openapi.editor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.MarkupIterator;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MarkupModelImpl extends UserDataHolderBase implements MarkupModelEx {
  private static final Logger LOG = Logger.getInstance(MarkupModelImpl.class);
  private final DocumentEx myDocument;

  private RangeHighlighter[] myCachedHighlighters;
  private final List<MarkupModelListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final RangeHighlighterTree myHighlighterTree;          // this tree holds regular highlighters with target = HighlighterTargetArea.EXACT_RANGE
  private final RangeHighlighterTree myHighlighterTreeForLines;  // this tree holds line range highlighters with target = HighlighterTargetArea.LINES_IN_RANGE

  MarkupModelImpl(@NotNull DocumentEx document) {
    myDocument = document;
    myHighlighterTree = new RangeHighlighterTree(this);
    myHighlighterTreeForLines = new RangeHighlighterTree(this);
  }

  @Override
  public void dispose() {
    myHighlighterTree.dispose();
    myHighlighterTreeForLines.dispose();
  }

  @Override
  public @NotNull RangeHighlighter addLineHighlighter(int line, int layer, @Nullable TextAttributes textAttributes) {
    return addLineHighlighter(null, textAttributes, line, layer);
  }

  @Override
  @NotNull
  public RangeHighlighter addLineHighlighter(@Nullable TextAttributesKey textAttributesKey, int lineNumber, int layer) {
    return addLineHighlighter(textAttributesKey, null, lineNumber, layer);
  }

  private RangeHighlighter addLineHighlighter(@Nullable TextAttributesKey textAttributesKey,
                                              @Nullable TextAttributes textAttributes,
                                              int lineNumber,
                                              int layer) {
    if (isNotValidLine(lineNumber)) {
      throw new IndexOutOfBoundsException("lineNumber:" + lineNumber + ". Must be in [0, " + (getDocument().getLineCount() - 1) + "]");
    }

    int offset = DocumentUtil.getFirstNonSpaceCharOffset(getDocument(), lineNumber);
    HighlighterTargetArea area = HighlighterTargetArea.LINES_IN_RANGE;
    Consumer<RangeHighlighterEx> changeAction = textAttributes == null ? null : ex -> ex.setTextAttributes(textAttributes);
    return addRangeHighlighterAndChangeAttributes(textAttributesKey, offset, offset, layer, area, false, changeAction);
  }

  @Override
  @Nullable
  public RangeHighlighterEx addPersistentLineHighlighter(@Nullable TextAttributesKey textAttributesKey, int lineNumber, int layer) {
    return addPersistentLineHighlighter(textAttributesKey, null, lineNumber, layer);
  }

  @Override
  @Nullable
  public RangeHighlighterEx addPersistentLineHighlighter(int lineNumber, int layer, @Nullable TextAttributes textAttributes) {
    return addPersistentLineHighlighter(null, textAttributes, lineNumber, layer);
  }

  @Nullable
  private RangeHighlighterEx addPersistentLineHighlighter(@Nullable TextAttributesKey textAttributesKey,
                                                          @Nullable TextAttributes textAttributes,
                                                          int lineNumber,
                                                          int layer) {
    if (isNotValidLine(lineNumber)) {
      return null;
    }
    int offset = DocumentUtil.getFirstNonSpaceCharOffset(getDocument(), lineNumber);

    Consumer<RangeHighlighterEx> changeAction = textAttributes == null ? null : ex -> ex.setTextAttributes(textAttributes);

    PersistentRangeHighlighterImpl rangeHighlighter = PersistentRangeHighlighterImpl.create(
      this, offset, layer, HighlighterTargetArea.LINES_IN_RANGE, textAttributesKey, false);
    return addRangeHighlighter(rangeHighlighter, changeAction);
  }

  private boolean isNotValidLine(int lineNumber) {
    return lineNumber >= getDocument().getLineCount() || lineNumber < 0;
  }

  // NB: Can return invalid highlighters
  @Override
  public RangeHighlighter @NotNull [] getAllHighlighters() {
    RangeHighlighter[] cachedHighlighters = myCachedHighlighters;
    if (cachedHighlighters == null) {
      int size = myHighlighterTree.size() + myHighlighterTreeForLines.size();
      if (size == 0) return RangeHighlighter.EMPTY_ARRAY;
      List<RangeHighlighterEx> list = new ArrayList<>(size);
      CommonProcessors.CollectProcessor<RangeHighlighterEx> collectProcessor = new CommonProcessors.CollectProcessor<>(list);
      myHighlighterTree.processAll(collectProcessor);
      myHighlighterTreeForLines.processAll(collectProcessor);
      myCachedHighlighters = cachedHighlighters = list.toArray(RangeHighlighter.EMPTY_ARRAY);
    }
    return cachedHighlighters;
  }

  @NotNull
  @Override
  public RangeHighlighterEx addRangeHighlighterAndChangeAttributes(@Nullable TextAttributesKey textAttributesKey,
                                                                   int startOffset,
                                                                   int endOffset,
                                                                   int layer,
                                                                   @NotNull HighlighterTargetArea targetArea,
                                                                   boolean isPersistent,
                                                                   @Nullable Consumer<? super RangeHighlighterEx> changeAttributesAction) {
    RangeHighlighterImpl rangeHighlighter = isPersistent ?
      PersistentRangeHighlighterImpl.create(this, startOffset, layer, targetArea, textAttributesKey, true)
      : new RangeHighlighterImpl(this, startOffset, endOffset, layer, targetArea, textAttributesKey, false, false);
    return addRangeHighlighter(rangeHighlighter, changeAttributesAction);
  }

  @NotNull
  private RangeHighlighterEx addRangeHighlighter(@NotNull RangeHighlighterImpl highlighter,
                                                 @Nullable Consumer<? super RangeHighlighterEx> changeAttributesAction) {
    myCachedHighlighters = null;
    if (changeAttributesAction != null) {
      highlighter.changeAttributesNoEvents(changeAttributesAction);
    }
    fireAfterAdded(highlighter);
    return highlighter;
  }

  @Override
  public void changeAttributesInBatch(@NotNull RangeHighlighterEx highlighter,
                                      @NotNull Consumer<? super RangeHighlighterEx> changeAttributesAction) {
    byte changeStatus = ((RangeHighlighterImpl)highlighter).changeAttributesNoEvents(changeAttributesAction);
    if (BitUtil.isSet(changeStatus, RangeHighlighterImpl.CHANGED_MASK)) {
      fireAttributesChanged(highlighter, 
                            BitUtil.isSet(changeStatus, RangeHighlighterImpl.RENDERERS_CHANGED_MASK),
                            BitUtil.isSet(changeStatus, RangeHighlighterImpl.FONT_STYLE_CHANGED_MASK),
                            BitUtil.isSet(changeStatus, RangeHighlighterImpl.FOREGROUND_COLOR_CHANGED_MASK));
    }
  }

  public void addRangeHighlighter(@NotNull RangeHighlighterEx marker,
                                  int start,
                                  int end,
                                  boolean greedyToLeft,
                                  boolean greedyToRight,
                                  int layer) {
    treeFor(marker).addInterval(marker, start, end, greedyToLeft, greedyToRight, false, layer);
  }

  @NotNull
  RangeHighlighterTree treeFor(RangeHighlighter marker) {
    return marker.getTargetArea() == HighlighterTargetArea.EXACT_RANGE ? myHighlighterTree : myHighlighterTreeForLines;
  }

  @Override
  @NotNull
  public RangeHighlighter addRangeHighlighter(@Nullable TextAttributesKey textAttributesKey,
                                              int startOffset,
                                              int endOffset,
                                              int layer,
                                              @NotNull HighlighterTargetArea targetArea) {
    return addRangeHighlighterAndChangeAttributes(textAttributesKey, startOffset, endOffset, layer, targetArea, false,
                                                  null);
  }

  @Override
  public @NotNull RangeHighlighter addRangeHighlighter(int startOffset,
                                                       int endOffset,
                                                       int layer,
                                                       @Nullable TextAttributes textAttributes,
                                                       @NotNull HighlighterTargetArea targetArea) {
    Consumer<RangeHighlighterEx> changeAction = textAttributes == null ? null : ex -> ex.setTextAttributes(textAttributes);
    return addRangeHighlighterAndChangeAttributes(null, startOffset, endOffset, layer, targetArea, false, changeAction);
  }

  @Override
  public void removeHighlighter(@NotNull RangeHighlighter segmentHighlighter) {
    myCachedHighlighters = null;
    if (!segmentHighlighter.isValid()) return;

    treeFor(segmentHighlighter).removeInterval((RangeHighlighterEx)segmentHighlighter);
  }

  @Override
  public void removeAllHighlighters() {
    for (RangeHighlighter highlighter : getAllHighlighters()) {
      highlighter.dispose();
    }
    myCachedHighlighters = null;
    myHighlighterTree.clear();
    myHighlighterTreeForLines.clear();
  }

  @Override
  @NotNull
  public Document getDocument() {
    return myDocument;
  }

  @Override
  public void addMarkupModelListener(@NotNull Disposable parentDisposable, @NotNull final MarkupModelListener listener) {
    myListeners.add(listener);
    Disposer.register(parentDisposable, () -> removeMarkupModelListener(listener));
  }

  private void removeMarkupModelListener(@NotNull MarkupModelListener listener) {
    boolean success = myListeners.remove(listener);
    LOG.assertTrue(success);
  }

  @Override
  public void setRangeHighlighterAttributes(@NotNull final RangeHighlighter highlighter, @NotNull final TextAttributes textAttributes) {
    ((RangeHighlighterEx)highlighter).setTextAttributes(textAttributes);
  }

  /**
   * @deprecated use {@code RangeHighlighterEx.setXXX()} methods to fire changes
   */
  @Deprecated
  @Override
  public void fireAttributesChanged(@NotNull RangeHighlighterEx highlighter, boolean renderersChanged, boolean fontStyleOrColorChanged) {
    fireAttributesChanged(highlighter, renderersChanged, fontStyleOrColorChanged, fontStyleOrColorChanged);
  }

  void fireAttributesChanged(@NotNull RangeHighlighterEx highlighter,
                             boolean renderersChanged, boolean fontStyleChanged, boolean foregroundColorChanged) {
    if (highlighter.isValid()) {
      for (MarkupModelListener listener : myListeners) {
        listener.attributesChanged(highlighter, renderersChanged, fontStyleChanged, foregroundColorChanged);
      }
    }
  }

  private void fireAfterAdded(@NotNull RangeHighlighterEx segmentHighlighter) {
    for (MarkupModelListener listener : myListeners) {
      listener.afterAdded(segmentHighlighter);
    }
  }

  void fireBeforeRemoved(@NotNull RangeHighlighterEx segmentHighlighter) {
    for (MarkupModelListener listener : myListeners) {
      listener.beforeRemoved(segmentHighlighter);
    }
  }

  void fireAfterRemoved(@NotNull RangeHighlighterEx segmentHighlighter) {
    for (MarkupModelListener listener : myListeners) {
      listener.afterRemoved(segmentHighlighter);
    }
  }

  @Override
  public boolean containsHighlighter(@NotNull final RangeHighlighter highlighter) {
    Processor<RangeHighlighterEx> equalId = h -> h.getId() != ((RangeHighlighterEx)highlighter).getId();
    return highlighter.isValid() && !treeFor(highlighter).processOverlappingWith(highlighter.getStartOffset(), highlighter.getEndOffset(), equalId);
  }

  @Override
  public boolean processRangeHighlightersOverlappingWith(int start, int end, @NotNull Processor<? super RangeHighlighterEx> processor) {
    MarkupIterator<RangeHighlighterEx> iterator = overlappingIterator(start, end);
    try {
      while (iterator.hasNext()) {
        if (!processor.process(iterator.next())) {
          return false;
        }
      }
      return true;
    }
    finally {
      iterator.dispose();
    }
  }

  @Override
  public boolean processRangeHighlightersOutside(int start, int end, @NotNull Processor<? super RangeHighlighterEx> processor) {
    return myHighlighterTree.processOverlappingWithOutside(start, end, processor)
           && myHighlighterTreeForLines.processOverlappingWithOutside(start, end, processor);
  }

  @Override
  @NotNull
  public MarkupIterator<RangeHighlighterEx> overlappingIterator(int startOffset, int endOffset) {
    startOffset = Math.max(0,startOffset);
    endOffset = Math.max(startOffset, endOffset);
    return IntervalTreeImpl
      .mergingOverlappingIterator(myHighlighterTree, new ProperTextRange(startOffset, endOffset), myHighlighterTreeForLines,
                                  roundToLineBoundaries(getDocument(), startOffset, endOffset), RangeHighlighterEx.BY_AFFECTED_START_OFFSET);
  }

  @NotNull
  public static TextRange roundToLineBoundaries(@NotNull Document document, int startOffset, int endOffset) {
    int textLength = document.getTextLength();
    int lineStartOffset = startOffset <= 0 ? 0 : startOffset > textLength ? textLength : document.getLineStartOffset(document.getLineNumber(startOffset));
    int lineEndOffset = endOffset <= 0 ? 0 : endOffset >= textLength ? textLength : document.getLineEndOffset(document.getLineNumber(endOffset));
    return new ProperTextRange(lineStartOffset, lineEndOffset);
  }
}
