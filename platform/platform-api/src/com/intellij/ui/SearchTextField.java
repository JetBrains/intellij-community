/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class SearchTextField extends JPanel {

  private int myHistorySize = 5;
  private final MyModel myModel;
  private final TextFieldWithProcessing myTextField;

  private JBPopup myPopup;
  private JLabel myClearFieldLabel;
  private JLabel myToggleHistoryLabel;
  private JPopupMenu myNativeSearchPopup;
  private JMenuItem myNoItems;

  public SearchTextField() {
    this(true);
  }

  public SearchTextField(boolean historyEnabled) {
    super(new BorderLayout());

    myModel = new MyModel();

    myTextField = new TextFieldWithProcessing() {
      @Override
      public void processKeyEvent(final KeyEvent e) {
        if (preprocessEventForTextField(e)) return;
        super.processKeyEvent(e);
      }

      @Override
      public void setBackground(final Color bg) {
        super.setBackground(bg);
        if (!hasIconsOutsideOfTextField()) {
          if (myClearFieldLabel != null) {
            myClearFieldLabel.setBackground(bg);
          }
        }
        if (myToggleHistoryLabel != null) {
          myToggleHistoryLabel.setBackground(bg);
        }
      }
    };
    myTextField.setColumns(15);
    myTextField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        onFocusLost();
        super.focusLost(e);
      }

      @Override
      public void focusGained(FocusEvent e) {
        onFocusGained();
        super.focusGained(e);
      }
    });
    add(myTextField, BorderLayout.CENTER);
    myTextField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_DOWN) {
          if (hasNativeLeopardSearchControl() && myNativeSearchPopup != null) {
            myNativeSearchPopup.show(myTextField, 5, myTextField.getHeight());
          } else if (myPopup == null || !myPopup.isVisible()) {
            showPopup();
          }
        }
      }
    });

    if (hasNativeLeopardSearchControl() || UIUtil.isUnderDarcula()) {
      myTextField.putClientProperty("JTextField.variant", "search");
    }
    if (hasNativeLeopardSearchControl()) {
      if (historyEnabled) {
        myNativeSearchPopup = new JBPopupMenu();
        myNoItems = new JBMenuItem("No recent searches");
        myNoItems.setEnabled(false);

        updateMenu();
        myTextField.putClientProperty("JTextField.Search.FindPopup", myNativeSearchPopup);
      }
    }
    else {
      myToggleHistoryLabel = new JLabel(AllIcons.Actions.Search);
      myToggleHistoryLabel.setOpaque(true);
      myToggleHistoryLabel.addMouseListener(new MouseAdapter() {
        public void mousePressed(MouseEvent e) {
          togglePopup();
        }
      });
      if (historyEnabled) {
        add(myToggleHistoryLabel, BorderLayout.WEST);
      }

      myClearFieldLabel = new JLabel(UIUtil.isUnderDarcula() ? AllIcons.Actions.Clean : AllIcons.Actions.CleanLight);
      myClearFieldLabel.setOpaque(true);
      add(myClearFieldLabel, BorderLayout.EAST);
      myClearFieldLabel.addMouseListener(new MouseAdapter() {
        public void mousePressed(MouseEvent e) {
          myTextField.setText("");
          onFieldCleared();
        }
      });

      if (!hasIconsOutsideOfTextField()) {
        final Border originalBorder;
        if (SystemInfo.isMac) {
          originalBorder = BorderFactory.createLoweredBevelBorder();
        }
        else {
          originalBorder = myTextField.getBorder();
        }

        myToggleHistoryLabel.setBackground(myTextField.getBackground());
        myClearFieldLabel.setBackground(myTextField.getBackground());

        setBorder(new CompoundBorder(IdeBorderFactory.createEmptyBorder(2, 0, 2, 0), originalBorder));

        myTextField.setOpaque(true);
        myTextField.setBorder(IdeBorderFactory.createEmptyBorder(0, 5, 0, 5));
      }
      else {
        setBorder(IdeBorderFactory.createEmptyBorder(2, 0, 2, 0));
      }
    }

    if (ApplicationManager.getApplication() != null) { //tests
      final ActionManager actionManager = ActionManager.getInstance();
      if (actionManager != null) {
        final AnAction clearTextAction = actionManager.getAction(IdeActions.ACTION_CLEAR_TEXT);
        if (clearTextAction.getShortcutSet().getShortcuts().length == 0) {
          clearTextAction.registerCustomShortcutSet(CommonShortcuts.ESCAPE, this);
        }
      }
    }
  }

  protected void onFieldCleared() {
  }

  protected void onFocusLost() {
  }

  protected void onFocusGained() {
  }

  private void updateMenu() {
    if (myNativeSearchPopup != null) {
      myNativeSearchPopup.removeAll();
      final int itemsCount = myModel.getSize();
      if (itemsCount == 0) {
        myNativeSearchPopup.add(myNoItems);
      }
      else {
        for (int i = 0; i < itemsCount; i++) {
          String item = (String)myModel.getElementAt(i);
          addMenuItem(item);
        }
      }
    }
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (UIUtil.isUnderDarcula() && false) {//todo[kb] fix DarculaTextBorder
      g.setColor(myTextField.getBackground());
      g.fillRect(2,3,getWidth(), getHeight()-5);
    }
  }

  private static boolean hasNativeLeopardSearchControl() {
    return (SystemInfo.isMacOSLeopard && UIUtil.isUnderAquaLookAndFeel()) || UIUtil.isUnderDarcula();
  }

  private static boolean hasIconsOutsideOfTextField() {
    return UIUtil.isUnderGTKLookAndFeel() || UIUtil.isUnderNimbusLookAndFeel();
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

  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    if (myToggleHistoryLabel != null) {
      final Color bg = enabled ? UIUtil.getTextFieldBackground() : UIUtil.getPanelBackground();
      myToggleHistoryLabel.setBackground(bg);
      myClearFieldLabel.setBackground(bg);
    }
  }

  public void setHistorySize(int aHistorySize) {
    myHistorySize = aHistorySize;
  }

  public void setHistory(List<String> aHistory) {
    myModel.setItems(aHistory);
  }

  public List<String> getHistory() {
    final int itemsCount = myModel.getSize();
    List<String> history = new ArrayList<String>(itemsCount);
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

  public void removeNotify() {
    super.removeNotify();
    hidePopup();
  }

  public void addCurrentTextToHistory() {
    final String item = getText();
    myModel.addElement(item);
  }

  private void addMenuItem(final String item) {
    if (myNativeSearchPopup != null) {
      myNativeSearchPopup.remove(myNoItems);
      final JMenuItem menuItem = new JBMenuItem(item);
      myNativeSearchPopup.add(menuItem);
      menuItem.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          myTextField.setText(item);
        }
      });
    }
  }

  public void selectText() {
    getTextEditor().selectAll();
  }

  public JTextField getTextEditor() {
    return myTextField;
  }

  public boolean requestFocusInWindow() {
    return myTextField.requestFocusInWindow();
  }

  public void requestFocus() {
    getTextEditor().requestFocus();
  }

  public class MyModel extends AbstractListModel {
    private List<String> myFullList = new ArrayList<String>();

    private Object mySelectedItem;

    public Object getElementAt(int index) {
      return myFullList.get(index);
    }

    public int getSize() {
      return Math.min(myHistorySize, myFullList.size());
    }

    public void addElement(Object obj) {
      String newItem = ((String)obj).trim();

      if (0 == newItem.length()) {
        return;
      }

      if (!contains(newItem)) {
        insertElementAt(newItem, 0);
      }
    }

    public void insertElementAt(Object obj, int index) {
      myFullList.add(index, (String)obj);
      fireContentsChanged();
    }

    public Object getSelectedItem() {
      return mySelectedItem;
    }

    public void setSelectedItem(Object anItem) {
      mySelectedItem = anItem;
    }

    public void fireContentsChanged() {
      fireContentsChanged(this, -1, -1);
      updateMenu();
    }

    public boolean contains(String aNewValue) {
      return myFullList.contains(aNewValue);
    }

    public void setItems(List<String> aList) {
      myFullList = new ArrayList<String>(aList);
      fireContentsChanged();
    }
  }

  private void hidePopup() {
    if (myPopup != null) {
      myPopup.cancel();
      myPopup = null;
    }
  }

  protected Runnable createItemChosenCallback(final JList list) {
    return new Runnable() {
      public void run() {
        final String value = (String)list.getSelectedValue();
        getTextEditor().setText(value != null ? value : "");
        if (myPopup != null) {
          myPopup.cancel();
          myPopup = null;
        }
      }
    };
  }

  protected void showPopup() {
    if (myPopup == null || !myPopup.isVisible()) {
      final JList list = new JBList(myModel);
      final Runnable chooseRunnable = createItemChosenCallback(list);
      myPopup = JBPopupFactory.getInstance().createListPopupBuilder(list)
        .setMovable(false)
        .setRequestFocus(true)
        .setItemChoosenCallback(chooseRunnable).createPopup();
      if (isShowing()) {
        myPopup.showUnderneathOf(getPopupLocationComponent());
      }
    }
  }

  protected Component getPopupLocationComponent() {
    return hasIconsOutsideOfTextField() ? myToggleHistoryLabel : this;
  }

  private void togglePopup() {
    if (myPopup == null) {
      showPopup();
    }
    else {
      hidePopup();
    }
  }

  public void setSelectedItem(final String s) {
    getTextEditor().setText(s);
  }

  public int getSelectedIndex() {
    return myModel.myFullList.indexOf(getText());
  }

  protected static class TextFieldWithProcessing extends JTextField {
    public void processKeyEvent(KeyEvent e) {
      super.processKeyEvent(e);
    }
  }

  public final void keyEventToTextField(KeyEvent e) {
    myTextField.processKeyEvent(e);
  }

  protected boolean preprocessEventForTextField(KeyEvent e) {
    return false;
  }
  
  public void setSearchIcon(final Icon icon) {
    if (! hasNativeLeopardSearchControl()) {
      myToggleHistoryLabel.setIcon(icon);
    }
  }
}
