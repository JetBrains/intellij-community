// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.impl.dataRules;

import com.intellij.ide.util.EditSourceUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class NavigatableRule implements GetDataRule {
  @Override
  public Object getData(@NotNull DataProvider dataProvider) {
    final Navigatable navigatable = CommonDataKeys.NAVIGATABLE.getData(dataProvider);
    if (navigatable instanceof OpenFileDescriptor openFileDescriptor) {

      if (openFileDescriptor.getFile().isValid()) {
        return openFileDescriptor;
      }
    }
    final PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataProvider);
    if (element instanceof Navigatable) {
      return element;
    }
    if (element != null) {
      return EditSourceUtil.getDescriptor(element);
    }

    final Object selection = PlatformCoreDataKeys.SELECTED_ITEM.getData(dataProvider);
    if (selection instanceof Navigatable) {
      return selection;
    }

    return null;
  }
}
