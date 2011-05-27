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
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.text.MatcherHolder;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
* @author Konstantin Bulenkov
*/
@SuppressWarnings({"GtkPreferredJComboBoxRenderer"})
public class InspectionListCellRenderer extends DefaultListCellRenderer implements MatcherHolder {
  private static final Icon EMPTY_ICON = EmptyIcon.ICON_18;

  private NameUtil.Matcher myMatcher;
  private final SimpleTextAttributes SELECTED;
  private final SimpleTextAttributes PLAIN;

  public InspectionListCellRenderer() {
    SELECTED = new SimpleTextAttributes(UIUtil.getListSelectionBackground(),
                                                                   UIUtil.getListSelectionForeground(),
                                                                   Color.RED,
                                                                   SimpleTextAttributes.STYLE_PLAIN);
    PLAIN = new SimpleTextAttributes(UIUtil.getListBackground(),
                                                                UIUtil.getListForeground(),
                                                                Color.RED,
                                                                SimpleTextAttributes.STYLE_PLAIN);
  }


  public Component getListCellRendererComponent(JList list, Object value, int index, boolean sel, boolean focus) {
    final JPanel panel = new JPanel(new BorderLayout());
    panel.setOpaque(true);

    final Color bg = sel ? UIUtil.getListSelectionBackground() : UIUtil.getListBackground();
    final Color fg = sel ? UIUtil.getListSelectionForeground() : UIUtil.getListForeground();
    panel.setBackground(bg);
    panel.setForeground(fg);

    SimpleTextAttributes attr = sel ? SELECTED : PLAIN;
    if (value instanceof InspectionProfileEntry) {
      final InspectionProfileEntry tool = (InspectionProfileEntry)value;
      final SimpleColoredComponent c = new SimpleColoredComponent();
      SpeedSearchUtil.appendColoredFragmentForMatcher("  " + tool.getDisplayName(), c, attr, myMatcher, bg, sel);
      panel.add(c, BorderLayout.WEST);

      //final JLabel groupLabel = new JLabel(tool.getGroupDisplayName() + "  ", EMPTY_ICON, LEFT);
      final SimpleColoredComponent g = new SimpleColoredComponent();
      SpeedSearchUtil.appendColoredFragmentForMatcher(tool.getGroupDisplayName() + "  ", g, attr, myMatcher, bg, sel);
      //groupLabel.setBackground(bg);
      //groupLabel.setForeground(fg);
      panel.add(g, BorderLayout.EAST);
    }

    return panel;
  }

  @Override
  public void setPatternMatcher(NameUtil.Matcher matcher) {
    myMatcher = matcher;
  }
}
