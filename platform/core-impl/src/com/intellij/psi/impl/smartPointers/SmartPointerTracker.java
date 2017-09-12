/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.FrozenDocument;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;

class SmartPointerTracker {
  private static final ReferenceQueue<SmartPsiElementPointerImpl> ourQueue = new ReferenceQueue<>();

  private int nextAvailableIndex;
  private int size;
  private PointerReference[] references = new PointerReference[10];
  private final MarkerCache markerCache = new MarkerCache(this);
  private boolean mySorted;

  static {
    LowMemoryWatcher.register(() -> processQueue(), ApplicationManager.getApplication());
  }

  synchronized boolean addReference(@NotNull PointerReference reference, @NotNull SmartPsiElementPointerImpl pointer) {
    if (!isActual(reference.file, reference.key)) {
      // this pointer list has been removed by another thread; clients should get/create an up-to-date list and try adding to it
      return false;
    }

    if (needsExpansion() || isTooSparse()) {
      resize();
      assert isActual(reference.file, reference.key);
    }

    assert references[nextAvailableIndex] == null : references[nextAvailableIndex];
    storePointerReference(references, nextAvailableIndex++, reference);
    size++;
    mySorted = false;
    if (((SelfElementInfo)pointer.getElementInfo()).hasRange()) {
      markerCache.rangeChanged();
    }
    return true;
  }

  boolean isActual(VirtualFile file, Key<SmartPointerTracker> key) {
    return file.getUserData(key) == this;
  }

  private boolean needsExpansion() {
    return nextAvailableIndex >= references.length;
  }

  private boolean isTooSparse() {
    return nextAvailableIndex > size * 2;
  }

  private void resize() {
    PointerReference[] newReferences = new PointerReference[size * 3 / 2 + 1];
    int index = 0;
    // don't use processAlivePointers/removeReference since it can unregister the whole pointer list, and we're not prepared to that
    for (PointerReference ref : references) {
      if (ref != null) {
        storePointerReference(newReferences, index++, ref);
      }
    }
    assert index == size : index + " != " + size;
    references = newReferences;
    nextAvailableIndex = index;
  }

  synchronized void removeReference(@NotNull PointerReference reference, @NotNull Key<SmartPointerTracker> expectedKey) {
    int index = reference.index;
    if (index < 0) return;

    assertActual(expectedKey, reference.file, reference.key);
    assert references[index] == reference : "At " + index + " expected " + reference + ", found " + references[index];
    references[index].index = -1;
    references[index] = null;
    if (--size == 0) {
      reference.file.replace(reference.key, this, null);
    }
  }

  private void assertActual(Key<SmartPointerTracker> expectedKey, VirtualFile file, Key<SmartPointerTracker> refKey) {
    assert isActual(file, refKey) : "Smart pointer list mismatch mismatch:" +
                                    " ref.key=" + expectedKey +
                                    ", manager.key=" + refKey +
                                    (file.getUserData(refKey) != null ? "; has another pointer list" : "");
  }

  private void processAlivePointers(@NotNull Processor<SmartPsiElementPointerImpl> processor) {
    for (int i = 0; i < nextAvailableIndex; i++) {
      PointerReference ref = references[i];
      if (ref == null) continue;

      assert isActual(ref.file, ref.key);
      SmartPsiElementPointerImpl pointer = ref.get();
      if (pointer == null) {
        removeReference(ref, ref.key);
        continue;
      }

      if (!processor.process(pointer)) {
        return;
      }
    }
  }

  private void ensureSorted() {
    if (!mySorted) {
      List<SmartPsiElementPointerImpl> pointers = new ArrayList<>();
      processAlivePointers(new CommonProcessors.CollectProcessor<>(pointers));
      assert size == pointers.size();

      pointers
        .sort((p1, p2) -> MarkerCache.INFO_COMPARATOR.compare((SelfElementInfo)p1.getElementInfo(), (SelfElementInfo)p2.getElementInfo()));

      for (int i = 0; i < pointers.size(); i++) {
        storePointerReference(references, i, pointers.get(i).pointerReference);
      }
      Arrays.fill(references, pointers.size(), nextAvailableIndex, null);
      nextAvailableIndex = pointers.size();
      mySorted = true;
    }
  }

  synchronized void updateMarkers(FrozenDocument frozen, List<DocumentEvent> events) {
    boolean stillSorted = markerCache.updateMarkers(frozen, events);
    if (!stillSorted) {
      mySorted = false;
    }
  }

  @Nullable
  synchronized Segment getUpdatedRange(SelfElementInfo info, FrozenDocument document, List<DocumentEvent> events) {
    return markerCache.getUpdatedRange(info, document, events);
  }

  synchronized void switchStubToAst(AnchorElementInfo info, PsiElement element) {
    info.switchToTreeRange(element);
    markerCache.rangeChanged();
    mySorted = false;
  }

  synchronized void fastenBelts() {
    processQueue();
    processAlivePointers(pointer -> {
      pointer.getElementInfo().fastenBelt();
      return true;
    });
  }

  synchronized void updatePointerTargetsAfterReparse() {
    processAlivePointers(pointer -> {
      if (!(pointer instanceof SmartPsiFileRangePointerImpl)) {
        updatePointerTarget(pointer, pointer.getPsiRange());
      }
      return true;
    });
  }

  private static <E extends PsiElement> void updatePointerTarget(@NotNull SmartPsiElementPointerImpl<E> pointer, @Nullable Segment pointerRange) {
    E cachedElement = pointer.getCachedElement();
    if (cachedElement == null) {
      return;
    }

    if (cachedElement.isValid()) {
      if (pointerRange == null) {
        // document change could be damaging, but if PSI survived after reparse, let's point to it
        ((SelfElementInfo)pointer.getElementInfo()).switchToAnchor(cachedElement);
        return;
      }
      // after reparse and its complex tree diff, the element might have "moved" to other range
      // but if an element of the same type can still be found at the old range, let's point there
      if (pointerRange.equals(cachedElement.getTextRange())) {
        return;
      }
    }

    pointer.cacheElement(pointer.doRestoreElement());
  }

  private static void storePointerReference(PointerReference[] references, int index, PointerReference ref) {
    references[index] = ref;
    ref.index = index;
  }

  synchronized List<SelfElementInfo> getSortedInfos() {
    ensureSorted();

    final List<SelfElementInfo> infos = ContainerUtil.newArrayListWithCapacity(size);
    processAlivePointers(pointer -> {
      SelfElementInfo info = (SelfElementInfo)pointer.getElementInfo();
      if (!info.hasRange()) return false;

      infos.add(info);
      return true;
    });
    return infos;
  }

  @TestOnly
  synchronized int getSize() {
    return size;
  }

  static class PointerReference extends WeakReference<SmartPsiElementPointerImpl> {
    @NotNull private final VirtualFile file;
    @NotNull private final Key<SmartPointerTracker> key;
    private int index = -2;

    PointerReference(@NotNull SmartPsiElementPointerImpl<?> pointer,
                     @NotNull VirtualFile containingFile,
                     @NotNull Key<SmartPointerTracker> key) {
      super(pointer, ourQueue);
      file = containingFile;
      this.key = key;
      pointer.pointerReference = this;
    }
  }

  static void processQueue() {
    while (true) {
      PointerReference reference = (PointerReference)ourQueue.poll();
      if (reference == null) break;

      SmartPointerTracker pointers = reference.file.getUserData(reference.key);
      if (pointers != null) {
        pointers.removeReference(reference, reference.key);
      }
    }
  }

}
