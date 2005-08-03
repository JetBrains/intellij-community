/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import javax.swing.*;
import javax.swing.event.DocumentListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.ActionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class TextFieldWithHistory extends JComboBox {

  private int myHistorySize = 5;
  private MyModel myModel;

  public TextFieldWithHistory() {
    myModel = new MyModel();
    setModel(myModel);
    setEditable(true);

    setEditor(new MyComboBoxEditor(getEditor()));

    getTextEditor().addKeyListener(new HistoricalValuesHighlighter());
  }

  public void addDocumentListener(DocumentListener listener) {
    getTextEditor().getDocument().addDocumentListener(listener);
  }

  public void setHistorySize(int aHistorySize) {
    myHistorySize = aHistorySize;
  }

  public void setHistory(List aHistory) {
    myModel.setItems(aHistory);
  }

  public List getHistory() {
    return myModel.getItems();
  }


  public int getHistorySize() {
    return myHistorySize;
  }

  public void setText(String aText) {
    getTextEditor().setText(aText);
  }

  public String getText() {
    return getTextEditor().getText();
  }

  public void addCurrentTextToHistory() {
    myModel.addElement(getText());
  }

  public void selectText() {
    getTextEditor().selectAll();
  }

  protected JTextField getTextEditor() {
    JTextField editor = (JTextField) getEditor().getEditorComponent();
    return editor;
  }


  public void requestFocus() {
    getTextEditor().requestFocus();
  }

  protected class HistoricalValuesHighlighter extends KeyAdapter {

    public void keyPressed(KeyEvent e) {
      if (e.getKeyCode() == KeyEvent.VK_DELETE) {
        updateCroppedList();
      }
    }

    public void keyTyped(KeyEvent e) {
      if (Character.isLetter(e.getKeyChar()) && noAltCtrl(e)) {
        updateCroppedList();
      }
    }

    private boolean noAltCtrl(KeyEvent aE) {
      return 0 == (aE.getModifiers() & (KeyEvent.ALT_MASK | KeyEvent.CTRL_MASK));
    }

    private void updateCroppedList() {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          String text = getTextEditor().getText();
          myModel.setSelectedItemAndCropList(text);
        }
      });

      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          if (0 == myModel.getSize()) {
            hidePopup();
            myModel.uncropList();
          }
          else {
            refreshPopup();
          }
        }
      });
    }

    private void refreshPopup() {
      Runnable hider = new Runnable() {
        public void run() {
          hidePopup();
        }
      };

      Runnable shower = new Runnable() {
        public void run() {
          showPopup();
        }
      };

      if (myModel.croppedListEnlarged()) {
        SwingUtilities.invokeLater(hider);
      }

      SwingUtilities.invokeLater(shower);
    }
  }

  public class MyModel extends AbstractListModel implements MutableComboBoxModel {

    private List myFullList = new ArrayList();
    private List myCroppedList = new ArrayList();
    private String myCroppedListElementsPrefix = "";
    private int myLastCroppedListSize = 0;

    private Object mySelectedItem;

    public boolean croppedListEnlarged() {
      return myLastCroppedListSize < getSize();
    }

    public Object getElementAt(int index) {
      return myCroppedList.get(index);
    }

    public int getSize() {
      return myCroppedList.size();
    }

    public void addElement(Object obj) {
      String newItem = ((String) obj).trim();

      if (0 == newItem.length()) {
        return;
      }

      if (!contains(newItem)) {
        if (getSize() >= getHistorySize()) {
          insertElementAt(newItem, 0);
          removeElementAt(getSize() - 1);
        }
        else {
          myFullList.add(newItem);
        }

        refreshCroppedList();
      }
    }

    public void insertElementAt(Object obj, int index) {
      myFullList.add(index, obj);
      refreshCroppedList();
    }

    public void removeElement(Object obj) {
      myFullList.remove(obj);
      refreshCroppedList();
    }

    public void removeElementAt(int index) {
      myFullList.remove(index);
      refreshCroppedList();
    }

    public Object getSelectedItem() {
      return mySelectedItem;
    }

    public void setSelectedItem(Object anItem) {
      mySelectedItem = anItem;
      refreshCroppedList();
    }

    private void refreshCroppedList() {
      if (null == getSelectedItem()) {
        return;
      }
      myLastCroppedListSize = myCroppedList.size();

      myCroppedList = new ArrayList();
      for (int i = 0; i < myFullList.size(); i++) {
        if (((String) myFullList.get(i)).startsWith(getCroppedListElementsPrefix())) {
          myCroppedList.add(myFullList.get(i));
        }
      }

      fireContentsChanged();
    }

    public void setSelectedItemAndCropList(String aItem) {
      setSelectedItem(aItem);
      myCroppedListElementsPrefix = aItem;
      refreshCroppedList();
    }

    public void uncropList() {
      myCroppedListElementsPrefix = "";
      refreshCroppedList();
    }

    public void fireContentsChanged() {
      fireContentsChanged(this, -1, -1);
    }

    public String getCroppedListElementsPrefix() {
      return myCroppedListElementsPrefix;
    }

    public boolean contains(String aNewValue) {
      return myFullList.contains(aNewValue);
    }

    public void setItems(List aList) {
      myFullList = aList;
      fireContentsChanged();
    }

    public List getItems() {
      return myFullList;
    }
  }

  /**
   * To override
   * @see com.sun.java.swing.plaf.windows.WindowsComboBoxUI.WindowsComboBoxEditor#setItem
   * otherwise every type (for letters only) produce selection of text Thus near every next typing removes previous ones.
   */
  private static class MyComboBoxEditor implements ComboBoxEditor {
    private ComboBoxEditor myDelegate;

    public MyComboBoxEditor(final ComboBoxEditor delegate) {
      myDelegate = delegate;
    }

    public Component getEditorComponent() {
      return myDelegate.getEditorComponent();
    }

    public void setItem(Object anObject) {
      final Component editorComponent = myDelegate.getEditorComponent();
      if (editorComponent instanceof JTextField){
        if ( anObject != null )  {
          ((JTextField)editorComponent).setText(anObject.toString());
        } else {
          ((JTextField)editorComponent).setText("");
        }
      } else {
        myDelegate.setItem(anObject);
      }
    }

    public Object getItem() {
      return myDelegate.getItem();
    }

    public void selectAll() {
      myDelegate.selectAll();
    }

    public void addActionListener(ActionListener l) {
      myDelegate.addActionListener(l);
    }

    public void removeActionListener(ActionListener l) {
      myDelegate.removeActionListener(l);
    }
  }
}