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
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TLongObjectHashMap;
import gnu.trove.TLongObjectProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author peter
 */
class MarkerCache {
  private final SmartPointerManagerImpl.FilePointersList myPointers;
  private final VirtualFile myVirtualFile;
  private volatile Trinity<Integer, TLongObjectHashMap<ManualRangeMarker>, FrozenDocument> myUpdatedRanges;

  MarkerCache(SmartPointerManagerImpl.FilePointersList pointers, VirtualFile virtualFile) {
    myPointers = pointers;
    myVirtualFile = virtualFile;
  }

  private TLongObjectHashMap<ManualRangeMarker> getUpdatedMarkers(@NotNull FrozenDocument frozen, @NotNull List<DocumentEvent> events) {
    int eventCount = events.size();
    assert eventCount > 0;

    Trinity<Integer, TLongObjectHashMap<ManualRangeMarker>, FrozenDocument> cache = myUpdatedRanges;
    if (cache != null && cache.first.intValue() == eventCount) return cache.second;

    //noinspection SynchronizeOnThis
    synchronized (this) {
      cache = myUpdatedRanges;
      if (cache != null && cache.first.intValue() == eventCount) return cache.second;

      TLongObjectHashMap<ManualRangeMarker> answer;
      if (cache != null && cache.first < eventCount) {
        // apply only the new events
        answer = cache.second.clone();
        frozen = applyEvents(cache.third, events.subList(cache.first, eventCount), answer);
      }
      else {
        answer = new TLongObjectHashMap<ManualRangeMarker>();
        for (SelfElementInfo info : getInfos()) {
          ProperTextRange range = info.getPsiRange();
          long key = info.markerCacheKey();
          if (range != null && key != 0) {
            boolean forInjected = info.isForInjected();
            answer.put(key, new ManualRangeMarker(frozen, range, forInjected, forInjected, !forInjected));
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
                                            final TLongObjectHashMap<ManualRangeMarker> map) {
    for (DocumentEvent event : events) {
      final DocumentEvent corrected;
      if ((event instanceof RetargetRangeMarkers)) {
        RetargetRangeMarkers retarget = (RetargetRangeMarkers)event;
        corrected = new RetargetRangeMarkers(frozen, retarget.getStartOffset(), retarget.getEndOffset(), retarget.getMoveDestinationOffset());
      }
      else {
        frozen = frozen.applyEvent(event, 0);
        corrected = new DocumentEventImpl(frozen, event.getOffset(), event.getOldFragment(), event.getNewFragment(), event.getOldTimeStamp(),
                                          event.isWholeTextReplaced());
      }

      map.forEachEntry(new TLongObjectProcedure<ManualRangeMarker>() {
        @Override
        public boolean execute(long key, ManualRangeMarker currentRange) {
          if (currentRange != null) {
            map.put(key, currentRange.getUpdatedRange(corrected));
          }
          return true;
        }
      });
    }
    return frozen;
  }

  synchronized void updateMarkers(@NotNull FrozenDocument frozen, @NotNull List<DocumentEvent> events) {
    TLongObjectHashMap<ManualRangeMarker> updated = getUpdatedMarkers(frozen, events);

    for (SelfElementInfo info : getInfos()) {
      long key = info.markerCacheKey();
      if (key != 0) {
        ManualRangeMarker newRange = updated.get(key);
        info.setRange(newRange == null ? null : newRange.getRange());
      }
    }

    myUpdatedRanges = null;
  }

  @NotNull
  private List<SelfElementInfo> getInfos() {
    return ContainerUtil.findAll(ContainerUtil.map(myPointers.getAlivePointers(), new NullableFunction<SmartPsiElementPointerImpl, SmartPointerElementInfo>() {
        @Override
        public SmartPointerElementInfo fun(SmartPsiElementPointerImpl pointer) {
          return pointer.getElementInfo();
        }
      }), SelfElementInfo.class);
  }

  @Nullable
  ProperTextRange getUpdatedRange(long rangeKey, @NotNull FrozenDocument frozen, @NotNull List<DocumentEvent> events) {
    ManualRangeMarker updated = getUpdatedMarkers(frozen, events).get(rangeKey);
    return updated == null ? null : updated.getRange();
  }

  synchronized void rangeChanged(long rangeKey) {
    if (myUpdatedRanges != null && !myUpdatedRanges.second.contains(rangeKey)) {
      myUpdatedRanges = null;
    }
  }

  VirtualFile getVirtualFile() {
    return myVirtualFile;
  }
}
