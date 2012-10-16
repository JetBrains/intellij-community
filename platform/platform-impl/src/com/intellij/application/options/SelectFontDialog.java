/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.application.options;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ListScrollingUtil;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.List;

public class SelectFontDialog extends DialogWrapper {
  private JList myFontList;
  private JCheckBox myShowMonospacedCheckbox;
  private final List<String> myFontNames;
  private final String myInitialFontName;
  private final List<String> myMonospacedFontNames;

  public SelectFontDialog(Component parent, List<String> fontNames, String initialFontName, List<String> monospacedFontNames) {
    super(parent, true);
    myMonospacedFontNames = monospacedFontNames;
    setTitle(ApplicationBundle.message("title.select.font"));
    myFontNames = fontNames;
    myInitialFontName = initialFontName;
    init();
  }

  protected JComponent createCenterPanel() {
    myShowMonospacedCheckbox = new JCheckBox(ApplicationBundle.message("checkbox.show.only.monospaced.fonts"));
    final boolean useOnlyMonospacedFonts = EditorColorsManager.getInstance().isUseOnlyMonospacedFonts();
    myFontList = new JBList();
    new ListSpeedSearch(myFontList);
    myFontList.setModel(new DefaultListModel());
    fillList(useOnlyMonospacedFonts);

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        doOKAction();
        return true;
      }
    }.installOn(myFontList);

    myFontList.setCellRenderer(new MyListCellRenderer());

    myShowMonospacedCheckbox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        boolean onlyMonospaced = myShowMonospacedCheckbox.isSelected();
        EditorColorsManager.getInstance().setUseOnlyMonospacedFonts(onlyMonospaced);
        String selection = (String) myFontList.getSelectedValue();
        fillList(onlyMonospaced);
        if (selection != null) {
          myFontList.setSelectedValue(selection, true);
          myFontList.ensureIndexIsVisible(myFontList.getSelectedIndex());
        }
      }
    });

    JPanel panel = new JPanel(new BorderLayout());

    panel.add(myShowMonospacedCheckbox, BorderLayout.NORTH);
    panel.add(ScrollPaneFactory.createScrollPane(myFontList), BorderLayout.CENTER);
    myShowMonospacedCheckbox.setSelected(useOnlyMonospacedFonts);
    myFontList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        setOKActionEnabled(myFontList.getSelectedValue() != null);
      }
    });

    return panel;
  }

  private void fillList(boolean onlyMonospaced) {
    DefaultListModel model = (DefaultListModel) myFontList.getModel();
    model.removeAllElements();
    for (String fontName : myFontNames) {
      if (!onlyMonospaced || myMonospacedFontNames.contains(fontName)) {
        model.addElement(fontName);
      }
    }
  }

  public void show() {
    ListScrollingUtil.selectItem(myFontList, myInitialFontName);
    super.show();
  }



  public JComponent getPreferredFocusedComponent() {
    return myFontList;
  }

  public String getFontName() {
    return (String)myFontList.getSelectedValue();
  }

  private static class MyListCellRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(
        JList list,
        Object value,
        int index,
        boolean isSelected,
        boolean cellHasFocus) {
      Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      String fontName = (String) value;
      c.setFont(new Font(fontName, Font.PLAIN, 14));
      return c;
    }
  }
}
