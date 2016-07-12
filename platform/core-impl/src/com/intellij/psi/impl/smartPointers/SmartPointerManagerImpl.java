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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.FrozenDocument;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.reference.SoftReference;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;

public class SmartPointerManagerImpl extends SmartPointerManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl");
  private static final ReferenceQueue<SmartPsiElementPointerImpl> ourQueue = new ReferenceQueue<SmartPsiElementPointerImpl>();

  private final Project myProject;
  private final Key<FilePointersList> POINTERS_KEY;
  private final PsiDocumentManagerBase myPsiDocManager;

  public SmartPointerManagerImpl(Project project) {
    myProject = project;
    myPsiDocManager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(myProject);
    POINTERS_KEY = Key.create("SMART_POINTERS for "+project);
  }

  static {
    LowMemoryWatcher.register(new Runnable() {
      @Override
      public void run() {
        processQueue();
      }
    }, ApplicationManager.getApplication());
  }

  private static void processQueue() {
    while (true) {
      PointerReference reference = (PointerReference)ourQueue.poll();
      if (reference == null) break;

      FilePointersList pointers = reference.file.getUserData(reference.key);
      if (pointers != null) {
        pointers.removeReference(reference);
      }
    }
  }

  public void fastenBelts(@NotNull VirtualFile file) {
    processQueue();
    FilePointersList pointers = getPointers(file);
    if (pointers != null) {
      pointers.processAlivePointers(new Processor<SmartPsiElementPointerImpl>() {
        @Override
        public boolean process(SmartPsiElementPointerImpl pointer) {
          pointer.getElementInfo().fastenBelt();
          return true;
        }
      });
    }
  }

  private static final Key<Reference<SmartPsiElementPointerImpl>> CACHED_SMART_POINTER_KEY = Key.create("CACHED_SMART_POINTER_KEY");
  @Override
  @NotNull
  public <E extends PsiElement> SmartPsiElementPointer<E> createSmartPsiElementPointer(@NotNull E element) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    PsiFile containingFile = element.getContainingFile();
    return createSmartPsiElementPointer(element, containingFile);
  }
  @Override
  @NotNull
  public <E extends PsiElement> SmartPsiElementPointer<E> createSmartPsiElementPointer(@NotNull E element, PsiFile containingFile) {
    return createSmartPsiElementPointer(element, containingFile, false);
  }

  @NotNull
  public <E extends PsiElement> SmartPsiElementPointer<E> createSmartPsiElementPointer(@NotNull E element,
                                                                                        PsiFile containingFile,
                                                                                        boolean forInjected) {
    if (containingFile != null && !containingFile.isValid() || containingFile == null && !element.isValid()) {
      PsiUtilCore.ensureValid(element);
      LOG.error("Invalid element:" + element);
    }
    processQueue();
    SmartPsiElementPointerImpl<E> pointer = getCachedPointer(element);
    if (pointer != null && pointer.incrementAndGetReferenceCount(1) > 0) {
      return pointer;
    }

    pointer = new SmartPsiElementPointerImpl<E>(myProject, element, containingFile, forInjected);
    if (containingFile != null) {
      trackPointer(pointer, containingFile.getViewProvider().getVirtualFile());
    }
    element.putUserData(CACHED_SMART_POINTER_KEY, new SoftReference<SmartPsiElementPointerImpl>(pointer));
    return pointer;
  }

  private static <E extends PsiElement> SmartPsiElementPointerImpl<E> getCachedPointer(@NotNull E element) {
    Reference<SmartPsiElementPointerImpl> data = element.getUserData(CACHED_SMART_POINTER_KEY);
    SmartPsiElementPointerImpl cachedPointer = SoftReference.dereference(data);
    if (cachedPointer != null) {
      PsiElement cachedElement = cachedPointer.getElement();
      if (cachedElement == null || cachedElement != element) {
        return null;
      }
    }
    //noinspection unchecked
    return cachedPointer;
  }

  @Override
  @NotNull
  public SmartPsiFileRange createSmartPsiFileRangePointer(@NotNull PsiFile file, @NotNull TextRange range) {
    return createSmartPsiFileRangePointer(file, range, false);
  }

  @NotNull
  public SmartPsiFileRange createSmartPsiFileRangePointer(@NotNull PsiFile file,
                                                          @NotNull TextRange range,
                                                          boolean forInjected) {
    if (!file.isValid()) {
      LOG.error("Invalid element:" + file);
    }
    processQueue();
    SmartPsiFileRangePointerImpl pointer = new SmartPsiFileRangePointerImpl(file, ProperTextRange.create(range), forInjected);
    trackPointer(pointer, file.getViewProvider().getVirtualFile());

    return pointer;
  }

  private <E extends PsiElement> void trackPointer(@NotNull SmartPsiElementPointerImpl<E> pointer, @NotNull VirtualFile containingFile) {
    SmartPointerElementInfo info = pointer.getElementInfo();
    if (!(info instanceof SelfElementInfo)) return;

    PointerReference reference = new PointerReference(pointer, containingFile, POINTERS_KEY);
    while (true) {
      FilePointersList pointers = getPointers(containingFile);
      if (pointers == null) {
        pointers = containingFile.putUserDataIfAbsent(POINTERS_KEY, new FilePointersList());
      }
      if (pointers.add(reference)) {
        if (((SelfElementInfo)info).hasRange()) {
          pointers.markerCache.rangeChanged();
        }
        break;
      }
    }
  }

  @Override
  public void removePointer(@NotNull SmartPsiElementPointer pointer) {
    if (!(pointer instanceof SmartPsiElementPointerImpl) || myProject.isDisposed()) {
      return;
    }
    PsiFile containingFile = pointer.getContainingFile();
    int refCount = ((SmartPsiElementPointerImpl)pointer).incrementAndGetReferenceCount(-1);
    if (refCount == 0) {
      PsiElement element = ((SmartPointerEx)pointer).getCachedElement();
      if (element != null) {
        element.putUserData(CACHED_SMART_POINTER_KEY, null);
      }

      SmartPointerElementInfo info = ((SmartPsiElementPointerImpl)pointer).getElementInfo();
      info.cleanup();

      if (containingFile == null) return;
      VirtualFile vFile = containingFile.getViewProvider().getVirtualFile();
      FilePointersList pointers = getPointers(vFile);
      PointerReference reference = ((SmartPsiElementPointerImpl)pointer).pointerReference;
      if (pointers != null && reference != null) {
        pointers.removeReference(reference);
      }
    }
  }

  @Nullable
  private FilePointersList getPointers(@NotNull VirtualFile containingFile) {
    return containingFile.getUserData(POINTERS_KEY);
  }

  @Nullable
  MarkerCache getMarkerCache(@NotNull VirtualFile file) {
    FilePointersList pointers = getPointers(file);
    return pointers == null ? null : pointers.markerCache;
  }

  @TestOnly
  public int getPointersNumber(@NotNull PsiFile containingFile) {
    VirtualFile file = containingFile.getViewProvider().getVirtualFile();
    FilePointersList pointers = getPointers(file);
    return pointers == null ? 0 : pointers.getSize();
  }

  @Override
  public boolean pointToTheSameElement(@NotNull SmartPsiElementPointer pointer1, @NotNull SmartPsiElementPointer pointer2) {
    return SmartPsiElementPointerImpl.pointsToTheSameElementAs(pointer1, pointer2);
  }

  public void updatePointers(Document document, FrozenDocument frozen, List<DocumentEvent> events) {
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    FilePointersList list = file == null ? null : getPointers(file);
    if (list == null) return;

    list.markerCache.updateMarkers(frozen, events);
  }

  public void updatePointerTargetsAfterReparse(@NotNull VirtualFile file) {
    FilePointersList list = getPointers(file);
    if (list == null) return;

    list.processAlivePointers(new Processor<SmartPsiElementPointerImpl>() {
      @Override
      public boolean process(SmartPsiElementPointerImpl pointer) {
        if (!(pointer instanceof SmartPsiFileRangePointerImpl)) {
          updatePointerTarget(pointer, pointer.getPsiRange());
        }
        return true;
      }
    });
  }

  // after reparse and its complex tree diff, the element might have "moved" to other range
  // but if an element of the same type can still be found at the old range, let's point there
  private static <E extends PsiElement> void updatePointerTarget(@NotNull SmartPsiElementPointerImpl<E> pointer, @Nullable Segment pointerRange) {
    E cachedElement = pointer.getCachedElement();
    if (cachedElement == null || cachedElement.isValid() && pointerRange != null && pointerRange.equals(cachedElement.getTextRange())) {
      return;
    }

    pointer.cacheElement(pointer.doRestoreElement());
  }


  Project getProject() {
    return myProject;
  }

  PsiDocumentManagerBase getPsiDocumentManager() {
    return myPsiDocManager;
  }

  static class PointerReference extends WeakReference<SmartPsiElementPointerImpl> {
    @NotNull private final VirtualFile file;
    @NotNull private final Key<FilePointersList> key;
    private int index = -2;

    private PointerReference(@NotNull SmartPsiElementPointerImpl<?> pointer,
                             @NotNull VirtualFile containingFile,
                             @NotNull Key<FilePointersList> key) {
      super(pointer, ourQueue);
      file = containingFile;
      this.key = key;
      pointer.pointerReference = this;
    }
  }

  static class FilePointersList {
    private int nextAvailableIndex;
    private int size;
    private PointerReference[] references = new PointerReference[10];
    private final MarkerCache markerCache = new MarkerCache(this);
    private boolean mySorted;

    private synchronized boolean add(@NotNull PointerReference reference) {
      if (reference.file.getUserData(reference.key) != this) {
        // this pointer list has been removed by another thread; clients should get/create an up-to-date list and try adding to it
        return false;
      }

      if (nextAvailableIndex >= references.length || nextAvailableIndex > size*2) {  // overflow or too many dead refs
        int newCapacity = (nextAvailableIndex >= references.length ? references.length : size) * 3 / 2 + 1;
        final PointerReference[] newReferences = new PointerReference[newCapacity];

        final int[] o = {0};
        processAlivePointers(new Processor<SmartPsiElementPointerImpl>() {
          @Override
          public boolean process(SmartPsiElementPointerImpl pointer) {
            storePointerReference(newReferences, o[0]++, pointer.pointerReference);
            return true;
          }
        });
        references = newReferences;
        size = nextAvailableIndex = o[0];
      }
      assert references[nextAvailableIndex] == null : references[nextAvailableIndex];
      storePointerReference(references, nextAvailableIndex++, reference);
      size++;
      mySorted = false;
      return true;
    }

    private synchronized void removeReference(@NotNull PointerReference reference) {
      int index = reference.index;
      if (index < 0) return;

      assert references[index] == reference : "At " + index + " expected " + reference + ", found " + references[index];
      references[index].index = -1;
      references[index] = null;
      if (--size == 0) {
        reference.file.replace(reference.key, this, null);
      }
    }

    synchronized boolean processAlivePointers(@NotNull Processor<SmartPsiElementPointerImpl> processor) {
      for (int i = 0; i < nextAvailableIndex; i++) {
        PointerReference ref = references[i];
        if (ref == null) continue;

        SmartPsiElementPointerImpl pointer = ref.get();
        if (pointer == null) {
          removeReference(ref);
          continue;
        }

        if (!processor.process(pointer)) {
          return false;
        }
      }
      return true;
    }

    private void ensureSorted() {
      if (!mySorted) {
        List<SmartPsiElementPointerImpl> pointers = new ArrayList<SmartPsiElementPointerImpl>();
        processAlivePointers(new CommonProcessors.CollectProcessor<SmartPsiElementPointerImpl>(pointers));
        assert size == pointers.size();

        Collections.sort(pointers, new Comparator<SmartPsiElementPointerImpl>() {
          @Override
          public int compare(SmartPsiElementPointerImpl p1, SmartPsiElementPointerImpl p2) {
            return MarkerCache.INFO_COMPARATOR.compare((SelfElementInfo)p1.getElementInfo(), (SelfElementInfo)p2.getElementInfo());
          }
        });

        for (int i = 0; i < pointers.size(); i++) {
          storePointerReference(references, i, pointers.get(i).pointerReference);
        }
        Arrays.fill(references, pointers.size(), nextAvailableIndex, null);
        nextAvailableIndex = pointers.size();
        mySorted = true;
      }
    }

    private static void storePointerReference(PointerReference[] references, int index, PointerReference ref) {
      references[index] = ref;
      ref.index = index;
    }

    synchronized List<SelfElementInfo> getSortedInfos() {
      ensureSorted();

      final List<SelfElementInfo> infos = ContainerUtil.newArrayListWithCapacity(size);
      processAlivePointers(new Processor<SmartPsiElementPointerImpl>() {
        @Override
        public boolean process(SmartPsiElementPointerImpl pointer) {
          SelfElementInfo info = (SelfElementInfo)pointer.getElementInfo();
          if (!info.hasRange()) return false;

          infos.add(info);
          return true;
        }
      });
      return infos;
    }

    int getSize() {
      return size;
    }

    synchronized void markUnsorted() {
      mySorted = false;
    }
  }
}
