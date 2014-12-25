/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.tree.MarkersHolderFileViewProvider;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.UnsafeWeakList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.lang.ref.Reference;
import java.util.List;

public class SmartPointerManagerImpl extends SmartPointerManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl");

  private final Project myProject;
  private final Object lock = new Object();
  private final Key<List<SmartPointerEx>> POINTERS_KEY;
  private final Key<Boolean> POINTERS_ARE_FASTENED_KEY;

  public SmartPointerManagerImpl(Project project) {
    myProject = project;
    POINTERS_KEY = Key.create("SMART_POINTERS for "+project);
    POINTERS_ARE_FASTENED_KEY = Key.create("SMART_POINTERS_ARE_FASTENED for "+project);
  }

  public void fastenBelts(@NotNull VirtualFile file, int offset, @Nullable RangeMarker[] cachedRangeMarkers) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    synchronized (lock) {
      List<SmartPointerEx> pointers = getPointers(file);
      if (pointers == null) return;

      if (getAndFasten(file)) return;

      if (pointers.isEmpty()) {
        file.putUserData(POINTERS_KEY, null);
      }
      else {
        for (SmartPointerEx pointer : pointers) {
          if (pointer != null) {
            pointer.fastenBelt(offset, cachedRangeMarkers);
          }
        }
      }

      PsiFile psiFile = ((PsiManagerEx)PsiManager.getInstance(myProject)).getFileManager().getCachedPsiFile(file);
      if (psiFile != null) {
        PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(myProject);
        for (DocumentWindow injectedDoc : InjectedLanguageManager.getInstance(myProject).getCachedInjectedDocuments(psiFile)) {
          PsiFile injectedFile = psiDocumentManager.getPsiFile(injectedDoc);
          if (injectedFile == null) continue;
          RangeMarker[] cachedMarkers = getCachedRangeMarkerToInjectedFragment(injectedFile);
          boolean relevant = false;
          for (Segment hostSegment : injectedDoc.getHostRanges()) {
            if (offset <= hostSegment.getEndOffset()) {
              relevant = true;
              break;
            }
          }
          if (relevant) {
            fastenBelts(injectedFile.getViewProvider().getVirtualFile(), 0, cachedMarkers);
          }
        }
      }
    }
  }

  @NotNull
  private static RangeMarker[] getCachedRangeMarkerToInjectedFragment(@NotNull PsiFile injectedFile) {
    MarkersHolderFileViewProvider provider = (MarkersHolderFileViewProvider)injectedFile.getViewProvider();
    return provider.getCachedMarkers();
  }

  public void unfastenBelts(@NotNull VirtualFile file, int offset) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    synchronized (lock) {
      List<SmartPointerEx> pointers = getPointers(file);
      if (pointers == null) return;

      if (!getAndUnfasten(file)) return;

      if (pointers.isEmpty()) {
        file.putUserData(POINTERS_KEY, null);
      }
      else {
        for (SmartPointerEx pointer : pointers) {
          if (pointer != null) {
            pointer.unfastenBelt(offset);
          }
        }
      }

      PsiFile psiFile = ((PsiManagerEx)PsiManager.getInstance(myProject)).getFileManager().getCachedPsiFile(file);
      if (psiFile != null) {
        PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(myProject);
        for (DocumentWindow injectedDoc : InjectedLanguageManager.getInstance(myProject).getCachedInjectedDocuments(psiFile)) {
          PsiFile injectedFile = psiDocumentManager.getPsiFile(injectedDoc);
          if (injectedFile == null) continue;
          unfastenBelts(injectedFile.getViewProvider().getVirtualFile(), 0);
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
    SmartPointerEx<E> pointer = getCachedPointer(element);
    if (pointer != null) {
      containingFile = containingFile == null ? element.getContainingFile() : containingFile;
      if (containingFile != null && areBeltsFastened(containingFile.getViewProvider().getVirtualFile())) {
        pointer.fastenBelt(0, null);
      }
    }
    else {
      pointer = new SmartPsiElementPointerImpl<E>(myProject, element, containingFile);
      if (containingFile != null) {
        initPointer(pointer, containingFile.getViewProvider().getVirtualFile());
      }
      element.putUserData(CACHED_SMART_POINTER_KEY, new SoftReference<SmartPointerEx>(pointer));
    }
    if (pointer instanceof SmartPsiElementPointerImpl) {
      synchronized (lock) {
        ((SmartPsiElementPointerImpl)pointer).incrementAndGetReferenceCount(1);
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
    SmartPsiFileRangePointerImpl pointer = new SmartPsiFileRangePointerImpl(file, ProperTextRange.create(range));
    initPointer(pointer, file.getViewProvider().getVirtualFile());

    return pointer;
  }

  private <E extends PsiElement> void initPointer(@NotNull SmartPointerEx<E> pointer, @NotNull VirtualFile containingFile) {
    synchronized (lock) {
      List<SmartPointerEx> pointers = getPointers(containingFile);
      if (pointers == null) {
        pointers = new UnsafeWeakList<SmartPointerEx>(); // we synchronise access anyway
        containingFile.putUserData(POINTERS_KEY, pointers);
      }
      pointers.add(pointer);

      if (areBeltsFastened(containingFile)) {
        pointer.fastenBelt(0, null);
      }
    }
  }

  @Override
  public boolean removePointer(@NotNull SmartPsiElementPointer pointer) {
    synchronized (lock) {
      if (pointer instanceof SmartPsiElementPointerImpl) {
        int refCount = ((SmartPsiElementPointerImpl)pointer).incrementAndGetReferenceCount(-1);
        if (refCount == 0) {
          PsiElement element = ((SmartPointerEx)pointer).getCachedElement();
          if (element != null) {
            element.putUserData(CACHED_SMART_POINTER_KEY, null);
          }
          PsiFile containingFile = pointer.getContainingFile();
          if (containingFile == null) return false;
          List<SmartPointerEx> pointers = getPointers(containingFile.getViewProvider().getVirtualFile());
          if (pointers == null) return false;
          SmartPointerElementInfo info = ((SmartPsiElementPointerImpl)pointer).getElementInfo();
          info.cleanup();
          return pointers.remove(pointer);
        }
      }
    }
    return false;
  }

  private List<SmartPointerEx> getPointers(@NotNull VirtualFile containingFile) {
    return containingFile.getUserData(POINTERS_KEY);
  }

  @TestOnly
  public int getPointersNumber(@NotNull PsiFile containingFile) {
    synchronized (lock) {
      List<SmartPointerEx> pointers = getPointers(containingFile.getViewProvider().getVirtualFile());
      return pointers == null ? 0 : ((UnsafeWeakList)pointers).toStrongList().size();
    }
  }

  private boolean getAndFasten(@NotNull VirtualFile file) {
    boolean fastened = areBeltsFastened(file);
    file.putUserData(POINTERS_ARE_FASTENED_KEY, Boolean.TRUE);
    return fastened;
  }
  private boolean getAndUnfasten(@NotNull VirtualFile file) {
    boolean fastened = areBeltsFastened(file);
    file.putUserData(POINTERS_ARE_FASTENED_KEY, null);
    return fastened;
  }
  private boolean areBeltsFastened(VirtualFile file) {
    return file.getUserData(POINTERS_ARE_FASTENED_KEY) == Boolean.TRUE;
  }

  @Override
  public boolean pointToTheSameElement(@NotNull SmartPsiElementPointer pointer1, @NotNull SmartPsiElementPointer pointer2) {
    return SmartPsiElementPointerImpl.pointsToTheSameElementAs(pointer1, pointer2);
  }
}
