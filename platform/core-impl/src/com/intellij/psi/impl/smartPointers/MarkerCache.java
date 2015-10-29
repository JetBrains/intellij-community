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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author peter
 */
class MarkerCache {
  private static final Comparator<SelfElementInfo> BY_RANGE_KEY = new Comparator<SelfElementInfo>() {
    @Override
    public int compare(SelfElementInfo info1, SelfElementInfo info2) {
      int o1 = info1.getPsiStartOffset();
      int o2 = info2.getPsiStartOffset();
      if (o1 != o2) return o1 > o2 ? 1 : -1;

      o1 = info1.getPsiEndOffset();
      o2 = info2.getPsiEndOffset();
      if (o1 != o2) return o1 > o2 ? 1 : -1;

      return (info1.isForInjected() ? 1 : 0) - (info2.isForInjected() ? 1 : 0);
    }
  };
  private final SmartPointerManagerImpl.FilePointersList myPointers;
  private final VirtualFile myVirtualFile;
  private volatile UpdatedRanges myUpdatedRanges;

  MarkerCache(SmartPointerManagerImpl.FilePointersList pointers, VirtualFile virtualFile) {
    myPointers = pointers;
    myVirtualFile = virtualFile;
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
        List<SelfElementInfo> infos = getSortedInfos();
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

    for (int i = 0; i < updated.myMarkers.length; i++) {
      updated.mySortedInfos.get(i).setRange(updated.myMarkers[i]);
    }

    myUpdatedRanges = null;
  }

  @NotNull
  private List<SelfElementInfo> getSortedInfos() {
    List<SelfElementInfo> infos = ContainerUtil.newArrayListWithCapacity(myPointers.getSize());
    for (Reference<SmartPointerEx> reference : myPointers.getReferences()) {
      if (reference != null) {
        SmartPointerEx pointer = reference.get();
        if (pointer != null) {
          SmartPointerElementInfo info = ((SmartPsiElementPointerImpl)pointer).getElementInfo();
          if (info instanceof SelfElementInfo && ((SelfElementInfo)info).hasRange()) {
            infos.add((SelfElementInfo)info);
          }
        }
      }
    }
    Collections.sort(infos, BY_RANGE_KEY);
    return infos;
  }

  @Nullable
  TextRange getUpdatedRange(@NotNull SelfElementInfo info, @NotNull FrozenDocument frozen, @NotNull List<DocumentEvent> events) {
    UpdatedRanges struct = getUpdatedMarkers(frozen, events);
    int i = Collections.binarySearch(struct.mySortedInfos, info, BY_RANGE_KEY);
    ManualRangeMarker updated = i >= 0 ? struct.myMarkers[i] : null;
    return updated == null ? null : new UnfairTextRange(updated.getStartOffset(), updated.getEndOffset());
  }

  void rangeChanged() {
    myUpdatedRanges = null;
  }

  VirtualFile getVirtualFile() {
    return myVirtualFile;
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
