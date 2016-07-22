/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel with "Add" and "Delete" buttons on the right side.
 *
 * @author Konstantin Bulenkov
 * @author anna
 * @since 5.1
 */
public abstract class AddDeleteListPanel<T> extends PanelWithButtons implements ComponentWithEmptyText {
  private final String myTitle;

  /**
   * @deprecated
   */
  protected JButton myAddButton = new JButton(CommonBundle.message("button.add"));
  /**
   * @deprecated
   */
  protected JButton myDeleteButton = new JButton(CommonBundle.message("button.delete"));

  protected DefaultListModel myListModel = new DefaultListModel();
  protected JBList myList = new JBList(myListModel);

  public AddDeleteListPanel(final String title, final List<T> initialList) {
    myTitle = title;
    for (Object o : initialList) {
      if (o != null) {
        myListModel.addElement(o);
      }
    }
    myList.setCellRenderer(getListCellRenderer());
    initPanel();
  }

  @Override
  protected void initPanel() {
    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myList)
      .disableUpAction()
      .disableDownAction()
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          addElement(findItemToAdd());
        }
      });
    customizeDecorator(decorator);
    setLayout(new BorderLayout());
    add(decorator.createPanel(), BorderLayout.CENTER);
    if (myTitle != null) {
      setBorder(IdeBorderFactory.createTitledBorder(myTitle, false));
    }
  }

  protected void customizeDecorator(ToolbarDecorator decorator) {
  }

  @NotNull
  @Override
  public StatusText getEmptyText() {
    return myList.getEmptyText();
  }

  protected void addElement(@Nullable T itemToAdd) {
    if (itemToAdd != null){
      myListModel.addElement(itemToAdd);
      myList.setSelectedValue(itemToAdd, true);
    }
  }

  @Nullable
  protected abstract T findItemToAdd();

  public Object [] getListItems(){
    List<Object> items = new ArrayList<>();
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
