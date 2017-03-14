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
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.util.treeView.TreeAnchorizer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class PsiTreeAnchorizer extends TreeAnchorizer {
  @Override
  public Object createAnchor(Object element) {
    if (element instanceof PsiElement) {
      PsiElement psi = (PsiElement)element;
      return ReadAction.compute(() -> {
        if (!psi.isValid()) return psi;
        return new SmartPointerWrapper(SmartPointerManager.getInstance(psi.getProject()).createSmartPsiElementPointer(psi));
      });
    }
    return super.createAnchor(element);
  }
  @Override
  @Nullable
  public Object retrieveElement(final Object pointer) {
    if (pointer instanceof SmartPointerWrapper) {
      return ReadAction.compute(() -> ((SmartPointerWrapper)pointer).myPointer.getElement());
    }

    return super.retrieveElement(pointer);
  }

  @Override
  public void freeAnchor(final Object element) {
    if (element instanceof SmartPointerWrapper) {
      ApplicationManager.getApplication().runReadAction(() -> {
        SmartPsiElementPointer pointer = ((SmartPointerWrapper)element).myPointer;
        Project project = pointer.getProject();
        if (!project.isDisposed()) {
          SmartPointerManager.getInstance(project).removePointer(pointer);
        }
      });
    }
  }

  private static class SmartPointerWrapper {
    private final SmartPsiElementPointer myPointer;

    private SmartPointerWrapper(@NotNull SmartPsiElementPointer pointer) {
      myPointer = pointer;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof SmartPointerWrapper)) return false;

      SmartPointerWrapper wrapper = (SmartPointerWrapper)o;

      if (!myPointer.equals(wrapper.myPointer)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myPointer.hashCode();
    }
  }
}
