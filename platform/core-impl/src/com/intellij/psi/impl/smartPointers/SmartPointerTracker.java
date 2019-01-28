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
import com.intellij.psi.PsiFile;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
      if (!isActual(reference.file, reference.key)) throw new AssertionError();
    }

    if (references[nextAvailableIndex] != null) throw new AssertionError(references[nextAvailableIndex]);
    storePointerReference(references, nextAvailableIndex++, reference);
    size++;
    mySorted = false;
    if (((SelfElementInfo)pointer.getElementInfo()).hasRange()) {
      markerCache.rangeChanged();
    }
    return true;
  }

  private boolean isActual(@NotNull VirtualFile file, @NotNull Key<SmartPointerTracker> key) {
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

  synchronized void removeReference(@NotNull PointerReference reference) {
    int index = reference.index;
    if (index < 0) return;

    assertActual(reference.file, reference.key);
    if (references[index] != reference) {
      throw new AssertionError("At " + index + " expected " + reference + ", found " + references[index]);
    }
    references[index].index = -1;
    references[index] = null;
    if (--size == 0) {
      disconnectTracker(reference.file, reference.key);
    }
  }

  private void disconnectTracker(VirtualFile file, Key<SmartPointerTracker> key) {
    if (!file.replace(key, this, null)) {
      throw new IllegalStateException("Couldn't clear smart pointer tracker " + this + ", current " + file.getUserData(key));
    }
  }

  private void assertActual(@NotNull VirtualFile file, @NotNull Key<SmartPointerTracker> refKey) {
    if (!isActual(file, refKey)) {
      SmartPointerTracker another = file.getUserData(refKey);
      throw new AssertionError("Smart pointer list mismatch:" +
                               " size=" + size +
                               ", ref.key=" + refKey +
                               (another != null ? "; has another pointer list with size " + another.size : ""));
    }
  }

  private void processAlivePointers(@NotNull Processor<? super SmartPsiElementPointerImpl<?>> processor) {
    for (int i = 0; i < nextAvailableIndex; i++) {
      PointerReference ref = references[i];
      if (ref == null) continue;

      if (!isActual(ref.file, ref.key)) throw new AssertionError();
      SmartPsiElementPointerImpl<?> pointer = ref.get();
      if (pointer == null) {
        removeReference(ref);
        continue;
      }

      if (!processor.process(pointer)) {
        return;
      }
    }
  }

  private void ensureSorted() {
    if (!mySorted) {
      List<SmartPsiElementPointerImpl<?>> pointers = new ArrayList<>();
      processAlivePointers(new CommonProcessors.CollectProcessor<>(pointers));
      if (size != pointers.size()) throw new AssertionError();

      pointers
        .sort((p1, p2) -> MarkerCache.INFO_COMPARATOR.compare((SelfElementInfo)p1.getElementInfo(), (SelfElementInfo)p2.getElementInfo()));

      for (int i = 0; i < pointers.size(); i++) {
        //noinspection ConstantConditions
        storePointerReference(references, i, pointers.get(i).pointerReference);
      }
      Arrays.fill(references, pointers.size(), nextAvailableIndex, null);
      nextAvailableIndex = pointers.size();
      mySorted = true;
    }
  }

  synchronized void updateMarkers(@NotNull FrozenDocument frozen, @NotNull List<? extends DocumentEvent> events) {
    boolean stillSorted = markerCache.updateMarkers(frozen, events);
    if (!stillSorted) {
      mySorted = false;
    }
  }

  @Nullable
  synchronized Segment getUpdatedRange(@NotNull SelfElementInfo info, @NotNull FrozenDocument document, @NotNull List<? extends DocumentEvent> events) {
    return markerCache.getUpdatedRange(info, document, events);
  }
  @Nullable
  synchronized Segment getUpdatedRange(@NotNull PsiFile containingFile, @NotNull Segment segment, boolean isSegmentGreedy, @NotNull FrozenDocument frozen, @NotNull List<? extends DocumentEvent> events) {
    return MarkerCache.getUpdatedRange(containingFile, segment, isSegmentGreedy, frozen, events);
  }

  synchronized void switchStubToAst(@NotNull AnchorElementInfo info, @NotNull PsiElement element) {
    info.switchToTreeRange(element);
    markerCache.rangeChanged();
    mySorted = false;
  }

  synchronized void fastenBelts(@NotNull SmartPointerManagerImpl manager) {
    processQueue();
    processAlivePointers(pointer -> {
      pointer.getElementInfo().fastenBelt(manager);
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

    boolean cachedValid = cachedElement.isValid();
    if (cachedValid) {
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

    E actual = pointer.doRestoreElement();
    if (actual == null && cachedValid && ((SelfElementInfo)pointer.getElementInfo()).updateRangeToPsi(pointerRange, cachedElement)) {
      return;
    }

    if (actual != cachedElement) {
      pointer.cacheElement(actual);
    }
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

  static class PointerReference extends WeakReference<SmartPsiElementPointerImpl<?>> {
    @NotNull final VirtualFile file;
    @NotNull final Key<SmartPointerTracker> key;
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

      if (reference.get() != null) {
        throw new IllegalStateException("Queued reference has referent!");
      }

      SmartPointerTracker pointers = reference.file.getUserData(reference.key);
      if (pointers != null) {
        pointers.removeReference(reference);
      }
    }
  }

}
