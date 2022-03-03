// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.ui.components.fields.ExtendableTextComponent.Extension;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicTextFieldUI;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.LinkedHashMap;
import java.util.Objects;

import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.MINIMUM_WIDTH;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.util.FontUtil.disableKerning;

/**
 * @author Konstantin Bulenkov
 */
public abstract class TextFieldWithPopupHandlerUI extends BasicTextFieldUI implements Condition {
  @NonNls private static final String DOCUMENT = "document";
  @NonNls private static final String MONOSPACED = "monospaced";
  @NonNls private static final String VARIANT = "JTextField.variant";
  @NonNls private static final String INPLACE_HISTORY = "JTextField.Search.InplaceHistory";
  @NonNls private static final String ON_CLEAR = "JTextField.Search.CancelAction";
  @NonNls private static final String HISTORY_POPUP_ENABLED = "History.Popup.Enabled";

  protected final LinkedHashMap<String, IconHolder> icons = new LinkedHashMap<>();
  private final Handler handler = new Handler();
  private boolean monospaced;
  private Object variant;
  private int cursor;

  public TextFieldWithPopupHandlerUI() {}

  /**
   * @return a search icon in one of the four states or {@code null} to hide it
   */
  protected Icon getSearchIcon(boolean hovered, boolean clickable) {
    return clickable ? AllIcons.Actions.SearchWithHistory : AllIcons.Actions.Search;
  }

  /**
   * @return a preferred space to paint the search icon
   */
  protected int getSearchIconPreferredSpace() {
    Icon icon = getSearchIcon(true, isSearchFieldWithHistoryPopup(this.getComponent()));
    return icon == null ? 0 : icon.getIconWidth() + getSearchIconGap();
  }

  /**
   * @return a gap between the search icon and the editable area
   */
  protected int getSearchIconGap() {
    return JBUIScale.scale(2);
  }

  /**
   * @return a clear icon in one of the four states or {@code null} to hide it
   */
  protected Icon getClearIcon(boolean hovered, boolean clickable) {
    return !clickable ? null : hovered ? AllIcons.Actions.CloseHovered : AllIcons.Actions.Close;
  }

  /**
   * @return a preferred space to paint the clear icon
   */
  protected int getClearIconPreferredSpace() {
    Icon icon = getClearIcon(true, true);
    return icon == null ? 0 : icon.getIconWidth() + getClearIconGap();
  }

  /**
   * @return a gap between the clear icon and the editable area
   */
  protected int getClearIconGap() {
    return JBUIScale.scale(2);
  }

  /**
   * @return {@code true} if component exists and contains non-empty string
   */
  protected boolean hasText() {
    JTextComponent component = getComponent();
    return (component != null) && !isEmpty(component.getText());
  }

  private void updateIconsLayout(Rectangle bounds) {
    JTextComponent c = getComponent();
    Insets margin = ComponentUtil.getParentOfType((Class<? extends JComboBox>)JComboBox.class, (Component)c) != null ||
                    ComponentUtil.getParentOfType((Class<? extends JSpinner>)JSpinner.class, (Component)c) != null ||
                    UIUtil.isClientPropertyTrue(c, "TextFieldWithoutMargins") ? JBInsets.emptyInsets() : getDefaultMargins();

    JBInsets.removeFrom(bounds, c.getInsets());
    JBInsets.removeFrom(bounds, margin);

    for (IconHolder holder : icons.values()) {
      int gap = holder.extension.getIconGap();
      if (holder.extension.isIconBeforeText()) {
        int offset = holder.extension.getAfterIconOffset();
        holder.bounds.x = bounds.x;

        int extensionWidth = holder.bounds.width + gap + offset;
        bounds.x += extensionWidth;
        bounds.width -= extensionWidth;
        margin.left += extensionWidth;
      }
      else {
        holder.bounds.x = bounds.x + bounds.width - holder.bounds.width;

        int extensionWidth = holder.bounds.width + gap;
        bounds.width -= extensionWidth;
        margin.right += extensionWidth;
      }
      int top = (bounds.height - holder.bounds.height) / 2;
      if (top > gap) {
        JTextComponent component = getComponent();
        boolean multiline = component != null && !Boolean.TRUE.equals(component.getDocument().getProperty("filterNewlines"));
        if (multiline) top = gap; // do not center icon for multiline text fields
      }
      holder.bounds.y = bounds.y + top;
    }

    c.setMargin(margin);
  }

