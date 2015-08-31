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
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
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
import java.util.List;

public class SmartPointerManagerImpl extends SmartPointerManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl");
  private static final Object lock = new Object();
  private static final ReferenceQueue<SmartPointerEx> ourQueue = new ReferenceQueue<SmartPointerEx>();
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
      synchronized (lock) {
        FilePointersList pointers = reference.file.getUserData(reference.key);
        if (pointers != null) {
          pointers.remove(reference);
          if (pointers.isEmpty()) {
            reference.file.putUserData(reference.key, null);
          }
        }
      }
    }
  }

  public void fastenBelts(@NotNull VirtualFile file) {
    processQueue();
    synchronized (lock) {
      FilePointersList pointers = getPointers(file);
      if (pointers != null && !pointers.isEmpty()) {
        for (PointerReference ref : pointers.references) {
          SmartPointerEx pointer = SoftReference.dereference(ref);
          if (pointer != null) {
            pointer.fastenBelt();
          }
        }
      }
    }
  }

  private static final Key<Reference<SmartPointerEx>> CACHED_SMART_POINTER_KEY = Key.create("CACHED_SMART_POINTER_KEY");
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
    SmartPointerEx<E> pointer = getCachedPointer(element);
    if (pointer == null) {
      pointer = new SmartPsiElementPointerImpl<E>(myProject, element, containingFile);
      if (containingFile != null) {
        initPointer((SmartPsiElementPointerImpl<E>)pointer, containingFile.getViewProvider().getVirtualFile());
      }
      element.putUserData(CACHED_SMART_POINTER_KEY, new SoftReference<SmartPointerEx>(pointer));
    }
    else {
      synchronized (lock) {
        if (pointer instanceof SmartPsiElementPointerImpl) {
          ((SmartPsiElementPointerImpl)pointer).incrementAndGetReferenceCount(1);
        }
      }
    }
    return pointer;
  }

  private static <E extends PsiElement> SmartPointerEx<E> getCachedPointer(@NotNull E element) {
    Reference<SmartPointerEx> data = element.getUserData(CACHED_SMART_POINTER_KEY);
    SmartPointerEx cachedPointer = SoftReference.dereference(data);
    if (cachedPointer != null) {
      PsiElement cachedElement = cachedPointer.getCachedElement();
      if (cachedElement != null && cachedElement != element) {
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
    initPointer(pointer, file.getViewProvider().getVirtualFile());

    return pointer;
  }

  private <E extends PsiElement> void initPointer(@NotNull SmartPsiElementPointerImpl<E> pointer, @NotNull VirtualFile containingFile) {
    synchronized (lock) {
      pointer.incrementAndGetReferenceCount(1);
      getNotNullPointerList(containingFile).add(new PointerReference(pointer, containingFile, ourQueue, POINTERS_KEY));
    }
  }

  @NotNull
  private FilePointersList getNotNullPointerList(@NotNull VirtualFile containingFile) {
    synchronized (lock) {
      FilePointersList pointers = getPointers(containingFile);
      if (pointers == null) {
        pointers = new FilePointersList(containingFile);
        containingFile.putUserData(POINTERS_KEY, pointers);
      }
      return pointers;
    }
  }

  @Override
  public boolean removePointer(@NotNull SmartPsiElementPointer pointer) {
    if (!(pointer instanceof SmartPsiElementPointerImpl)) {
      return false;
    }
    PsiFile containingFile = pointer.getContainingFile();
    synchronized (lock) {
      int refCount = ((SmartPsiElementPointerImpl)pointer).incrementAndGetReferenceCount(-1);
      if (refCount == 0) {
        PsiElement element = ((SmartPointerEx)pointer).getCachedElement();
        if (element != null) {
          element.putUserData(CACHED_SMART_POINTER_KEY, null);
        }

        SmartPointerElementInfo info = ((SmartPsiElementPointerImpl)pointer).getElementInfo();
        info.cleanup();

        if (containingFile == null) return false;
        VirtualFile vFile = containingFile.getViewProvider().getVirtualFile();
        FilePointersList pointers = getPointers(vFile);
        if (pointers == null) return false;
        boolean result = pointers.remove(pointer);
        if (pointers.isEmpty()) {
          vFile.putUserData(POINTERS_KEY, null);
        }
        return result;
      }
    }
    return false;
  }

  @Nullable
  private FilePointersList getPointers(@NotNull VirtualFile containingFile) {
    return containingFile.getUserData(POINTERS_KEY);
  }

  @NotNull
  MarkerCache getMarkerCache(@NotNull VirtualFile file) {
    return getNotNullPointerList(file).markerCache;
  }

  @TestOnly
  public int getPointersNumber(@NotNull PsiFile containingFile) {
    synchronized (lock) {
      VirtualFile file = containingFile.getViewProvider().getVirtualFile();
      FilePointersList pointers = getPointers(file);
      return pointers == null ? 0 : pointers.size;
    }
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

  Project getProject() {
    return myProject;
  }

  PsiDocumentManagerBase getPsiDocumentManager() {
    return myPsiDocManager;
  }

  private static class PointerReference extends WeakReference<SmartPointerEx> {
    @NotNull private final VirtualFile file;
    @NotNull private final Key<FilePointersList> key;

    private PointerReference(@NotNull SmartPointerEx<?> pointer,
                             @NotNull VirtualFile containingFile,
                             @NotNull ReferenceQueue<SmartPointerEx> queue,
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
    private final MarkerCache markerCache;

    FilePointersList(VirtualFile file) {
      markerCache = new MarkerCache(this, file);
    }

    private void add(@NotNull PointerReference reference) {
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
    }

    private void remove(@NotNull PointerReference reference) {
      int index = ArrayUtil.indexOf(references, reference);
      if (index != -1) {
        references[index] = null;
        size--;
      }
    }

    private boolean remove(@NotNull SmartPsiElementPointer smartPointer) {
      boolean result = false;
      for (int i = 0; i < references.length; i++) {
        PointerReference reference = references[i];
        if (reference != null && reference.get() == smartPointer) {
          references[i] = null;
          result = true;
          break;
        }
      }
      size--;
      return result;
    }

    private boolean isEmpty() {
      return size == 0;
    }

    @NotNull
    List<SmartPsiElementPointerImpl> getAlivePointers() {
      return ContainerUtil.mapNotNull(references, new Function<PointerReference, SmartPsiElementPointerImpl>() {
        @Override
        public SmartPsiElementPointerImpl fun(PointerReference reference) {
          return (SmartPsiElementPointerImpl)SoftReference.dereference(reference);
        }
      });
    }
  }
}
