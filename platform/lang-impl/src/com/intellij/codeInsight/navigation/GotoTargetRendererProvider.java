// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.navigation;

import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated use {@link GotoTargetPresentationProvider}
 */
@Deprecated
public interface GotoTargetRendererProvider {

  ExtensionPointName<GotoTargetRendererProvider> EP_NAME = ExtensionPointName.create("com.intellij.gotoTargetRendererProvider");

  @Nullable
  PsiElementListCellRenderer getRenderer(@NotNull PsiElement element, @NotNull GotoTargetHandler.GotoData gotoData);
}
