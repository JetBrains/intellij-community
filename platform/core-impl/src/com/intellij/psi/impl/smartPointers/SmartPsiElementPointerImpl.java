// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.smartPointers;

import com.intellij.extapi.psi.ASTDelegatePsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageUtil;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.FreeThreadedFileViewProvider;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;

class SmartPsiElementPointerImpl<E extends PsiElement> implements SmartPointerEx<E> {
  private static final Logger LOG = Logger.getInstance(SmartPsiElementPointerImpl.class);

  private Reference<E> myElement;
  private final SmartPointerElementInfo myElementInfo;
  protected final SmartPointerManagerImpl myManager;
  private byte myReferenceCount = 1;
  @Nullable SmartPointerTracker.PointerReference pointerReference;

  SmartPsiElementPointerImpl(@NotNull SmartPointerManagerImpl manager,
                             @NotNull E element,
                             @Nullable PsiFile containingFile,
                             boolean forInjected) {
    this(manager, element, createElementInfo(manager, element, containingFile, forInjected));
  }
  SmartPsiElementPointerImpl(@NotNull SmartPointerManagerImpl manager,
                             @NotNull E element,
                             @NotNull SmartPointerElementInfo elementInfo) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    myElementInfo = elementInfo;
    myManager = manager;
    cacheElement(element);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof SmartPsiElementPointer && pointsToTheSameElementAs(this, (SmartPsiElementPointer<?>)obj);
  }

  @Override
  public int hashCode() {
    return myElementInfo.elementHashCode();
  }

  @Override
  @NotNull
  public Project getProject() {
    return myManager.getProject();
  }

  @Override
  @Nullable
  public E getElement() {
    if (getProject().isDisposed()) return null;

    E element = getCachedElement();
    if (element == null || !element.isValid()) {
      element = doRestoreElement();
      cacheElement(element);
    }
    return element;
  }

  @Nullable
  E doRestoreElement() {
    //noinspection unchecked
    E element = (E)myElementInfo.restoreElement(myManager);
    if (element != null && !element.isValid()) {
      return null;
    }
    return element;
  }

  void cacheElement(@Nullable E element) {
    myElement = element == null ? null :
                PsiManagerEx.getInstanceEx(getProject()).isBatchFilesProcessingMode() ? new WeakReference<>(element) :
                new SoftReference<>(element);
  }

  @Override
  public E getCachedElement() {
    return com.intellij.reference.SoftReference.dereference(myElement);
  }

  @Override
  public PsiFile getContainingFile() {
    PsiFile file = getElementInfo().restoreFile(myManager);

    if (file != null) {
      return file;
    }

    final Document doc = myElementInfo.getDocumentToSynchronize();
    if (doc == null) {
      final E resolved = getElement();
      return resolved == null ? null : resolved.getContainingFile();
    }
    return PsiDocumentManager.getInstance(getProject()).getPsiFile(doc);
  }

  @Override
  public VirtualFile getVirtualFile() {
    return myElementInfo.getVirtualFile();
  }

  @Override
  public Segment getRange() {
    return myElementInfo.getRange(myManager);
  }

  @Nullable
  @Override
  public Segment getPsiRange() {
    return myElementInfo.getPsiRange(myManager);
  }

  @NotNull
  private static <E extends PsiElement> SmartPointerElementInfo createElementInfo(@NotNull SmartPointerManagerImpl manager,
                                                                                  @NotNull E element,
                                                                                  @Nullable PsiFile containingFile,
                                                                                  boolean forInjected) {
    SmartPointerElementInfo elementInfo = doCreateElementInfo(manager.getProject(), element, containingFile, forInjected);
    if (ApplicationManager.getApplication().isUnitTestMode() && !ApplicationManagerEx.isInStressTest()) {
      PsiElement restoredElement = elementInfo.restoreElement(manager);
      if (restoredElement == null) {
        // The problem might be with injection. It's a questionable solution, requires more discussion.
        elementInfo = doCreateElementInfo(manager.getProject(), element, containingFile, !forInjected);
        restoredElement = elementInfo.restoreElement(manager);
      }
      if (!element.equals(restoredElement)) {
        // likely cause: PSI having isPhysical==true, but which can't be restored by containing file and range. To fix, make isPhysical return false
        LOG.error("Cannot restore " + element + " of " + element.getClass() + " from " + elementInfo +
                  "; restored=" + restoredElement + (restoredElement == null ? "" : " of " + restoredElement.getClass()) + " in " + element.getProject());
      }
    }
    return elementInfo;
  }

  @NotNull
  private static <E extends PsiElement> SmartPointerElementInfo doCreateElementInfo(@NotNull Project project,
                                                                                    @NotNull E element,
                                                                                    @Nullable PsiFile containingFile,
                                                                                    boolean forInjected) {
    if (element instanceof PsiDirectory) {
      return new DirElementInfo((PsiDirectory)element);
    }
    if (element instanceof PsiCompiledElement || containingFile == null) {
      if (element instanceof StubBasedPsiElement && element instanceof PsiCompiledElement) {
        if (element instanceof PsiFile) {
          return new FileElementInfo((PsiFile)element);
        }
        PsiAnchor.StubIndexReference stubReference = PsiAnchor.createStubReference(element, containingFile);
        if (stubReference != null) {
          return new ClsElementInfo(stubReference);
        }
      }
      return new HardElementInfo(element);
    }

    FileViewProvider viewProvider = containingFile.getViewProvider();
    if (viewProvider instanceof FreeThreadedFileViewProvider && hasReliableRange(element, containingFile)) {
      PsiLanguageInjectionHost hostContext = InjectedLanguageManager.getInstance(containingFile.getProject()).getInjectionHost(containingFile);
      TextRange elementRange = element.getTextRange();
      if (hostContext != null && elementRange != null) {
        SmartPsiElementPointer<PsiLanguageInjectionHost> hostPointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(hostContext);
        return new InjectedSelfElementInfo(project, element, elementRange, containingFile, hostPointer);
      }
    }

    VirtualFile virtualFile = viewProvider.getVirtualFile();
    if (element instanceof PsiFile) {
      FileViewProvider restored = PsiManager.getInstance(project).findViewProvider(virtualFile);
      return restored != null && restored.getPsi(LanguageUtil.getRootLanguage(element)) == element
             ? new FileElementInfo((PsiFile)element)
             : new HardElementInfo(element);
    }

    if (!hasReliableRange(element, containingFile)) {
      return new HardElementInfo(element);
    }

    Document document = FileDocumentManager.getInstance().getCachedDocument(virtualFile);
    if (document != null &&
        ((PsiDocumentManagerBase)PsiDocumentManager.getInstance(project)).getSynchronizer().isDocumentAffectedByTransactions(document)) {
      LOG.error("Smart pointers must not be created during PSI changes");
    }

    SmartPointerElementInfo info = createAnchorInfo(element, containingFile);
    if (info != null) {
      return info;
    }

    TextRange elementRange = element.getTextRange();
    if (elementRange == null) {
      return new HardElementInfo(element);
    }
    Identikit.ByType identikit = Identikit.fromPsi(element, LanguageUtil.getRootLanguage(element));
    if (elementRange.isEmpty() &&
        identikit.findPsiElement(containingFile, elementRange.getStartOffset(), elementRange.getEndOffset()) != element) {
      // PSI has empty range, no text, but complicated structure (e.g. PSI built on C-style macro expansions). It can't be reliably
      // restored by just one offset in a file, so hold it on a hard reference
      return new HardElementInfo(element);
    }

    if (!containingFile.isPhysical() && document == null) {
      // there's no document whose events could be tracked and used for restoration by offset
      return new HardElementInfo(element);
    }

    ProperTextRange proper = ProperTextRange.create(elementRange);
    return new SelfElementInfo(proper, identikit, containingFile, forInjected);
  }

  // check it's not some fake PSI that overrides getContainingFile/getTextRange/isPhysical/etc and confuses everyone
  private static boolean hasReliableRange(@NotNull PsiElement element, @NotNull PsiFile containingFile) {
    return (element instanceof ASTDelegatePsiElement || element instanceof ASTNode) && !isFakePsiInNormalFile(element, containingFile);
  }

  private static boolean isFakePsiInNormalFile(@NotNull PsiElement element, @NotNull PsiFile containingFile) {
    if (element.isPhysical()) return false;
    if (containingFile.isPhysical()) return true; // non-physical PSI in physical file, suspicious

    // in normal non-physical files there might also be fake PSI, so let's (expensively!) check we can find it by offset
    // hopefully in some future we'll prohibit such fake PSI
    TextRange range = element.getTextRange();
    return range == null ||
           PsiTreeUtil.findElementOfClassAtRange(containingFile, range.getStartOffset(), range.getEndOffset(), element.getClass()) != element;
  }

  @Nullable
  private static SmartPointerElementInfo createAnchorInfo(@NotNull PsiElement element, @NotNull PsiFile containingFile) {
    if (element instanceof StubBasedPsiElement && containingFile instanceof PsiFileImpl) {
      IStubFileElementType<?> stubType = ((PsiFileImpl)containingFile).getElementTypeForStubBuilder();
      if (stubType != null && stubType.shouldBuildStubFor(containingFile.getViewProvider().getVirtualFile())) {
        StubBasedPsiElement<?> stubPsi = (StubBasedPsiElement<?>)element;
        int stubId = PsiAnchor.calcStubIndex(stubPsi);
        if (stubId != -1) {
          return new AnchorElementInfo(element, (PsiFileImpl)containingFile, stubId, stubPsi.getElementType());
        }
      }
    }

    Pair<Identikit.ByAnchor, PsiElement> pair = Identikit.withAnchor(element, LanguageUtil.getRootLanguage(containingFile));
    if (pair != null) {
      return new AnchorElementInfo(pair.second, containingFile, pair.first);
    }
    return null;
  }

  @NotNull
  SmartPointerElementInfo getElementInfo() {
    return myElementInfo;
  }

  static boolean pointsToTheSameElementAs(@NotNull SmartPsiElementPointer<?> pointer1, @NotNull SmartPsiElementPointer<?> pointer2) {
    if (pointer1 == pointer2) return true;
    ProgressManager.checkCanceled();
    if (pointer1 instanceof SmartPsiElementPointerImpl && pointer2 instanceof SmartPsiElementPointerImpl) {
      SmartPsiElementPointerImpl<?> impl1 = (SmartPsiElementPointerImpl<?>)pointer1;
      SmartPsiElementPointerImpl<?> impl2 = (SmartPsiElementPointerImpl<?>)pointer2;
      SmartPointerElementInfo elementInfo1 = impl1.getElementInfo();
      SmartPointerElementInfo elementInfo2 = impl2.getElementInfo();
      if (!elementInfo1.pointsToTheSameElementAs(elementInfo2, impl1.myManager)) return false;
      PsiElement cachedElement1 = impl1.getCachedElement();
      PsiElement cachedElement2 = impl2.getCachedElement();
      return cachedElement1 == null || cachedElement2 == null || cachedElement1.equals(cachedElement2);
    }
    return Comparing.equal(pointer1.getElement(), pointer2.getElement());
  }

  synchronized int incrementAndGetReferenceCount(int delta) {
    if (myReferenceCount == Byte.MAX_VALUE) return Byte.MAX_VALUE; // saturated
    if (myReferenceCount == 0) return -1; // disposed, not to be reused again
    return myReferenceCount += delta;
  }

  @Override
  public String toString() {
    return myElementInfo.toString();
  }
}
