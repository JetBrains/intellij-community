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
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.Trinity;
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
  private WeakValueHashMap<Trinity, ManualRangeMarker> myByRange = new WeakValueHashMap<Trinity, ManualRangeMarker>();
  private volatile Trinity<Integer, Map<Trinity, ManualRangeMarker>, FrozenDocument> myUpdatedRanges;

  @Nullable
  private static Trinity keyOf(@NotNull ManualRangeMarker marker) {
    ProperTextRange range = marker.getRange();
    return range == null ? null : Trinity.create(range, marker.isGreedyLeft(), marker.isGreedyRight());
  }

  @NotNull
  synchronized ManualRangeMarker obtainMarker(@NotNull ProperTextRange range, @NotNull FrozenDocument frozen, boolean greedyLeft, boolean greedyRight) {
    Trinity key = Trinity.create(frozen, greedyLeft, greedyRight);
    if (myByRange == null) {
      myByRange = new WeakValueHashMap<Trinity, ManualRangeMarker>();
      for (ManualRangeMarker marker1 : myMarkerSet) {
        Trinity eachKey = keyOf(marker1);
        if (eachKey != null) {
          myByRange.put(eachKey, marker1);
        }
      }
    }
    ManualRangeMarker marker = myByRange.get(key);
    if (marker == null) {
      marker = new ManualRangeMarker(frozen, range, greedyLeft, greedyRight, true);
      myMarkerSet.add(marker);
      myByRange.put(key, marker);
      myUpdatedRanges = null;
    }
    return marker;
  }

  private Map<Trinity, ManualRangeMarker> getUpdatedMarkers(@NotNull FrozenDocument frozen, @NotNull List<DocumentEvent> events) {
    int eventCount = events.size();

    Trinity<Integer, Map<Trinity, ManualRangeMarker>, FrozenDocument> cache = myUpdatedRanges;
    if (cache != null && cache.first.intValue() == eventCount) return cache.second;

    //noinspection SynchronizeOnThis
    synchronized (this) {
      cache = myUpdatedRanges;
      if (cache != null && cache.first.intValue() == eventCount) return cache.second;

      Map<Trinity, ManualRangeMarker> answer = ContainerUtil.newHashMap();
      if (cache != null && cache.first < eventCount) {
        // apply only the new events
        answer.putAll(cache.second);
        frozen = applyEvents(cache.third, events.subList(cache.first, eventCount), answer);
      }
      else {
        for (ManualRangeMarker marker : myMarkerSet) {
          Trinity key = keyOf(marker);
          if (key != null) {
            answer.put(key, marker);
          }
        }
        frozen = applyEvents(frozen, events, answer);
      }

      if (eventCount > 0) {
        myUpdatedRanges = Trinity.create(eventCount, answer, frozen);
      }
      return answer;
    }
  }

  private static FrozenDocument applyEvents(@NotNull FrozenDocument frozen,
                                  @NotNull List<DocumentEvent> events,
                                  Map<Trinity, ManualRangeMarker> map) {
    for (DocumentEvent event : events) {
      frozen = frozen.applyEvent(event, 0);
      final DocumentEvent corrected = SelfElementInfo.withFrozen(frozen, event);
      for (Map.Entry<Trinity, ManualRangeMarker> entry : map.entrySet()) {
        ManualRangeMarker currentRange = entry.getValue();
        if (currentRange != null) {
          entry.setValue(currentRange.getUpdatedRange(corrected));
        }
      }
    }
    return frozen;
  }

  synchronized void updateMarkers(@NotNull FrozenDocument frozen, @NotNull List<DocumentEvent> events, @NotNull List<SmartPsiElementPointerImpl> pointers) {
    Map<Trinity, ManualRangeMarker> updated = getUpdatedMarkers(frozen, events);
    myMarkerSet.clear();
    for (SmartPsiElementPointerImpl pointer : pointers) {
      SmartPointerElementInfo info = pointer.getElementInfo();
      if (info instanceof SelfElementInfo) {
        ManualRangeMarker marker = ((SelfElementInfo)info).getRangeMarker();
        Trinity key = marker == null ? null : keyOf(marker);
        if (key != null) {
          marker.applyState(updated.get(key));
          ((SelfElementInfo)info).updateValidity();
          myMarkerSet.add(marker); //re-add only alive markers
        }
      }
    }
    myByRange = null;
    myUpdatedRanges = null;
  }

  @Nullable
  ProperTextRange getUpdatedRange(@NotNull ManualRangeMarker marker, @NotNull FrozenDocument frozen, @NotNull List<DocumentEvent> events) {
    ManualRangeMarker updated = getUpdatedMarkers(frozen, events).get(keyOf(marker));
    return updated == null ? null : updated.getRange();
  }

}
