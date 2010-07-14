package com.intellij.ui;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * @author peter
 */
public abstract class AddEditDeleteListPanel<T> extends AddDeleteListPanel<T> {
  private JButton myEditButton;

  public AddEditDeleteListPanel(final String title, final List<T> initialList) {
    super(title, initialList);
    myEditButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        editSelectedItem();
      }
    });
    myList.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
          editSelectedItem();
        }
      }
    });
  }

  @Override protected JButton[] createButtons() {
    myEditButton = new JButton(CommonBundle.message("button.edit"));
    return new JButton[] { myAddButton, myEditButton, myDeleteButton };
  }

  @Nullable
  protected abstract T editSelectedItem(T item);

  private void editSelectedItem() {
    int index = myList.getSelectedIndex();
    if (index >= 0) {
      T newValue = editSelectedItem((T) myListModel.get(index));
      if (newValue != null) {
        myListModel.set(index, newValue);
      }
    }
  }


}
