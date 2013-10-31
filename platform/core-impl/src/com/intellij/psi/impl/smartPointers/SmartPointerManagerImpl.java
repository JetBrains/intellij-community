/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.MarkersHolderFileViewProvider;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.UnsafeWeakList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.lang.ref.Reference;
import java.util.List;

public class SmartPointerManagerImpl extends SmartPointerManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl");

  private static final Key<List<SmartPointerEx>> SMART_POINTERS_IN_PSI_FILE_KEY = Key.create("SMART_POINTERS_IN_PSI_FILE_KEY");
  private static final Key<Boolean> BELTS_ARE_FASTEN_KEY = Key.create("BELTS_ARE_FASTEN_KEY");

  private final Project myProject;
  private final Object lock = new Object();

  public SmartPointerManagerImpl(Project project) {
    myProject = project;
  }

  public void fastenBelts(@NotNull PsiFile file, int offset, @Nullable RangeMarker[] cachedRangeMarkers) {
    synchronized (lock) {
      if (areBeltsFastened(file)) return;

      file.putUserData(BELTS_ARE_FASTEN_KEY, Boolean.TRUE);

      List<SmartPointerEx> pointers = getPointers(file);
      if (pointers == null) return;
      PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(file.getProject());

      for (SmartPointerEx pointer : pointers) {
        if (pointer != null) {
          pointer.fastenBelt(offset, cachedRangeMarkers);
        }
      }

      for (DocumentWindow injectedDoc : InjectedLanguageManager.getInstance(myProject).getCachedInjectedDocuments(file)) {
        PsiFile injectedFile = psiDocumentManager.getPsiFile(injectedDoc);
        if (injectedFile == null) continue;
        RangeMarker[] cachedMarkers = getCachedRangeMarkerToInjectedFragment(injectedFile);
        fastenBelts(injectedFile, 0, cachedMarkers);
      }
    }
  }

  @NotNull
  private static RangeMarker[] getCachedRangeMarkerToInjectedFragment(@NotNull PsiFile injectedFile) {
    MarkersHolderFileViewProvider provider = (MarkersHolderFileViewProvider)injectedFile.getViewProvider();
    return provider.getCachedMarkers();
  }

  public void unfastenBelts(@NotNull PsiFile file, int offset) {
    synchronized (lock) {
      PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(file.getProject());
      file.putUserData(BELTS_ARE_FASTEN_KEY, null);

      List<SmartPointerEx> pointers = getPointers(file);
      if (pointers == null) return;

      for (SmartPointerEx pointer : pointers) {
        if (pointer != null) {
          pointer.unfastenBelt(offset);
        }
      }

      for (DocumentWindow injectedDoc : InjectedLanguageManager.getInstance(myProject).getCachedInjectedDocuments(file)) {
        PsiFile injectedFile = psiDocumentManager.getPsiFile(injectedDoc);
        if (injectedFile == null) continue;
        unfastenBelts(injectedFile, 0);
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
      LOG.error("Invalid element:" + element);
    }
    SmartPointerEx<E> pointer = getCachedPointer(element);
    if (pointer != null) {
      containingFile = containingFile == null ? element.getContainingFile() : containingFile;
      if (containingFile != null && areBeltsFastened(containingFile)) {
        pointer.fastenBelt(0, null);
      }
    }
    else {
      pointer = new SmartPsiElementPointerImpl<E>(myProject, element, containingFile);
      initPointer(pointer, containingFile);
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
    SmartPointerEx cachedPointer = data == null ? null : data.get();
    if (cachedPointer != null) {
      PsiElement cachedElement = cachedPointer.getCachedElement();
      if (cachedElement != null && cachedElement != element) {
        return null;
      }
    }
    return cachedPointer;
  }

  @Override
  @NotNull
  public SmartPsiFileRange createSmartPsiFileRangePointer(@NotNull PsiFile file, @NotNull TextRange range) {
    if (!file.isValid()) {
      LOG.error("Invalid element:" + file);
    }
    SmartPsiFileRangePointerImpl pointer = new SmartPsiFileRangePointerImpl(file, ProperTextRange.create(range));
    initPointer(pointer, file);

    return pointer;
  }

  private <E extends PsiElement> void initPointer(@NotNull SmartPointerEx<E> pointer, PsiFile containingFile) {
    if (containingFile == null) return;
    synchronized (lock) {
      List<SmartPointerEx> pointers = getPointers(containingFile);
      if (pointers == null) {
        pointers = new UnsafeWeakList<SmartPointerEx>(); // we synchronise access anyway
        containingFile.putUserData(SMART_POINTERS_IN_PSI_FILE_KEY, pointers);
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
          List<SmartPointerEx> pointers = getPointers(containingFile);
          if (pointers == null) return false;
          SmartPointerElementInfo info = ((SmartPsiElementPointerImpl)pointer).getElementInfo();
          info.cleanup();
          return pointers.remove(pointer);
        }
      }
    }
    return false;
  }

  private static List<SmartPointerEx> getPointers(@NotNull PsiFile containingFile) {
    return containingFile.getUserData(SMART_POINTERS_IN_PSI_FILE_KEY);
  }

  @TestOnly
  public int getPointersNumber(@NotNull PsiFile containingFile) {
    synchronized (lock) {
      List<SmartPointerEx> pointers = getPointers(containingFile);
      return pointers == null ? 0 : pointers.size();
    }
  }

  private static boolean areBeltsFastened(@NotNull PsiFile file) {
    return file.getUserData(BELTS_ARE_FASTEN_KEY) == Boolean.TRUE;
  }


  @Override
  public boolean pointToTheSameElement(@NotNull SmartPsiElementPointer pointer1, @NotNull SmartPsiElementPointer pointer2) {
    return SmartPsiElementPointerImpl.pointsToTheSameElementAs(pointer1, pointer2);
  }
}
