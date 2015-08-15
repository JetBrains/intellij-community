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
package com.intellij.psi.impl.smartPointers;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.FrozenDocument;
import com.intellij.openapi.editor.impl.ManualRangeMarker;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.WeakHashMap;
import com.intellij.util.containers.WeakValueHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author peter
 */
class MarkerCache {
  private final Set<ManualRangeMarker> myMarkerSet = Collections.newSetFromMap(new WeakHashMap<ManualRangeMarker, Boolean>());
  private WeakValueHashMap<RangeKey, ManualRangeMarker> myByRange = new WeakValueHashMap<RangeKey, ManualRangeMarker>();
  private volatile Trinity<Integer, Map<RangeKey, ManualRangeMarker>, FrozenDocument> myUpdatedRanges;

  @Nullable
  private static RangeKey keyOf(@NotNull ManualRangeMarker marker) {
    ProperTextRange range = marker.getRange();
    return range == null ? null : new RangeKey(range, marker.isGreedyLeft(), marker.isGreedyRight());
  }

  @NotNull
  synchronized ManualRangeMarker obtainMarker(@NotNull ProperTextRange range, @NotNull FrozenDocument frozen, boolean greedyLeft, boolean greedyRight) {
    WeakValueHashMap<RangeKey, ManualRangeMarker> byRange = getByRangeCache();
    RangeKey key = new RangeKey(range, greedyLeft, greedyRight);
    ManualRangeMarker marker = byRange.get(key);
    if (marker == null) {
      marker = new ManualRangeMarker(frozen, range, greedyLeft, greedyRight, true);
      myMarkerSet.add(marker);
      byRange.put(key, marker);
      myUpdatedRanges = null;
    }
    return marker;
  }

  private WeakValueHashMap<RangeKey, ManualRangeMarker> getByRangeCache() {
    if (myByRange == null) {
      myByRange = new WeakValueHashMap<RangeKey, ManualRangeMarker>();
      for (ManualRangeMarker marker : myMarkerSet) {
        RangeKey key = keyOf(marker);
        if (key != null) {
          myByRange.put(key, marker);
        }
      }
    }
    return myByRange;
  }

  private Map<RangeKey, ManualRangeMarker> getUpdatedMarkers(@NotNull FrozenDocument frozen, @NotNull List<DocumentEvent> events) {
    int eventCount = events.size();
    assert eventCount > 0;

    Trinity<Integer, Map<RangeKey, ManualRangeMarker>, FrozenDocument> cache = myUpdatedRanges;
    if (cache != null && cache.first.intValue() == eventCount) return cache.second;

    //noinspection SynchronizeOnThis
    synchronized (this) {
      cache = myUpdatedRanges;
      if (cache != null && cache.first.intValue() == eventCount) return cache.second;

      Map<RangeKey, ManualRangeMarker> answer = ContainerUtil.newHashMap();
      if (cache != null && cache.first < eventCount) {
        // apply only the new events
        answer.putAll(cache.second);
        frozen = applyEvents(cache.third, events.subList(cache.first, eventCount), answer);
      }
      else {
        for (ManualRangeMarker marker : myMarkerSet) {
          RangeKey key = keyOf(marker);
          if (key != null) {
            answer.put(key, marker);
          }
        }
        frozen = applyEvents(frozen, events, answer);
      }

      myUpdatedRanges = Trinity.create(eventCount, answer, frozen);
      return answer;
    }
  }

  private static FrozenDocument applyEvents(@NotNull FrozenDocument frozen,
                                  @NotNull List<DocumentEvent> events,
                                  Map<RangeKey, ManualRangeMarker> map) {
    for (DocumentEvent event : events) {
      frozen = frozen.applyEvent(event, 0);
      final DocumentEvent corrected =
        new DocumentEventImpl(frozen, event.getOffset(), event.getOldFragment(), event.getNewFragment(), event.getOldTimeStamp(),
                              event.isWholeTextReplaced());
      for (Map.Entry<RangeKey, ManualRangeMarker> entry : map.entrySet()) {
        ManualRangeMarker currentRange = entry.getValue();
        if (currentRange != null) {
          entry.setValue(currentRange.getUpdatedRange(corrected));
        }
      }
    }
    return frozen;
  }

  synchronized void updateMarkers(@NotNull FrozenDocument frozen, @NotNull List<DocumentEvent> events, @NotNull List<SmartPsiElementPointerImpl> pointers) {
    List<SelfElementInfo> infos = ContainerUtil.findAll(ContainerUtil.map(pointers, new NullableFunction<SmartPsiElementPointerImpl, SmartPointerElementInfo>() {
      @Nullable
      @Override
      public SmartPointerElementInfo fun(SmartPsiElementPointerImpl pointer) {
        return pointer.getElementInfo();
      }
    }), SelfElementInfo.class);

    Map<RangeKey, ManualRangeMarker> updated = getUpdatedMarkers(frozen, events);
    Map<ManualRangeMarker, ManualRangeMarker> newStates = ContainerUtil.newHashMap();
    for (SelfElementInfo info : infos) {
      ManualRangeMarker marker = info.getRangeMarker();
      RangeKey key = marker == null ? null : keyOf(marker);
      if (key != null) {
        newStates.put(marker, updated.get(key));
      }
    }

    myMarkerSet.clear();
    for (Map.Entry<ManualRangeMarker, ManualRangeMarker> entry : newStates.entrySet()) {
      ManualRangeMarker marker = entry.getKey();
      marker.applyState(entry.getValue());
      if (marker.isValid()) {
        myMarkerSet.add(marker); //re-add only alive markers
      }
    }
    myByRange = null;
    myUpdatedRanges = null;

    for (SelfElementInfo info : infos) {
      info.updateValidity();
    }
  }

  @Nullable
  ProperTextRange getUpdatedRange(@NotNull ManualRangeMarker marker, @NotNull FrozenDocument frozen, @NotNull List<DocumentEvent> events) {
    ManualRangeMarker updated = getUpdatedMarkers(frozen, events).get(keyOf(marker));
    return updated == null ? null : updated.getRange();
  }

  private static class RangeKey {
    final int start;
    final int end;
    final int flags;

    RangeKey(ProperTextRange range, boolean greedyLeft, boolean greedyRight) {
      start = range.getStartOffset();
      end = range.getEndOffset();
      flags = (greedyLeft ? 2 : 0) + (greedyRight ? 1 : 0);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof RangeKey)) return false;

      RangeKey key = (RangeKey)o;

      if (start != key.start) return false;
      if (end != key.end) return false;
      if (flags != key.flags) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = start;
      result = 31 * result + end;
      result = 31 * result + flags;
      return result;
    }

    @Override
    public String toString() {
      return "RangeKey{" +
             "start=" + start +
             ", end=" + end +
             ", flags=" + flags +
             '}';
    }
  }

}
