// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl;

import com.intellij.openapi.editor.FoldRegion;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.impl.source.tree.injected.FoldingRegionWindow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class EditorFoldingInfoWindow extends EditorFoldingInfo {
  private final EditorFoldingInfo myDelegate;

  EditorFoldingInfoWindow(@NotNull EditorFoldingInfo delegate) {
    myDelegate = delegate;
  }

  @Override
  public @Nullable PsiElement getPsiElement(@NotNull FoldRegion region) {
    return myDelegate.getPsiElement(getHostRegion(region));
  }

  @Override
  void addRegion(@NotNull FoldRegion region, @NotNull SmartPsiElementPointer<?> pointer) {
    myDelegate.addRegion(getHostRegion(region), pointer);
  }

  @Override
  public void removeRegion(@NotNull FoldRegion region) {
    myDelegate.removeRegion(getHostRegion(region));
  }

  @Override
  void dispose() {
    myDelegate.dispose();
  }

  private static FoldRegion getHostRegion(@NotNull FoldRegion injectedRegion) {
    return injectedRegion instanceof FoldingRegionWindow ? ((FoldingRegionWindow)injectedRegion).getDelegate() : injectedRegion;
  }
}
