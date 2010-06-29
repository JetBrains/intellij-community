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

package com.intellij.application.options.colors;

import com.intellij.ui.ListScrollingUtil;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.EventDispatcher;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashSet;
import java.util.Set;

public class OptionsPanelImpl extends JPanel implements OptionsPanel {
  private final JList myOptionsList;
  private final ColorAndFontDescriptionPanel myOptionsPanel;

  private final ColorAndFontOptions myOptions;
  private final SchemesPanel mySchemesProvider;
  private final String myCategoryName;

  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);

  public OptionsPanelImpl(ColorAndFontDescriptionPanel optionsPanel, ColorAndFontOptions options, SchemesPanel schemesProvider,
                      String categoryName) {
    super(new BorderLayout());
    myOptions = options;
    mySchemesProvider = schemesProvider;
    myCategoryName = categoryName;

    optionsPanel.addActionListener(new ActionListener(){
      public void actionPerformed(final ActionEvent e) {
        myDispatcher.getMulticaster().settingsChanged();
      }
    });

    myOptionsList = new JBList();

    myOptionsList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        if (!mySchemesProvider.areSchemesLoaded()) return;
        processListValueChanged();
      }
    });
    myOptionsList.setCellRenderer(new DefaultListCellRenderer(){
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof ColorAndFontDescription) {
          setIcon(((ColorAndFontDescription)value).getIcon());
          setToolTipText(((ColorAndFontDescription)value).getToolTip());
        }
        return component;
      }
    });

    myOptionsList.setModel(new DefaultListModel());
    myOptionsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    JBScrollPane scrollPane = new JBScrollPane(myOptionsList);
    scrollPane.setPreferredSize(new Dimension(230, 60));
    JPanel north = new JPanel(new BorderLayout());
    north.add(scrollPane, BorderLayout.WEST);
    north.add(optionsPanel, BorderLayout.CENTER);
    myOptionsPanel = optionsPanel;
    add(north, BorderLayout.NORTH);
  }

  public void addListener(ColorAndFontSettingsListener listener) {
    myDispatcher.addListener(listener);
  }

  private void processListValueChanged() {
    Object selectedValue = myOptionsList.getSelectedValue();
    ColorAndFontDescription description = (ColorAndFontDescription)selectedValue;
    ColorAndFontDescriptionPanel optionsPanel = myOptionsPanel;
    if (description == null) {
      optionsPanel.resetDefault();
      return;
    }
    optionsPanel.reset(description);

    myDispatcher.getMulticaster().selectedOptionChanged(description);

  }

  private void fillOptionsList() {
    int selIndex = myOptionsList.getSelectedIndex();

    DefaultListModel listModel = (DefaultListModel)myOptionsList.getModel();
    listModel.removeAllElements();

    EditorSchemeAttributeDescriptor[] descriptions = myOptions.getCurrentDescriptions();

    for (EditorSchemeAttributeDescriptor description : descriptions) {
      if (description.getGroup().equals(myCategoryName)) {
        listModel.addElement(description);
      }
    }
    if (selIndex >= 0) {
      myOptionsList.setSelectedIndex(selIndex);
    }
    ListScrollingUtil.ensureSelectionExists(myOptionsList);

    Object selected = myOptionsList.getSelectedValue();
    if (selected instanceof EditorSchemeAttributeDescriptor) {
      myDispatcher.getMulticaster().selectedOptionChanged(selected);
    }
  }

  public JPanel getPanel() {
    return this;
  }

  public void updateOptionsList() {
    fillOptionsList();
    processListValueChanged();
  }

  public Runnable showOption(final String option) {

    DefaultListModel model = (DefaultListModel)myOptionsList.getModel();

    for (int i = 0; i < model.size(); i++) {
      Object o = model.get(i);
      if (o instanceof EditorSchemeAttributeDescriptor) {
        String type = ((EditorSchemeAttributeDescriptor)o).getType();
        if (type.toLowerCase().contains(option.toLowerCase())) {
          final int i1 = i;
          return new Runnable() {
            public void run() {
              ListScrollingUtil.selectItem(myOptionsList, i1);
            }
          };

        }
      }
    }

    return null;
  }

  public void applyChangesToScheme() {
    Object selectedValue = myOptionsList.getSelectedValue();
    if (selectedValue instanceof ColorAndFontDescription) {
      myOptionsPanel.apply((ColorAndFontDescription)selectedValue,myOptions.getSelectedScheme());
    }

  }

  public void selectOption(final String typeToSelect) {
    DefaultListModel model = (DefaultListModel)myOptionsList.getModel();

    for (int i = 0; i < model.size(); i++) {
      Object o = model.get(i);
      if (o instanceof EditorSchemeAttributeDescriptor) {
        if (typeToSelect.equals(((EditorSchemeAttributeDescriptor)o).getType())) {
          ListScrollingUtil.selectItem(myOptionsList, i);
          return;
        }
      }
    }

  }

  public Set<String> processListOptions() {
    HashSet<String> result = new HashSet<String>();
    EditorSchemeAttributeDescriptor[] descriptions = myOptions.getCurrentDescriptions();

    for (EditorSchemeAttributeDescriptor description : descriptions) {
      if (description.getGroup().equals(myCategoryName)) {
        result.add(description.toString());
      }
    }


    return result;
  }
}
