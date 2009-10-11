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
import com.intellij.psi.*;
import com.intellij.util.ReflectionCache;
import org.jetbrains.annotations.NotNull;

public class LazyPointerImpl<E extends PsiElement> implements SmartPointerEx<E> {
  private E myElement = null;
  private PsiAnchor myAnchor = null;
  private SmartPsiElementPointer myPointer = null;
  private final Class<? extends PsiElement> myElementClass;
  private final Project myProject;

  public LazyPointerImpl(E element) {
    myElementClass = element.getClass();
    if (element instanceof PsiCompiledElement) {
      myElement = element;
    }
    else {
      myAnchor = PsiAnchor.create(element);
    }
    myProject = element.getProject();
  }

  private static SmartPsiElementPointer setupPointer(PsiElement element) {
    return SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);
  }

  public void fastenBelt() {
    if (myAnchor != null) {
      final PsiElement element = myAnchor.retrieve();
      if (element != null) {
        myPointer = setupPointer(element);
        ((SmartPointerEx)myPointer).fastenBelt();
        myAnchor = null;
      }
      else myAnchor = null;
    }
  }

  public void documentAndPsiInSync() {
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof LazyPointerImpl)) return false;

    final LazyPointerImpl that = (LazyPointerImpl)o;

    if (myAnchor != null ? !myAnchor.equals(that.myAnchor) : that.myAnchor != null) return false;
    if (myElement != null ? !myElement.equals(that.myElement) : that.myElement != null) return false;
    if (myElementClass != null ? !myElementClass.equals(that.myElementClass) : that.myElementClass != null) return false;
    if (myPointer != null ? !myPointer.equals(that.myPointer) : that.myPointer != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myElement != null ? myElement.hashCode() : 0);
    result = 31 * result + (myAnchor != null ? myAnchor.hashCode() : 0);
    result = 31 * result + (myPointer != null ? myPointer.hashCode() : 0);
    result = 31 * result + (myElementClass != null ? myElementClass.hashCode() : 0);
    return result;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public E getElement() {
    if (myElement != null) return myElement.isValid() ? myElement : null;
    if (myPointer != null) return (E) myPointer.getElement();
    if (myAnchor != null) {
      final PsiElement psiElement = myAnchor.retrieve();
      if (psiElement != null) {
        return ReflectionCache.isAssignable(myElementClass, psiElement.getClass()) ? (E) psiElement : null;
      }
    }

    return null;
  }

  public PsiFile getContainingFile() {
    if (myElement != null) {
      return myElement.getContainingFile();
    }

    if (myAnchor != null) {
      return myAnchor.getFile();
    }

    return null;
  }

}
