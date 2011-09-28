package com.intellij.ui.components;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.util.Arrays;

/**
 * @author evgeny.zakrevsky
 */
public class JBTabbedPane extends JTabbedPane implements HierarchyListener {
  public JBTabbedPane() {
  }

  public JBTabbedPane(int tabPlacement) {
    super(tabPlacement);
  }

  public JBTabbedPane(int tabPlacement, int tabLayoutPolicy) {
    super(tabPlacement, tabLayoutPolicy);
  }

  @Override
  public void setComponentAt(int index, Component component) {
    super.setComponentAt(index, component);
    component.addHierarchyListener(this);
    UIUtil.setNotOpaqueRecursively(component);
    setInsets(component);
  }

  @Override
  public void insertTab(String title, Icon icon, Component component, String tip, int index) {
    super.insertTab(title, icon, component, tip, index);
    component.addHierarchyListener(this);
    UIUtil.setNotOpaqueRecursively(component);
    setInsets(component);
  }

  private void setInsets(Component component) {
    if (component instanceof JComponent) {
      if (((JComponent)component).getBorder() == null) {
        ((JComponent)component).setBorder(new EmptyBorder(UIUtil.PANEL_SMALL_INSETS));
      }
      else {
        ((JComponent)component)
          .setBorder(new CompoundBorder(new EmptyBorder(UIUtil.PANEL_SMALL_INSETS), ((JComponent)component).getBorder()));
      }
    }
  }

  @Override
  public void hierarchyChanged(HierarchyEvent e) {
    UIUtil.setNotOpaqueRecursively(e.getComponent());
  }
}
