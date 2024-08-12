// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.impl.dataRules;

import com.intellij.model.Pointer;
import com.intellij.navigation.PsiElementNavigationItem;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiAwareObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PsiElementFromSelectionRule implements GetDataRule {
  @Override
  public Object getData(@NotNull DataProvider dataProvider) {
    Object item = PlatformCoreDataKeys.SELECTED_ITEM.getData(dataProvider);
    PsiElement element = null;
    if (item instanceof PsiElement) {
      element = (PsiElement)item;
    }
    else if (item instanceof PsiAwareObject) {
      Project project = CommonDataKeys.PROJECT.getData(dataProvider);
      element = project == null ? null : ((PsiAwareObject)item).findElement(project);
    }
    else if (item instanceof PsiElementNavigationItem) {
      element = ((PsiElementNavigationItem)item).getTargetElement();
    }
    else if (item instanceof VirtualFile) {
      Project project = CommonDataKeys.PROJECT.getData(dataProvider);
      element = project == null || !((VirtualFile)item).isValid() ? null :
                PsiManager.getInstance(project).findFile((VirtualFile)item);
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
