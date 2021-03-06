// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ui;

import com.intellij.CommonBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel with "Add" and "Delete" buttons on the right side.
 *
 * @author Konstantin Bulenkov
 * @author anna
 */
public abstract class AddDeleteListPanel<T> extends PanelWithButtons implements ComponentWithEmptyText {
  private final @NlsContexts.BorderTitle String myTitle;

  protected DefaultListModel<T> myListModel = new DefaultListModel<>();
  protected JBList<T> myList = new JBList<>(myListModel);

  public AddDeleteListPanel(@NlsContexts.BorderTitle final String title, final List<T> initialList) {
    myTitle = title;
    for (T o : initialList) {
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
      setBorder(createTitledBorder(myTitle));
    }
  }

  protected Border createTitledBorder(@NlsContexts.BorderTitle String title) {
    return IdeBorderFactory.createTitledBorder(title, false);
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

  @Override
  protected String getLabelText() {
    return myTitle;
  }

  @Override
  protected JButton[] createButtons() {
    return new JButton[]{new JButton(CommonBundle.message("button.add")), new JButton(CommonBundle.message("button.delete"))};
  }

  @Override
  protected JComponent createMainComponent() {
    if (!myListModel.isEmpty()) myList.setSelectedIndex(0);
    return ScrollPaneFactory.createScrollPane(myList);
  }

  protected ListCellRenderer getListCellRenderer(){
    return new DefaultListCellRenderer();
  }
}
