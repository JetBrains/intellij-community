/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Apr 19, 2002
 * Time: 2:26:19 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.MarkupIterator;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MarkupModelImpl extends UserDataHolderBase implements MarkupModelEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.MarkupModelImpl");
  private final DocumentEx myDocument;

  private RangeHighlighter[] myCachedHighlighters;
  private final List<MarkupModelListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final RangeHighlighterTree myHighlighterTree;          // this tree holds regular highlighters with target = HighlighterTargetArea.EXACT_RANGE
  private final RangeHighlighterTree myHighlighterTreeForLines;  // this tree holds line range highlighters with target = HighlighterTargetArea.LINES_IN_RANGE

  MarkupModelImpl(@NotNull DocumentEx document) {
    myDocument = document;
    myHighlighterTree = new RangeHighlighterTree(document, this);
    myHighlighterTreeForLines = new RangeHighlighterTree(document, this);
  }

  @Override
  public void dispose() {
    myHighlighterTree.dispose();
    myHighlighterTreeForLines.dispose();
  }

  @Override
  @NotNull
  public RangeHighlighter addLineHighlighter(int lineNumber, int layer, TextAttributes textAttributes) {
    if (isNotValidLine(lineNumber)) {
      throw new IndexOutOfBoundsException("lineNumber:" + lineNumber + ". Must be in [0, " + (getDocument().getLineCount() - 1) + "]");
    }

    int offset = DocumentUtil.getFirstNonSpaceCharOffset(getDocument(), lineNumber);
    return addRangeHighlighter(offset, offset, layer, textAttributes, HighlighterTargetArea.LINES_IN_RANGE);
  }

  @Override
  @Nullable
  public RangeHighlighterEx addPersistentLineHighlighter(int lineNumber, int layer, TextAttributes textAttributes) {
    if (isNotValidLine(lineNumber)) {
      return null;
    }

    int offset = DocumentUtil.getFirstNonSpaceCharOffset(getDocument(), lineNumber);
    return addRangeHighlighter(PersistentRangeHighlighterImpl.create(this, offset, layer, HighlighterTargetArea.LINES_IN_RANGE, textAttributes, false), null);
  }

  private boolean isNotValidLine(int lineNumber) {
    return lineNumber >= getDocument().getLineCount() || lineNumber < 0;
  }

  // NB: Can return invalid highlighters
  @Override
  @NotNull
  public RangeHighlighter[] getAllHighlighters() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myCachedHighlighters == null) {
      int size = myHighlighterTree.size() + myHighlighterTreeForLines.size();
      if (size == 0) return RangeHighlighter.EMPTY_ARRAY;
      List<RangeHighlighterEx> list = new ArrayList<>(size);
      CommonProcessors.CollectProcessor<RangeHighlighterEx> collectProcessor = new CommonProcessors.CollectProcessor<>(list);
      myHighlighterTree.process(collectProcessor);
      myHighlighterTreeForLines.process(collectProcessor);
      myCachedHighlighters = list.toArray(new RangeHighlighter[list.size()]);
    }
    return myCachedHighlighters;
  }

  @NotNull
  @Override
  public RangeHighlighterEx addRangeHighlighterAndChangeAttributes(int startOffset,
                                                                   int endOffset,
                                                                   int layer,
                                                                   TextAttributes textAttributes,
                                                                   @NotNull HighlighterTargetArea targetArea,
                                                                   boolean isPersistent,
                                                                   @Nullable Consumer<RangeHighlighterEx> changeAttributesAction) {
    return addRangeHighlighter(isPersistent
                               ? PersistentRangeHighlighterImpl.create(this, startOffset, layer, targetArea, textAttributes, true)
                               : new RangeHighlighterImpl(this, startOffset, endOffset, layer, targetArea, textAttributes, false,
                                                          false), changeAttributesAction);
  }

  @NotNull
  private RangeHighlighterEx addRangeHighlighter(@NotNull RangeHighlighterImpl highlighter,
                                                 @Nullable Consumer<RangeHighlighterEx> changeAttributesAction) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myCachedHighlighters = null;
    if (changeAttributesAction != null) {
      highlighter.changeAttributesNoEvents(changeAttributesAction);
    }
    fireAfterAdded(highlighter);
    return highlighter;
  }

  @Override
  public void changeAttributesInBatch(@NotNull RangeHighlighterEx highlighter,
                                      @NotNull Consumer<RangeHighlighterEx> changeAttributesAction) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    byte changeStatus = ((RangeHighlighterImpl)highlighter).changeAttributesNoEvents(changeAttributesAction);
    if (BitUtil.isSet(changeStatus, RangeHighlighterImpl.CHANGED_MASK)) {
      fireAttributesChanged(highlighter, 
                            BitUtil.isSet(changeStatus, RangeHighlighterImpl.RENDERERS_CHANGED_MASK),
                            BitUtil.isSet(changeStatus, RangeHighlighterImpl.FONT_STYLE_OR_COLOR_CHANGED_MASK));
    }
  }

  @Override
  public void addRangeHighlighter(@NotNull RangeHighlighterEx marker,
                                  int start,
                                  int end,
                                  boolean greedyToLeft,
                                  boolean greedyToRight,
                                  int layer) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    treeFor(marker).addInterval(marker, start, end, greedyToLeft, greedyToRight, layer);
  }

  private RangeHighlighterTree treeFor(RangeHighlighter marker) {
    return marker.getTargetArea() == HighlighterTargetArea.EXACT_RANGE ? myHighlighterTree : myHighlighterTreeForLines;
  }

  @Override
  @NotNull
  public RangeHighlighter addRangeHighlighter(int startOffset,
                                              int endOffset,
                                              int layer,
                                              TextAttributes textAttributes,
                                              @NotNull HighlighterTargetArea targetArea) {
    return addRangeHighlighterAndChangeAttributes(startOffset, endOffset, layer, textAttributes, targetArea, false, null);
  }

  @Override
  public void removeHighlighter(@NotNull RangeHighlighter segmentHighlighter) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myCachedHighlighters = null;
    if (!segmentHighlighter.isValid()) return;

    boolean removed = treeFor(segmentHighlighter).removeInterval((RangeHighlighterEx)segmentHighlighter);
    LOG.assertTrue(removed);
  }

  @Override
  public void removeAllHighlighters() {
    ApplicationManager.getApplication().assertIsDispatchThread();
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
    ApplicationManager.getApplication().assertIsDispatchThread();
    ((RangeHighlighterEx)highlighter).setTextAttributes(textAttributes);
  }

  @Override
  public void fireAttributesChanged(@NotNull RangeHighlighterEx segmentHighlighter,
                                    boolean renderersChanged, boolean fontStyleOrColorChanged) {
    for (MarkupModelListener listener : myListeners) {
      listener.attributesChanged(segmentHighlighter, renderersChanged, fontStyleOrColorChanged);
    }
  }

  @Override
  public void fireAfterAdded(@NotNull RangeHighlighterEx segmentHighlighter) {
    for (MarkupModelListener listener : myListeners) {
      listener.afterAdded(segmentHighlighter);
    }
  }

  @Override
  public void fireBeforeRemoved(@NotNull RangeHighlighterEx segmentHighlighter) {
    for (MarkupModelListener listener : myListeners) {
      listener.beforeRemoved(segmentHighlighter);
    }
  }

  @Override
  public boolean containsHighlighter(@NotNull final RangeHighlighter highlighter) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    Processor<RangeHighlighterEx> equalId = h -> h.getId() != ((RangeHighlighterEx)highlighter).getId();
    return !treeFor(highlighter).processOverlappingWith(highlighter.getStartOffset(), highlighter.getEndOffset(), equalId);
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
      .mergingOverlappingIterator(myHighlighterTree, new TextRangeInterval(startOffset, endOffset), myHighlighterTreeForLines,
                                  roundToLineBoundaries(getDocument(), startOffset, endOffset), RangeHighlighterEx.BY_AFFECTED_START_OFFSET);
  }

  @NotNull
  public static TextRangeInterval roundToLineBoundaries(@NotNull Document document, int startOffset, int endOffset) {
    int textLength = document.getTextLength();
    int lineStartOffset = startOffset <= 0 ? 0 : startOffset > textLength ? textLength : document.getLineStartOffset(document.getLineNumber(startOffset));
    int lineEndOffset = endOffset <= 0 ? 0 : endOffset >= textLength ? textLength : document.getLineEndOffset(document.getLineNumber(endOffset));
    return new TextRangeInterval(lineStartOffset, lineEndOffset);
  }
}
