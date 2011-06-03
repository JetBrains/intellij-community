/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedFileViewProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;

class SmartPsiElementPointerImpl<E extends PsiElement> implements SmartPointerEx<E> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.smartPointers.SmartPsiElementPointerImpl");

  private Reference<E> myElement;
  private final SmartPointerElementInfo myElementInfo;
  private final Class<? extends PsiElement> myElementClass;

  public SmartPsiElementPointerImpl(@NotNull Project project, @NotNull E element, @Nullable PsiFile containingFile) {
    this(element, createElementInfo(project, element, containingFile), element.getClass());
  }
  public SmartPsiElementPointerImpl(@NotNull E element,
                                    @NotNull SmartPointerElementInfo elementInfo,
                                    @NotNull Class<? extends PsiElement> elementClass) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    cacheElement(element);
    myElementClass = elementClass;
    myElementInfo = elementInfo;
    // Assert document committed.
    //todo
    //if (containingFile != null) {
    //  final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    //  if (psiDocumentManager instanceof PsiDocumentManagerImpl) {
    //    Document doc = psiDocumentManager.getCachedDocument(containingFile);
    //    if (doc != null) {
    //      //[ven] this is a really NASTY hack; when no smart pointer is kept on UsageInfo then remove this conditional
    //      if (!(element instanceof PsiFile)) {
    //        LOG.assertTrue(!psiDocumentManager.isUncommited(doc) || ((PsiDocumentManagerImpl)psiDocumentManager).isCommittingDocument(doc));
    //      }
    //    }
    //  }
    //}
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof SmartPsiElementPointer)) return false;
    SmartPsiElementPointer pointer = (SmartPsiElementPointer)obj;
    return SmartPointerManager.getInstance(getProject()).pointToTheSameElement(this, pointer);
  }

  public int hashCode() {
    return myElementInfo.elementHashCode();
  }

  @NotNull
  public Project getProject() {
    return myElementInfo.getProject();
  }

  @Nullable
  public E getElement() {
    E element = getCachedElement();
    if (element != null && !element.isValid()) {
      element = null;
    }
    if (element == null) {
      element = (E)myElementInfo.restoreElement();
      if (element != null && (!element.getClass().equals(myElementClass) || !element.isValid())) {
        element = null;
      }

      cacheElement(element);
    }

    return element;
  }

  private void cacheElement(E element) {
    myElement = element == null ? null : new SoftReference<E>(element);
  }

  private E getCachedElement() {
    Reference<E> ref = myElement;
    return ref == null ? null : ref.get();
  }

  public PsiFile getContainingFile() {
    VirtualFile virtualFile = myElementInfo.getVirtualFile();
    if (virtualFile != null && virtualFile.isValid()) {
      PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(virtualFile);
      if (psiFile != null) return psiFile;
    }

    final Document doc = myElementInfo.getDocumentToSynchronize();
    if (doc == null) {
      final E resolved = getElement();
      return resolved != null ? resolved.getContainingFile() : null;
    }
    return PsiDocumentManager.getInstance(getProject()).getPsiFile(doc);
  }

  public VirtualFile getVirtualFile() {
    return myElementInfo.getVirtualFile();
  }

  @Override
  public Segment getRange() {
    return myElementInfo.getRange();
  }

  @NotNull
  private static <E extends PsiElement> SmartPointerElementInfo createElementInfo(@NotNull Project project, @NotNull E element, PsiFile containingFile) {
    if (element instanceof PsiCompiledElement || !element.isPhysical()) {
      if (element instanceof StubBasedPsiElement && element instanceof PsiCompiledElement) {
        if (element instanceof PsiFile) {
          return new FileElementInfo((PsiFile)element);
        }
        PsiAnchor.StubIndexReference stubReference = PsiAnchor.createStubReference(element, containingFile);
        if (stubReference != null) {
          return new ClsElementInfo(stubReference);
        }
      }
      return new HardElementInfo(project, element);
    }
    if (element instanceof PsiDirectory) {
      return new DirElementInfo((PsiDirectory)element);
    }
    if (containingFile == null) {
      return new HardElementInfo(project, element);
    }

    for(SmartPointerElementInfoFactory factory: Extensions.getExtensions(SmartPointerElementInfoFactory.EP_NAME)) {
      final SmartPointerElementInfo result = factory.createElementInfo(element);
      if (result != null) return result;
    }

    FileViewProvider viewProvider = containingFile.getViewProvider();
    if (viewProvider instanceof InjectedFileViewProvider) {
      PsiElement context = containingFile.getContext();
      if (context != null) return new InjectedSelfElementInfo(project, element, context);
    }

    if (element instanceof PsiFile) {
      return new FileElementInfo((PsiFile)element);
    }

    TextRange elementRange = element.getTextRange();
    if (elementRange == null) {
      return new HardElementInfo(project, element);
    }

    LOG.assertTrue(element.isPhysical());
    LOG.assertTrue(element.isValid());

    boolean isMultiRoot = viewProvider.getAllFiles().size() > 1;
    VirtualFile virtualFile = containingFile.getVirtualFile();
    boolean isElementInMainRoot = virtualFile == null || containingFile.getManager().findFile(virtualFile) == containingFile;
    if (isMultiRoot && !isElementInMainRoot) {
      return new MultiRootSelfElementInfo(project, elementRange, element.getClass(), containingFile, containingFile.getLanguage());
    }
    return new SelfElementInfo(project, elementRange, element.getClass(), containingFile, containingFile.getLanguage());
  }

  public void documentAndPsiInSync() {
    myElementInfo.documentAndPsiInSync();
  }

  @Override
  public void unfastenBelt(int offset) {
    myElementInfo.unfastenBelt(offset);
  }

  public void fastenBelt(int offset) {
    myElementInfo.fastenBelt(offset);
  }

  @NotNull
  SmartPointerElementInfo getElementInfo() {
    return myElementInfo;
  }
}
