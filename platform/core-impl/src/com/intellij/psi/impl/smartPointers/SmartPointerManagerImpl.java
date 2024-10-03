// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.smartPointers;

import com.intellij.openapi.Disposable;
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
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.*;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

import static com.intellij.reference.SoftReference.dereference;

public final class SmartPointerManagerImpl extends SmartPointerManager implements Disposable {
  private static final Logger LOG = Logger.getInstance(SmartPointerManagerImpl.class);
  private final Project myProject;
  private final PsiDocumentManagerBase myPsiDocManager;
  private final Key<WeakReference<SmartPointerTracker>> LIGHT_TRACKER_KEY;
  private final ConcurrentMap<VirtualFile, SmartPointerTracker> myPhysicalTrackers = CollectionFactory.createConcurrentWeakValueMap();

  public SmartPointerManagerImpl(@NotNull Project project) {
    myProject = project;
    myPsiDocManager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(project);
    LIGHT_TRACKER_KEY = Key.create("SMART_POINTERS " + (project.isDefault() ? "default" : project.hashCode()));
  }

  @Override
  public void dispose() {
    SmartPointerTracker.processQueue();
  }

  private static @NotNull @NonNls String anonymize(@NotNull Project project) {
    return
      (project.isDisposed() ? "(Disposed)" : "") +
      (project.isDefault() ? "(Default)" : "") +
      project.hashCode();
  }

  public void fastenBelts(@NotNull VirtualFile file) {
    SmartPointerTracker pointers = getTracker(file);
    if (pointers != null) pointers.fastenBelts(this);
  }

