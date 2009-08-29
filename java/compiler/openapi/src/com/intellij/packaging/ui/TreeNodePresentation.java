package com.intellij.packaging.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ui.SimpleTextAttributes;

/**
 * @author nik
 */
public abstract class TreeNodePresentation {
  public abstract String getPresentableName();

  public String getSearchName() {
    return getPresentableName();
  }

  public abstract void render(@NotNull PresentationData presentationData, SimpleTextAttributes mainAttributes,
                              SimpleTextAttributes commentAttributes);

  @Nullable
  public String getTooltipText() {
    return null;
  }

  public boolean canNavigateToSource() {
    return false;
  }

  public void navigateToSource() {
  }

  @Nullable
  public Object getSourceObject() {
    return null;
  }

  public abstract int getWeight();
}
