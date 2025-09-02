// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.impl.dataRules;

import com.intellij.ide.util.EditSourceUtil;
import com.intellij.openapi.actionSystem.DataMap;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.actionSystem.CommonDataKeys.NAVIGATABLE;
import static com.intellij.openapi.actionSystem.CommonDataKeys.PSI_ELEMENT;
import static com.intellij.openapi.actionSystem.PlatformCoreDataKeys.SELECTED_ITEM;

final class NavigatableRule {
  static @Nullable Navigatable getData(@NotNull DataMap dataProvider) {
    Navigatable navigatable = dataProvider.get(NAVIGATABLE);
    if (navigatable instanceof OpenFileDescriptor o) {
      if (o.getFile().isValid()) {
        return o;
      }
    }
    PsiElement element = dataProvider.get(PSI_ELEMENT);
    if (element instanceof Navigatable o) {
      return o;
    }
    if (element != null) {
      return EditSourceUtil.getDescriptor(element);
    }

    Object selection = dataProvider.get(SELECTED_ITEM);
    if (selection instanceof Navigatable o) {
      return o;
    }

    return null;
  }
}
