// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.jetbrains.annotations.ApiStatus.Internal;

public interface GotoTargetPresentationProvider {

  @Internal
  ExtensionPointName<GotoTargetPresentationProvider> EP_NAME = ExtensionPointName.create("com.intellij.gotoTargetPresentationProvider");

  /**
   * @param element        target to render in the popup
   * @param differentNames whether all targets in the popup have the same name, which means their container should be rendered instead
   * @see com.intellij.ide.util.PsiElementRenderingInfo#targetPresentation
   * @see TargetPresentation#builder
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  @Nullable TargetPresentation getTargetPresentation(@NotNull PsiElement element, boolean differentNames);

  @RequiresReadLock
  @RequiresBackgroundThread
  static @Nullable TargetPresentation getTargetPresentationFromProviders(@NotNull PsiElement element, boolean differentNames) {
    for (GotoTargetPresentationProvider provider : EP_NAME.getExtensionList()) {
      TargetPresentation presentation = provider.getTargetPresentation(element, differentNames);
      if (presentation != null) {
        return presentation;
      }
    }
    return null;
  }
}
