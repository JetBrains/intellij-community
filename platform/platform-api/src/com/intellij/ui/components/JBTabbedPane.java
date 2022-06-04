// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.ScreenUtil;
import com.intellij.util.ui.JBSwingUtilities;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;

/**
 * @author evgeny.zakrevsky
 */
public class JBTabbedPane extends JTabbedPane implements HierarchyListener {
  public static final String LABEL_FROM_TABBED_PANE = "JBTabbedPane.labelFromTabbedPane";

  private Insets myTabComponentInsets = UIUtil.PANEL_SMALL_INSETS;

  public JBTabbedPane() {
  }

  public JBTabbedPane(@JdkConstants.TabPlacement int tabPlacement) {
    super(tabPlacement);
  }

  public JBTabbedPane(@JdkConstants.TabPlacement int tabPlacement, @JdkConstants.TabLayoutPolicy int tabLayoutPolicy) {
    super(tabPlacement, tabLayoutPolicy);
  }

  @Override
  public void setComponentAt(int index, Component component) {
    super.setComponentAt(index, component);
    component.addHierarchyListener(this);
    setInsets(component);
    revalidate();
    repaint();
  }

  @Override
  public void insertTab(@Nls(capitalization = Nls.Capitalization.Title) String title, Icon icon, Component component,
                        @Nls(capitalization = Nls.Capitalization.Sentence) String tip, int index) {
    super.insertTab(title, icon, component, tip, index);

    //set custom label for correct work spotlighting in settings
    JLabel label = new JLabel(title);
    label.setIcon(icon);
    label.setBorder(JBUI.Borders.empty(1));
    label.setFont(getFont());
    setTabComponentAt(index, label);
    label.putClientProperty(LABEL_FROM_TABBED_PANE, Boolean.TRUE);

    component.addHierarchyListener(this);
    setInsets(component);

    revalidate();
    repaint();
  }

  @Override
  public void setTitleAt(int index, @NlsContexts.TabTitle String title) {
    super.setTitleAt(index, title);
    Component tabComponent = getTabComponentAt(index);
    if (tabComponent instanceof JLabel) {
      JLabel label = (JLabel) tabComponent;
      if (Boolean.TRUE.equals(label.getClientProperty(LABEL_FROM_TABBED_PANE))) {
        label.setText(title);
      }
    }
  }

  @Override
  public void setSelectedIndex(int index) {
    super.setSelectedIndex(index);
    revalidate();
    repaint();
  }

  private void setInsets(Component component) {
    if (component instanceof JComponent && myTabComponentInsets != null) {
      UIUtil.addInsets((JComponent)component, getInsetsForTabComponent());
    }
  }

  /** @deprecated Use {@link JBTabbedPane#setTabComponentInsets(Insets)} instead of overriding */
  @Deprecated(forRemoval = true)
  @NotNull
  protected Insets getInsetsForTabComponent() {
    return myTabComponentInsets;
  }

  @Nullable
  public Insets getTabComponentInsets() {
    return myTabComponentInsets;
  }

  public void setTabComponentInsets(@Nullable Insets tabInsets) {
    myTabComponentInsets = tabInsets;
  }

  @Override
  public void hierarchyChanged(HierarchyEvent e) {
    repaint();
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    if (!ScreenUtil.isStandardAddRemoveNotify(this)) {
      return;
    }
    for (int i = 0; i < getTabCount(); i++) {
      getComponentAt(i).removeHierarchyListener(this);
    }
  }

  @Override
  protected Graphics getComponentGraphics(Graphics graphics) {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics));
  }
}
