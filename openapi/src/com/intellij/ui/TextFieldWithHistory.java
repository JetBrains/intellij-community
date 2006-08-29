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

import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class TextFieldWithHistory extends JPanel {

  private int myHistorySize = 5;
  private MyModel myModel;
  private JTextField myTextField;

  private JBPopup myPopup;
  private JLabel myClearFieldLabel;
  private JLabel myToggleHistoryLabel;

  private boolean myFreaze = false;

  public TextFieldWithHistory() {
    super(new BorderLayout());

    myModel = new MyModel();

    myTextField = new JTextField();
    myTextField.setColumns(15);

    add(myTextField, BorderLayout.CENTER);

    myToggleHistoryLabel = new JLabel(IconLoader.findIcon("/actions/search.png"));
    myToggleHistoryLabel.setOpaque(true);
    myToggleHistoryLabel.setBackground(myTextField.getBackground());
    myToggleHistoryLabel.setBorder(IdeBorderFactory.createEmptyBorder(0, 0, 0, 4));
    myToggleHistoryLabel.addMouseListener(new MouseAdapter() {
      public void mousePressed(MouseEvent e) {
        togglePopup();
      }
    });
    add(myToggleHistoryLabel, BorderLayout.WEST);

    myClearFieldLabel = new JLabel(IconLoader.findIcon("/actions/clean.png"));
    myClearFieldLabel.setOpaque(true);
    myClearFieldLabel.setBackground(myTextField.getBackground());
    myClearFieldLabel.setBorder(IdeBorderFactory.createEmptyBorder(0, 4, 0, 0));
    add(myClearFieldLabel, BorderLayout.EAST);
    myClearFieldLabel.addMouseListener(new MouseAdapter() {
      public void mousePressed(MouseEvent e) {
        myTextField.setText("");
      }
    });

    // myTextField.addKeyListener(new HistoricalValuesHighlighter());
    myTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        if (myFreaze) return; //do not suggest during batch update
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
    });

    setBorder(new CompoundBorder(IdeBorderFactory.createEmptyBorder(4, 0, 4, 0), myTextField.getBorder()));
    
    myTextField.setBorder(null);
  }

  public void addDocumentListener(DocumentListener listener) {
    getTextEditor().getDocument().addDocumentListener(listener);
  }


  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    final Color bg = enabled
                     ? UIUtil.getTextFieldBackground()
                     : UIUtil.getPanelBackground();
    myToggleHistoryLabel.setBackground(bg);
    myClearFieldLabel.setBackground(bg);
  }

  public void setHistorySize(int aHistorySize) {
    myHistorySize = aHistorySize;
  }

  public void setHistory(List<String> aHistory) {
    myModel.setItems(aHistory);
  }

  public List getHistory() {
    return myModel.getItems();
  }

  public int getHistorySize() {
    return myHistorySize;
  }

  public void setText(String aText) {
    myFreaze = true;
    getTextEditor().setText(aText);
    myFreaze = false;
  }

  public String getText() {
    return getTextEditor().getText();
  }

  public void removeNotify() {
    super.removeNotify();
    hidePopup();
  }

  public void addCurrentTextToHistory() {
    myModel.addElement(getText());
  }

  public void selectText() {
    getTextEditor().selectAll();
  }

  protected JTextField getTextEditor() {
    return myTextField;
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

  public class MyModel extends AbstractListModel {

    private List<String> myFullList = new ArrayList<String>();
    private List<String> myCroppedList = new ArrayList<String>();
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
      myFullList.add(index, (String)obj);
      refreshCroppedList();
    }

    @SuppressWarnings({"SuspiciousMethodCalls"})
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
      if (null == getSelectedItem() && myCroppedList.size() > 0) {
        return;
      }
      myLastCroppedListSize = myCroppedList.size();

      myCroppedList = new ArrayList<String>();
      for (String item : myFullList) {
        if (item.startsWith(getCroppedListElementsPrefix()) && !item.equals(getCroppedListElementsPrefix())) {
          myCroppedList.add(item);
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

    public void setItems(List<String> aList) {
      myFullList = aList;
      uncropList();
      fireContentsChanged();
    }

    public List getItems() {
      return myFullList;
    }
  }

  private void hidePopup() {
    if (myPopup != null) {
      myPopup.cancel();
      myPopup = null;
    }
  }

  private void showPopup() {
    if (myPopup == null) {
      final JList list = new JList(myModel);
      myPopup = JBPopupFactory.getInstance().createListPopupBuilder(list)
        .setMovable(false)
        .setRequestFocus(false)
        .setItemChoosenCallback(new Runnable() {
        public void run() {
          final String value = (String)list.getSelectedValue();
          getTextEditor().setText(value != null ? value : "");
          myPopup = null;
        }
      }).createPopup();

      if (isShowing()) myPopup.showUnderneathOf(this);
    }
  }

  private void togglePopup() {
    if (myPopup == null) {
      myModel.uncropList();
      showPopup();
    }
    else {
      hidePopup();
    }
  }

  public void setSelectedItem(final String s) {
    myFreaze = true;
    getTextEditor().setText(s);
    myFreaze = false;
  }

  public int getSelectedIndex() {
    return myModel.myCroppedList.indexOf(getText());
  }
}