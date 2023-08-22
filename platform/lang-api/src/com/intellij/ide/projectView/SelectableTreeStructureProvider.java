// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.projectView;

import org.jetbrains.annotations.Nullable;
import com.intellij.psi.PsiElement;


public interface SelectableTreeStructureProvider extends TreeStructureProvider {
  /**
   * Returns the element which should be selected in the tree when the "Select In" action is
   * invoked for the specified target.
   *
   * @param element element on which "Select In" was invoked.
   * @return the element to select in the tree, or null if default selection logic should be used.
   */
  @Nullable
  PsiElement getTopLevelElement(PsiElement element);
}
