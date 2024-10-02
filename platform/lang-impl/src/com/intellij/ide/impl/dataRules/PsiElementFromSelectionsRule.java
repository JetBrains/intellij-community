// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.impl.dataRules;

import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.model.Pointer;
import com.intellij.navigation.PsiElementNavigationItem;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiAwareObject;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class PsiElementFromSelectionsRule implements GetDataRule {

  @Override
  public Object getData(@NotNull DataProvider dataProvider) {
    Object[] objects = PlatformCoreDataKeys.SELECTED_ITEMS.getData(dataProvider);
    if (objects == null) return null;

    Project project = CommonDataKeys.PROJECT.getData(dataProvider);
    PsiElement[] elements = new PsiElement[objects.length];
    for (int i = 0, len = objects.length; i < len; i++) {
      Object o = AbstractProjectViewPane.extractValueFromNode(objects[i]);
      PsiElement element = o instanceof PsiElement ? (PsiElement)o :
                           o instanceof PsiAwareObject && project != null ? ((PsiAwareObject)o).findElement(project) :
                           o instanceof PsiElementNavigationItem ? ((PsiElementNavigationItem)o).getTargetElement() :
                           o instanceof Pointer<?> ? PsiElementFromSelectionRule.getElement((Pointer<?>)o) : null;
      if (element == null || !element.isValid()) return null;
      elements[i] = element;
    }

    return elements;
  }
}
