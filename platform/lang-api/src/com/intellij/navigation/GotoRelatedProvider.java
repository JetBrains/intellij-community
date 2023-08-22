// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Provides items for "Navigate -> Related Symbol" action.
 * <p>
 * If related items are represented as icons on the gutter use {@link com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider}
 * to provide both line markers and 'goto related' targets
 * <p>
 * Use extension point `com.intellij.gotoRelatedProvider`.
 */
public abstract class GotoRelatedProvider {
  @NotNull
  public List<? extends GotoRelatedItem> getItems(@NotNull PsiElement psiElement) {
    return Collections.emptyList();
  }

  @NotNull
  public List<? extends GotoRelatedItem> getItems(@NotNull DataContext context) {
    return Collections.emptyList();
  }
}
