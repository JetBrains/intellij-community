package com.intellij.ui.components;

import com.intellij.ui.AnchorableComponent;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author evgeny.zakrevsky
 */
public class JBRadioButton extends JRadioButton implements AnchorableComponent {
  private JComponent myAnchor;

  public JBRadioButton() {
  }

  public JBRadioButton(Icon icon) {
    super(icon);
  }

  public JBRadioButton(Action a) {
    super(a);
  }

  public JBRadioButton(Icon icon, boolean selected) {
    super(icon, selected);
  }

  public JBRadioButton(String text) {
    super(text);
  }

  public JBRadioButton(String text, boolean selected) {
    super(text, selected);
  }

  public JBRadioButton(String text, Icon icon) {
    super(text, icon);
  }

  public JBRadioButton(String text, Icon icon, boolean selected) {
    super(text, icon, selected);
  }

  @Override
  public JComponent getAnchor() {
    return myAnchor;
  }

  @Override
  public void setAnchor(@Nullable JComponent anchor) {
    this.myAnchor = anchor;
  }

  @Override
  public Dimension getPreferredSize() {
    return myAnchor == null || myAnchor == this ? super.getPreferredSize() : myAnchor.getPreferredSize();
  }

  @Override
  public Dimension getMinimumSize() {
    return myAnchor == null || myAnchor == this ? super.getMinimumSize() : myAnchor.getMinimumSize();
  }
}
