/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.actions;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
* @author Konstantin Bulenkov
*/
@SuppressWarnings({"GtkPreferredJComboBoxRenderer"})
public class InspectionListCellRenderer extends DefaultListCellRenderer {
  private static final EmptyIcon EMPTY_ICON = new EmptyIcon(18, 18);

  public Component getListCellRendererComponent(JList list, Object value, int index, boolean sel, boolean focus) {
    final JPanel panel = new JPanel(new BorderLayout());
    panel.setOpaque(true);

    final Color bg = sel ? UIUtil.getListSelectionBackground() : UIUtil.getListBackground();
    panel.setBackground(bg);

    if (value instanceof InspectionProfileEntry) {
      final InspectionProfileEntry tool = (InspectionProfileEntry)value;
      final Color fg = sel ? UIUtil.getListSelectionForeground() : UIUtil.getListForeground();

      final JLabel label = new JLabel("  " + tool.getDisplayName());
      panel.add(label, BorderLayout.WEST);

      final JLabel groupLabel = new JLabel(tool.getGroupDisplayName() + "  ", EMPTY_ICON, LEFT);
      groupLabel.setBackground(bg);
      groupLabel.setForeground(fg);
      panel.add(groupLabel, BorderLayout.EAST);
    }

    return panel;
  }
}
