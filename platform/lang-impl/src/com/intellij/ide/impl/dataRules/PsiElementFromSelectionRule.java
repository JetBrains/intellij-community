// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.impl.dataRules;

import com.intellij.model.Pointer;
import com.intellij.navigation.PsiElementNavigationItem;
import com.intellij.openapi.actionSystem.DataMap;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiAwareObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.actionSystem.PlatformCoreDataKeys.PROJECT;
import static com.intellij.openapi.actionSystem.PlatformCoreDataKeys.SELECTED_ITEM;

final class PsiElementFromSelectionRule {
  static @Nullable PsiElement getData(@NotNull DataMap dataProvider) {
    Object item = dataProvider.get(SELECTED_ITEM);
    PsiElement element = null;
    if (item instanceof PsiElement o) {
      element = o;
    }
    else if (item instanceof PsiAwareObject o) {
      Project project = dataProvider.get(PROJECT);
      element = project == null ? null : o.findElement(project);
    }
    else if (item instanceof PsiElementNavigationItem o) {
      element = o.getTargetElement();
    }
    else if (item instanceof VirtualFile o) {
      Project project = dataProvider.get(PROJECT);
      element = project == null || !o.isValid() ? null :
                PsiManager.getInstance(project).findFile(o);
    }
    else if (item instanceof Pointer<?>) {
      element = getElement((Pointer<?>)item);
    }
    return element != null && element.isValid() ? element : null;
  }

  static @Nullable PsiElement getElement(Pointer<?> item) {
    Object o = item.dereference();
    return o instanceof PsiElement ? (PsiElement)o : null;
  }
}
