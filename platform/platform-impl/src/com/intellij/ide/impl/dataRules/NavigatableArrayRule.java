// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.util.SlowOperations;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class NavigatableArrayRule implements GetDataRule {
  @Override
  public Object getData(@NotNull DataProvider dataProvider) {
    Navigatable[] result = null;
    Object[] selection = PlatformCoreDataKeys.SELECTED_ITEMS.getData(dataProvider);
    if (selection != null) {
      List<Navigatable> res = ContainerUtil.filterIsInstance(selection, Navigatable.class);
      result = res.isEmpty() ? null : res.toArray(Navigatable.EMPTY_NAVIGATABLE_ARRAY);
    }
    if (result == null) {
      Navigatable element = CommonDataKeys.NAVIGATABLE.getData(dataProvider);
      result = element == null ? null : new Navigatable[]{element};
    }
    if (result != null &&
        EDT.isCurrentThreadEdt() &&
        ContainerUtil.findInstance(result, PsiElement.class) != null &&
        SlowOperations.isInSection(SlowOperations.FORCE_ASSERT)) {
      return null; // in precaching, the error is already reported
    }
    return result;
  }
}
