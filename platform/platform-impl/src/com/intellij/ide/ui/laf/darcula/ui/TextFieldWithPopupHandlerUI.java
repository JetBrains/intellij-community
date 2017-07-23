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
import com.intellij.ide.ui.laf.intellij.MacIntelliJTextFieldUI;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.Expandable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicTextFieldUI;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Objects;

import static com.intellij.openapi.keymap.KeymapUtil.getFirstKeyboardShortcutText;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.util.ui.JBUI.scale;

/**
 * @author Konstantin Bulenkov
 * @author Sergey Malenkov
 */
public abstract class TextFieldWithPopupHandlerUI extends BasicTextFieldUI implements Condition {
  private static final String DOCUMENT = "document";
  private static final String VARIANT = "JTextField.variant";
  private static final String POPUP = "JTextField.Search.FindPopup";
  private static final String ON_CLEAR = "JTextField.Search.CancelAction";
  protected final HashMap<String, IconHolder> icons = new HashMap<>();
  protected final JTextField myTextField;
  private final Handler handler = new Handler();
  private Object variant;
  private int iconsWidth;
  private int cursor;

  public TextFieldWithPopupHandlerUI(JTextField textField) {
    myTextField = textField;
  }

  /**
   * @return a gap between icons and the editable area.
   */
  protected int getIconGap() {
    return scale(2);
  }

  /**
   * @return a search icon in one of the four states or {@code null} to hide it
   */
  protected Icon getSearchIcon(boolean hovered, boolean clickable) {
    return AllIcons.Actions.Search;
  }

  /**
   * @return a clear icon in one of the four states or {@code null} to hide it
   */
  protected Icon getClearIcon(boolean hovered, boolean clickable) {
    return !clickable ? null : hovered ? AllIcons.Actions.Clean : AllIcons.Actions.CleanLight;
  }

  /**
   * @return an expand icon in one of the two states or {@code null} to hide it
   */
  protected Icon getExpandIcon(boolean hovered) {
    return hovered ? AllIcons.General.ExpandComponentHover : AllIcons.General.ExpandComponent;
  }

  /**
   * @return {@code true} if component exists and contains non-empty string
   */
  protected boolean hasText() {
    JTextComponent component = getComponent();
    return (component != null) && !isEmpty(component.getText());
  }

  protected SearchAction getActionUnder(@NotNull Point p) {
    return null;
  }

  protected void showSearchPopup() {
  }

  /**
   * Adds listeners to the current text component and sets its variant.
   */
  @Override
  protected void installListeners() {
    JTextComponent component = getComponent();
    handler.installListener(component.getDocument());
    component.addPropertyChangeListener(handler);
    component.addMouseMotionListener(handler);
    component.addMouseListener(handler);
    setVariant(component.getClientProperty(VARIANT));
  }

