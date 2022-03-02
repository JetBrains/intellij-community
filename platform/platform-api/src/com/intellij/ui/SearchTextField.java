// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.TextUI;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

public class SearchTextField extends JPanel {

  public static final DataKey<SearchTextField> KEY = DataKey.create("search.text.field");
  public static final KeyStroke SHOW_HISTORY_KEYSTROKE = KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK);
  public static final CustomShortcutSet SHOW_HISTORY_SHORTCUT = new CustomShortcutSet(SHOW_HISTORY_KEYSTROKE);
  public static final KeyStroke ALT_SHOW_HISTORY_KEYSTROKE = KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK);
  public static final CustomShortcutSet ALT_SHOW_HISTORY_SHORTCUT = new CustomShortcutSet(ALT_SHOW_HISTORY_KEYSTROKE);

  private int myHistorySize = 5;
  private int myCurrentHistoryIndex;
  private final MyModel myModel;
  private final TextFieldWithProcessing myTextField;

  @Nullable
  private JBPopup myPopup;
  private String myHistoryPropertyName;
  private final boolean historyPopupEnabled;

  public SearchTextField() {
    this(true);
  }

  public SearchTextField(boolean historyPopupEnabled) {
    this(historyPopupEnabled, null);
  }

  public SearchTextField(@NonNls String historyPropertyName) {
    this(true, historyPropertyName);
  }

  public SearchTextField(boolean historyPopupEnabled, @Nullable String historyPropertyName) {
    super(new BorderLayout());
    this.historyPopupEnabled = historyPopupEnabled;

    myModel = new MyModel();
    myTextField = new TextFieldWithProcessing() {
      {
        this.putClientProperty("History.Popup.Enabled", historyPopupEnabled);
      }

      @Override
      public void processKeyEvent(final KeyEvent e) {
        if (preprocessEventForTextField(e)) return;
        super.processKeyEvent(e);
      }

      @Override
      protected void processMouseEvent(MouseEvent e) {
        TextUI ui = getUI();
        //noinspection unchecked
        if (ui instanceof Condition && ((Condition)ui).value(e)) return;

        if(e.getX() < JBUIScale.scale(28) && myModel.myFullList.size() > 0) {
          myTextField.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
          if (e.getClickCount() == 1) {
           showPopup();
          }
        } else {
          myTextField.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        }
        super.processMouseEvent(e);
      }

      @Override
      protected Rectangle getEmptyTextComponentBounds(Rectangle bounds) {
        Integer gap = (Integer)getClientProperty("JTextField.Search.GapEmptyText");
        if (gap != null) {
          bounds.x += gap;
          bounds.width -= 2 * gap;
        }
        return bounds;
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

    setHistoryPropertyName(historyPropertyName);

    if (historyPropertyName != null) {
      myTextField.getActionMap().put("showPrevHistoryItem", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          if (!myModel.myFullList.contains(getText())) addCurrentTextToHistory();
          if (myModel.getSize() < 2) return;
          myCurrentHistoryIndex--;
          if (myCurrentHistoryIndex < 0) myCurrentHistoryIndex = myModel.getSize() - 1;
          setText(myModel.getElementAt(myCurrentHistoryIndex));
        }
      });
      myTextField.getInputMap().put(ALT_SHOW_HISTORY_KEYSTROKE, "showPrevHistoryItem");
      myTextField.getActionMap().put("showNextHistoryItem", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          if (!myModel.myFullList.contains(getText())) addCurrentTextToHistory();
          if (myModel.getSize() < 2) return;
          myCurrentHistoryIndex++;
          if (myCurrentHistoryIndex > myModel.getSize() - 1) myCurrentHistoryIndex = 0;
          setText(myModel.getElementAt(myCurrentHistoryIndex));
        }
      });
      myTextField.getInputMap().put(SHOW_HISTORY_KEYSTROKE, "showNextHistoryItem");
    }

    myTextField.putClientProperty("JTextField.variant", "search");
    myTextField.putClientProperty("JTextField.Search.Gap", JBUIScale.scale(6));
    myTextField.putClientProperty("JTextField.Search.CancelAction", (ActionListener)e -> {
      myTextField.setText("");
      onFieldCleared();
    });
    DumbAwareAction.create(event -> {
      showPopup();
    }).registerCustomShortcutSet(KeymapUtil.getActiveKeymapShortcuts("ShowSearchHistory"), myTextField);
  }

  @Override
  public void addNotify() {
    super.addNotify();

    if (toClearTextOnEscape()) {
      ActionManager actionManager = ActionManager.getInstance();
      if (actionManager != null) {
        EmptyAction.registerWithShortcutSet(IdeActions.ACTION_CLEAR_TEXT, CommonShortcuts.ESCAPE, this);
      }
    }
  }

  protected boolean toClearTextOnEscape() {
    return ApplicationManager.getApplication() != null;
  }

  protected void onFieldCleared() {
  }

  protected void onFocusLost() {
    addCurrentTextToHistory();
  }

  protected void onFocusGained() {
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

  public void setHistorySize(int historySize) {
    if (historySize <= 0) throw new IllegalArgumentException("history size must be a positive number");
    myHistorySize = historySize;
  }

  public void setHistory(List<String> aHistory) {
    myModel.setItems(aHistory);
  }

  public List<String> getHistory() {
    final int itemsCount = myModel.getSize();
    final List<String> history = new ArrayList<>(itemsCount);
    for (int i = 0; i < itemsCount; i++) {
      history.add(myModel.getElementAt(i));
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

  public void addCurrentTextToHistory() {
    if (myModel.addElement(getText()) && myHistoryPropertyName != null) {
      PropertiesComponent.getInstance().setValue(myHistoryPropertyName, StringUtil.join(getHistory(), "\n"));
    }
  }

  protected void historyItemChosen(String item) {
  }

  public void selectText() {
    getTextEditor().selectAll();
  }

  public JBTextField getTextEditor() {
    return myTextField;
  }

  @Override
  public boolean requestFocusInWindow() {
    return myTextField.requestFocusInWindow();
  }

  @Override
  public void requestFocus() {
    IdeFocusManager.getGlobalInstance()
            .doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(getTextEditor(), true));
  }

  protected void setHistoryPropertyName(String historyPropertyName) {
    myHistoryPropertyName = historyPropertyName;
    myTextField.putClientProperty("JTextField.Search.InplaceHistory", myHistoryPropertyName);
    reset();
  }

  public void reset() {
    if (myHistoryPropertyName == null) return;
    final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
    final String history = propertiesComponent.getValue(myHistoryPropertyName);
    if (history != null) {
      final String[] items = history.split("\n");
      ArrayList<String> result = new ArrayList<>();
      for (String item : items) {
        if (item != null && item.length() > 0) {
          result.add(item);
        }
      }
      setHistory(result);
    }
    else {
      setEmptyHistory();
    }
    setSelectedItem("");
  }

  protected void setEmptyHistory() {
  }

  public class MyModel extends AbstractListModel<String> {
    private List<String> myFullList = new ArrayList<>();

    private String mySelectedItem;

    @Override
    public String getElementAt(int index) {
      return myFullList.get(index);
    }

    @Override
    public int getSize() {
      return Math.min(myHistorySize, myFullList.size());
    }

    public boolean addElement(String item) {
      final String newItem = item.trim();
      if (newItem.isEmpty()) {
        return false;
      }

      final int length = myFullList.size();
      int index = -1;
      for (int i = 0; i < length; i++) {
        if (StringUtil.equalsIgnoreCase(myFullList.get(i), newItem)) {
          index = i;
          break;
        }
      }
      if (index == 0) {
        // item is already at the top of the list
        return false;
      }
      if (index > 0) {
        // move item to top of the list
        myFullList.remove(index);
      }
      else if (myFullList.size() >= myHistorySize && myFullList.size() > 0) {
        // trim list
        myFullList.remove(myFullList.size() - 1);
      }
      insertElementAt(newItem, 0);
      return true;
    }

    public void insertElementAt(String item, int index) {
      myFullList.add(index, item);
      fireContentsChanged();
    }

    public String getSelectedItem() {
      return mySelectedItem;
    }

    public void setSelectedItem(String anItem) {
      mySelectedItem = anItem;
    }

    public void fireContentsChanged() {
      fireContentsChanged(this, -1, -1);
      updatePopup();
    }

    public void setItems(List<String> aList) {
      myFullList = new ArrayList<>(aList);
      fireContentsChanged();
    }
  }

  protected void hidePopup() {
    if (myPopup != null) {
      myPopup.cancel();
      myPopup = null;
    }
  }

  protected Runnable createItemChosenCallback(final JList list) {
    return () -> {
      final String value = (String)list.getSelectedValue();
      getTextEditor().setText(value != null ? value : "");
      addCurrentTextToHistory();
    };
  }

  protected void showPopup() {
    addCurrentTextToHistory();
    if (myPopup != null && myPopup.isVisible()) return;
    if (historyPopupEnabled) {
      doShowPopup();
    }
  }

  private void updatePopup() {
    if (myPopup != null && myPopup.isVisible()) {
      hidePopup();
      doShowPopup();
    }
  }

  private void doShowPopup() {
    if (ApplicationManager.getApplication() != null &&
        JBPopupFactory.getInstance() != null &&
        isShowing()) {
      final JList<String> list = new JBList<>(myModel);
      final Runnable chooseRunnable = createItemChosenCallback(list);
      myPopup = JBPopupFactory.getInstance().createListPopupBuilder(list)
        .setMovable(false)
        .setRequestFocus(true)
        .setItemChoosenCallback(chooseRunnable).createPopup();
      myPopup.showUnderneathOf(getPopupLocationComponent());
    }
  }

  protected Component getPopupLocationComponent() {
    return this;
  }

  public void setSelectedItem(final String s) {
    getTextEditor().setText(s);
  }

  public int getSelectedIndex() {
    return myModel.myFullList.indexOf(getText());
  }

  protected static class TextFieldWithProcessing extends JBTextField {
    @Override
    public void processKeyEvent(KeyEvent e) {
      super.processKeyEvent(e);
    }
  }

  protected final void keyEventToTextField(KeyEvent e) {
    myTextField.processKeyEvent(e);
  }

  protected boolean preprocessEventForTextField(KeyEvent e) {
    if (SHOW_HISTORY_KEYSTROKE.equals(KeyStroke.getKeyStrokeForEvent(e))) {
      showPopup();
      return true;
    }
    return false;
  }

  /**
   * @deprecated unused
   */
  @Deprecated(forRemoval = true)
  public void setSearchIcon(final Icon icon) {
  }

  public static final class FindAction extends DumbAwareAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      SearchTextField search = event.getData(KEY);
      if (search != null) {
        search.selectText();
        search.requestFocus();
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(e.getData(KEY) != null);
    }
  }
}
