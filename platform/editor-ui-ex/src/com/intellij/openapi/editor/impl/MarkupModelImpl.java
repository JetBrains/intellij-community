// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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

  private volatile RangeHighlighter[] myCachedHighlighters;
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
  public String toString() {
    return "MarkupModel for "+myDocument;
  }

  @Override
  public @NotNull RangeHighlighter addLineHighlighter(int line, int layer, @Nullable TextAttributes textAttributes) {
    return addLineHighlighter(null, textAttributes, line, layer);
  }

  @Override
  public @NotNull RangeHighlighter addLineHighlighter(@Nullable TextAttributesKey textAttributesKey, int lineNumber, int layer) {
    return addLineHighlighter(textAttributesKey, null, lineNumber, layer);
  }

  private @NotNull RangeHighlighter addLineHighlighter(@Nullable TextAttributesKey textAttributesKey,
                                                       @Nullable TextAttributes textAttributes,
                                                       int lineNumber,
                                                       int layer) {
    Document document = getDocument();
    if (!DocumentUtil.isValidLine(lineNumber, document)) {
      throw new IndexOutOfBoundsException("lineNumber:" + lineNumber + ". Must be in [0, " + (document.getLineCount() - 1) + "]");
    }

    int offset = DocumentUtil.getFirstNonSpaceCharOffset(document, lineNumber);
    HighlighterTargetArea area = HighlighterTargetArea.LINES_IN_RANGE;
    Consumer<RangeHighlighterEx> changeAction = textAttributes == null ? null : ex -> ex.setTextAttributes(textAttributes);
    return addRangeHighlighterAndChangeAttributes(textAttributesKey, offset, offset, layer, area, false, changeAction);
  }

  @Override
  public @Nullable RangeHighlighterEx addPersistentLineHighlighter(@Nullable TextAttributesKey textAttributesKey, int lineNumber, int layer) {
    return addPersistentLineHighlighter(textAttributesKey, null, lineNumber, layer);
  }

  @Override
  public @Nullable RangeHighlighterEx addPersistentLineHighlighter(int lineNumber, int layer, @Nullable TextAttributes textAttributes) {
    return addPersistentLineHighlighter(null, textAttributes, lineNumber, layer);
  }

  private @Nullable RangeHighlighterEx addPersistentLineHighlighter(@Nullable TextAttributesKey textAttributesKey,
                                                                    @Nullable TextAttributes textAttributes,
                                                                    int lineNumber,
                                                                    int layer) {
    Document document = getDocument();
    if (!DocumentUtil.isValidLine(lineNumber, document)) {
      return null;
    }
    int offset = DocumentUtil.getFirstNonSpaceCharOffset(document, lineNumber);

    Consumer<RangeHighlighterEx> changeAction = textAttributes == null ? null : ex -> ex.setTextAttributes(textAttributes);

    PersistentRangeHighlighterImpl highlighter = PersistentRangeHighlighterImpl.create(
      this, offset, layer, HighlighterTargetArea.LINES_IN_RANGE, textAttributesKey, false);
    addRangeHighlighter(highlighter, changeAction);
    return highlighter;
  }

  // NB: Can return invalid highlighters
  @Override
  public @NotNull RangeHighlighter @NotNull [] getAllHighlighters() {
    RangeHighlighter[] cachedHighlighters = myCachedHighlighters;
    if (cachedHighlighters == null) {
      myCachedHighlighters = cachedHighlighters = computeAllHighlighters();
    }
    return cachedHighlighters;
  }

  private @NotNull RangeHighlighter @NotNull [] computeAllHighlighters() {
    int size = myHighlighterTree.size() + myHighlighterTreeForLines.size();
    if (size == 0) return RangeHighlighter.EMPTY_ARRAY;
    List<RangeHighlighterEx> list = new ArrayList<>(size);
    CommonProcessors.CollectProcessor<RangeHighlighterEx> collectProcessor = new CommonProcessors.CollectProcessor<>(list);
    myHighlighterTree.processAll(collectProcessor);
    myHighlighterTreeForLines.processAll(collectProcessor);
    return list.toArray(RangeHighlighter.EMPTY_ARRAY);
  }
  @Override
  public @NotNull RangeHighlighterEx addRangeHighlighterAndChangeAttributes(@Nullable TextAttributesKey textAttributesKey,
                                                                            int startOffset,
                                                                            int endOffset,
                                                                            int layer,
                                                                            @NotNull HighlighterTargetArea targetArea,
                                                                            boolean isPersistent,
                                                                            @Nullable Consumer<? super RangeHighlighterEx> changeAttributesAction) {
    RangeHighlighterImpl highlighter = isPersistent ?
      PersistentRangeHighlighterImpl.create(this, startOffset, layer, targetArea, textAttributesKey, true)
      : new RangeHighlighterImpl(this, startOffset, endOffset, layer, targetArea, textAttributesKey, false, false);
    addRangeHighlighter(highlighter, changeAttributesAction);
    return highlighter;
  }

  private void addRangeHighlighter(@NotNull RangeHighlighterImpl highlighter, @Nullable Consumer<? super RangeHighlighterEx> changeAttributesAction) {
    myCachedHighlighters = null;
    if (changeAttributesAction != null) {
      highlighter.changeAttributesNoEvents(changeAttributesAction);
    }
    fireAfterAdded(highlighter);
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
  RangeHighlighterTree treeFor(@NotNull RangeHighlighter highlighter) {
    return highlighter.getTargetArea() == HighlighterTargetArea.EXACT_RANGE ? myHighlighterTree : myHighlighterTreeForLines;
  }

  @Override
  public @NotNull RangeHighlighter addRangeHighlighter(@Nullable TextAttributesKey textAttributesKey,
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
  public void removeHighlighter(@NotNull RangeHighlighter highlighter) {
    myCachedHighlighters = null;
    if (!highlighter.isValid()) return;

    treeFor(highlighter).removeInterval((RangeHighlighterEx)highlighter);
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
  public @NotNull Document getDocument() {
    return myDocument;
  }

  @Override
  public void addMarkupModelListener(@NotNull Disposable parentDisposable, final @NotNull MarkupModelListener listener) {
    myListeners.add(listener);
    Disposer.register(parentDisposable, () -> removeMarkupModelListener(listener));
  }

  private void removeMarkupModelListener(@NotNull MarkupModelListener listener) {
    boolean success = myListeners.remove(listener);
    LOG.assertTrue(success);
  }

  @Override
  public void setRangeHighlighterAttributes(final @NotNull RangeHighlighter highlighter, final @NotNull TextAttributes textAttributes) {
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

  private void fireAfterAdded(@NotNull RangeHighlighterEx highlighter) {
    for (MarkupModelListener listener : myListeners) {
      listener.afterAdded(highlighter);
    }
  }

  void fireBeforeRemoved(@NotNull RangeHighlighterEx highlighter) {
    myCachedHighlighters = null;
    for (MarkupModelListener listener : myListeners) {
      listener.beforeRemoved(highlighter);
    }
  }

  void fireAfterRemoved(@NotNull RangeHighlighterEx highlighter) {
    for (MarkupModelListener listener : myListeners) {
      listener.afterRemoved(highlighter);
    }
  }

  @Override
  public boolean containsHighlighter(final @NotNull RangeHighlighter highlighter) {
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
  public @NotNull MarkupIterator<RangeHighlighterEx> overlappingIterator(int startOffset, int endOffset) {
    startOffset = Math.max(0,startOffset);
    endOffset = Math.max(startOffset, endOffset);
    return IntervalTreeImpl
      .mergingOverlappingIterator(myHighlighterTree, new ProperTextRange(startOffset, endOffset), myHighlighterTreeForLines,
                                  roundToLineBoundaries(getDocument(), startOffset, endOffset), RangeHighlighterEx.BY_AFFECTED_START_OFFSET);
  }

  public static @NotNull TextRange roundToLineBoundaries(@NotNull Document document, int startOffset, int endOffset) {
    int textLength = document.getTextLength();
    int lineStartOffset = startOffset <= 0 ? 0 : startOffset > textLength ? textLength : document.getLineStartOffset(document.getLineNumber(startOffset));
    int lineEndOffset = endOffset <= 0 ? 0 : endOffset >= textLength ? textLength : document.getLineEndOffset(document.getLineNumber(endOffset));
    return new ProperTextRange(lineStartOffset, lineEndOffset);
  }
}
