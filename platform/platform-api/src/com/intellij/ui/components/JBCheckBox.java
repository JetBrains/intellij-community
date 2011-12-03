package com.intellij.ui.components;

import com.intellij.ui.AnchorableComponent;

import javax.swing.*;
import java.awt.*;

/**
 * @author evgeny.zakrevsky
 */
public class JBCheckBox extends JCheckBox implements AnchorableComponent {
  private JComponent myAnchor;

  public JBCheckBox() {
    super();
  }

  public JBCheckBox(Icon icon) {
    super(icon);
  }

  public JBCheckBox(Icon icon, boolean selected) {
    super(icon, selected);
  }

  public JBCheckBox(String text) {
    super(text);
  }

  public JBCheckBox(Action a) {
    super(a);
  }

  public JBCheckBox(String text, boolean selected) {
    super(text, selected);
  }

  public JBCheckBox(String text, Icon icon) {
    super(text, icon);
  }

  public JBCheckBox(String text, Icon icon, boolean selected) {
    super(text, icon, selected);
  }


  @Override
  public JComponent getAnchor() {
    return myAnchor;
  }

  @Override
  public void setAnchor(JComponent anchor) {
    if (this != anchor) {
      this.myAnchor = anchor;
    }
  }

  @Override
  public Dimension getPreferredSize() {
    return myAnchor == null ? super.getPreferredSize() : myAnchor.getPreferredSize();
  }

  @Override
  public Dimension getMinimumSize() {
    return myAnchor == null ? super.getMinimumSize() : myAnchor.getMinimumSize();
  }
}
