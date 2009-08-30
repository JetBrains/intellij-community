package com.intellij.packaging.impl.ui;

import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.packaging.ui.TreeNodePresentation;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class DelegatedPackagingElementPresentation extends PackagingElementPresentation {
  private TreeNodePresentation myDelegate;

  public DelegatedPackagingElementPresentation(TreeNodePresentation delegate) {
    myDelegate = delegate;
  }

  public String getPresentableName() {
    return myDelegate.getPresentableName();
  }

  public String getSearchName() {
    return myDelegate.getSearchName();
  }

  public void render(@NotNull PresentationData presentationData, SimpleTextAttributes mainAttributes, SimpleTextAttributes commentAttributes) {
    myDelegate.render(presentationData, mainAttributes, commentAttributes);
  }

  @Nullable
  public String getTooltipText() {
    return myDelegate.getTooltipText();
  }

  public boolean canNavigateToSource() {
    return myDelegate.canNavigateToSource();
  }

  public void navigateToSource() {
    myDelegate.navigateToSource();
  }

  @Nullable
  public Object getSourceObject() {
    return myDelegate.getSourceObject();
  }

  public int getWeight() {
    return myDelegate.getWeight();
  }
}
