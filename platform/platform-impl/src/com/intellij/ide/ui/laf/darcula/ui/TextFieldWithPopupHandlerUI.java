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

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.basic.BasicTextFieldUI;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import java.awt.*;
import java.awt.event.*;

/**
 * @author Konstantin Bulenkov
 */
public abstract class TextFieldWithPopupHandlerUI extends BasicTextFieldUI implements Condition {
  protected final JTextField myTextField;
  private MyMouseMotionAdapter myMyMouseMotionAdapter;
  private MyMouseAdapter myMouseAdapter;
  private FocusAdapter myFocusAdapter;

  public TextFieldWithPopupHandlerUI(JTextField textField) {
    myTextField = textField;
    installListeners();
  }

  protected boolean hasText() {
    JTextComponent component = getComponent();
    return (component != null) && !StringUtil.isEmpty(component.getText());
  }

  protected abstract SearchAction getActionUnder(@NotNull Point p);

  protected abstract void showSearchPopup();

  protected void installListeners() {
    final TextFieldWithPopupHandlerUI ui = this;
    myFocusAdapter = new MyFocusAdapter();
    myTextField.addFocusListener(myFocusAdapter);
    myMyMouseMotionAdapter = new MyMouseMotionAdapter(ui);
    myTextField.addMouseMotionListener(myMyMouseMotionAdapter);
    myMouseAdapter = new MyMouseAdapter(ui);
    myTextField.addMouseListener(myMouseAdapter);
  }

  @Override
  protected void uninstallListeners() {
    myTextField.removeFocusListener(myFocusAdapter);
    myTextField.removeMouseMotionListener(myMyMouseMotionAdapter);
    myTextField.removeMouseListener(myMouseAdapter);
  }

  @Override
  public int getNextVisualPositionFrom(JTextComponent t, int pos, Position.Bias b, int direction, Position.Bias[] biasRet)
    throws BadLocationException {
    int position = DarculaUIUtil.getPatchedNextVisualPositionFrom(t, pos, direction);
    return position != -1 ? position : super.getNextVisualPositionFrom(t, pos, b, direction, biasRet);
  }

  @Override
  public boolean value(Object o) {
    if (o instanceof MouseEvent) {
      MouseEvent me = (MouseEvent)o;
      if (getActionUnder(me.getPoint()) != null) {
        if (me.getID() == MouseEvent.MOUSE_CLICKED) {
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(() -> myMouseAdapter.mouseClicked(me));
        }
        return true;
      }
    }
    return false;
  }

  public static boolean isSearchField(Component c) {
    return c instanceof JTextField && "search".equals(((JTextField)c).getClientProperty("JTextField.variant"));
  }

  public static boolean isSearchFieldWithHistoryPopup(Component c) {
    return isSearchField(c) && ((JTextField)c).getClientProperty("JTextField.Search.FindPopup") instanceof JPopupMenu;
  }

  @Nullable
  public static AbstractAction getNewLineAction(Component c) {
    if ( !isSearchField(c) || !Registry.is("ide.find.show.add.newline.hint")) return null;
    Object action = ((JTextField)c).getClientProperty("JTextField.Search.NewLineAction");
    return action instanceof AbstractAction ? (AbstractAction)action : null;
  }

  public enum SearchAction {
    POPUP, CLEAR, NEWLINE
  }

  private class MyMouseMotionAdapter extends MouseMotionAdapter {
    private final TextFieldWithPopupHandlerUI myUi;

    public MyMouseMotionAdapter(TextFieldWithPopupHandlerUI ui) {myUi = ui;}

    @Override
    public void mouseMoved(MouseEvent e) {
      if (myUi.getComponent() != null && isSearchField(myTextField)) {
        SearchAction action = myUi.getActionUnder(e.getPoint());
        if (action == SearchAction.POPUP && !isSearchFieldWithHistoryPopup(myTextField)) {
          action = null;
        }
        if (action != null) {
          myTextField.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        } else {
          myTextField.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        }
      }
    }
  }

  private class MyMouseAdapter extends MouseAdapter {
    private final TextFieldWithPopupHandlerUI myUi;

    public MyMouseAdapter(TextFieldWithPopupHandlerUI ui) {myUi = ui;}

    @Override
    public void mouseClicked(MouseEvent e) {
      if (isSearchField(myTextField)) {
        final SearchAction action = myUi.getActionUnder(e.getPoint());
        if (action != null) {
          switch (action) {
            case POPUP:
              myUi.showSearchPopup();
              break;
            case CLEAR:
              Object listener = myTextField.getClientProperty("JTextField.Search.CancelAction");
              if (listener instanceof ActionListener) {
                ((ActionListener)listener).actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "action"));
              }
              myTextField.setText("");
              break;
            case NEWLINE: {
              AbstractAction newLineAction = getNewLineAction(myTextField);
              if (newLineAction != null) {
                newLineAction.actionPerformed(new ActionEvent(myTextField, ActionEvent.ACTION_PERFORMED, "action"));
              }
              break;
            }
          }
          e.consume();
        }
      }
    }
  }

  private class MyFocusAdapter extends FocusAdapter {
    @Override
    public void focusGained(FocusEvent e) {
      myTextField.repaint();
    }

    @Override
    public void focusLost(FocusEvent e) {
      myTextField.repaint();
    }
  }
}
