// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.packaging.ui.SourceItemPresentation;
import com.intellij.packaging.ui.TreeNodePresentation;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DelegatedSourceItemPresentation extends SourceItemPresentation {
  private final TreeNodePresentation myPresentation;

  public DelegatedSourceItemPresentation(TreeNodePresentation presentation) {
    myPresentation = presentation;
  }

  @Override
  public String getPresentableName() {
    return myPresentation.getPresentableName();
  }

  @Override
  public String getSearchName() {
    return myPresentation.getSearchName();
  }

  @Override
  public void render(@NotNull PresentationData presentationData, SimpleTextAttributes mainAttributes, SimpleTextAttributes commentAttributes) {
    myPresentation.render(presentationData, mainAttributes, commentAttributes);
  }

  @Override
  public @Nullable String getTooltipText() {
    return myPresentation.getTooltipText();
  }

  @Override
  public boolean canNavigateToSource() {
    return myPresentation.canNavigateToSource();
  }

  @Override
  public void navigateToSource() {
    myPresentation.navigateToSource();
  }

  @Override
  public int getWeight() {
    return myPresentation.getWeight();
  }
}
