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

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.FreeThreadedFileViewProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;

class SmartPsiElementPointerImpl<E extends PsiElement> implements SmartPointerEx<E> {
  private Reference<E> myElement;
  private final SmartPointerElementInfo myElementInfo;
  private final Class<? extends PsiElement> myElementClass;
  private byte myReferenceCount;

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
  }

  public boolean equals(Object obj) {
    return obj instanceof SmartPsiElementPointer && pointsToTheSameElementAs(this, (SmartPsiElementPointer)obj);
  }

  public int hashCode() {
    return myElementInfo.elementHashCode();
  }

  @Override
  @NotNull
  public Project getProject() {
    return myElementInfo.getProject();
  }

  @Override
  @Nullable
  public E getElement() {
    E element = getCachedElement();
    if (element != null && !element.isValid()) {
      element = null;
    }
    if (element == null) {
      //noinspection unchecked
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

  @Override
  public E getCachedElement() {
    return com.intellij.reference.SoftReference.dereference(myElement);
  }

  @Override
  public PsiFile getContainingFile() {
    PsiFile file = getElementInfo().restoreFile();

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
    return myElementInfo.getRange();
  }

  @NotNull
  static <E extends PsiElement> SmartPointerElementInfo createElementInfo(@NotNull Project project, @NotNull E element, PsiFile containingFile) {
    if (element instanceof PsiCompiledElement || containingFile == null || !containingFile.isPhysical() || !element.isPhysical()) {
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

    for(SmartPointerElementInfoFactory factory: Extensions.getExtensions(SmartPointerElementInfoFactory.EP_NAME)) {
      final SmartPointerElementInfo result = factory.createElementInfo(element);
      if (result != null) return result;
    }

    FileViewProvider viewProvider = containingFile.getViewProvider();
    if (viewProvider instanceof FreeThreadedFileViewProvider) {
      PsiElement hostContext = InjectedLanguageManager.getInstance(containingFile.getProject()).getInjectionHost(containingFile);
      if (hostContext != null) return new InjectedSelfElementInfo(project, element, element.getTextRange(), containingFile, hostContext);
    }

    if (element instanceof PsiFile) {
      return new FileElementInfo((PsiFile)element);
    }

    TextRange elementRange = element.getTextRange();
    if (elementRange == null) {
      return new HardElementInfo(project, element);
    }
    ProperTextRange proper = ProperTextRange.create(elementRange);

    return new SelfElementInfo(project, proper, element.getClass(), containingFile, containingFile.getLanguage());
  }

  @Override
  public void unfastenBelt(int offset) {
    myElementInfo.unfastenBelt(offset);
  }

  @Override
  public void fastenBelt(int offset, @Nullable RangeMarker[] cachedRangeMarkers) {
    myElementInfo.fastenBelt(offset, cachedRangeMarkers);
  }

  @NotNull
  public SmartPointerElementInfo getElementInfo() {
    return myElementInfo;
  }

  protected static boolean pointsToTheSameElementAs(@NotNull SmartPsiElementPointer pointer1, @NotNull SmartPsiElementPointer pointer2) {
    if (pointer1 == pointer2) return true;
    if (pointer1 instanceof SmartPsiElementPointerImpl && pointer2 instanceof SmartPsiElementPointerImpl) {
      SmartPsiElementPointerImpl impl1 = (SmartPsiElementPointerImpl)pointer1;
      SmartPsiElementPointerImpl impl2 = (SmartPsiElementPointerImpl)pointer2;
      SmartPointerElementInfo elementInfo1 = impl1.getElementInfo();
      SmartPointerElementInfo elementInfo2 = impl2.getElementInfo();
      if (!elementInfo1.pointsToTheSameElementAs(elementInfo2)) return false;
      PsiElement cachedElement1 = impl1.getCachedElement();
      PsiElement cachedElement2 = impl2.getCachedElement();
      return cachedElement1 == null || cachedElement2 == null || Comparing.equal(cachedElement1, cachedElement2);
    }
    return Comparing.equal(pointer1.getElement(), pointer2.getElement());
  }

  int incrementAndGetReferenceCount(int delta) {
    if (myReferenceCount == Byte.MAX_VALUE) return Byte.MAX_VALUE; // saturated
    return myReferenceCount += delta;
  }
}
