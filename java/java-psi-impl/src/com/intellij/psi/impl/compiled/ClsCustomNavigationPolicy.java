// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ClsCustomNavigationPolicy {
  ExtensionPointName<ClsCustomNavigationPolicy> EP_NAME = ExtensionPointName.create("com.intellij.psi.clsCustomNavigationPolicy");

  default @Nullable PsiElement getNavigationElement(@SuppressWarnings("unused") @NotNull ClsFileImpl clsFile) { return null; }

  default @Nullable PsiElement getNavigationElement(@SuppressWarnings("unused") @NotNull ClsClassImpl clsClass) { return null; }

  default @Nullable PsiElement getNavigationElement(@SuppressWarnings("unused") @NotNull ClsMethodImpl clsMethod) { return null; }

  default @Nullable PsiElement getNavigationElement(@SuppressWarnings("unused") @NotNull ClsFieldImpl clsField) { return null; }
}