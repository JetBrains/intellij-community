package com.intellij.ui.roots;

import javax.swing.*;
import java.awt.*;

/**
 * @author cdr
 */
public class ResizingWrapper extends JComponent {
  protected final JComponent myWrappedComponent;
  public ResizingWrapper(JComponent wrappedComponent) {
    myWrappedComponent = wrappedComponent;
    setLayout(new GridBagLayout());
    setOpaque(false);
    this.add(wrappedComponent, new GridBagConstraints(0, 0, 1, 1, 0.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
    this.add(Box.createHorizontalGlue(), new GridBagConstraints(1, 0, 1, 1, 1.0, 1.0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
  }
}