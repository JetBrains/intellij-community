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

/**
 * @author Yura Cangea
 */
package com.intellij.tools;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

class OutputFiltersDialog extends DialogWrapper {
  private final DefaultListModel myFiltersModel = new DefaultListModel();
  private final JList myFiltersList = new JBList(myFiltersModel);
  private final JButton myAddButton = new JButton(ToolsBundle.message("tools.filters.add.button"));
  private final JButton myEditButton = new JButton(ToolsBundle.message("tools.filters.edit.button"));
  private final JButton myRemoveButton = new JButton(ToolsBundle.message("tools.filters.remove.button"));
  private final JButton myMoveUpButton = new JButton(ToolsBundle.message("tools.filters.move.up.button"));
  private final JButton myMoveDownButton = new JButton(ToolsBundle.message("tools.filters.move.down.button"));
  private final CommandButtonGroup myButtonGroup = new CommandButtonGroup(BoxLayout.Y_AXIS);
  private boolean myModified = false;
  private FilterInfo[] myFilters;

  public OutputFiltersDialog(Component parent, FilterInfo[] filters) {
    super(parent, true);
    myFilters = filters;

    setTitle(ToolsBundle.message("tools.filters.title"));
    init();
    initGui();
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(),getCancelAction(),getHelpAction()};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("reference.settings.ide.settings.external.tools.output.filters");
  }

  private void initGui() {
    myFiltersList.setCellRenderer(new ColoredListCellRenderer() {
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        FilterInfo info = (FilterInfo)value;
        append(info.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    });

    myButtonGroup.setBorder(BorderFactory.createEmptyBorder(5, 5, 0, 0));

    myButtonGroup.addButton(myAddButton);
    myButtonGroup.addButton(myEditButton);
    myButtonGroup.addButton(myRemoveButton);
    myButtonGroup.addButton(myMoveUpButton);
    myButtonGroup.addButton(myMoveDownButton);

    myEditButton.setEnabled(false);
    myRemoveButton.setEnabled(false);
    myMoveUpButton.setEnabled(false);
    myMoveDownButton.setEnabled(false);

    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        FilterInfo filterInfo = new FilterInfo();
        filterInfo.setName(suggestFilterName());
        boolean wasCreated = FilterDialog.editFilter(filterInfo, myAddButton, ToolsBundle.message("tools.filters.add.title"));
        if (wasCreated) {
          myFiltersModel.addElement(filterInfo);
          setModified(true);
          enableButtons();
        }
        myFiltersList.requestFocus();
      }
    });

    myEditButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        int index = myFiltersList.getSelectedIndex();
        FilterInfo filterInfo = (FilterInfo)myFiltersModel.getElementAt(index);
        boolean wasEdited = FilterDialog.editFilter(filterInfo, myEditButton, ToolsBundle.message("tools.filters.edit.title"));
        if (wasEdited) {
          setModified(true);
          enableButtons();
        }
        myFiltersList.requestFocus();
      }
    });


    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myFiltersList.getSelectedIndex() >= 0) {
          myFiltersModel.removeElementAt(myFiltersList.getSelectedIndex());
          setModified(true);
        }
        enableButtons();
        myFiltersList.requestFocus();
      }
    });
    myMoveUpButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        int movedCount = ListUtil.moveSelectedItemsUp(myFiltersList);
        if (movedCount > 0) {
          setModified(true);
        }
        myFiltersList.requestFocus();
      }
    });
    myMoveDownButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        int movedCount = ListUtil.moveSelectedItemsDown(myFiltersList);
        if (movedCount > 0) {
          setModified(true);
        }
        myFiltersList.requestFocus();
      }
    });

    myFiltersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myFiltersList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        enableButtons();
      }
    });

    ListScrollingUtil.ensureSelectionExists(myFiltersList);
  }

  private String suggestFilterName(){
    String prefix = ToolsBundle.message("tools.filters.name.template") + " ";

    int number = 1;
    for (int i=0; i < myFiltersModel.getSize(); i++) {
      FilterInfo wrapper = (FilterInfo)myFiltersModel.getElementAt(i);
      String name = wrapper.getName();
      if (name.startsWith(prefix)) {
        try {
          int n = Integer.valueOf(name.substring(prefix.length()).trim()).intValue();
          number = Math.max(number, n + 1);
        }
        catch (NumberFormatException e) {
        }
      }
    }

    return prefix + number;
  }

  protected void doOKAction() {
    if (myModified) {
      myFilters = new FilterInfo[myFiltersModel.getSize()];
      for (int i = 0; i < myFiltersModel.getSize(); i++) {
        myFilters[i] = (FilterInfo)myFiltersModel.get(i);
      }
    }
    super.doOKAction();
  }

  protected JComponent createCenterPanel() {
    for (int i = 0; i < myFilters.length; i++) {
      myFiltersModel.addElement(myFilters[i].createCopy());
    }

    JPanel panel = new JPanel(new BorderLayout());

    panel.add(new JBScrollPane(myFiltersList), BorderLayout.CENTER);
    panel.add(myButtonGroup, BorderLayout.EAST);

    panel.setPreferredSize(new Dimension(400, 200));

    return panel;
  }

  private void enableButtons() {
    int size = myFiltersModel.getSize();
    int index = myFiltersList.getSelectedIndex();
    myEditButton.setEnabled(size != 0 && index != -1);
    myRemoveButton.setEnabled(size != 0 & index != -1);
    myMoveUpButton.setEnabled(ListUtil.canMoveSelectedItemsUp(myFiltersList));
    myMoveDownButton.setEnabled(ListUtil.canMoveSelectedItemsDown(myFiltersList));
  }

  public JComponent getPreferredFocusedComponent() {
    return myFiltersList;
  }

  private void setModified(boolean modified) {
    myModified = modified;
  }

  public FilterInfo[] getData() {
    return myFilters;
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.tools.OutputFiltersDialog";
  }
}
