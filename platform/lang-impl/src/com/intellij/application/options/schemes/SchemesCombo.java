/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.application.options.schemes;

import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.MessageType;
import com.intellij.ui.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.Collection;

public class SchemesCombo<T extends Scheme> {
  private ComboBox<SchemeListItem<T>> myComboBox;
  private JPanel myRootPanel;
  private AbstractSchemesPanel<T> mySchemesPanel;
  private final CardLayout myLayout;
  private final JTextField myNameEditorField;
  
  private final static KeyStroke ESC_KEY_STROKE = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false);
  private final static KeyStroke ENTER_KEY_STROKE = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false);

  public SchemesCombo(@NotNull AbstractSchemesPanel<T> schemesPanel) {
    mySchemesPanel = schemesPanel;
    myLayout = new CardLayout();
    myRootPanel = new JPanel(myLayout);
    createCombo();
    myRootPanel.add(myComboBox);
    myNameEditorField = createNameEditorField();
    myRootPanel.add(myNameEditorField);
    myRootPanel.setMaximumSize(new Dimension(myNameEditorField.getPreferredSize().width, Short.MAX_VALUE));
  }

  private JTextField createNameEditorField() {
    JTextField nameEditorField = new JTextField(15);
    nameEditorField.registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        cancelEdit();
      }
    }, ESC_KEY_STROKE, JComponent.WHEN_FOCUSED);
    nameEditorField.registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        stopEdit();
      }
    }, ENTER_KEY_STROKE, JComponent.WHEN_FOCUSED);
    return nameEditorField;
  }

  private void stopEdit() {
    String newName = myNameEditorField.getText();
    SchemeListItem<T> selectedItem = getSelectedItem();
    String validationMessage = selectedItem != null ? selectedItem.validateSchemeName(newName) : null;
    if (validationMessage != null) {
      mySchemesPanel.showInfo(validationMessage, MessageType.ERROR);
    }
    else {
      cancelEdit();
      if (selectedItem != null && selectedItem.getScheme() != null) {
        mySchemesPanel.getActions().doRename(selectedItem.getScheme(), newName);
      }
    }
  }
  
  private void cancelEdit() {
    mySchemesPanel.clearInfo();
    myLayout.first(myRootPanel);
    myRootPanel.requestFocus();
  }

  private void createCombo() {
    myComboBox = new ComboBox<>();
    myComboBox.setRenderer(new MyListCellRenderer());
    myComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        mySchemesPanel.getActions().onSchemeChanged(getSelectedScheme());
      }
    });
    myComboBox.setModel(new DefaultComboBoxModel<SchemeListItem<T>>() {
      @Override
      public void setSelectedItem(Object anObject) {
        if (anObject instanceof SchemeListItem && ((SchemeListItem)anObject).isSeparator()) {
          return;
        }
        super.setSelectedItem(anObject);
      }
    });
  }
  
  public void startEdit() {
    T scheme = getSelectedScheme();
    if (scheme != null) {
      myNameEditorField.setText(scheme.getName());
      myLayout.last(myRootPanel);
      myNameEditorField.requestFocus();
    }
  }

  private SimpleTextAttributes getSchemeAttributes(@NotNull SchemeListItem<T> item) {
    return item.isDeleteAvailable() ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
  }
  
  public void resetSchemes(@NotNull Collection<T> schemes) {
    myComboBox.removeAllItems();
    SchemeListItem.SchemeLevel currSchemeLevel = SchemeListItem.SchemeLevel.IDE_Only;
    for (T scheme : schemes) {
      SchemeListItem<T> item = mySchemesPanel.createItem(scheme);
      SchemeListItem.SchemeLevel schemeLevel = item.getSchemeLevel();
      if (!currSchemeLevel.equals(schemeLevel)) {
        currSchemeLevel = schemeLevel;
        if (!schemeLevel.equals(SchemeListItem.SchemeLevel.IDE_Only)) {
          myComboBox.addItem(mySchemesPanel.createSeparator(currSchemeLevel.toString()));
        }
      }
      myComboBox.addItem(item);
    }
  }

  private class MyListCellRenderer extends ColoredListCellRenderer<SchemeListItem<T>> {
    private ListCellRendererWrapper<SchemeListItem> myWrapper = new ListCellRendererWrapper<SchemeListItem>() {
      @Override
      public void customize(JList list,
                            SchemeListItem value,
                            int index,
                            boolean selected,
                            boolean hasFocus) {
        if (value.isSeparator()) {
          setText(" Stored in " + value.getPresentableText());
          setSeparator();
        }
      }
    };

    @Override
    public Component getListCellRendererComponent(JList<? extends SchemeListItem<T>> list,
                                                  SchemeListItem<T> value,
                                                  int index,
                                                  boolean selected,
                                                  boolean hasFocus) {
      if (value.isSeparator()) {
        Component c = myWrapper.getListCellRendererComponent(list, value, index, selected, hasFocus);
        if (c instanceof TitledSeparator) {
          ((TitledSeparator)c).getLabel().setForeground(JBColor.GRAY);
          return c;
        }
      }
      return super.getListCellRendererComponent(list, value, index, selected, hasFocus);
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends SchemeListItem<T>> list,
                                         SchemeListItem<T> value,
                                         int index,
                                         boolean selected,
                                         boolean hasFocus) {
      if (value.getScheme() != null) {
        append(value.getPresentableText(), getSchemeAttributes(value));
        SchemeListItem.SchemeLevel schemeLevel = value.getSchemeLevel();
        if (index == -1 && !SchemeListItem.SchemeLevel.IDE_Only.equals(schemeLevel)) {
          append("  " + schemeLevel.toString(), SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
      }
    }
  }
  
  @Nullable
  public T getSelectedScheme() {
    SchemeListItem<T> item = getSelectedItem();
    return item != null ? item.getScheme() : null;
  }
  
  @Nullable
  public SchemeListItem<T> getSelectedItem() {
    int i = myComboBox.getSelectedIndex();
    return i >= 0 ? myComboBox.getItemAt(i) : null;
  }
  
  public void selectScheme(@Nullable T scheme) {
    for (int i = 0; i < myComboBox.getItemCount(); i ++) {
      if (myComboBox.getItemAt(i).getScheme() == scheme) {
        myComboBox.setSelectedIndex(i);
        break;
      }
    }
  }
  
  public JComponent getComponent() {
    return myRootPanel;
  }
  
}
