package com.intellij.ui;

import com.intellij.CommonBundle;

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
  private String myTitle;
  private JButton myAddButton = new JButton(CommonBundle.message("button.add"));
  private JButton myDeleteButton = new JButton(CommonBundle.message("button.delete"));
  private DefaultListModel myListModel = new DefaultListModel();
  private JList myList = new JList(myListModel);

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
