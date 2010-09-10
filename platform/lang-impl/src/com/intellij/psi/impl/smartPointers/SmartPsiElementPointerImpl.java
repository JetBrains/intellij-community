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
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class SmartPsiElementPointerImpl<E extends PsiElement> implements SmartPointerEx<E> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.smartPointers.SmartPsiElementPointerImpl");

  private E myElement;
  private SmartPointerElementInfo myElementInfo;
  private final Project myProject;

  public SmartPsiElementPointerImpl(Project project, E element) {
    myProject = project;
    ApplicationManager.getApplication().assertReadAccessAllowed();
    myElement = element;
    myElementInfo = null;

    // Assert document committed.
    PsiFile file = element.getContainingFile();
    if (file != null) {
      final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
      if (psiDocumentManager instanceof PsiDocumentManagerImpl) {
        Document doc = psiDocumentManager.getCachedDocument(file);
        if (doc != null) {
          //[ven] this is a really NASTY hack; when no smart pointer is kept on UsageInfo then remove this conditional
          if (!(element instanceof PsiFile)) {
            LOG.assertTrue(!psiDocumentManager.isUncommited(doc) || ((PsiDocumentManagerImpl)psiDocumentManager).isCommittingDocument(doc));
          }
        }
      }
    }
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof SmartPsiElementPointer)) return false;
    SmartPsiElementPointer pointer = (SmartPsiElementPointer)obj;
    return Comparing.equal(pointer.getElement(), getElement());
  }

  public int hashCode() {
    PsiElement element = getElement();
    return element != null ? element.hashCode() : 0;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Nullable
  public E getElement() {
    if (myElement != null && !myElement.isValid()) {
      if (myElementInfo == null) {
        myElement = null;
      }
      else {
        PsiElement restored = myElementInfo.restoreElement();
        if (restored != null && (!areElementKindEqual(restored, myElement) || !restored.isValid())) {
          restored = null;
        }

        myElement = (E) restored;
      }
    }

    //if (myElementInfo != null && myElement != null) {
    //  Document document = myElementInfo.getDocumentToSynchronize();
    //  if (document != null && PsiDocumentManager.getInstance(myProject).isUncommited(document)) return myElement; // keep element info if document is modified
    //}
    // myElementInfo = null;

    return myElement;
  }

  public PsiFile getContainingFile() {
    if (myElement != null) {
      return myElement.getContainingFile();
    }

    final Document doc = myElementInfo == null ? null : myElementInfo.getDocumentToSynchronize();
    if (doc == null) {
      final E resolved = getElement();
      return resolved != null ? resolved.getContainingFile() : null;
    }
    return PsiDocumentManager.getInstance(myProject).getPsiFile(doc);
  }

  @Nullable
  private SmartPointerElementInfo createElementInfo() {
    if (myElement instanceof PsiCompiledElement) return null;

    final PsiFile containingFile = myElement.getContainingFile();
    if (containingFile == null) return null;
    if (!myElement.isPhysical()) return null;

    for(SmartPointerElementInfoFactory factory: Extensions.getExtensions(SmartPointerElementInfoFactory.EP_NAME)) {
      final SmartPointerElementInfo result = factory.createElementInfo(myElement);
      if (result != null) {
        return result;
      }
    }

    if (myElement instanceof PsiFile) {
      return new FileElementInfo((PsiFile)myElement);
    }
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(containingFile);
    if (document == null) return null;   // must be non-text file

    if (containingFile.getContext() != null) {
      return new InjectedSelfElementInfo(myElement, document);
    }

    return new SelfElementInfo(myElement, document);
  }

  private static boolean areElementKindEqual(PsiElement element1, PsiElement element2) {
    return element1.getClass().equals(element2.getClass()); //?
  }

  public void documentAndPsiInSync() {
    if (myElementInfo != null) {
      myElementInfo.documentAndPsiInSync();
    }
  }

  public void fastenBelt() {
    if (myElementInfo != null && myElement != null && myElement.isValid()) return;

    if (myElementInfo == null && myElement != null && myElement.isValid()) {
      myElementInfo = createElementInfo();
    }
  }

}
