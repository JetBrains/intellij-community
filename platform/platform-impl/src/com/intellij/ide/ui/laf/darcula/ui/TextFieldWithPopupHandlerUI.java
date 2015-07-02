/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.ui.laf.darcula.ui;

import javax.swing.*;
import javax.swing.plaf.basic.BasicTextFieldUI;
import java.awt.*;
import java.awt.event.*;

/**
 * @author Konstantin Bulenkov
 */
public abstract class TextFieldWithPopupHandlerUI extends BasicTextFieldUI {
  protected final JTextField myTextField;

  public TextFieldWithPopupHandlerUI(JTextField textField) {
    myTextField = textField;
    installListeners();
  }

  protected abstract SearchAction getActionUnder(MouseEvent e);

  protected abstract void showSearchPopup();

  protected void installListeners() {
    final TextFieldWithPopupHandlerUI ui = this;
    myTextField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        myTextField.repaint();
      }

      @Override
      public void focusLost(FocusEvent e) {
        myTextField.repaint();
      }
    });
    myTextField.addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        if (ui.getComponent() != null && isSearchField(myTextField)) {
          if (ui.getActionUnder(e) != null) {
            myTextField.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
          } else {
            myTextField.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
          }
        }
      }
    });
    myTextField.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (isSearchField(myTextField)) {
          final SearchAction action = ui.getActionUnder(e);
          if (action != null) {
            switch (action) {
              case POPUP:
                ui.showSearchPopup();
                break;
              case CLEAR:
                Object listener = myTextField.getClientProperty("JTextField.Search.CancelAction");
                if (listener instanceof ActionListener) {
                  ((ActionListener)listener).actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "action"));
                }
                myTextField.setText("");
                break;
            }
            e.consume();
          }
        }
      }
    });
  }

  public static boolean isSearchField(Component c) {
    return c instanceof JTextField && "search".equals(((JTextField)c).getClientProperty("JTextField.variant"));
  }

  public static boolean isSearchFieldWithHistoryPopup(Component c) {
    return isSearchField(c) && ((JTextField)c).getClientProperty("JTextField.Search.FindPopup") instanceof JPopupMenu;
  }

  public enum SearchAction {
    POPUP, CLEAR
  }
}
