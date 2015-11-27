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
import com.intellij.openapi.editor.impl.event.RetargetRangeMarkers;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UnfairTextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author peter
 */
class MarkerCache {
  static final Comparator<SelfElementInfo> INFO_COMPARATOR = new Comparator<SelfElementInfo>() {
    @Override
    public int compare(SelfElementInfo info1, SelfElementInfo info2) {
      int o1 = info1.getPsiStartOffset();
      int o2 = info2.getPsiStartOffset();
      if (o1 < 0 || o2 < 0) return o1 >= 0 ? -1 : o2 >= 0 ? 1 : 0; // infos without range go after infos with range
      if (o1 != o2) return o1 > o2 ? 1 : -1;

      o1 = info1.getPsiEndOffset();
      o2 = info2.getPsiEndOffset();
      if (o1 != o2) return o1 > o2 ? 1 : -1;

      return (info1.isForInjected() ? 1 : 0) - (info2.isForInjected() ? 1 : 0);
    }
  };
  private final SmartPointerManagerImpl.FilePointersList myPointers;
  private volatile UpdatedRanges myUpdatedRanges;

  MarkerCache(SmartPointerManagerImpl.FilePointersList pointers) {
    myPointers = pointers;
  }

  private UpdatedRanges getUpdatedMarkers(@NotNull FrozenDocument frozen, @NotNull List<DocumentEvent> events) {
    int eventCount = events.size();
    assert eventCount > 0;

    UpdatedRanges cache = myUpdatedRanges;
    if (cache != null && cache.myEventCount == eventCount) return cache;

    //noinspection SynchronizeOnThis
    synchronized (this) {
      cache = myUpdatedRanges;
      if (cache != null && cache.myEventCount == eventCount) return cache;

      UpdatedRanges answer;
      if (cache != null && cache.myEventCount < eventCount) {
        // apply only the new events
        answer = applyEvents(events.subList(cache.myEventCount, eventCount), cache);
      }
      else {
        List<SelfElementInfo> infos = myPointers.getSortedInfos();
        ManualRangeMarker[] markers = createMarkers(infos);
        answer = applyEvents(events, new UpdatedRanges(0, frozen, infos, markers));
      }

      myUpdatedRanges = answer;
      return answer;
    }
  }

  @NotNull
  private static ManualRangeMarker[] createMarkers(List<SelfElementInfo> infos) {
    ManualRangeMarker[] markers = new ManualRangeMarker[infos.size()];
    int i = 0;
    while (i < markers.length) {
      SelfElementInfo info = infos.get(i);
      boolean forInjected = info.isForInjected();
      int start = info.getPsiStartOffset();
      int end = info.getPsiEndOffset();
      markers[i] = new ManualRangeMarker(start, end, forInjected, forInjected, !forInjected, null);

      i++;
      while (i < markers.length && rangeEquals(infos.get(i), start, end, forInjected)) {
        markers[i] = markers[i - 1];
        i++;
      }
    }
    return markers;
  }

  private static boolean rangeEquals(SelfElementInfo info, int start, int end, boolean injected) {
    return start == info.getPsiStartOffset() && end == info.getPsiEndOffset() && injected == info.isForInjected();
  }

  private static UpdatedRanges applyEvents(@NotNull List<DocumentEvent> events, final UpdatedRanges struct) {
    FrozenDocument frozen = struct.myResultDocument;
    ManualRangeMarker[] resultMarkers = struct.myMarkers.clone();
    for (DocumentEvent event : events) {
      final FrozenDocument before = frozen;
      final DocumentEvent corrected;
      if ((event instanceof RetargetRangeMarkers)) {
        RetargetRangeMarkers retarget = (RetargetRangeMarkers)event;
        corrected = new RetargetRangeMarkers(frozen, retarget.getStartOffset(), retarget.getEndOffset(), retarget.getMoveDestinationOffset());
      }
      else {
        frozen = frozen.applyEvent(event, 0);
        corrected = new DocumentEventImpl(frozen, event.getOffset(), event.getOldFragment(), event.getNewFragment(), event.getOldTimeStamp(),
                                          event.isWholeTextReplaced(),
                                          ((DocumentEventImpl) event).getInitialStartOffset(), ((DocumentEventImpl) event).getInitialOldLength());
      }

      int i = 0;
      while (i < resultMarkers.length) {
        ManualRangeMarker currentRange = resultMarkers[i];

        int sameMarkersEnd = i + 1;
        while (sameMarkersEnd < resultMarkers.length && resultMarkers[sameMarkersEnd] == currentRange) {
          sameMarkersEnd++;
        }

        ManualRangeMarker updatedRange = currentRange == null ? null : currentRange.getUpdatedRange(corrected, before);
        while (i < sameMarkersEnd) {
          resultMarkers[i] = updatedRange;
          i++;
        }
      }
    }
    return new UpdatedRanges(struct.myEventCount + events.size(), frozen, struct.mySortedInfos, resultMarkers);
  }

  synchronized void updateMarkers(@NotNull FrozenDocument frozen, @NotNull List<DocumentEvent> events) {
    UpdatedRanges updated = getUpdatedMarkers(frozen, events);

    boolean sorted = true;
    for (int i = 0; i < updated.myMarkers.length; i++) {
      SelfElementInfo info = updated.mySortedInfos.get(i);
      info.setRange(updated.myMarkers[i]);
      if (sorted && i > 0 && INFO_COMPARATOR.compare(updated.mySortedInfos.get(i - 1), info) > 0) {
        sorted = false;
      }
    }

    if (!sorted) {
      myPointers.markUnsorted();
    }

    myUpdatedRanges = null;
  }

  @Nullable
  TextRange getUpdatedRange(@NotNull SelfElementInfo info, @NotNull FrozenDocument frozen, @NotNull List<DocumentEvent> events) {
    UpdatedRanges struct = getUpdatedMarkers(frozen, events);
    int i = Collections.binarySearch(struct.mySortedInfos, info, INFO_COMPARATOR);
    ManualRangeMarker updated = i >= 0 ? struct.myMarkers[i] : null;
    return updated == null ? null : new UnfairTextRange(updated.getStartOffset(), updated.getEndOffset());
  }

  void rangeChanged() {
    myUpdatedRanges = null;
    myPointers.markUnsorted();
  }

  private static class UpdatedRanges {
    private final int myEventCount;
    private final FrozenDocument myResultDocument;
    private final List<SelfElementInfo> mySortedInfos;
    private final ManualRangeMarker[] myMarkers;

    public UpdatedRanges(int eventCount,
                         FrozenDocument resultDocument,
                         List<SelfElementInfo> sortedInfos, ManualRangeMarker[] markers) {
      myEventCount = eventCount;
      myResultDocument = resultDocument;
      mySortedInfos = sortedInfos;
      myMarkers = markers;
    }
  }
}
