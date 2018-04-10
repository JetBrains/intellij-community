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

import com.intellij.openapi.fileChooser.FileTextField;
import com.intellij.openapi.ui.ComboBox;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TextFieldWithHistory extends ComboBox {
  private int myHistorySize = 5;
  private final MyModel myModel;

  public TextFieldWithHistory() {
    myModel = new MyModel();
    setModel(myModel);
    setEditable(true);
  }

  public void addDocumentListener(DocumentListener listener) {
    getTextEditor().getDocument().addDocumentListener(listener);
  }

  public void removeDocumentListener(DocumentListener listener) {
    getTextEditor().getDocument().removeDocumentListener(listener);
  }

  public void addKeyboardListener(final KeyListener listener) {
    getTextEditor().addKeyListener(listener);
  }

  /**
   * @param aHistorySize -1 means unbounded
   */
  public void setHistorySize(int aHistorySize) {
    myHistorySize = aHistorySize;
  }

  public void setHistory(List<String> aHistory) {
    myModel.setItems(aHistory);
  }

  public List<String> getHistory() {
    final int itemsCount = myModel.getSize();
    List<String> history = new ArrayList<>(itemsCount);
    for (int i = 0; i < itemsCount; i++) {
      history.add((String)myModel.getElementAt(i));
    }
    return history;
  }

  public void setText(String aText) {
    getTextEditor().setText(aText);
  }

  public String getText() {
    return getTextEditor().getText();
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    hidePopup();
  }

  public void setTextAndAddToHistory(String text) {
    setText(text);
    addCurrentTextToHistory();
  }

  public void addCurrentTextToHistory() {
    final String item = getText();
    myModel.addElement(item);
    myModel.setSelectedItem(item);
  }

  public void selectText() {
    getTextEditor().selectAll();
  }

  public JTextField getTextEditor() {
    return (JTextField)getEditor().getEditorComponent();
  }

  @Override
  public void setPopupVisible(boolean v) {
    if (v) {
      final FileTextField fileTextField = (FileTextField)getTextEditor().getClientProperty(FileTextField.KEY);
      // don't allow showing combobox popup when file completion popup is displayed (IDEA-68711)
      if (fileTextField != null && fileTextField.isPopupDisplayed()) {
        return;
      }
    }
    super.setPopupVisible(v);
  }

  public class MyModel extends AbstractListModel implements ComboBoxModel{
    private List<String> myFullList = new ArrayList<>();

    private Object mySelectedItem;

    @Override
    public Object getElementAt(int index) {
      return myFullList.get(index);
    }

    @Override
    public int getSize() {
      return Math.min(myHistorySize == -1 ? Integer.MAX_VALUE : myHistorySize, myFullList.size());
    }

    public void addElement(Object obj) {
      String newItem = ((String)obj).trim();

      if (newItem.isEmpty()) {
        return;
      }

      if (!contains(newItem)) {
        // set newly added item as selected.
        // otherwise current selection will be set to editor
        mySelectedItem = newItem;
        insertElementAt(newItem, 0);
      }
    }

    public void insertElementAt(Object obj, int index) {
      myFullList.add(index, (String)obj);
      fireIntervalAdded(this, index, index);
    }

    @Override
    public Object getSelectedItem() {
      return mySelectedItem;
    }

    @Override
    public void setSelectedItem(Object anItem) {
      if (!Objects.equals(anItem, mySelectedItem)) {
        mySelectedItem = anItem;
        fireContentsChanged();
      }
    }

    public void fireContentsChanged() {
      fireContentsChanged(this, -1, -1);
    }

    public boolean contains(String aNewValue) {
      return myFullList.contains(aNewValue);
    }

    public void setItems(List<String> aList) {
      myFullList = new ArrayList<>(aList);
      fireContentsChanged();
    }
  }

  protected static class TextFieldWithProcessing extends JTextField {
    @Override
    public void processKeyEvent(KeyEvent e) {
      super.processKeyEvent(e);
    }
  }
}