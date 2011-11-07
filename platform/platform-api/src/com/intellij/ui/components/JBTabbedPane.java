package com.intellij.ui.components;

import com.intellij.openapi.util.SystemInfo;
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
  public static final String LABEL_FROM_TABBED_PANE = "JBTabbedPane.labelFromTabbedPane";
  private int previousSelectedIndex = -1;
  
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
    revalidate();
    repaint();
  }

  @Override
  public void insertTab(String title, Icon icon, Component component, String tip, int index) {
    super.insertTab(title, icon, component, tip, index);

    //set custom label for correct work spotlighting in settings
    JLabel label = new JLabel(title);
    label.setIcon(icon);
    label.setBorder(new EmptyBorder(1,1,1,1));
    setTabComponentAt(index, label);
    updateSelectedTabForeground();
    label.putClientProperty(LABEL_FROM_TABBED_PANE, Boolean.TRUE);

    component.addHierarchyListener(this);
    UIUtil.setNotOpaqueRecursively(component);
    setInsets(component);

    revalidate();
    repaint();
  }

  @Override
  public void setSelectedIndex(int index) {
    previousSelectedIndex = getSelectedIndex();
    super.setSelectedIndex(index);
    updateSelectedTabForeground();
    revalidate();
    repaint();
  }

  private void updateSelectedTabForeground() {
    if (UIUtil.isUnderAquaLookAndFeel() && SystemInfo.isMacOSLion) {
      if (getSelectedIndex() != -1 && getTabComponentAt(getSelectedIndex()) != null) {
        getTabComponentAt(getSelectedIndex()).setForeground(Color.WHITE);
      }
      if (previousSelectedIndex != -1 && getTabComponentAt(previousSelectedIndex) != null) {
        getTabComponentAt(previousSelectedIndex).setForeground(Color.BLACK);
      }
    }
  }

  private static void setInsets(Component component) {
    if (component instanceof JComponent) {
      UIUtil.addInsets((JComponent)component, UIUtil.PANEL_SMALL_INSETS);
    }
  }

  @Override
  public void hierarchyChanged(HierarchyEvent e) {
    UIUtil.setNotOpaqueRecursively(e.getComponent());
    repaint();
  }
}