  /**
   * Removes all installed listeners from the current text component.
   */
  @Override
  protected void uninstallListeners() {
    JTextComponent component = getComponent();
    component.removeMouseListener(handler);
    component.removeMouseMotionListener(handler);
    component.removePropertyChangeListener(handler);
    handler.uninstallListener(component.getDocument());
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
          SwingUtilities.invokeLater(() -> handler.mouseClicked(me));
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

  /**
   * Default handler for mouse moved, mouse clicked, property changed and document modified.
   */
  private final class Handler extends MouseAdapter implements DocumentListener, PropertyChangeListener {
    /**
     * Starts listening changes in the specified document.
     */
    private void installListener(Document document) {
      if (document != null) document.addDocumentListener(this);
    }

    /**
     * Stops listening changes in the specified document.
     */
    private void uninstallListener(Document document) {
      if (document != null) document.removeDocumentListener(this);
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
      if (DOCUMENT.equals(event.getPropertyName())) {
        if (event.getOldValue() instanceof Document) uninstallListener((Document)event.getOldValue());
        if (event.getNewValue() instanceof Document) installListener((Document)event.getNewValue());
      }
      else if (VARIANT.equals(event.getPropertyName())) {
        setVariant(event.getNewValue());
      }
      else if (POPUP.equals(event.getPropertyName())) {
        updateIcon(icons.get("search"));
      }
    }

    @Override
    public void insertUpdate(DocumentEvent event) {
      changedUpdate(event);
    }

    @Override
    public void removeUpdate(DocumentEvent event) {
      changedUpdate(event);
    }

    @Override
    public void changedUpdate(DocumentEvent event) {
      updateIcon(icons.get("clear"));
    }

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

  @Override
  public String getToolTipText(JTextComponent component, Point point) {
    if (!icons.isEmpty()) {
      for (IconHolder holder : icons.values()) {
        if (holder.bounds.contains(point)) {
          return holder.getTooltip();
        }
      }
    }
    return super.getToolTipText(component, point);
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
    size.width += iconsWidth;
  }

  @Override
  protected Rectangle getVisibleEditorRect() {
    Rectangle bounds = super.getVisibleEditorRect();
    if (bounds != null) updateVisibleEditorRect(bounds);
    return bounds;
  }

  protected void updateVisibleEditorRect(Rectangle bounds) {
    if (icons.isEmpty()) return;
    int gap = getIconGap();
    IconHolder search = icons.get("search");
    if (search != null && search.icon != null) {
      search.bounds.x = bounds.x;
      search.bounds.y = bounds.y + (bounds.height - search.bounds.height) / 2;
      bounds.width -= search.bounds.width + gap;
      bounds.x += search.bounds.width + gap;
    }
    IconHolder expand = icons.get("expand");
    if (expand != null && expand.icon != null) {
      bounds.width -= expand.bounds.width;
      expand.bounds.x = bounds.x + bounds.width;
      expand.bounds.y = bounds.y + (bounds.height - expand.bounds.height) / 2;
      bounds.width -= gap;
    }
    IconHolder clear = icons.get("clear");
    if (clear != null && clear.icon != null) {
      bounds.width -= clear.bounds.width;
      clear.bounds.x = bounds.x + bounds.width;
      clear.bounds.y = bounds.y + (bounds.height - clear.bounds.height) / 2;
      bounds.width -= gap;
    }
  }

  /**
   * Always calls the {@link #paintBackground} method before painting the current text component,
   * and then paints visible icons if needed.
   *
   * @see #getVisibleEditorRect
   */
  @Override
  protected void paintSafely(Graphics g) {
    JTextComponent component = getComponent();
    if (!component.isOpaque()) paintBackground(g);
    Shape clip = g.getClip();
    super.paintSafely(g);
    if (!icons.isEmpty()) {
      g.setClip(clip);
      for (IconHolder holder : icons.values()) {
        if (holder.icon != null) {
          holder.icon.paintIcon(component, g, holder.bounds.x, holder.bounds.y);
        }
      }
    }
  }

  /**
   * Notifies a repaint manager to repaint the current text component later.
   *
   * @param invalid {@code true} if needed to revalidate before painting.
   */
  private void repaint(boolean invalid) {
    JTextComponent component = getComponent();
    if (component != null) {
      if (invalid) component.revalidate();
      component.repaint();
    }
  }

  private void updateIcon(IconHolder holder) {
    if (holder != null) {
      Icon icon = holder.getIcon();
      if (holder.icon != icon) repaint(holder.setIcon(icon));
    }
  }

  private void handleMouse(MouseEvent event, boolean run) {
    JTextComponent component = getComponent();
    if (component != null) {
      boolean invalid = false;
      boolean repaint = false;
      IconHolder result = null;
      for (IconHolder holder : icons.values()) {
        holder.hovered = holder.bounds.contains(event.getX(), event.getY());
        if (holder.hovered) result = holder;
        Icon icon = holder.getIcon();
        if (holder.icon != icon) {
          if (holder.setIcon(icon)) invalid = true;
          repaint = true;
        }
      }
      if (repaint) repaint(invalid);
      Runnable action = result == null ? null : result.getAction();
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
      JTextComponent component = getComponent();
      if (component != null) component.setCursor(Cursor.getPredefinedCursor(cursor));
    }
  }

  private void setVariant(Object variant) {
    if (!Objects.equals(this.variant, variant)) {
      this.variant = variant;

      icons.clear();
      iconsWidth = 0;
      if (Expandable.VARIANT.equals(variant)) {
        icons.put("expand", new ExpandIconHolder());
        iconsWidth = getPreferredWidth(getExpandIcon(true));
      }
      else if ("search".equals(variant) && this instanceof MacIntelliJTextFieldUI) {
        icons.put("clear", new ClearIconHolder());
        icons.put("search", new SearchIconHolder());
        iconsWidth = getPreferredWidth(getSearchIcon(true, true), getClearIcon(true, true));
      }
    }
  }

  /**
   * @return a preferred width of the all specified icons including a gap for every icon.
   */
  private int getPreferredWidth(Icon... icons) {
    int gap = getIconGap();
    int width = 0;
    for (Icon icon : icons) {
      if (icon != null) {
        width += gap + icon.getIconWidth();
      }
    }
    return width;
  }

  public static abstract class IconHolder {
    public final Rectangle bounds = new Rectangle();
    public boolean hovered;
    public Icon icon;

    private IconHolder() {
      //noinspection AbstractMethodCallInConstructor
      setIcon(getIcon());
    }

    public String getTooltip() {
      return null;
    }

    public abstract Runnable getAction();

    public abstract Icon getIcon();

    boolean setIcon(Icon icon) {
      this.icon = icon;
      int width = icon == null ? 0 : icon.getIconWidth();
      int height = icon == null ? 0 : icon.getIconHeight();
      if (bounds.width == width && bounds.height == height) return false;
      bounds.width = width;
      bounds.height = height;
      return true;
    }
  }


  private final class ExpandIconHolder extends IconHolder {
    @Override
    public String getTooltip() {
      String text = getFirstKeyboardShortcutText("ExpandExpandableComponent");
      return text.isEmpty() ? "Expand" : "Expand (" + text + ")";
    }

    @Override
    public Icon getIcon() {
      Expandable expandable = getExpandable();
      return expandable == null ? null : getExpandIcon(hovered);
    }

    @Override
    public Runnable getAction() {
      Expandable expandable = getExpandable();
      return expandable == null ? null : expandable::expand;
    }

    private Expandable getExpandable() {
      JTextComponent component = getComponent();
      return component instanceof Expandable ? (Expandable)component : null;
    }
  }


  private final class SearchIconHolder extends IconHolder {
    @Override
    public Icon getIcon() {
      return getSearchIcon(hovered, null != getAction());
    }

    @Override
    public Runnable getAction() {
      JTextComponent component = getComponent();
      Object property = component == null ? null : component.getClientProperty(POPUP);
      JPopupMenu popup = property instanceof JPopupMenu ? (JPopupMenu)property : null;
      return popup == null ? null : () -> {
        Rectangle editor = getVisibleEditorRect();
        if (editor != null) popup.show(component, bounds.x, editor.y + editor.height);
      };
    }
  }


  private final class ClearIconHolder extends IconHolder {
    @Override
    public Icon getIcon() {
      return getClearIcon(hovered, hasText());
    }

    @Override
    public Runnable getAction() {
      JTextComponent component = getComponent();
      return component == null ? null : () -> {
        component.setText(null);
        Object property = component.getClientProperty(ON_CLEAR);
        if (property instanceof ActionListener) {
          ActionListener listener = (ActionListener)property;
          listener.actionPerformed(new ActionEvent(component, ActionEvent.ACTION_PERFORMED, "clear"));
        }
      };
    }
  }
}
