// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.smartPointers;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.FrozenDocument;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.Segment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import org.jetbrains.annotations.*;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@ApiStatus.Internal
public final class SmartPointerTracker {
  private static final ReferenceQueue<SmartPsiElementPointerImpl<?>> ourQueue = new ReferenceQueue<>();

  private int nextAvailableIndex;
  private int size;
  private PointerReference[] references = new PointerReference[10];
  private final MarkerCache markerCache = new MarkerCache(this);
  private boolean mySorted;

  static {
    Application application = ApplicationManager.getApplication();
    if (!application.isDisposed()) {
      LowMemoryWatcher.register(() -> processQueue(), application);
    }
  }

  synchronized void addReference(@NotNull SmartPsiElementPointerImpl<?> pointer) {
    PointerReference reference = new PointerReference(pointer, this);
    if (needsExpansion() || isTooSparse()) {
      resize();
    }

    if (references[nextAvailableIndex] != null) throw new AssertionError(references[nextAvailableIndex]);
    storePointerReference(references, nextAvailableIndex++, reference);
    size++;
    mySorted = false;
    if (((SelfElementInfo)pointer.getElementInfo()).hasRange()) {
      markerCache.rangeChanged();
    }
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

    if (references[index] != reference) {
      throw new AssertionError("At " + index + " expected " + reference + ", found " + references[index]);
    }
    references[index].index = -1;
    references[index] = null;
    size--;
  }

  private void processAlivePointers(@NotNull Processor<? super SmartPsiElementPointerImpl<?>> processor) {
    for (int i = 0; i < nextAvailableIndex; i++) {
      PointerReference ref = references[i];
      if (ref == null) continue;

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

  synchronized @Nullable Segment getUpdatedRange(@NotNull SelfElementInfo info, @NotNull FrozenDocument document, @NotNull List<? extends DocumentEvent> events) {
    return markerCache.getUpdatedRange(info, document, events);
  }
  synchronized @Nullable Segment getUpdatedRange(@NotNull PsiFile containingFile, @NotNull Segment segment, boolean isSegmentGreedy, @NotNull FrozenDocument frozen, @NotNull List<? extends DocumentEvent> events) {
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

    List<SelfElementInfo> infos = new ArrayList<>(size);
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

  static final class PointerReference extends WeakReference<SmartPsiElementPointerImpl<?>> {
    final @NotNull SmartPointerTracker tracker;
    private int index = -2;

    private PointerReference(@NotNull SmartPsiElementPointerImpl<?> pointer, @NotNull SmartPointerTracker tracker) {
      super(pointer, ourQueue);
      this.tracker = tracker;
      pointer.pointerReference = this;
    }
  }

  @VisibleForTesting
  public static void processQueue() {
    while (true) {
      PointerReference reference = (PointerReference)ourQueue.poll();
      if (reference == null) break;

      if (reference.get() != null) {
        throw new IllegalStateException("Queued reference has referent!");
      }

      reference.tracker.removeReference(reference);
    }
  }

}
