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
package com.intellij.codeInsight.completion;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import gnu.trove.THashMap;

import java.util.Map;
import java.util.Set;

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
      addOffset(key, endOffset);
    }
    return endOffset;
  }

  /**
   * Register key-offset binding. Offset will change together with {@link Document} editing operations
   * unless an operation replaces completely the offset vicinity.
   * @param key
   * @param offset
   */
  public void addOffset(OffsetKey key, int offset) {
    if (offset < 0) {
      removeOffset(key);
      return;
    }

    final RangeMarker marker = myDocument.createRangeMarker(offset, offset);
    marker.setGreedyToRight(key.isMoveableToRight());
    myMap.put(key, marker);
  }

  public void removeOffset(OffsetKey key) {
    myMap.remove(key);
  }

  public Set<OffsetKey> keySet() {
    return myMap.keySet();
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder("OffsetMap:");
    for (final OffsetKey key : myMap.keySet()) {
      builder.append(key).append("->").append(myMap.get(key)).append(";");
    }
    return builder.toString();
  }
}
