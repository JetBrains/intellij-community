/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.ide.util.gotoByName.ChooseByNameBase;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.text.Matcher;
import com.intellij.util.text.MatcherHolder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
* @author Konstantin Bulenkov
*/
@SuppressWarnings({"GtkPreferredJComboBoxRenderer"})
public class InspectionListCellRenderer extends DefaultListCellRenderer implements MatcherHolder {
  private Matcher myMatcher;
  private final SimpleTextAttributes SELECTED;
  private final SimpleTextAttributes PLAIN;

  public InspectionListCellRenderer() {
    SELECTED = new SimpleTextAttributes(UIUtil.getListSelectionBackground(),
                                                                   UIUtil.getListSelectionForeground(),
                                                                   JBColor.RED,
                                                                   SimpleTextAttributes.STYLE_PLAIN);
    PLAIN = new SimpleTextAttributes(UIUtil.getListBackground(),
                                                                UIUtil.getListForeground(),
                                                                JBColor.RED,
                                                                SimpleTextAttributes.STYLE_PLAIN);
  }


  @Override
  public Component getListCellRendererComponent(JList list, Object value, int index, boolean sel, boolean focus) {
    final JPanel panel = new JPanel(new BorderLayout());
    panel.setOpaque(true);

    final Color bg = sel ? UIUtil.getListSelectionBackground() : UIUtil.getListBackground();
    final Color fg = sel ? UIUtil.getListSelectionForeground() : UIUtil.getListForeground();
    panel.setBackground(bg);
    panel.setForeground(fg);

    SimpleTextAttributes attr = sel ? SELECTED : PLAIN;
    if (value instanceof InspectionToolWrapper) {
      final InspectionToolWrapper toolWrapper = (InspectionToolWrapper)value;
      final SimpleColoredComponent c = new SimpleColoredComponent();
      SpeedSearchUtil.appendColoredFragmentForMatcher("  " + toolWrapper.getDisplayName(), c, attr, myMatcher, bg, sel);
      panel.add(c, BorderLayout.WEST);

      final SimpleColoredComponent group = new SimpleColoredComponent();
      SpeedSearchUtil.appendColoredFragmentForMatcher(toolWrapper.getGroupDisplayName() + "  ", group, attr, myMatcher, bg, sel);
      final JPanel right = new JPanel(new BorderLayout());
      right.setBackground(bg);
      right.setForeground(fg);
      right.add(group, BorderLayout.CENTER);
      final JLabel icon = new JLabel(getIcon(toolWrapper));
      icon.setBackground(bg);
      icon.setForeground(fg);
      right.add(icon, BorderLayout.EAST);
      panel.add(right, BorderLayout.EAST);
    }
    else {
      // E.g. "..." item
      return value == ChooseByNameBase.NON_PREFIX_SEPARATOR ? ChooseByNameBase.renderNonPrefixSeparatorComponent(UIUtil.getListBackground()) :
             super.getListCellRendererComponent(list, value, index, sel, focus);
    }

    return panel;
  }

  @NotNull
  private static Icon getIcon(@NotNull InspectionToolWrapper tool) {
    Icon icon = null;
    final Language language = Language.findLanguageByID(tool.getLanguage());
    if (language != null) {
      final LanguageFileType fileType = language.getAssociatedFileType();
      if (fileType != null) {
        icon = fileType.getIcon();
      }
    }
    if (icon == null) {
      icon = UnknownFileType.INSTANCE.getIcon();
    }
    assert icon != null;
    return icon;
  }

  @Override
  public void setPatternMatcher(Matcher matcher) {
    myMatcher = matcher;
  }
}
