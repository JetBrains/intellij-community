package com.intellij.packaging.ui;

import com.intellij.ide.projectView.PresentationData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class PackagingElementPresentation {

  public abstract String getPresentableName();

  public String getSearchName() {
    return getPresentableName();
  }

  public abstract void render(@NotNull PresentationData presentationData);

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
