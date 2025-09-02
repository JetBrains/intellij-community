// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon;

import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides an additional coloring layer to improve the visual distinction of several related items (e.g., method parameters, local variables).
 * <p/>
 * Register in {@code com.intellij.highlightVisitor} extension point.
 * <p/>
 * Implement {@link com.intellij.openapi.options.colors.RainbowColorSettingsPage} in corresponding {@link com.intellij.openapi.options.colors.ColorSettingsPage}.
 */
public abstract class RainbowVisitor implements HighlightVisitor {
  private HighlightInfoHolder myHolder;
  private RainbowHighlighter myRainbowHighlighter;

  @Override
  public abstract @NotNull HighlightVisitor clone();

  protected @NotNull RainbowHighlighter getHighlighter() {
    return myRainbowHighlighter;
  }

  @Override
  public boolean analyze(@NotNull PsiFile psiFile,
                         boolean updateWholeFile,
                         @NotNull HighlightInfoHolder holder,
                         @NotNull Runnable action) {
    myHolder = holder;
    myRainbowHighlighter = new RainbowHighlighter(myHolder.getColorsScheme());
    try {
      action.run();
    }
    finally {
      myHolder = null;
      myRainbowHighlighter = null;
    }
    return true;
  }


  protected void addInfo(@Nullable HighlightInfo highlightInfo) {
    myHolder.add(highlightInfo);
  }

  protected HighlightInfo getInfo(final @NotNull PsiElement context,
                                  final @NotNull PsiElement rainbowElement,
                                  final @NotNull String name,
                                  final @Nullable TextAttributesKey colorKey) {
    int colorIndex = UsedColors.getOrAddColorIndex((UserDataHolderEx)context, name, getHighlighter().getColorsCount());
    return getHighlighter().getInfo(colorIndex, rainbowElement, colorKey);
  }
}
