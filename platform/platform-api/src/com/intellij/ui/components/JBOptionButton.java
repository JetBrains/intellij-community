/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ui.components;

import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.Weighted;
import com.intellij.ui.ScreenUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;

public class JBOptionButton extends JButton implements Weighted {

  public static final String PROP_OPTIONS = "OptionActions";
  public static final String PROP_OPTION_TOOLTIP = "OptionTooltip";

  private Action[] myOptions;

  private JPopupMenu myPopup;
  private boolean myPopupIsShowing;

  private String myOptionTooltipText;

  private Set<OptionInfo> myOptionInfos = new HashSet<>();
  private boolean myOkToProcessDefaultMnemonics = true;

  public JBOptionButton(Action action, Action[] options) {
    super(action);

    setOptions(options);
    applyOptions();

    installShowPopupShortcut();
  }

  @Override
  public String getUIClassID() {
    return "OptionButtonUI";
  }

  @Override
  public OptionButtonUI getUI() {
    return (OptionButtonUI)super.getUI();
  }

  @Override
  public double getWeight() {
    return 0.5;
  }

  public void togglePopup() {
    if (myPopupIsShowing) {
      closePopup();
    } else {
      showPopup(null, false);
    }
  }

  public void showPopup(final Action actionToSelect, final boolean ensureSelection) {
    if (myPopupIsShowing || isSimpleButton()) return;

    myPopupIsShowing = true;
    final Point loc = getLocationOnScreen();
    final Rectangle screen = ScreenUtil.getScreenRectangle(loc);
    final Dimension popupSize = myPopup.getPreferredSize();
    final Rectangle intersection = screen.intersection(new Rectangle(new Point(loc.x, loc.y + getHeight()), popupSize));
    final boolean above = intersection.height < popupSize.height;
    int y = above ? getY() - popupSize.height : getY() + getHeight();
    final JPopupMenu popup = myPopup;

    final Ref<PopupMenuListener> listener = new Ref<>();
    listener.set(new PopupMenuListener() {
      @Override
      public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
      }

      @Override
      public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        if (popup != null && listener.get() != null) {
          popup.removePopupMenuListener(listener.get());
        }
        SwingUtilities.invokeLater(() -> myPopupIsShowing = false);
      }

      @Override
      public void popupMenuCanceled(PopupMenuEvent e) {
      }
    });
    popup.addPopupMenuListener(listener.get());
    popup.show(this, 0, y);

    SwingUtilities.invokeLater(() -> {
      if (isSimpleButton() || !popup.isShowing() || !myPopupIsShowing) return;

      Action selection = actionToSelect;
      if (selection == null && myOptions.length > 0 && ensureSelection) {
        selection = myOptions[0];
      }

      if (selection == null) return;

      final MenuElement[] elements = popup.getSubElements();
      for (MenuElement eachElement : elements) {
        if (eachElement instanceof JMenuItem) {
          JMenuItem eachItem = (JMenuItem)eachElement;
          if (selection.equals(eachItem.getAction())) {
            final MenuSelectionManager mgr = MenuSelectionManager.defaultManager();
            final MenuElement[] path = new MenuElement[2];
            path[0] = popup;
            path[1] = eachItem;
            mgr.setSelectedPath(path);
            break;
          }
        }
      }
    });
  }

  public void closePopup() {
    myPopup.setVisible(false);
  }

  @Nullable
  public Action[] getOptions() {
    return myOptions;
  }

  public void setOptions(@Nullable Action[] options) {
    Action[] oldOptions = myOptions;
    myOptions = options;
    firePropertyChange(PROP_OPTIONS, oldOptions, myOptions);
  }

  public void updateOptions(@Nullable Action[] options) {
    closePopup();
    setOptions(options);
    applyOptions();

    repaint();
  }

  private void installShowPopupShortcut() {
    DumbAwareAction.create(e -> showPopup(null, true))
      .registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0)), this);
  }

  private void applyOptions() {
    myPopup = fillMenu();
  }

  public boolean isSimpleButton() {
    return myOptions == null || myOptions.length == 0;
  }

  private JPopupMenu fillMenu() {
    final JPopupMenu result = new JBPopupMenu();
    if (isSimpleButton()) {
      myOptionInfos.clear();
      return result;
    }

    for (Action each : myOptions) {
      if (getAction() == each) continue;
      final OptionInfo info = getMenuInfo(each);
      final JMenuItem eachItem = new JBMenuItem(each);

      configureItem(info, eachItem);
      result.add(eachItem);
    }

    return result;
  }

  private void configureItem(OptionInfo info, JMenuItem eachItem) {
    eachItem.setText(info.myPlainText);
    if (info.myMnemonic >= 0) {
      eachItem.setMnemonic(info.myMnemonic);
      eachItem.setDisplayedMnemonicIndex(info.myMnemonicIndex);
    }
    myOptionInfos.add(info);
  }

  public boolean isOkToProcessDefaultMnemonics() {
    return myOkToProcessDefaultMnemonics;
  }


  public static class OptionInfo {

    String myPlainText;
    int myMnemonic;
    int myMnemonicIndex;
    JBOptionButton myButton;
    Action myAction;

    OptionInfo(String plainText, int mnemonic, int mnemonicIndex, JBOptionButton button, Action action) {
      myPlainText = plainText;
      myMnemonic = mnemonic;
      myMnemonicIndex = mnemonicIndex;
      myButton = button;
      myAction = action;
    }

    public String getPlainText() {
      return myPlainText;
    }

    public int getMnemonic() {
      return myMnemonic;
    }

    public int getMnemonicIndex() {
      return myMnemonicIndex;
    }

    public JBOptionButton getButton() {
      return myButton;
    }

    public Action getAction() {
      return myAction;
    }
  }

  private OptionInfo getMenuInfo(Action each) {
    final String text = (String)each.getValue(Action.NAME);
    int mnemonic = -1;
    int mnemonicIndex = -1;
    StringBuilder plainText = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      if (ch == '&' || ch == '_') {
        if (i + 1 < text.length()) {
          final char mnemonicsChar = text.charAt(i + 1);
          mnemonic = Character.toUpperCase(mnemonicsChar);
          mnemonicIndex = i;          
        }
        continue;
      }
      plainText.append(ch);
    }

    return new OptionInfo(plainText.toString(), mnemonic, mnemonicIndex, this, each);

  }

  public Set<OptionInfo> getOptionInfos() {
    return myOptionInfos;
  }

  @Nullable
  public String getOptionTooltipText() {
    return myOptionTooltipText;
  }

  public void setOptionTooltipText(@Nullable String text) {
    String oldValue = myOptionTooltipText;
    myOptionTooltipText = text;
    firePropertyChange(PROP_OPTION_TOOLTIP, oldValue, myOptionTooltipText);
  }

  public void setOkToProcessDefaultMnemonics(boolean ok) {
    myOkToProcessDefaultMnemonics = ok;
  }
}