  private static final Key<Reference<SmartPsiElementPointerImpl<?>>> CACHED_SMART_POINTER_KEY = Key.create("CACHED_SMART_POINTER_KEY");
  @Override
  public @NotNull <E extends PsiElement> SmartPsiElementPointer<E> createSmartPsiElementPointer(@NotNull E element) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    PsiFile containingFile = element.getContainingFile();
    return createSmartPsiElementPointer(element, containingFile);
  }
  @Override
  public @NotNull <E extends PsiElement> SmartPsiElementPointer<E> createSmartPsiElementPointer(@NotNull E element, PsiFile containingFile) {
    return createSmartPsiElementPointer(element, containingFile, false);
  }

  public @NotNull <E extends PsiElement> SmartPsiElementPointer<E> createSmartPsiElementPointer(@NotNull E element,
                                                                                                PsiFile containingFile,
                                                                                                boolean forInjected) {
    ensureValid(element, containingFile);
    SmartPointerTracker.processQueue();
    ensureMyProject(containingFile != null ? containingFile.getProject() : element.getProject());
    SmartPsiElementPointerImpl<E> pointer = getCachedPointer(element);
    if (pointer != null &&
        (!(pointer.getElementInfo() instanceof SelfElementInfo) || ((SelfElementInfo)pointer.getElementInfo()).isForInjected() == forInjected) &&
        pointer.incrementAndGetReferenceCount(1) > 0) {
      return pointer;
    }

    pointer = new SmartPsiElementPointerImpl<>(this, element, containingFile, forInjected);
    if (containingFile != null) {
      trackPointer(pointer, containingFile.getViewProvider().getVirtualFile());
    }
    element.putUserData(CACHED_SMART_POINTER_KEY, new SoftReference<>(pointer));
    return pointer;
  }

  private void ensureMyProject(@NotNull Project project) {
    if (project != myProject) {
      boolean basePathEquals = Objects.equals(myProject.getBasePath(), project.getBasePath());
      boolean namesEquals = myProject.getName().equals(project.getName());
      throw new IllegalArgumentException("Element from alien project: "+ anonymize(project) + " expected: " + anonymize(myProject) +
                                         "; basePathEquals: " + basePathEquals + "; nameEquals: " + namesEquals);
    }
  }

  private static void ensureValid(@NotNull PsiElement element, @Nullable PsiFile containingFile) {
    boolean valid = containingFile != null ? containingFile.isValid() : element.isValid();
    if (!valid) {
      PsiUtilCore.ensureValid(element);
      if (containingFile != null && !containingFile.isValid()) {
        throw new PsiInvalidElementAccessException(containingFile, "Element " + element.getClass() + "(" + element.getLanguage() + ")" + " claims to be valid but returns invalid containing file ");
      }
    }
  }

  private static <E extends PsiElement> SmartPsiElementPointerImpl<E> getCachedPointer(@NotNull E element) {
    Reference<SmartPsiElementPointerImpl<?>> data = element.getUserData(CACHED_SMART_POINTER_KEY);
    SmartPsiElementPointerImpl<?> cachedPointer = dereference(data);
    if (cachedPointer != null) {
      PsiElement cachedElement = cachedPointer.getElement();
      if (cachedElement != element) {
        return null;
      }
    }
    //noinspection unchecked
    return (SmartPsiElementPointerImpl<E>)cachedPointer;
  }

  @Override
  public @NotNull SmartPsiFileRange createSmartPsiFileRangePointer(@NotNull PsiFile file, @NotNull TextRange range) {
    return createSmartPsiFileRangePointer(file, range, false);
  }

  public @NotNull SmartPsiFileRange createSmartPsiFileRangePointer(@NotNull PsiFile file,
                                                                   @NotNull TextRange range,
                                                                   boolean forInjected) {
    PsiUtilCore.ensureValid(file);
    SmartPointerTracker.processQueue();
    SmartPsiFileRangePointerImpl pointer = new SmartPsiFileRangePointerImpl(this, file, ProperTextRange.create(range), forInjected);
    trackPointer(pointer, file.getViewProvider().getVirtualFile());

    return pointer;
  }

  private <E extends PsiElement> void trackPointer(@NotNull SmartPsiElementPointerImpl<E> pointer, @NotNull VirtualFile containingFile) {
    SmartPointerElementInfo info = pointer.getElementInfo();
    if (!(info instanceof SelfElementInfo)) return;

    SmartPointerTracker tracker = getTracker(containingFile);
    if (tracker == null) {
      tracker = getOrCreateTracker(containingFile);
    }
    tracker.addReference(pointer);
  }

  @Override
  public void removePointer(@NotNull SmartPsiElementPointer<?> pointer) {
    if (!(pointer instanceof SmartPsiElementPointerImpl) || myProject.isDisposed()) {
      return;
    }
    ensureMyProject(pointer.getProject());
    int refCount = ((SmartPsiElementPointerImpl<?>)pointer).incrementAndGetReferenceCount(-1);
    if (refCount == -1) {
      LOG.error("Double smart pointer removal");
      return;
    }

    if (refCount == 0) {
      PsiElement element = ((SmartPointerEx<?>)pointer).getCachedElement();
      if (element != null) {
        element.putUserData(CACHED_SMART_POINTER_KEY, null);
      }

      SmartPointerElementInfo info = ((SmartPsiElementPointerImpl<?>)pointer).getElementInfo();
      info.cleanup();

      SmartPointerTracker.PointerReference reference = ((SmartPsiElementPointerImpl<?>)pointer).pointerReference;
      if (reference != null) {
        if (reference.get() != pointer) {
          throw new IllegalStateException("Reference points to " + reference.get());
        }
        reference.tracker.removeReference(reference);
      }
    }
  }

  @Nullable
  SmartPointerTracker getTracker(@NotNull VirtualFile file) {
    return file instanceof LightVirtualFile ? dereference(file.getUserData(LIGHT_TRACKER_KEY)) : myPhysicalTrackers.get(file);
  }

  private @NotNull SmartPointerTracker getOrCreateTracker(@NotNull VirtualFile file) {
    synchronized (myPhysicalTrackers) {
      SmartPointerTracker tracker = getTracker(file);
      if (tracker == null) {
        tracker = new SmartPointerTracker();
        if (file instanceof LightVirtualFile) {
          file.putUserData(LIGHT_TRACKER_KEY, new WeakReference<>(tracker));
        } else {
          myPhysicalTrackers.put(file, tracker);
        }
      }
      return tracker;
    }
  }

  @TestOnly
  public int getPointersNumber(@NotNull PsiFile containingFile) {
    VirtualFile file = containingFile.getViewProvider().getVirtualFile();
    SmartPointerTracker pointers = getTracker(file);
    return pointers == null ? 0 : pointers.getSize();
  }

  @Override
  public boolean pointToTheSameElement(@NotNull SmartPsiElementPointer<?> pointer1, @NotNull SmartPsiElementPointer<?> pointer2) {
    return SmartPsiElementPointerImpl.pointsToTheSameElementAs(pointer1, pointer2);
  }

  @ApiStatus.Internal
  public void updatePointers(@NotNull Document document, @NotNull FrozenDocument frozen, @NotNull List<? extends DocumentEvent> events) {
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    SmartPointerTracker list = file == null ? null : getTracker(file);
    if (list != null) list.updateMarkers(frozen, events);
  }

  public void updatePointerTargetsAfterReparse(@NotNull VirtualFile file) {
    SmartPointerTracker list = getTracker(file);
    if (list != null) list.updatePointerTargetsAfterReparse();
  }

  @NotNull
  Project getProject() {
    return myProject;
  }

  @NotNull
  PsiDocumentManagerBase getPsiDocumentManager() {
    return myPsiDocManager;
  }
}
