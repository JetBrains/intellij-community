// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.MathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.Supplier;

public final class LookupOffsets implements DocumentListener {
  private static final Logger LOG = Logger.getInstance(LookupOffsets.class);

  private @NotNull String myAdditionalPrefix = "";

  private boolean myStableStart;
  private @Nullable Supplier<String> myStartMarkerDisposeInfo = null;
  private @NotNull RangeMarker myLookupStartMarker;
  private int myRemovedPrefix;
  private RangeMarker myLookupOriginalStartMarker;
  private final Editor myEditor;

  public LookupOffsets(Editor editor) {
    myEditor = editor;
    int caret = getPivotOffset();
    myLookupOriginalStartMarker = createLeftGreedyMarker(caret);
    myLookupStartMarker = createLeftGreedyMarker(caret);
    myEditor.getDocument().addDocumentListener(this);
  }

  @Override
  public void documentChanged(@NotNull DocumentEvent e) {
    if (!myLookupStartMarker.isValid()) {
      // in the scenario of concurrent document modifications by many remote clients (CWM) there may be a situation
      // when one client (or host) has created a lookup and the another client immediately deleted a text under the offset
      // so the range marker became invalid because it's out of the document bounds
      // here we try to recreate the range marker recalculating the start offset within the new document bounds
      int start = calculateStartOffset(0, true);
      myLookupStartMarker.dispose();
      myLookupStartMarker = createLeftGreedyMarker(start);
      myStartMarkerDisposeInfo = null;
    }

    if (!myLookupOriginalStartMarker.isValid()) {
      // the original marker shouldn't take into account myAdditionalPrefix and myRemovedPrefix values
      int start = calculateStartOffset(0, false);
      myLookupOriginalStartMarker.dispose();
      myLookupOriginalStartMarker = createLeftGreedyMarker(start);
    }
    // capture the trace in the case when range marker is still invalid by some reasons
    if (myStartMarkerDisposeInfo == null && !myLookupStartMarker.isValid()) {
      Throwable throwable = new Throwable();
      String eString = e.toString();
      myStartMarkerDisposeInfo = () -> eString + "\n" + ExceptionUtil.getThrowableText(throwable);
    }
  }

  private RangeMarker createLeftGreedyMarker(int start) {
    RangeMarker marker = myEditor.getDocument().createRangeMarker(start, start);
    marker.setGreedyToLeft(true);
    return marker;
  }

  private int getPivotOffset() {
    return myEditor.getSelectionModel().hasSelection()
                 ? myEditor.getSelectionModel().getSelectionStart()
                 : myEditor.getCaretModel().getOffset();
  }

  public @NotNull String getAdditionalPrefix() {
    return myAdditionalPrefix;
  }

  public void appendPrefix(char c) {
    LOG.debug("Append prefix :: char=" + c + ", myAdditionalPrefix: " + myAdditionalPrefix);
    myAdditionalPrefix += c;
  }

  public boolean truncatePrefix() {
    final int len = myAdditionalPrefix.length();
    if (len == 0) {
      myRemovedPrefix++;
      return false;
    }
    myAdditionalPrefix = myAdditionalPrefix.substring(0, len - 1);
    return true;
  }

  void destabilizeLookupStart() {
    myStableStart = false;
  }

  void checkMinPrefixLengthChanges(Collection<? extends LookupElement> items, LookupImpl lookup) {
    if (myStableStart) return;
    if (!lookup.isCalculating() && !items.isEmpty()) {
      myStableStart = true;
    }

    int minPrefixLength = items.isEmpty() ? 0 : Integer.MAX_VALUE;
    for (final LookupElement item : items) {
      if (!(item instanceof EmptyLookupItem)) {
        minPrefixLength = Math.min(lookup.itemMatcher(item).getPrefix().length(), minPrefixLength);
      }
    }

    int start = calculateStartOffset(minPrefixLength, true);
    if (myLookupStartMarker.isValid() && myLookupStartMarker.getStartOffset() == start && myLookupStartMarker.getEndOffset() == start) {
      return;
    }

    myLookupStartMarker.dispose();
    myLookupStartMarker = createLeftGreedyMarker(start);
    myStartMarkerDisposeInfo = null;
  }

  private int calculateStartOffset(int minLookupItemPrefixLength, boolean considerPrefixes) {
    int start = getPivotOffset() - minLookupItemPrefixLength;
    if (considerPrefixes) {
      start = start - myAdditionalPrefix.length() + myRemovedPrefix;
    }
    start = MathUtil.clamp(start, 0, myEditor.getDocument().getTextLength());
    return start;
  }

  int getLookupStart(@Nullable Throwable disposeTrace) {
    if (!myLookupStartMarker.isValid()) {
      throw new AssertionError(
        "Invalid lookup start: " + myLookupStartMarker + ", " + myEditor +
        ", disposeTrace=" + (disposeTrace == null ? null : ExceptionUtil.getThrowableText(disposeTrace)) +
        "\n================\n start dispose trace=" + (myStartMarkerDisposeInfo == null ? null : myStartMarkerDisposeInfo.get()));
    }
    return myLookupStartMarker.getStartOffset();
  }

  int getLookupOriginalStart() {
    return myLookupOriginalStartMarker.isValid() ? myLookupOriginalStartMarker.getStartOffset() : -1;
  }

  boolean performGuardedChange(Runnable change) {
    if (!myLookupStartMarker.isValid()) {
      throw new AssertionError("Invalid start: " + myEditor + ", trace=" + (myStartMarkerDisposeInfo == null ? null : myStartMarkerDisposeInfo
        .get()));
    }
    change.run();
    return myLookupStartMarker.isValid();
  }

  void clearAdditionalPrefix() {
    myAdditionalPrefix = "";
    myRemovedPrefix = 0;
  }

  void disposeMarkers() {
    myEditor.getDocument().removeDocumentListener(this);
    myLookupStartMarker.dispose();
    myLookupOriginalStartMarker.dispose();
  }

  public int getPrefixLength(LookupElement item, LookupImpl lookup) {
    return lookup.itemPattern(item).length() - myRemovedPrefix;
  }
}
