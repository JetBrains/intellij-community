// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.border.IdeaTitledBorder;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static com.intellij.openapi.util.Pair.pair;
import static java.awt.GridBagConstraints.*;

/**
 * This class creates a nicely formatted panel with components. Useful for option panels.
 */
public class OptionGroup implements PanelWithAnchor {
  private final @NlsContexts.BorderTitle String myTitle;
  private final List<Object> myOptions = new ArrayList<>();
  private final BitSet myIndented = new BitSet();
  private JComponent myAnchor;

  /**
   * Creates a panel without a border.
   */
  public OptionGroup() {
    this(null);
  }

  public OptionGroup(@Nullable @NlsContexts.BorderTitle String title) {
    myTitle = title;
  }

  public void add(JComponent component) {
    add(component, false);
  }

  public void add(JComponent component, boolean indented) {
    myOptions.add(component);
    myIndented.set(myOptions.size() - 1, indented);
  }

  public void add(JComponent leftComponent, JComponent rightComponent) {
    add(leftComponent, rightComponent, false);
  }

  public void add(JComponent leftComponent, JComponent rightComponent, boolean indented) {
    myOptions.add(pair(leftComponent, rightComponent));
    myIndented.set(myOptions.size() - 1, indented);
  }

  public JPanel createPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());

    for (int i = 0; i < myOptions.size(); i++) {
      int top = i == 0 ? 0 : UIUtil.DEFAULT_VGAP;
      int left = myIndented.get(i) ? IdeBorderFactory.TITLED_BORDER_INDENT : 0;

      Object option = myOptions.get(i);
      if (option instanceof JComponent) {
        JComponent component = (JComponent)option;
        panel.add(component, new GridBagConstraints(0, i, REMAINDER, 1, 1, 0, WEST, getFill(component), JBUI.insets(top, left, 0, 0), 0, 0));
      }
      else {
        JComponent first = (JComponent)((Pair<?, ?>)option).first;
        panel.add(first, new GridBagConstraints(0, i, 1, 1, 1, 0, WEST, getFill(first), JBUI.insets(top, left, 0, 0), 0, 0));
        JComponent second = (JComponent)((Pair<?, ?>)option).second;
        panel.add(second, new GridBagConstraints(1, i, 1, 1, 1, 0, EAST, HORIZONTAL, JBUI.insets(top, UIUtil.DEFAULT_HGAP, 0, 0), 0, 0));
        if (first instanceof JLabel) {
          ((JLabel)first).setLabelFor(second);
        }
      }
    }

    JPanel p = new JPanel();
    p.setPreferredSize(new Dimension(0, 0));
    panel.add(p, new GridBagConstraints(0, myOptions.size(), REMAINDER, 1, 0, 1, NORTH, NONE, JBInsets.emptyInsets(), 0, 0));

    if (myTitle != null) {
      IdeaTitledBorder titledBorder = IdeBorderFactory.createTitledBorder(myTitle, true);
      panel.setBorder(titledBorder);
      titledBorder.acceptMinimumSize(panel);
    }

    return panel;
  }

  private static int getFill(JComponent component) {
    return component instanceof JCheckBox ? NONE : HORIZONTAL;
  }

  @Override
  public JComponent getAnchor() {
    return myAnchor;
  }

  @Override
  public void setAnchor(@Nullable JComponent anchor) {
    myAnchor = anchor;
    for (Object o : myOptions) {
      if (o instanceof Pair && ((Pair<?, ?>)o).first instanceof AnchorableComponent) {
        ((AnchorableComponent)((Pair<?, ?>)o).first).setAnchor(anchor);
      }
    }
  }

  public JComponent[] getComponents() {
    List<JComponent> components = new ArrayList<>();
    for (Object o : myOptions) {
      if (o instanceof Pair) {
        components.add((JComponent)((Pair<?, ?>)o).first);
        components.add((JComponent)((Pair<?, ?>)o).second);
      }
      else {
        components.add((JComponent)o);
      }
    }
    return components.toArray(new JComponent[0]);
  }

  @Nullable
  public JComponent findAnchor() {
    double maxWidth = -1;
    JComponent anchor = null;
    for (Object o : myOptions) {
      if (o instanceof Pair && ((Pair<?, ?>)o).first instanceof AnchorableComponent && ((Pair<?, ?>)o).first instanceof JComponent) {
        JComponent component = (JComponent)((Pair<?, ?>)o).first;
        if (component.getPreferredSize().getWidth() > maxWidth) {
          maxWidth = component.getPreferredSize().getWidth();
          anchor = component;
        }
      }
    }
    return anchor;
  }
}