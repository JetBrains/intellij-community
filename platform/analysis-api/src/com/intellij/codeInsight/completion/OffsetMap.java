// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

public final class OffsetMap implements Disposable {
  private static final Logger LOG = Logger.getInstance(OffsetMap.class);
  private final Document myDocument;
  private final Map<OffsetKey, RangeMarker> myMap = new HashMap<>();
  private final Set<OffsetKey> myModified = new HashSet<>();
  private volatile boolean myDisposed;

  public OffsetMap(final Document document) {
    myDocument = document;
  }

  /**
   * @param key key
   * @return offset An offset registered earlier with this key.
   * -1 if offset wasn't registered or became invalidated due to document changes
   */
  public int getOffset(OffsetKey key) {
    synchronized (myMap) {
      final RangeMarker marker = myMap.get(key);
      if (marker == null) throw new IllegalArgumentException("Offset " + key + " is not registered");
      if (!marker.isValid()) {
        removeOffset(key);
        throw new IllegalStateException("Offset " + key + " is invalid: " + marker +
           ", document.valid=" + (!(myDocument instanceof DocumentWindow) || ((DocumentWindow)myDocument).isValid()) +
           (myDocument instanceof DocumentWindow ? " (injected: " + Arrays.toString(((DocumentWindow)myDocument).getHostRanges()) + ")" : "")
        );
      }

      final int endOffset = marker.getEndOffset();
      if (marker.getStartOffset() != endOffset) {
        saveOffset(key, endOffset, false);
      }
      return endOffset;
    }
  }

  public boolean containsOffset(OffsetKey key) {
    final RangeMarker marker = myMap.get(key);
    return marker != null && marker.isValid();
  }

  /**
   * Register key-offset binding. Offset will change together with {@link Document} editing operations
   * unless an operation replaces completely the offset vicinity.
   * @param key offset key
   * @param offset offset in the document
   */
  public void addOffset(OffsetKey key, int offset) {
    synchronized (myMap) {
      if (offset < 0) {
        removeOffset(key);
        return;
      }

      saveOffset(key, offset, true);
    }
  }

  private void saveOffset(OffsetKey key, int offset, boolean externally) {
    LOG.assertTrue(!myDisposed);
    if (externally && myMap.containsKey(key)) {
      myModified.add(key);
    }

    RangeMarker old = myMap.get(key);
    if (old != null) old.dispose();
    final RangeMarker marker = myDocument.createRangeMarker(offset, offset);
    marker.setGreedyToRight(key.isMovableToRight());
    myMap.put(key, marker);
  }

  public void removeOffset(OffsetKey key) {
    synchronized (myMap) {
      ProgressManager.checkCanceled();
      LOG.assertTrue(!myDisposed);
      myModified.add(key);
      RangeMarker old = myMap.get(key);
      if (old != null) old.dispose();

      myMap.remove(key);
    }
  }

  public List<OffsetKey> getAllOffsets() {
    synchronized (myMap) {
      ProgressManager.checkCanceled();
      LOG.assertTrue(!myDisposed);
      return ContainerUtil.filter(myMap.keySet(), this::containsOffset);
    }
  }

  @Override
  public String toString() {
    synchronized (myMap) {
      final StringBuilder builder = new StringBuilder("OffsetMap:");
      for (final OffsetKey key : myMap.keySet()) {
        builder.append(key).append("->").append(myMap.get(key)).append(";");
      }
      return builder.toString();
    }
  }

  public boolean wasModified(OffsetKey key) {
    synchronized (myMap) {
      return myModified.contains(key);
    }
  }

  @Override
  public void dispose() {
    synchronized (myMap) {
      myDisposed = true;
      for (RangeMarker rangeMarker : myMap.values()) {
        rangeMarker.dispose();
      }
    }
  }

  @ApiStatus.Internal
  @NotNull
  public Document getDocument() {
    return myDocument;
  }

  @ApiStatus.Internal
  @NotNull
  public OffsetMap copyOffsets(@NotNull Document anotherDocument) {
    if (anotherDocument.getTextLength() != myDocument.getTextLength()) {
      LOG.error("Different document lengths: " + myDocument.getTextLength() +
                " for " + myDocument +
                " and " + anotherDocument.getTextLength() +
                " for " + anotherDocument);
    }
    return mapOffsets(anotherDocument, Function.identity());
  }

  @ApiStatus.Internal
  @NotNull
  public OffsetMap mapOffsets(@NotNull Document anotherDocument, @NotNull Function<? super Integer, Integer> mapping) {
    OffsetMap result = new OffsetMap(anotherDocument);
    for (OffsetKey key : getAllOffsets()) {
      result.addOffset(key, mapping.apply(getOffset(key)));
    }
    return result;
  }
}
