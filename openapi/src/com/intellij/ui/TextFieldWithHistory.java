/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

public class TextFieldWithHistory extends JComboBox {

  private int myHistorySize = 5;
  private MyModel myModel;

  public TextFieldWithHistory() {
    myModel = new MyModel();
    setModel(myModel);
    setEditable(true);

    getTextEditor().addKeyListener(new HistoricalValuesHighlighter());
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
}