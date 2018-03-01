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
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.lang.ref.Reference;
import java.util.List;

public class SmartPointerManagerImpl extends SmartPointerManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl");
  private final Project myProject;
  private final Key<SmartPointerTracker> POINTERS_KEY;
  private final PsiDocumentManagerBase myPsiDocManager;

  public SmartPointerManagerImpl(Project project) {
    myProject = project;
    myPsiDocManager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(myProject);
    POINTERS_KEY = Key.create("SMART_POINTERS for "+project);
  }

  public void fastenBelts(@NotNull VirtualFile file) {
    SmartPointerTracker pointers = getTracker(file);
    if (pointers != null) pointers.fastenBelts();
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
      if (containingFile != null) {
        PsiUtilCore.ensureValid(containingFile);
      }
      PsiUtilCore.ensureValid(element);
      LOG.error("Invalid element:" + element);
    }
    SmartPointerTracker.processQueue();
    SmartPsiElementPointerImpl<E> pointer = getCachedPointer(element);
    if (pointer != null &&
        (!(pointer.getElementInfo() instanceof SelfElementInfo) || ((SelfElementInfo)pointer.getElementInfo()).isForInjected() == forInjected) &&
        pointer.incrementAndGetReferenceCount(1) > 0) {
      return pointer;
    }

    pointer = new SmartPsiElementPointerImpl<>(myProject, element, containingFile, forInjected);
    if (containingFile != null) {
      trackPointer(pointer, containingFile.getViewProvider().getVirtualFile());
    }
    element.putUserData(CACHED_SMART_POINTER_KEY, new SoftReference<>(pointer));
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
    PsiUtilCore.ensureValid(file);
    SmartPointerTracker.processQueue();
    SmartPsiFileRangePointerImpl pointer = new SmartPsiFileRangePointerImpl(file, ProperTextRange.create(range), forInjected);
    trackPointer(pointer, file.getViewProvider().getVirtualFile());

    return pointer;
  }

  private <E extends PsiElement> void trackPointer(@NotNull SmartPsiElementPointerImpl<E> pointer, @NotNull VirtualFile containingFile) {
    SmartPointerElementInfo info = pointer.getElementInfo();
    if (!(info instanceof SelfElementInfo)) return;

    SmartPointerTracker.PointerReference reference = new SmartPointerTracker.PointerReference(pointer, containingFile, POINTERS_KEY);
    while (true) {
      SmartPointerTracker pointers = getTracker(containingFile);
      if (pointers == null) {
        pointers = containingFile.putUserDataIfAbsent(POINTERS_KEY, new SmartPointerTracker());
      }
      if (pointers.addReference(reference, pointer)) {
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
    if (refCount == -1) {
      LOG.error("Double smart pointer removal: " + pointer);
      return;
    }

    if (refCount == 0) {
      PsiElement element = ((SmartPointerEx)pointer).getCachedElement();
      if (element != null) {
        element.putUserData(CACHED_SMART_POINTER_KEY, null);
      }

      SmartPointerElementInfo info = ((SmartPsiElementPointerImpl)pointer).getElementInfo();
      info.cleanup();

      if (containingFile == null) return;

      assert containingFile.getProject() == myProject : "Project mismatch: expected " + myProject + ", got " + containingFile.getProject();

      VirtualFile vFile = containingFile.getViewProvider().getVirtualFile();
      SmartPointerTracker pointers = getTracker(vFile);
      SmartPointerTracker.PointerReference reference = ((SmartPsiElementPointerImpl)pointer).pointerReference;
      if (pointers != null && reference != null) {
        pointers.removeReference(reference, POINTERS_KEY);
      }
    }
  }

  @Nullable
  SmartPointerTracker getTracker(@NotNull VirtualFile containingFile) {
    return containingFile.getUserData(POINTERS_KEY);
  }

  @TestOnly
  public int getPointersNumber(@NotNull PsiFile containingFile) {
    VirtualFile file = containingFile.getViewProvider().getVirtualFile();
    SmartPointerTracker pointers = getTracker(file);
    return pointers == null ? 0 : pointers.getSize();
  }

  @Override
  public boolean pointToTheSameElement(@NotNull SmartPsiElementPointer pointer1, @NotNull SmartPsiElementPointer pointer2) {
    return SmartPsiElementPointerImpl.pointsToTheSameElementAs(pointer1, pointer2);
  }

  public void updatePointers(Document document, FrozenDocument frozen, List<DocumentEvent> events) {
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    SmartPointerTracker list = file == null ? null : getTracker(file);
    if (list != null) list.updateMarkers(frozen, events);
  }

  public void updatePointerTargetsAfterReparse(@NotNull VirtualFile file) {
    SmartPointerTracker list = getTracker(file);
    if (list != null) list.updatePointerTargetsAfterReparse();
  }

  Project getProject() {
    return myProject;
  }

  PsiDocumentManagerBase getPsiDocumentManager() {
    return myPsiDocManager;
  }
}
