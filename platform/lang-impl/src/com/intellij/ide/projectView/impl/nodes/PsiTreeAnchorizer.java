// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

public final class PsiTreeAnchorizer extends TreeAnchorizer {
  @Override
  public @NotNull Object createAnchor(@NotNull Object element) {
    if (element instanceof PsiElement psi) {
      return ReadAction.compute(() -> {
        if (!psi.isValid()) return psi;
        return SmartPointerManager.getInstance(psi.getProject()).createSmartPsiElementPointer(psi);
      });
    }
    return super.createAnchor(element);
  }
  @Override
  public @Nullable Object retrieveElement(final @NotNull Object pointer) {
    if (pointer instanceof SmartPsiElementPointer) {
      return ReadAction.compute(() -> ((SmartPsiElementPointer<?>)pointer).getElement());
    }

    return super.retrieveElement(pointer);
  }

  @Override
  public void freeAnchor(final Object element) {
    if (element instanceof SmartPsiElementPointer) {
      ApplicationManager.getApplication().runReadAction(() -> {
        SmartPsiElementPointer<?> pointer = (SmartPsiElementPointer<?>)element;
        Project project = pointer.getProject();
        if (!project.isDisposed()) {
          SmartPointerManager.getInstance(project).removePointer(pointer);
        }
      });
    }
  }
}
