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
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SmartPointerManagerImpl extends SmartPointerManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl");
  private static final ReferenceQueue<SmartPsiElementPointerImpl> ourQueue = new ReferenceQueue<SmartPsiElementPointerImpl>();
  @SuppressWarnings("unused") private static final LowMemoryWatcher ourWatcher = LowMemoryWatcher.register(new Runnable() {
    @Override
    public void run() {
      processQueue();
    }
  });

  private final Project myProject;
  private final Key<FilePointersList> POINTERS_KEY;
  private final PsiDocumentManagerBase myPsiDocManager;

  public SmartPointerManagerImpl(Project project) {
    myProject = project;
    myPsiDocManager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(myProject);
    POINTERS_KEY = Key.create("SMART_POINTERS for "+project);
  }

  private static void processQueue() {
    while (true) {
      PointerReference reference = (PointerReference)ourQueue.poll();
      if (reference == null) break;

      FilePointersList pointers = reference.file.getUserData(reference.key);
      if (pointers != null) {
        pointers.remove(reference);
      }
    }
  }

  public void fastenBelts(@NotNull VirtualFile file) {
    processQueue();
    FilePointersList pointers = getPointers(file);
    if (pointers != null) {
      for (Reference<SmartPsiElementPointerImpl> ref : pointers.getReferences()) {
        SmartPsiElementPointerImpl pointer = SoftReference.dereference(ref);
        if (pointer != null) {
          pointer.getElementInfo().fastenBelt();
        }
      }
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
    if (containingFile != null && !containingFile.isValid() || containingFile == null && !element.isValid()) {
      PsiUtilCore.ensureValid(element);
      LOG.error("Invalid element:" + element);
    }
    processQueue();
    SmartPsiElementPointerImpl<E> pointer = getCachedPointer(element);
    if (pointer != null && pointer.incrementAndGetReferenceCount(1) > 0) {
      return pointer;
    }

    pointer = new SmartPsiElementPointerImpl<E>(myProject, element, containingFile);
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

    while (true) {
      FilePointersList pointers = getPointers(containingFile);
      if (pointers == null) {
        pointers = containingFile.putUserDataIfAbsent(POINTERS_KEY, new FilePointersList());
      }
      if (pointers.add(new PointerReference(pointer, containingFile, ourQueue, POINTERS_KEY))) {
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
      if (pointers == null) return;
      pointers.remove(pointer);
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

    for (SmartPsiElementPointerImpl pointer : list.getAlivePointers()) {
      if (!(pointer instanceof SmartPsiFileRangePointerImpl)) {
        updatePointerTarget(pointer, pointer.getPsiRange());
      }
    }
  }

  // after reparse and its complex tree diff, the element might have "moved" to other range
  // but if an element of the same type can still be found at the old range, let's point there
  private static <E extends PsiElement> void updatePointerTarget(@NotNull SmartPsiElementPointerImpl<E> pointer, @Nullable Segment pointerRange) {
    E cachedElement = pointer.getCachedElement();
    if (cachedElement == null || cachedElement.isValid() && pointerRange != null && pointerRange.equals(cachedElement.getTextRange())) {
      return;
    }

    E newTarget = pointer.doRestoreElement();
    if (newTarget != null) {
      pointer.cacheElement(newTarget);
    }
  }


  Project getProject() {
    return myProject;
  }

  PsiDocumentManagerBase getPsiDocumentManager() {
    return myPsiDocManager;
  }

  private static class PointerReference extends WeakReference<SmartPsiElementPointerImpl> {
    @NotNull private final VirtualFile file;
    @NotNull private final Key<FilePointersList> key;

    private PointerReference(@NotNull SmartPsiElementPointerImpl<?> pointer,
                             @NotNull VirtualFile containingFile,
                             @NotNull ReferenceQueue<SmartPsiElementPointerImpl> queue,
                             @NotNull Key<FilePointersList> key) {
      super(pointer, queue);
      file = containingFile;
      this.key = key;
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
        int newCapacity = nextAvailableIndex >= references.length ? references.length * 3/2 +1 : size * 3/2+1;
        PointerReference[] newReferences = new PointerReference[newCapacity];

        int o = 0;
        for (PointerReference oldRef : references) {
          if (SoftReference.dereference(oldRef) != null) {
            newReferences[o++] = oldRef;
          }
        }
        references = newReferences;
        size = nextAvailableIndex = o;
      }
      references[nextAvailableIndex++] = reference;
      size++;
      mySorted = false;
      return true;
    }

    private synchronized void remove(@NotNull PointerReference reference) {
      int index = ArrayUtil.indexOf(references, reference);
      if (index != -1) {
        removeReference(reference, index);
      }
    }

    private synchronized void remove(@NotNull SmartPsiElementPointer smartPointer) {
      for (int i = 0; i < references.length; i++) {
        PointerReference reference = references[i];
        if (reference != null && reference.get() == smartPointer) {
          removeReference(reference, i);
          return;
        }
      }
    }

    private void removeReference(@NotNull PointerReference reference, int index) {
      references[index] = null;
      if (--size == 0) {
        reference.file.replace(reference.key, this, null);
      }
    }

    Reference<SmartPsiElementPointerImpl>[] getReferences() {
      return references;
    }

    synchronized List<SelfElementInfo> getSortedInfos() {
      if (!mySorted) {
        List<SmartPsiElementPointerImpl> hardRefs = ContainerUtil.newArrayListWithCapacity(size);
        for (int i = 0; i < references.length; i++) {
          PointerReference reference = references[i];
          if (reference == null) continue;

          SmartPsiElementPointerImpl pointer = reference.get();
          if (pointer != null) {
            hardRefs.add(pointer);
          } else {
            removeReference(reference, i);
            if (size == 0) {
              return Collections.emptyList();
            }
          }
        }
        assert size == hardRefs.size();

        Arrays.sort(references, 0, nextAvailableIndex, new Comparator<PointerReference>() {
          @Override
          public int compare(PointerReference o1, PointerReference o2) {
            SmartPsiElementPointerImpl p1 = SoftReference.dereference(o1);
            SmartPsiElementPointerImpl p2 = SoftReference.dereference(o2);
            if (p1 == null || p2 == null) {
              return p1 != null ? -1 : p2 != null ? 1 : 0; // null references to the end
            }
            return MarkerCache.INFO_COMPARATOR.compare((SelfElementInfo)p1.getElementInfo(), (SelfElementInfo)p2.getElementInfo());
          }
        });
        nextAvailableIndex = hardRefs.size();
        mySorted = true;
      }

      List<SelfElementInfo> infos = ContainerUtil.newArrayListWithCapacity(size);
      for (Reference<SmartPsiElementPointerImpl> reference : references) {
        SmartPsiElementPointerImpl pointer = SoftReference.dereference(reference);
        if (pointer != null) {
          SelfElementInfo info = (SelfElementInfo)pointer.getElementInfo();
          if (!info.hasRange()) break;

          infos.add(info);
        }
      }
      return infos;
    }

    int getSize() {
      return size;
    }

    @NotNull
    List<SmartPsiElementPointerImpl> getAlivePointers() {
      return ContainerUtil.mapNotNull(references, new Function<PointerReference, SmartPsiElementPointerImpl>() {
        @Override
        public SmartPsiElementPointerImpl fun(PointerReference reference) {
          return SoftReference.dereference(reference);
        }
      });
    }

    synchronized void markUnsorted() {
      mySorted = false;
    }
  }
}
