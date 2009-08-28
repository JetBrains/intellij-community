package com.intellij.openapi.roots.ui.util;

import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;

public abstract class BaseTextCommentCellAppearance implements CellAppearance {
  private SimpleTextAttributes myCommentAttributes = SimpleTextAttributes.GRAY_ATTRIBUTES;
  private SimpleTextAttributes myTextAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;

  protected abstract Icon getIcon();

  protected abstract String getSecondaryText();

  protected abstract String getPrimaryText();

  public void customize(SimpleColoredComponent component) {
    component.setIcon(getIcon());
    component.append(getPrimaryText(), myTextAttributes);
    String secondaryText = getSecondaryText();
    if (secondaryText != null && secondaryText.length() > 0)
      component.append(" (" + secondaryText + ")", myCommentAttributes);
  }

  public String getText() {
    String secondaryText = getSecondaryText();
    if (secondaryText != null && secondaryText.length() >0)
      return getPrimaryText() + " (" + secondaryText + ")";
    return getPrimaryText();
  }

  public void setCommentAttributes(SimpleTextAttributes commentAttributes) {
    myCommentAttributes = commentAttributes;
  }

  public void setTextAttributes(SimpleTextAttributes textAttributes) {
    myTextAttributes = textAttributes;
  }
}
