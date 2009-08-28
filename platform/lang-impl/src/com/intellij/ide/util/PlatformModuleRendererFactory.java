package com.intellij.ide.util;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class PlatformModuleRendererFactory extends ModuleRendererFactory {
  public DefaultListCellRenderer getModuleRenderer() {
    return new DefaultListCellRenderer() {
      public Component getListCellRendererComponent(final JList list,
                                                    final Object value,
                                                    final int index,
                                                    final boolean isSelected,
                                                    final boolean cellHasFocus) {
        final Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        setText("");
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 2));
        setHorizontalTextPosition(SwingConstants.LEFT);
        setBackground(isSelected ? UIUtil.getListSelectionBackground() : UIUtil.getListBackground());
        setForeground(isSelected ? UIUtil.getListSelectionForeground() : UIUtil.getInactiveTextColor());
        return component;
      }
    };
  }
}
