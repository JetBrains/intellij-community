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
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.ui.AddAction;
import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.codeInspection.ui.RemoveAction;

import javax.swing.*;
import java.util.List;

public class ListEditForm {

  JPanel contentPanel;
  JButton addButton;
  JButton removeButton;
  ListTable table;
  private List<String> myStringList;
  private String myTitle;

  public ListEditForm(String title, List<String> stringList) {
    myStringList = stringList;
    myTitle = title;
    removeButton.setAction(new RemoveAction(table));
    addButton.setAction(new AddAction(table));
  }

  private void createUIComponents() {
    table = new ListTable(new ListWrappingTableModel(myStringList, myTitle));
  }

  public JComponent getContentPanel() {
    return contentPanel;
  }
}
