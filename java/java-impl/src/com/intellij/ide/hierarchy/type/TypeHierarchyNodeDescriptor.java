// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.hierarchy.type;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.JavaHierarchyNodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public final class TypeHierarchyNodeDescriptor extends JavaHierarchyNodeDescriptor {
  public TypeHierarchyNodeDescriptor(@NotNull Project project, HierarchyNodeDescriptor parentDescriptor, @NotNull PsiElement classOrFunctionalExpression, boolean isBase) {
    super(project, parentDescriptor, classOrFunctionalExpression, isBase);
  }

  public PsiElement getPsiClass() {
    return getPsiElement();
  }

  @Override
  public boolean update() {
    boolean changes = super.update();

    if (getPsiElement() == null) {
      return invalidElement();
    }

    if (changes && myIsBase) {
      setIcon(getBaseMarkerIcon(getIcon()));
    }
    return recalculateHighlightedText(getPsiClass()) || changes;
  }
}
