// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface InjectedFormattingOptionsProvider {
  ExtensionPointName<InjectedFormattingOptionsProvider> EP_NAME = ExtensionPointName.create("com.intellij.formatting.injectedOptions");

  @Nullable Boolean shouldDelegateToTopLevel(@NotNull PsiFile file);
}
