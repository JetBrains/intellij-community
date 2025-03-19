// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.ui;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class TreeNodePresentation {
  public abstract @NlsSafe String getPresentableName();

  public String getSearchName() {
    return getPresentableName();
  }

  public abstract void render(@NotNull PresentationData presentationData, SimpleTextAttributes mainAttributes,
                              SimpleTextAttributes commentAttributes);

  public @Nullable @NlsContexts.Tooltip String getTooltipText() {
    return null;
  }

  public boolean canNavigateToSource() {
    return false;
  }

  public void navigateToSource() {
  }

  public abstract int getWeight();
}
