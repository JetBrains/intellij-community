package com.intellij.openapi.roots.ui.util;

import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;

public class SimpleTextCellAppearance implements ModifiableCellAppearance {
  private Icon myIcon;
  private SimpleTextAttributes myTextAttributes;
  private String myText;

  public static SimpleTextCellAppearance invalid(String text, Icon icon) {
    return new SimpleTextCellAppearance(text, icon, SimpleTextAttributes.ERROR_ATTRIBUTES);
  }

  public static CellAppearance normal(String text, Icon icon) {
    CompositeAppearance result = CompositeAppearance.single(text);
    result.setIcon(icon);
    return result;
  }

  public SimpleTextCellAppearance(String text, Icon icon, SimpleTextAttributes textAttributes) {
    myIcon = icon;
    myTextAttributes = textAttributes;
    myText = text;
  }

  public void customize(SimpleColoredComponent component) {
    component.setIcon(myIcon);
    component.append(myText, myTextAttributes);
  }

  public String getText() {
    return myText;
  }

  public SimpleTextAttributes getTextAttributes() {
    return myTextAttributes;
  }

  public static SimpleTextCellAppearance syntetic(String text, Icon icon) {
    return new SimpleTextCellAppearance(text, icon, SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
  }

  public void setIcon(Icon icon) { myIcon = icon; }
}