  @SuppressWarnings("unused")
  protected SearchAction getActionUnder(@NotNull Point p) {
    return null;
  }

  protected void showSearchPopup() {
  }

  @Override
  protected void installDefaults() {
    super.installDefaults();
    if (SystemInfo.isMacOSCatalina) {
      JTextComponent component = getComponent();
      component.setFont(disableKerning(component.getFont()));
    }
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
    component.addFocusListener(handler);
    setVariant(component.getClientProperty(VARIANT));
    setMonospaced(component.getClientProperty(MONOSPACED));
  }

  /**
   * Removes all installed listeners from the current text component.
   */
  @Override
  protected void uninstallListeners() {
    JTextComponent component = getComponent();
    component.removeFocusListener(handler);
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
  protected Caret createCaret() {
    return new MouseDragAwareCaret();
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
    if(!(c instanceof JTextField)){
      return false;
    }
    var variant = ((JTextField)c).getClientProperty(VARIANT);
    return "search".equals(variant) || "searchWithJbPopup".equals(variant);
  }

  @Nullable
  public static AbstractAction getNewLineAction(Component c) {
    if (!isSearchField(c)) return null;
    Object action = ((JTextField)c).getClientProperty("JTextField.Search.NewLineAction");
    return action instanceof AbstractAction ? (AbstractAction)action : null;
  }

  public enum SearchAction {
    CLEAR, NEWLINE
  }

  @TestOnly
  @NotNull
  public Point getExtensionIconLocation(@NotNull final String extensionName) {
    final IconHolder iconHolder = icons.get(extensionName);
    if (iconHolder == null) {
      throw new IllegalArgumentException("The " + extensionName + " extension does not exist in this text field");
    }
    return iconHolder.bounds.getLocation();
  }

  @NotNull
  public Rectangle getExtensionIconBounds(@NotNull Extension extension) {
    for (IconHolder holder : icons.values()) {
      if (holder.extension == extension) {
        return new Rectangle(holder.bounds);
      }
    }
    throw new IllegalArgumentException("The " + extension + " extension does not exist in this text field");
  }

  /**
   * Default handler for mouse moved, mouse clicked, property changed and document modified.
   */
  private final class Handler extends MouseAdapter implements DocumentListener, FocusListener, PropertyChangeListener {
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
      else if (MONOSPACED.equals(event.getPropertyName())) {
        setMonospaced(event.getNewValue());
      }
      else if (VARIANT.equals(event.getPropertyName())) {
        setVariant(event.getNewValue());
      }
    }

    @Override
    public void focusGained(FocusEvent event) {
      repaint(false);
    }

    @Override
    public void focusLost(FocusEvent event) {
      repaint(false);
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
      if (!icons.isEmpty()) {
        for (IconHolder holder : icons.values()) {
          updateIcon(holder);
        }
      }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      if (!icons.isEmpty()) {
        handleMouse(e, false);
      }
      else if (getComponent() != null && isSearchField(getComponent())) {
        SearchAction action = getActionUnder(e.getPoint());
        setCursor(action != null ? Cursor.HAND_CURSOR : Cursor.TEXT_CURSOR);
      }
    }

    @Override
    public void mouseExited(MouseEvent e) {
      if (!icons.isEmpty()) {
        handleMouse(e, false);
      }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      if (!icons.isEmpty()) {
        handleMouse(e, true);
      }
      else if (isSearchField(getComponent())) {
        final SearchAction action = getActionUnder(e.getPoint());
        if (action != null) {
          switch (action) {
            case CLEAR:
              Object listener = getComponent().getClientProperty(ON_CLEAR);
              if (listener instanceof ActionListener) {
                ((ActionListener)listener).actionPerformed(new ActionEvent(getComponent(), ActionEvent.ACTION_PERFORMED, "action"));
              }
              getComponent().setText("");
              break;
            case NEWLINE: {
              AbstractAction newLineAction = getNewLineAction(getComponent());
              if (newLineAction != null) {
                newLineAction.actionPerformed(new ActionEvent(getComponent(), ActionEvent.ACTION_PERFORMED, "action"));
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
    if (!icons.isEmpty() && component != null && component.isEnabled()) {
      for (IconHolder holder : icons.values()) {
        if (holder.bounds.contains(point)) {
          return holder.extension.getTooltip();
        }
      }
    }
    return super.getToolTipText(component, point);
  }

  @Override
  public Dimension getMinimumSize(JComponent c) {
    Dimension size = super.getMinimumSize(c);
    if (size != null) updatePreferredSize(c, size);
    return size;
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    Dimension size = super.getPreferredSize(c);
    if (size != null) {

      JTextComponent component = getComponent();
      int columns = component instanceof JTextField ? ((JTextField)component).getColumns() : 0;
      if (columns != 0) {
        Insets insets = component.getInsets();

        FontMetrics fm = component.getFontMetrics(component.getFont());
        size.width = columns * fm.charWidth('m') + insets.left + insets.right;
      }

      updatePreferredSize(component, size);
    }

    return size;
  }

  protected void updatePreferredSize(JComponent c, Dimension size) {
    if (!isUnderComboBox(c)) {
      JBInsets.addTo(size, getDefaultMargins());
      size.width += icons.values().stream().mapToInt(h -> h.extension.getPreferredSpace()).sum();

      size.height = Math.max(size.height, getMinimumHeight(size.height));
      size.width = Math.max(size.width, MINIMUM_WIDTH.get());
    }
  }

  private static boolean isUnderComboBox(JComponent c) {
    Component parent = c.getParent();
    return parent instanceof JComboBox || (parent != null && parent.getParent() instanceof JComboBox);
  }

  protected int getMinimumHeight(int textHeight) {
    return 0;
  }

  protected Insets getDefaultMargins() {
    return JBInsets.emptyInsets();
  }

  @Override
  protected Rectangle getVisibleEditorRect() {
    JTextComponent c = getComponent();
    Rectangle bounds = new Rectangle(c.getSize());
    updateIconsLayout(bounds);
    return bounds;
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

  @Override
  public int viewToModel(JTextComponent tc, Point pt, Position.Bias[] biasReturn) {
    return icons.values().stream().anyMatch(p -> p.bounds.contains(pt)) ? -1 : super.viewToModel(tc, pt, biasReturn);
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
      Icon icon = holder.extension.getIcon(holder.hovered);
      if (holder.icon != icon) repaint(holder.setIcon(icon));
    }
  }

  private void handleMouse(MouseEvent event, boolean run) {
    JTextComponent component = getComponent();
    if (component != null) {
      IconHolder result = getIconHolder(component, event.getX(), event.getY());
      Runnable action = result == null ? null : result.extension.getActionOnClick();
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

  @Nullable
  private IconHolder getIconHolder(@NotNull JTextComponent component, int x, int y) {
    boolean invalid = false;
    boolean repaint = false;
    IconHolder result = null;
    for (IconHolder holder : icons.values()) {
      holder.hovered = component.isEnabled() && holder.bounds.contains(x, y);
      if (holder.hovered) result = holder;
      Icon icon = holder.extension.getIcon(holder.hovered);
      if (holder.icon != icon) {
        if (holder.setIcon(icon)) invalid = true;
        repaint = true;
      }
    }
    if (repaint) repaint(invalid);
    return result;
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
      if (ExtendableTextComponent.VARIANT.equals(variant)) {
        JTextComponent component = getComponent();
        if (component instanceof ExtendableTextComponent) {
          ExtendableTextComponent field = (ExtendableTextComponent)component;
          for (Extension extension : field.getExtensions()) {
            if (extension != null) addExtension(extension);
          }
        }
      }
      else if ("search".equals(variant)) {
        Object extension = getComponent().getClientProperty("search.extension");
        if (extension instanceof Extension) {
          addExtension((Extension)extension);
        }
        addExtension(new SearchExtension());
        addExtension(new ClearExtension());
      }
    }
    updateIconsLayout(new Rectangle(getComponent().getSize())); // Effectively update margins
  }

  protected void addExtension(Extension extension) {
    icons.put(extension.toString(), new IconHolder(extension));
  }

  private void setMonospaced(Object value) {
    boolean monospaced = Boolean.TRUE.equals(value);
    if (this.monospaced != monospaced) {
      this.monospaced = monospaced;
      JTextComponent component = getComponent();
      if (component != null) {
        Font font = component.getFont();
        if (font == null || font instanceof UIResource) {
          font = UIManager.getFont(getPropertyPrefix() + ".font");
          if (font == null) font = UIManager.getFont("TextField.font");
          if (font == null) font = UIManager.getFont("Label.font");
          component.setFont(!monospaced
                            ? !SystemInfo.isMacOSCatalina ? font : disableKerning(font)
                            : EditorUtil.getEditorFont(font.getSize()));
        }
      }
    }
  }


  public static final class IconHolder {
    public final Rectangle bounds = new Rectangle();
    public final Extension extension;

    public boolean hovered;
    public Icon icon;

    private IconHolder(Extension extension) {
      this.extension = extension;
      setIcon(extension.getIcon(false));
    }

    private boolean setIcon(Icon icon) {
      this.icon = icon;
      int width = icon == null ? 0 : icon.getIconWidth();
      int height = icon == null ? 0 : icon.getIconHeight();
      if (bounds.width == width && bounds.height == height) return false;
      bounds.width = width;
      bounds.height = height;
      return true;
    }

    public boolean isClickable() {
      return null != extension.getActionOnClick();
    }
  }


  private final class SearchExtension implements Extension {

    @Override
    public Icon getIcon(boolean hovered) {
      return getSearchIcon(hovered, isSearchFieldWithHistoryPopup(TextFieldWithPopupHandlerUI.this.getComponent()));
    }

    @Override
    public int getAfterIconOffset() {
      Integer gap = (Integer)getComponent().getClientProperty("JTextField.Search.Gap");
      return gap == null ? 0 : gap;
    }

    @Override
    public int getIconGap() {
      return getSearchIconGap();
    }

    @Override
    public boolean isIconBeforeText() {
      return true;
    }

    @Override
    public String getTooltip() {
      String prefix = null;
      if (UIUtil.getClientProperty(getComponent(), INPLACE_HISTORY) != null) prefix = IdeBundle.message("tooltip.recent.search");
      if (getActionOnClick() != null) prefix = IdeBundle.message("tooltip.search.history");
      return (prefix == null) ? null : prefix + " (" + KeymapUtil.getFirstKeyboardShortcutText("ShowSearchHistory") + ")";
    }

    @Override
    public String toString() {
      return "search";
    }
  }

  private class ClearExtension implements Extension {
    @Override
    public Icon getIcon(boolean hovered) {
      return getClearIcon(hovered, hasText());
    }

    @Override
    public int getPreferredSpace() {
      Icon icon = getClearIcon(false, true);
      return icon != null ? getIconGap() + icon.getIconWidth() : 0;
    }

    @Override
    public int getIconGap() {
      return getClearIconGap();
    }

    @Override
    public Runnable getActionOnClick() {
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

    @Override
    public String toString() {
      return "clear";
    }
  }

  public static class MarginAwareCaret extends BasicCaret {
    @Override
    protected void adjustVisibility(Rectangle r) {
      Insets m = getComponent().getMargin();
      r.x -= m.left;
      r.width += m.left;
      super.adjustVisibility(r);
    }
  }

  static class MouseDragAwareCaret extends MarginAwareCaret {
    @Override
    public void mouseDragged(MouseEvent e) {
      if (e.getID() == MouseEvent.MOUSE_DRAGGED && !isMultiline(getComponent())) {
        boolean consumed = e.isConsumed();
        e = new MouseEvent(e.getComponent(), e.getID(), e.getWhen(), e.getModifiers() | e.getModifiersEx(), e.getX(),
                           getComponent().getHeight() / 2, e.getClickCount(), e.isPopupTrigger(), e.getButton());
        if (consumed) e.consume();
      }
      super.mouseDragged(e);
    }

    public boolean isMultiline(JTextComponent component) {
      return component.getText().contains("\n")
             || (component instanceof JTextArea && ((JTextArea) component).getLineWrap());
    }
  }

  public static boolean isSearchFieldWithHistoryPopup(Component c) {
    if(c instanceof JComponent) {
      var historyPopupEnabled = ((JComponent)c).getClientProperty(HISTORY_POPUP_ENABLED);
      var searchPopupDisabled = historyPopupEnabled != null && historyPopupEnabled.equals(false);
      return isSearchField(c) && !searchPopupDisabled;
    }
    return false;
  }
}
