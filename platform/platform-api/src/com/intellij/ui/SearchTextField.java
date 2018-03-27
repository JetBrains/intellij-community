// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.Consumer;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.TextUI;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class SearchTextField extends JPanel {
  public static final DataKey<SearchTextField> KEY = DataKey.create("search.text.field");
  public static final KeyStroke SHOW_HISTORY_KEYSTROKE = KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK);
  public static final CustomShortcutSet SHOW_HISTORY_SHORTCUT = new CustomShortcutSet(SHOW_HISTORY_KEYSTROKE);
  public static final KeyStroke ALT_SHOW_HISTORY_KEYSTROKE = KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK);
  public static final CustomShortcutSet ALT_SHOW_HISTORY_SHORTCUT = new CustomShortcutSet(ALT_SHOW_HISTORY_KEYSTROKE);

  private int myHistorySize = 5;
  private int myCurrentHistoryIndex = 0;
  private final MyModel myModel;
  private final TextFieldWithProcessing myTextField;

  private JBPopup myPopup;
  private JLabel myClearFieldLabel;
  private JLabel myToggleHistoryLabel;
  private JPopupMenu myNativeSearchPopup;
  private JMenuItem myNoItems;
  private String myHistoryPropertyName = null;

  public SearchTextField() {
    this(true);
  }

  public SearchTextField(boolean historyPopupEnabled) {
    this(historyPopupEnabled, null);
  }

  public SearchTextField(String historyPropertyName) {
    this(true, historyPropertyName);
  }

  public SearchTextField(boolean historyPopupEnabled, @Nullable String historyPropertyName) {
    super(new BorderLayout());

    myModel = new MyModel();

    myTextField = new TextFieldWithProcessing() {
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

        super.processMouseEvent(e);
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

      @Override
      public void setUI(TextUI ui) {
        if (customSetupUIAndTextField(this, textUI -> super.setUI(textUI))) return;

        super.setUI(ui);
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

    if (historyPopupEnabled) {
      myTextField.getActionMap().put("showHistory", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          if (isSearchControlUISupported() && myNativeSearchPopup != null) {
            myNativeSearchPopup.show(myTextField, 5, myTextField.getHeight());
          } else if (myPopup == null || !myPopup.isVisible()) {
            showPopup();
          }
        }
      });
      myTextField.getInputMap().put(SHOW_HISTORY_KEYSTROKE, "showHistory");
    } else if (historyPropertyName != null) {
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

    if (isSearchControlUISupported()) {
      myTextField.putClientProperty("JTextField.variant", "search");
      myTextField.putClientProperty("JTextField.Search.CancelAction", new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          myTextField.setText("");
          onFieldCleared();
        }
      });

      if (historyPopupEnabled) {
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
      myToggleHistoryLabel.setToolTipText("Search History (" + KeymapUtil.getKeystrokeText(SHOW_HISTORY_KEYSTROKE)+ ")");
      myToggleHistoryLabel.addMouseListener(new MouseAdapter() {
        public void mousePressed(MouseEvent e) {
          togglePopup();
        }
      });
      if (historyPopupEnabled) {
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

        setBorder(new CompoundBorder(JBUI.Borders.empty(2, 0, 2, 0), originalBorder));

        myTextField.setOpaque(true);
        myTextField.setBorder(JBUI.Borders.empty(0, 5, 0, 5));
      }
      else {
        setBorder(JBUI.Borders.empty(2, 0, 2, 0));
      }
    }

    if (toClearTextOnEscape()) {
      final ActionManager actionManager = ActionManager.getInstance();
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

  private void updateMenu() {
    if (myNativeSearchPopup != null) {
      myNativeSearchPopup.removeAll();
      final int itemsCount = myModel.getSize();
      if (itemsCount == 0) {
        myNativeSearchPopup.add(myNoItems);
      }
      else {
        for (int i = 0; i < itemsCount; i++) {
          final String item = myModel.getElementAt(i);
          addMenuItem(item);
        }
      }
    }
  }

  protected boolean isSearchControlUISupported() {
    return (SystemInfo.isMacOSLeopard && UIUtil.isUnderAquaLookAndFeel()) || UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF();
  }

  protected boolean hasIconsOutsideOfTextField() {
    return UIUtil.isUnderGTKLookAndFeel();
  }

  protected boolean customSetupUIAndTextField(@NotNull TextFieldWithProcessing textField, @NotNull Consumer<TextUI> uiConsumer) {
    if (SystemInfo.isMac) {
      try {
        Class<?> uiClass = UIUtil.isUnderIntelliJLaF() ? Class.forName("com.intellij.ide.ui.laf.intellij.MacIntelliJTextFieldUI")
                                                       : Class.forName("com.intellij.ide.ui.laf.darcula.ui.DarculaTextFieldUI");
        Method method = ReflectionUtil.getMethod(uiClass, "createUI", JComponent.class);
        if (method != null) {
          uiConsumer.consume((TextUI)method.invoke(uiClass, textField));
          Class<?> borderClass = UIUtil.isUnderIntelliJLaF() ? Class.forName("com.intellij.ide.ui.laf.intellij.MacIntelliJTextBorder")
                                                             : Class.forName("com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder");
          textField.setBorder((Border)ReflectionUtil.newInstance(borderClass));
          textField.setOpaque(false);
        }
        return true;
      }
      catch (Exception ignored) {
      }
    }
    return false;
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

  public void removeNotify() {
    super.removeNotify();
    hidePopup();
  }

  public void addCurrentTextToHistory() {
    if (myModel.addElement(getText()) && myHistoryPropertyName != null) {
      PropertiesComponent.getInstance().setValue(myHistoryPropertyName, StringUtil.join(getHistory(), "\n"));
    }
  }

  private void addMenuItem(final String item) {
    if (myNativeSearchPopup != null) {
      myNativeSearchPopup.remove(myNoItems);
      final JMenuItem menuItem = new JBMenuItem(item);
      myNativeSearchPopup.add(menuItem);
      menuItem.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          myTextField.setText(item);
          addCurrentTextToHistory();
        }
      });
    }
  }

  public void selectText() {
    getTextEditor().selectAll();
  }

  public JBTextField getTextEditor() {
    return myTextField;
  }

  public boolean requestFocusInWindow() {
    return myTextField.requestFocusInWindow();
  }

  public void requestFocus() {
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
      IdeFocusManager.getGlobalInstance().requestFocus(getTextEditor(), true);
    });
  }

  public void setHistoryPropertyName(String historyPropertyName) {
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
    setSelectedItem("");
  }


  public class MyModel extends AbstractListModel {
    private List<String> myFullList = new ArrayList<>();

    private String mySelectedItem;

    public String getElementAt(int index) {
      return myFullList.get(index);
    }

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
      else if (index > 0) {
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
      updateMenu();
    }

    public void setItems(List<String> aList) {
      myFullList = new ArrayList<>(aList);
      fireContentsChanged();
    }
  }

  private void hidePopup() {
    if (myPopup != null) {
      myPopup.cancel();
      myPopup = null;
    }
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    Border border = super.getBorder();
    if (border != null && UIUtil.isUnderAquaLookAndFeel()) {
      JBInsets.addTo(size, border.getBorderInsets(this));
    }
    return size;
  }

  protected Runnable createItemChosenCallback(final JList list) {
    return () -> {
      final String value = (String)list.getSelectedValue();
      getTextEditor().setText(value != null ? value : "");
      addCurrentTextToHistory();
      if (myPopup != null) {
        myPopup.cancel();
        myPopup = null;
      }
    };
  }

  protected void showPopup() {
    addCurrentTextToHistory();
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

  protected static class TextFieldWithProcessing extends JBTextField {
    public void processKeyEvent(KeyEvent e) {
      super.processKeyEvent(e);
    }
  }

  public final void keyEventToTextField(KeyEvent e) {
    myTextField.processKeyEvent(e);
  }

  protected boolean preprocessEventForTextField(KeyEvent e) {
    if (SHOW_HISTORY_KEYSTROKE.equals(KeyStroke.getKeyStrokeForEvent(e)) && getClientProperty("JTextField.Search.FindPopup") instanceof JPopupMenu) {
      showPopup();
      return true;
    }
    return false;
  }
  
  public void setSearchIcon(final Icon icon) {
    if (! isSearchControlUISupported()) {
      myToggleHistoryLabel.setIcon(icon);
    }
  }

  public static final class FindAction extends DumbAwareAction {
    @Override
    public void actionPerformed(AnActionEvent event) {
      SearchTextField search = event.getData(KEY);
      if (search != null) {
        search.selectText();
        search.requestFocus();
      }
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(e.getData(KEY) != null);
    }
  }
}
