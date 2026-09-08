// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.MarkupIterator;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.editor.ex.RangeMarkers;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.TextRangeScalarUtil;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.BitUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Consumer;
import com.intellij.util.DocumentUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MarkupModelImpl extends UserDataHolderBase implements MarkupModelEx {
  private static final Logger LOG = Logger.getInstance(MarkupModelImpl.class);
  private final DocumentEx myDocument;

  private volatile RangeHighlighter[] myCachedHighlighters;
  private final List<MarkupModelListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  /// this tree holds line range highlighters with [RangeHighlighter#getTargetArea()] = [HighlighterTargetArea#EXACT_RANGE]
  private final @Nullable RangeHighlighterTree myHighlighterTree;
  /// this tree holds line range highlighters with [RangeHighlighter#getTargetArea()] = [HighlighterTargetArea#LINES_IN_RANGE]
  private final @Nullable RangeHighlighterTree myHighlighterTreeForLines;
  /// Stores snapshot highlighters for documents that support document snapshots.
  private final @Nullable SnapshotHighlighterStorage mySnapshotHighlighterStorage;

  @ApiStatus.Internal
  protected MarkupModelImpl(@NotNull DocumentEx document) {
    myDocument = document;
    if (document instanceof DocumentImpl documentImpl && RangeMarkers.Holder.USE_PMARKER_IMPLEMENTATION) {
      myHighlighterTree = null;
      myHighlighterTreeForLines = null;
      mySnapshotHighlighterStorage = new SnapshotHighlighterStorage(this, documentImpl);
    }
    else {
      myHighlighterTree = new RangeHighlighterTree(this);
      myHighlighterTreeForLines = new RangeHighlighterTree(this);
      mySnapshotHighlighterStorage = null;
    }
  }

  @Override
  public void dispose() {
    SnapshotHighlighterStorage snapshotStorage = mySnapshotHighlighterStorage;
    if (snapshotStorage != null) {
      snapshotStorage.dispose();
    }
    else {
      Objects.requireNonNull(myHighlighterTree).dispose();
      Objects.requireNonNull(myHighlighterTreeForLines).dispose();
    }
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

    RangeHighlighterEx highlighter = mySnapshotHighlighterStorage != null ?
                                     SnapshotRangeHighlighterImpl.createPersistent(
                                       mySnapshotHighlighterStorage,
                                       offset,
                                       layer,
                                       HighlighterTargetArea.LINES_IN_RANGE,
                                       textAttributesKey,
                                       false
                                     ) :
                                     PersistentRangeHighlighterImpl.create(
                                       this, offset, layer, HighlighterTargetArea.LINES_IN_RANGE, textAttributesKey, false
                                     );
    changeAttributes(highlighter, changeAction);
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
    SnapshotHighlighterStorage snapshotStorage = mySnapshotHighlighterStorage;
    if (snapshotStorage != null) {
      List<RangeHighlighterEx> highlighters = snapshotStorage.collectAll();
      return highlighters.isEmpty() ? RangeHighlighter.EMPTY_ARRAY : highlighters.toArray(RangeHighlighter.EMPTY_ARRAY);
    }

    RangeHighlighterTree highlighterTree = Objects.requireNonNull(myHighlighterTree);
    RangeHighlighterTree lineHighlighterTree = Objects.requireNonNull(myHighlighterTreeForLines);
    int size = highlighterTree.size() + lineHighlighterTree.size();
    if (size == 0) return RangeHighlighter.EMPTY_ARRAY;
    List<RangeHighlighterEx> list = new ArrayList<>(size);
    CommonProcessors.CollectProcessor<RangeHighlighterEx> collectProcessor = new CommonProcessors.CollectProcessor<>(list);
    highlighterTree.processAll(collectProcessor);
    lineHighlighterTree.processAll(collectProcessor);
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
    if (mySnapshotHighlighterStorage != null) mySnapshotHighlighterStorage.assertMayChange();
    RangeHighlighterEx highlighter;
    if (mySnapshotHighlighterStorage != null) {
      highlighter = isPersistent ?
                    SnapshotRangeHighlighterImpl.createPersistent(
                      mySnapshotHighlighterStorage, startOffset, layer, targetArea, textAttributesKey, true
                    ) :
                    SnapshotRangeHighlighterImpl.create(
                      mySnapshotHighlighterStorage, startOffset, endOffset, layer, targetArea, textAttributesKey, false, false
                    );
    }
    else {
      highlighter = isPersistent ?
                    PersistentRangeHighlighterImpl.create(this, startOffset, layer, targetArea, textAttributesKey, true) :
                    new RangeHighlighterImpl(this, startOffset, endOffset, layer, targetArea, textAttributesKey, false, false);
    }
    changeAttributes(highlighter, changeAttributesAction);
    return highlighter;
  }

  private void changeAttributes(@NotNull RangeHighlighterEx highlighter,
                                @Nullable Consumer<? super RangeHighlighterEx> changeAttributesAction) {
    myCachedHighlighters = null;
    if (changeAttributesAction != null) {
      changeAttributesNoEvents(highlighter, changeAttributesAction);
    }
    fireAfterAdded(highlighter);
  }

  @Override
  public void changeAttributesInBatch(@NotNull RangeHighlighterEx highlighter,
                                      @NotNull Consumer<? super RangeHighlighterEx> changeAttributesAction) {
    byte changeStatus = changeAttributesNoEvents(highlighter, changeAttributesAction);
    if (BitUtil.isSet(changeStatus, RangeHighlighterImpl.CHANGED_MASK)) {
      fireAttributesChanged(highlighter, 
                            BitUtil.isSet(changeStatus, RangeHighlighterImpl.RENDERERS_CHANGED_MASK),
                            BitUtil.isSet(changeStatus, RangeHighlighterImpl.FONT_STYLE_CHANGED_MASK),
                            BitUtil.isSet(changeStatus, RangeHighlighterImpl.FOREGROUND_COLOR_CHANGED_MASK));
    }
  }

  private static byte changeAttributesNoEvents(@NotNull RangeHighlighterEx highlighter,
                                               @NotNull Consumer<? super RangeHighlighterEx> changeAttributesAction) {
    if (highlighter instanceof SnapshotRangeHighlighterImpl snapshotHighlighter) {
      return snapshotHighlighter.changeAttributesNoEvents(changeAttributesAction);
    }
    return ((RangeHighlighterImpl)highlighter).changeAttributesNoEvents(changeAttributesAction);
  }

  void invalidateHighlighterCache() {
    myCachedHighlighters = null;
  }

  @TestOnly
  boolean containsSnapshotHighlighterId(long markerId) {
    return mySnapshotHighlighterStorage != null && mySnapshotHighlighterStorage.containsHighlighterId(markerId);
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
  private RangeHighlighterTree treeFor(@NotNull RangeHighlighter highlighter) {
    return Objects.requireNonNull(
      highlighter.getTargetArea() == HighlighterTargetArea.EXACT_RANGE ? myHighlighterTree : myHighlighterTreeForLines
    );
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
    if (highlighter instanceof SnapshotRangeHighlighterImpl snapshotHighlighter) {
      snapshotHighlighter.dispose();
      return;
    }
    myCachedHighlighters = null;
    boolean removed = treeFor(highlighter).removeInterval((RangeHighlighterEx)highlighter);
    if (!removed && LOG.isDebugEnabled()) {
      LOG.debug("MMI.removeInterval=false: "+highlighter);
    }
    myCachedHighlighters = null;
  }

  @Override
  public void removeAllHighlighters() {
    for (RangeHighlighter highlighter : getAllHighlighters()) {
      highlighter.dispose();
    }
    myCachedHighlighters = null;
    if (mySnapshotHighlighterStorage == null) {
      Objects.requireNonNull(myHighlighterTree).clear();
      Objects.requireNonNull(myHighlighterTreeForLines).clear();
    }
  }

  @Override
  public @NotNull Document getDocument() {
    return myDocument;
  }

  @Override
  public void addMarkupModelListener(@NotNull Disposable parentDisposable, final @NotNull MarkupModelListener listener) {
    List<MarkupModelListener> listeners = myListeners;
    listeners.add(listener);
    boolean isRegistered = Disposer.tryRegister(parentDisposable, () -> {
      boolean success = listeners.remove(listener);
      LOG.assertTrue(success);
    });
    if (!isRegistered) {
      listeners.remove(listener);
    }
  }

  @Override
  public void setRangeHighlighterAttributes(final @NotNull RangeHighlighter highlighter, final @NotNull TextAttributes textAttributes) {
    ((RangeHighlighterEx)highlighter).setTextAttributes(textAttributes);
  }

  /// @deprecated use `RangeHighlighterEx.setXXX()` methods to fire changes
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
    restoreDeliciousInvariants(highlighter); // after attribute change the highlighter can become error-stripe-visible, or vice versa
  }

  private static void restoreDeliciousInvariants(@NotNull RangeHighlighter highlighter) {
    if (highlighter instanceof SnapshotRangeHighlighterImpl snapshotHighlighter) {
      snapshotHighlighter.updateFlavor();
      return;
    }
    RangeMarkerTree.RMNode<RangeMarkerEx> node = ((RangeMarkerImpl)highlighter).myNode;
    if (node != null) {
      node.attributesChanged();
    }
  }

  private void fireAfterAdded(@NotNull RangeHighlighterEx highlighter) {
    for (MarkupModelListener listener : myListeners) {
      listener.afterAdded(highlighter);
    }
    restoreDeliciousInvariants(highlighter);
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
    if (highlighter instanceof SnapshotRangeHighlighterImpl snapshotHighlighter) {
      return snapshotHighlighter.isValid();
    }
    Processor<RangeHighlighterEx> equalId = h -> h.getId() != ((RangeHighlighterEx)highlighter).getId();
    return highlighter.isValid() && !treeFor(highlighter).processOverlappingWith(highlighter.getStartOffset(), highlighter.getEndOffset(), equalId);
  }

  @Override
  public boolean processRangeHighlightersOverlappingWith(int start, int end, @NotNull Processor<? super RangeHighlighterEx> processor) {
    try (MarkupIterator<RangeHighlighterEx> iterator = overlappingIterator(start, end)) {
      return ContainerUtil.process(iterator, processor);
    }
  }

  @Override
  public boolean processRangeHighlightersOutside(int start, int end, @NotNull Processor<? super RangeHighlighterEx> processor) {
    SnapshotHighlighterStorage snapshotStorage = mySnapshotHighlighterStorage;
    if (snapshotStorage != null) {
      // TODO optimize if there's enough evidence it's in the hot path. Right now nobody's using it
      for (RangeHighlighterEx highlighter : snapshotStorage.collectAll()) {
        if ((highlighter.getAffectedAreaStartOffset() < start || highlighter.getAffectedAreaEndOffset() > end)
            && !processor.process(highlighter)) {
          return false;
        }
      }
      return true;
    }
    return Objects.requireNonNull(myHighlighterTree).processOverlappingWithOutside(start, end, processor)
           && Objects.requireNonNull(myHighlighterTreeForLines).processOverlappingWithOutside(start, end, processor);
  }

  @Override
  public @NotNull MarkupIterator<RangeHighlighterEx> overlappingIterator(int startOffset, int endOffset) {
    return overlappingIterator(startOffset, endOffset, (byte)0);
  }

  private @NotNull MarkupIterator<RangeHighlighterEx> overlappingIterator(int startOffset, int endOffset, byte tastePreference) {
    startOffset = TextRangeScalarUtil.coerce(startOffset, 0, getDocument().getTextLength());
    endOffset = TextRangeScalarUtil.coerce(endOffset, startOffset, getDocument().getTextLength());
    SnapshotHighlighterStorage snapshotStorage = mySnapshotHighlighterStorage;
    if (snapshotStorage != null) {
      return snapshotStorage.overlappingIterator(startOffset, endOffset, tastePreference);
    }
    return IntervalTreeImpl.mergingOverlappingIterator(
      Objects.requireNonNull(myHighlighterTree), new ProperTextRange(startOffset, endOffset),
      Objects.requireNonNull(myHighlighterTreeForLines), roundToLineBoundaries(getDocument(), startOffset, endOffset),
      tastePreference, RangeHighlighterEx.BY_AFFECTED_START_OFFSET
    );
  }

  @Override
  public @NotNull MarkupIterator<RangeHighlighterEx> overlappingErrorStripeIterator(int startOffset, int endOffset) {
    return overlappingIterator(startOffset, endOffset, RangeHighlighterTree.ERROR_STRIPE_FLAVOR_FLAG);
  }

  @Override
  public @NotNull MarkupIterator<RangeHighlighterEx> overlappingGutterIterator(int startOffset, int endOffset) {
    return overlappingIterator(startOffset, endOffset, RangeHighlighterTree.RENDER_IN_GUTTER_FLAVOR_FLAG);
  }

  public static @NotNull TextRange roundToLineBoundaries(@NotNull Document document, int startOffset, int endOffset) {
    int textLength = document.getTextLength();
    int lineStartOffset = startOffset <= 0 ? 0 : startOffset > textLength ? textLength : document.getLineStartOffset(document.getLineNumber(startOffset));
    int lineEndOffset = endOffset <= 0 ? 0 : endOffset >= textLength ? textLength : document.getLineEndOffset(document.getLineNumber(endOffset));
    return new ProperTextRange(lineStartOffset, lineEndOffset);
  }
}
