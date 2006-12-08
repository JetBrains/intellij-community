package com.intellij.debugger.ui.impl;

import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ThreadDescriptorImpl;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import java.awt.*;

public class DebuggerComboBoxRenderer extends BasicComboBoxRenderer {

  public Component getListCellRendererComponent(
    JList list,
    Object value,
    int index,
    boolean isSelected,
    boolean cellHasFocus) {

    JLabel component = (JLabel)super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    if (list.getComponentCount() > 0) {
      Icon icon = getIcon(value);
      if (icon != null) {
        component.setIcon(icon);
      }
    }
    else {
      component.setIcon(null);
    }
    return component;
  }

  @Nullable
  private static Icon getIcon(Object item) {
    if (item instanceof ThreadDescriptorImpl) {
      ThreadDescriptorImpl descriptor = (ThreadDescriptorImpl)item;
      return descriptor.getIcon();
    }
    if (item instanceof StackFrameDescriptorImpl) {
      return ((StackFrameDescriptorImpl)item).getIcon();
    }
    return null;
  }
}