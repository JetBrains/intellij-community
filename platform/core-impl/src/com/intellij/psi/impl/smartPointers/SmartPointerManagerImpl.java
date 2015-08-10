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
import com.intellij.openapi.editor.impl.ManualRangeMarker;
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
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

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

  public SmartPointerManagerImpl(Project project) {
    myProject = project;
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
    ApplicationManager.getApplication().assertIsDispatchThread();
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
    if (!file.isValid()) {
      LOG.error("Invalid element:" + file);
    }
    processQueue();
    SmartPsiFileRangePointerImpl pointer = new SmartPsiFileRangePointerImpl(file, ProperTextRange.create(range));
    initPointer(pointer, file.getViewProvider().getVirtualFile());

    return pointer;
  }

  private <E extends PsiElement> void initPointer(@NotNull SmartPsiElementPointerImpl<E> pointer, @NotNull VirtualFile containingFile) {
    synchronized (lock) {
      FilePointersList pointers = getPointers(containingFile);
      if (pointers == null) {
        pointers = new FilePointersList(); // we synchronise access anyway
        containingFile.putUserData(POINTERS_KEY, pointers);
      }
      pointer.incrementAndGetReferenceCount(1);

      pointers.add(new PointerReference(pointer, containingFile, ourQueue, POINTERS_KEY));
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
  ManualRangeMarker obtainMarker(@NotNull Document document, @NotNull ProperTextRange range) {
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    FilePointersList pointers = file == null ? null : getPointers(file);
    ConcurrentMap<ProperTextRange, ManualRangeMarker> cache = pointers == null ? null : pointers.getMarkerCache();
    ManualRangeMarker marker = cache == null ? null : cache.get(range);
    if (marker != null) {
      return marker;
    }

    FrozenDocument frozen = ((PsiDocumentManagerBase)PsiDocumentManager.getInstance(myProject)).getLastCommittedDocument(document);
    marker = new ManualRangeMarker(frozen, range, false, false, true);
    if (cache != null) {
      marker = ConcurrencyUtil.cacheOrGet(cache, range, marker);
    }
    return marker;
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
    FilePointersList pointers = file == null ? null : getPointers(file);
    if (pointers == null) return;

    pointers.markerCache = null;

    List<SelfElementInfo> infos = ContainerUtil.mapNotNull(pointers.getAlivePointers(), new Function<SmartPsiElementPointerImpl, SelfElementInfo>() {
      @Override
      public SelfElementInfo fun(SmartPsiElementPointerImpl pointer) {
        final SmartPointerElementInfo info = pointer.getElementInfo();
        return info instanceof SelfElementInfo ? (SelfElementInfo)info : null;
      }
    });

    for (DocumentEvent event : events) {
      THashSet<ManualRangeMarker> processedMarkers = ContainerUtil.newIdentityTroveSet();
      
      frozen = frozen.applyEvent(event, 0);
      final DocumentEvent corrected = SelfElementInfo.withFrozen(frozen, event);
      
      for (SelfElementInfo info : infos) {
        info.updateRange(corrected, processedMarkers);
      }
    }
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

  private static class FilePointersList {
    private int nextAvailableIndex;
    private int size;
    private PointerReference[] references = new PointerReference[10];
    private volatile ConcurrentMap<ProperTextRange, ManualRangeMarker> markerCache;

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

    @Nullable
    private ConcurrentMap<ProperTextRange, ManualRangeMarker> getMarkerCache() {
      ConcurrentMap<ProperTextRange, ManualRangeMarker> cache = markerCache;
      if (cache == null) {
        cache = ContainerUtil.newConcurrentMap();
        for (SmartPsiElementPointerImpl pointer : getAlivePointers()) {
          SmartPointerElementInfo info = pointer == null ? null : pointer.getElementInfo();
          ManualRangeMarker marker = info instanceof SelfElementInfo ? ((SelfElementInfo)info).getRangeMarker() : null;
          ProperTextRange key = marker == null ? null : marker.getRange();
          if (key != null) {
            cache.putIfAbsent(key, marker);
          }
        }
        markerCache = cache;
      }
      return cache;
    }

    @NotNull
    private List<SmartPsiElementPointerImpl> getAlivePointers() {
      return ContainerUtil.mapNotNull(references, new Function<PointerReference, SmartPsiElementPointerImpl>() {
        @Override
        public SmartPsiElementPointerImpl fun(PointerReference reference) {
          return (SmartPsiElementPointerImpl)SoftReference.dereference(reference);
        }
      });
    }
  }
}
