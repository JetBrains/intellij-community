// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.ui;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.packaging.ui.TreeNodePresentation;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DelegatedPackagingElementPresentation extends PackagingElementPresentation {
  private final TreeNodePresentation myDelegate;

  public DelegatedPackagingElementPresentation(TreeNodePresentation delegate) {
    myDelegate = delegate;
  }

  @Override
  public String getPresentableName() {
    return myDelegate.getPresentableName();
  }

  @Override
  public String getSearchName() {
    return myDelegate.getSearchName();
  }

  @Override
  public void render(@NotNull PresentationData presentationData, SimpleTextAttributes mainAttributes, SimpleTextAttributes commentAttributes) {
    myDelegate.render(presentationData, mainAttributes, commentAttributes);
  }

  @Override
  public @Nullable String getTooltipText() {
    return myDelegate.getTooltipText();
  }

  @Override
  public boolean canNavigateToSource() {
    return myDelegate.canNavigateToSource();
  }

  @Override
  public void navigateToSource() {
    myDelegate.navigateToSource();
  }

  @Override
  public int getWeight() {
    return myDelegate.getWeight();
  }
}
