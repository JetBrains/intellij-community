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

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.Expandable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.basic.BasicTextFieldUI;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;

import static com.intellij.openapi.keymap.KeymapUtil.getFirstKeyboardShortcutText;

/**
 * @author Konstantin Bulenkov
 */
public abstract class TextFieldWithPopupHandlerUI extends BasicTextFieldUI implements Condition {
  protected final HashMap<String, IconHolder> icons = new HashMap<>();
  protected final JTextField myTextField;
  private MyMouseAdapter myMouseAdapter;
  private FocusAdapter myFocusAdapter;
  private int cursor;

  public TextFieldWithPopupHandlerUI(JTextField textField) {
    myTextField = textField;
    installListeners();
    if (textField instanceof Expandable) {
      icons.put("expand", createExpand((Expandable)textField));
    }
  }

  protected boolean hasText() {
    JTextComponent component = getComponent();
    return (component != null) && !StringUtil.isEmpty(component.getText());
  }

  protected abstract SearchAction getActionUnder(@NotNull Point p);

  protected abstract void showSearchPopup();

  protected void installListeners() {
    myFocusAdapter = new MyFocusAdapter();
    myTextField.addFocusListener(myFocusAdapter);
    myMouseAdapter = new MyMouseAdapter();
    myTextField.addMouseListener(myMouseAdapter);
    myTextField.addMouseMotionListener(myMouseAdapter);
  }

  @Override
  protected void uninstallListeners() {
    myTextField.removeFocusListener(myFocusAdapter);
    myTextField.removeMouseListener(myMouseAdapter);
    myTextField.removeMouseMotionListener(myMouseAdapter);
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
    if (!isSearchField(c) || !Registry.is("ide.find.show.add.newline.hint")) return null;
    Object action = ((JTextField)c).getClientProperty("JTextField.Search.NewLineAction");
    return action instanceof AbstractAction ? (AbstractAction)action : null;
  }

  public enum SearchAction {
    POPUP, CLEAR, NEWLINE
  }

  private class MyMouseAdapter extends MouseAdapter {
    @Override
    public void mouseMoved(MouseEvent e) {
      if (!icons.isEmpty()) {
        handleMouse(e, false);
      }
      else if (getComponent() != null && isSearchField(myTextField)) {
        SearchAction action = getActionUnder(e.getPoint());
        if (action == SearchAction.POPUP && !isSearchFieldWithHistoryPopup(myTextField)) {
          action = null;
        }
        setCursor(action != null ? Cursor.HAND_CURSOR : Cursor.TEXT_CURSOR);
      }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      if (!icons.isEmpty()) {
        handleMouse(e, true);
      }
      else if (isSearchField(myTextField)) {
        final SearchAction action = getActionUnder(e.getPoint());
        if (action != null) {
          switch (action) {
            case POPUP:
              showSearchPopup();
              break;
            case CLEAR:
              Object listener = myTextField.getClientProperty("JTextField.Search.CancelAction");
              if (listener instanceof ActionListener) {
                ((ActionListener)listener).actionPerformed(new ActionEvent(myTextField, ActionEvent.ACTION_PERFORMED, "action"));
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

  @Override
  public Dimension getMinimumSize(JComponent c) {
    Dimension size = super.getMinimumSize(c);
    if (size != null) updatePreferredSize(size);
    return size;
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    Dimension size = super.getPreferredSize(c);
    if (size != null) updatePreferredSize(size);
    return size;
  }

  protected void updatePreferredSize(Dimension size) {
    if (icons.isEmpty()) return;
    Insets insets = myTextField.getInsets();
    for (IconHolder holder : icons.values()) {
      size.width += holder.bounds.width + 2; // gap between text and icons
      int height = holder.bounds.height;
      if (insets != null) height += insets.top + insets.bottom;
      if (height > size.height) size.height = height;
    }
  }

  @Override
  protected Rectangle getVisibleEditorRect() {
    Rectangle bounds = super.getVisibleEditorRect();
    if (bounds != null) updateVisibleEditorRect(bounds);
    return bounds;
  }

  protected void updateVisibleEditorRect(Rectangle bounds) {
    if (icons.isEmpty()) return;
    IconHolder expand = icons.get("expand");
    if (expand != null && expand.icon != null) {
      bounds.width -= expand.bounds.width;
      expand.bounds.x = bounds.x + bounds.width;
      expand.bounds.y = bounds.y + (bounds.height - expand.bounds.height) / 2;
      bounds.width -= 2; // gap between text and icons
    }
  }

  protected void paintIcons(Graphics g) {
    if (!icons.isEmpty()) {
      for (IconHolder holder : icons.values()) {
        if (holder.icon != null) {
          holder.icon.paintIcon(myTextField, g, holder.bounds.x, holder.bounds.y);
        }
      }
    }
  }

  private void handleMouse(MouseEvent event, boolean run) {
    boolean invalid = false;
    boolean repaint = false;
    IconHolder result = null;
    for (IconHolder holder : icons.values()) {
      boolean hovered = holder.bounds.contains(event.getX(), event.getY());
      if (hovered) result = holder;
      Icon icon = holder.getIcon(hovered);
      if (holder.icon != icon) {
        if (holder.setIcon(icon)) invalid = true;
        repaint = true;
      }
    }
    if (invalid) myTextField.revalidate();
    if (repaint) myTextField.repaint();
    if (result == null) {
      myTextField.setToolTipText(null);
      setCursor(Cursor.TEXT_CURSOR);
    }
    else {
      Runnable action = result.getAction();
      myTextField.setToolTipText(result.tooltip);
      if (action == null) {
        setCursor(Cursor.TEXT_CURSOR);
      }
      else {
        setCursor(Cursor.HAND_CURSOR);
        if (run) {
          action.run();
          event.consume();
        }
      }
    }
  }

  private void setCursor(int cursor) {
    if (this.cursor != cursor) {
      this.cursor = cursor; // do not update cursor every time
      myTextField.setCursor(Cursor.getPredefinedCursor(cursor));
    }
  }

  private static IconHolder createExpand(Expandable expandable) {
    String text = getFirstKeyboardShortcutText("ExpandExpandableComponent");
    return new IconHolder(text.isEmpty() ? "Expand" : "Expand (" + text + ")") {
      @Override
      protected Icon getIcon(boolean hovered) {
        return hovered ? AllIcons.General.ExpandComponentHover : AllIcons.General.ExpandComponent;
      }

      @Override
      protected Runnable getAction() {
        return expandable::expand;
      }
    };
  }

  public static abstract class IconHolder {
    public final Rectangle bounds = new Rectangle();
    public Icon icon;

    private final String tooltip;

    public IconHolder(String tooltip) {
      this.tooltip = tooltip;
      //noinspection AbstractMethodCallInConstructor
      setIcon(getIcon(false));
    }

    protected abstract Runnable getAction();

    protected abstract Icon getIcon(boolean hovered);

    private boolean setIcon(Icon icon) {
      this.icon = icon;
      int width = icon == null ? 0 : icon.getIconWidth();
      int height = icon == null ? 0 : icon.getIconHeight();
      if (bounds.width == width && bounds.height == height) return false;
      bounds.width = width;
      bounds.height = height;
      return true;
    }
  }
}
