// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.impl.dataRules;

import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.model.Pointer;
import com.intellij.navigation.PsiElementNavigationItem;
import com.intellij.openapi.actionSystem.DataMap;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiAwareObject;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.actionSystem.PlatformCoreDataKeys.PROJECT;
import static com.intellij.openapi.actionSystem.PlatformCoreDataKeys.SELECTED_ITEMS;

@ApiStatus.Internal
final class PsiElementFromSelectionsRule {

  static PsiElement @Nullable [] getData(@NotNull DataMap dataProvider) {
    Object[] objects = dataProvider.get(SELECTED_ITEMS);
    if (objects == null) return null;

    Project project = dataProvider.get(PROJECT);
    PsiElement[] elements = new PsiElement[objects.length];
    for (int i = 0, len = objects.length; i < len; i++) {
      Object o = AbstractProjectViewPane.extractValueFromNode(objects[i]);
      PsiElement element = o instanceof PsiElement oo ? oo :
                           o instanceof PsiAwareObject oo && project != null ? oo.findElement(project) :
                           o instanceof PsiElementNavigationItem oo ? oo.getTargetElement() :
                           o instanceof Pointer<?> oo ? PsiElementFromSelectionRule.getElement(oo) : null;
      if (element == null || !element.isValid()) return null;
      elements[i] = element;
    }

    return elements;
  }
}
