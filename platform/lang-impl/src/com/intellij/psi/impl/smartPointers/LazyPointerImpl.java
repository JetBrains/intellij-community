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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.ReflectionCache;
import org.jetbrains.annotations.NotNull;

public class LazyPointerImpl<E extends PsiElement> implements SmartPointerEx<E> {
  private E myElement = null;
  private PsiAnchor myAnchor = null;
  private SmartPsiElementPointer myPointer = null;
  private final Class<? extends PsiElement> myElementClass;
  private final Project myProject;

  LazyPointerImpl(@NotNull E element) {
    myElementClass = element.getClass();
    if (element instanceof PsiCompiledElement) {
      myElement = element;
    }
    else {
      myAnchor = PsiAnchor.create(element);
    }
    myProject = element.getProject();
  }

  public boolean pointsToTheSameElementAs(LazyPointerImpl pointer) {
    if (myElementClass != pointer.myElementClass) return false;
    if (myElement != null) return pointer.myElement == myElement;
    if (myAnchor != null && pointer.myAnchor != null) {
      return myAnchor.pointsToTheSameElementAs(pointer.myAnchor);
    }
    if (myPointer != null && pointer.myPointer != null) {
      return SmartPointerManager.getInstance(myProject).pointToTheSameElement(myPointer, pointer.myPointer);
    }
    return Comparing.equal(getElement(), pointer.getElement());
  }

  private static SmartPsiElementPointer setupPointer(PsiElement element) {
    return SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);
  }

  public void fastenBelt(int offset) {
    if (myAnchor != null) {
      final PsiElement element = myAnchor.retrieve();
      if (element != null) {
        myPointer = setupPointer(element);
        //((SmartPointerEx)myPointer).fastenBelt(offset);
        myAnchor = null;
      }
    }
  }
  @Override
  public void unfastenBelt(int offset) {
  }

  public void documentAndPsiInSync() {
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof LazyPointerImpl)) return false;
    final LazyPointerImpl that = (LazyPointerImpl)o;
    return pointsToTheSameElementAs(that);
  }

  public int hashCode() {
    int result = myElement != null ? myElement.hashCode() : 0;
    result = 31 * result + (myAnchor != null ? myAnchor.hashCode() : 0);
    result = 31 * result + (myPointer != null ? myPointer.hashCode() : 0);
    result = 31 * result + (myElementClass != null ? myElementClass.hashCode() : 0);
    return result;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Override
  public VirtualFile getVirtualFile() {
    PsiFile psiFile = getContainingFile();
    return psiFile == null ? null : psiFile.getVirtualFile();
  }

  public E getElement() {
    E element = myElement;
    if (element != null) return element.isValid() ? element : null;
    SmartPsiElementPointer pointer = myPointer;
    if (pointer != null) return (E) pointer.getElement();
    PsiAnchor anchor = myAnchor;
    if (anchor != null) {
      final PsiElement psiElement = anchor.retrieve();
      if (psiElement != null) {
        return ReflectionCache.isAssignable(myElementClass, psiElement.getClass()) ? (E) psiElement : null;
      }
    }

    return null;
  }

  public PsiFile getContainingFile() {
    E element = myElement;
    if (element != null) {
      return element.getContainingFile();
    }

    PsiAnchor anchor = myAnchor;
    if (anchor != null) {
      return anchor.getFile();
    }

    SmartPsiElementPointer pointer = myPointer;
    if (pointer != null) {
      return pointer.getContainingFile();
    }

    return null;
  }

  @Override
  public void dispose() {
    if (myPointer != null) ((SmartPointerEx)myPointer).dispose();
  }

  @Override
  public Segment getSegment() {
    E element = myElement;
    if (element != null && element.isValid()) return element.getTextRange();
    SmartPsiElementPointer pointer = myPointer;
    if (pointer != null) return pointer.getSegment();
    PsiAnchor anchor = myAnchor;
    if (anchor != null) {
      final PsiElement psiElement = anchor.retrieve();
      if (psiElement != null) {
        return psiElement.getTextRange();
      }
    }

    return null;
  }
}
