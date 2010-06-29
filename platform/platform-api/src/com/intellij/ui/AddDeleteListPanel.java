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

package com.intellij.ui;

import com.intellij.CommonBundle;
import com.intellij.ui.components.JBList;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel with "Add" and "Delete" buttons on the right side.
 *
 * @author anna
 * @since 5.1
 */
public abstract class AddDeleteListPanel extends PanelWithButtons {
  private final String myTitle;
  protected JButton myAddButton = new JButton(CommonBundle.message("button.add"));
  protected JButton myDeleteButton = new JButton(CommonBundle.message("button.delete"));
  protected DefaultListModel myListModel = new DefaultListModel();
  protected JList myList = new JBList(myListModel);

  public AddDeleteListPanel(final String title,
                            final List initialList) {
    myTitle = title;
    for (Object o : initialList) {
      if (o != null) {
        myListModel.addElement(o);
      }
    }
    myList.setCellRenderer(getListCellRenderer());
    myList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        myDeleteButton.setEnabled(ListUtil.canRemoveSelectedItems(myList));
      }
    });
    myAddButton.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e) {
        final Object itemToAdd = findItemToAdd();
        if (itemToAdd != null){
          myListModel.addElement(itemToAdd);
          myList.setSelectedValue(itemToAdd, true);
        }
      }
    });
    myDeleteButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        ListUtil.removeSelectedItems(myList);
      }
    });
    initPanel();
  }

  protected abstract Object findItemToAdd();

  public Object [] getListItems(){
    List<Object> items = new ArrayList<Object>();
    for (int i = 0; i < myListModel.size(); i++){
      items.add(myListModel.getElementAt(i));
    }
    return items.toArray();
  }

  protected String getLabelText() {
    return myTitle;
  }

  protected JButton[] createButtons() {
    return new JButton[]{myAddButton, myDeleteButton};
  }

  protected JComponent createMainComponent() {
    if (!myListModel.isEmpty()) myList.setSelectedIndex(0);
    return ScrollPaneFactory.createScrollPane(myList);
  }

  protected ListCellRenderer getListCellRenderer(){
    return new DefaultListCellRenderer();
  }
}
