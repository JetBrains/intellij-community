/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import gnu.trove.THashMap;

import java.util.Map;

/**
 * @author peter
 */
public class OffsetMap {
  private final Document myDocument;
  private final Map<OffsetKey, RangeMarker> myMap = new THashMap<OffsetKey, RangeMarker>();

  public OffsetMap(final Document document) {
    myDocument = document;
  }

  /**
   * @param key key
   * @return offset An offset registered earlier with this key.
   * -1 if offset wasn't registered or became invalidated due to document changes
   */
  public int getOffset(OffsetKey key) {
    final RangeMarker marker = myMap.get(key);
    if (marker == null) return -1;
    if (!marker.isValid()) {
      removeOffset(key);
      return -1;
    }

    final int endOffset = marker.getEndOffset();
    if (marker.getStartOffset() != endOffset) {
      addOffset(key, endOffset, true);
    }
    return endOffset;
  }

  /**
   * Register key-offset binding. Offset will change together with {@link Document} editing operations
   * unless an operation replaces completely the offset vicinity.
   * @param key
   * @param offset
   * @param moveableToRight whether the offset should move right when a string is inserted directly into
   * this offset position
   */
  public void addOffset(OffsetKey key, int offset, boolean moveableToRight) {
    if (offset < 0) {
      removeOffset(key);
      return;
    }

    final RangeMarker marker = myDocument.createRangeMarker(offset, offset);
    marker.setGreedyToRight(moveableToRight);
    myMap.put(key, marker);
  }

  public void removeOffset(OffsetKey key) {
    myMap.remove(key);
  }
}
