package com.intellij.ui.dualView;

import javax.swing.*;
import java.awt.*;

/**
 * author: lesya
 */
public interface CellWrapper{
  void wrap(Component component, JTable table,
                                                 Object value,
                                                 boolean isSelected,
                                                 boolean hasFocus,
                                                 int row,
                                                 int column, Object treeNode);
}
