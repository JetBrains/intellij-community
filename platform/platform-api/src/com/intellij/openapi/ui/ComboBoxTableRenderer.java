// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.EventListenerList;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.lang.ref.WeakReference;
import java.util.EventObject;
import java.util.List;

/**
 * @author spleaner
 */
public class ComboBoxTableRenderer<T> extends JLabel implements TableCellRenderer, TableCellEditor, JBPopupListener {
  private final T[] myValues;
  private WeakReference<ListPopup> myPopupRef;
  private ChangeEvent myChangeEvent = null;
  private T myValue;
  private int myClickCount = 2;

  protected EventListenerList myListenerList = new EventListenerList();

  private Runnable myFinalRunnable;

  public ComboBoxTableRenderer(T[] values) {
    myValues = values;
    setFont(UIUtil.getButtonFont());
    setBorder(JBUI.Borders.empty(0, 5));
  }

  public ComboBoxTableRenderer<T> withClickCount(int clickCount) {
    myClickCount = clickCount;
    return this;
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension size = addIconSize(super.getPreferredSize());

    if (myValues != null) {
      String oldText = getText();
      Icon oldIcon = getIcon();
      for (T v : myValues) {
        setText(getTextFor(v));
        setIcon(getIconFor(v));

        Dimension vSize = addIconSize(super.getPreferredSize());

        size.width = Math.max(size.width, vSize.width);
        size.height = Math.max(size.height, vSize.height);
      }
      setText(oldText);
      setIcon(oldIcon);
    }

    return size;
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  private static Dimension addIconSize(Dimension d) {
    return new Dimension(d.width + AllIcons.General.ArrowDown.getIconWidth() + JBUIScale.scale(2),
                         Math.max(d.height, AllIcons.General.ArrowDown.getIconHeight()));
  }

  protected @NlsContexts.Label String getTextFor(@NotNull T value) {
    //noinspection HardCodedStringLiteral (can't be fixed without upgrading the inspection or breaking clients)
    return value.toString();
  }

  protected Icon getIconFor(@NotNull T value) {
    return null;
  }

  protected ListSeparator getSeparatorAbove(T value) {
    return null;
  }

  protected Runnable onChosen(@NotNull T value) {
    stopCellEditing(value);

    return () -> stopCellEditing(value);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    if (!StringUtil.isEmpty(getText())) {
      Rectangle r = getBounds();
      Insets i = getInsets();
      int x = r.width - i.right - AllIcons.General.ArrowDown.getIconWidth();
      int y = i.top + (r.height - i.top - i.bottom - AllIcons.General.ArrowDown.getIconHeight()) / 2;
      AllIcons.General.ArrowDown.paintIcon(this, g, x, y);
    }
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    @SuppressWarnings("unchecked") T t = (T)value;
    customizeComponent(t, table, isSelected);
    return this;
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    @SuppressWarnings("unchecked") T t = (T)value;
    myValue = t;
    customizeComponent(t, table, true);

    SwingUtilities.invokeLater(() -> showPopup(t, row));

    return this;
  }

  protected boolean isApplicable(T value, int row) {
    return true;
  }

  private void showPopup(T value, int row) {
    List<T> filtered = ContainerUtil.findAll(myValues, t -> isApplicable(t, row));
    ListPopup popup = JBPopupFactory.getInstance().createListPopup(new ListStep<>(this, filtered, value));
    popup.addListener(this);
    popup.setRequestFocus(false);

    myPopupRef = new WeakReference<>(popup);
    popup.showUnderneathOf(this);
  }

  @Override
  public void onClosed(@NotNull LightweightWindowEvent event) {
    fireEditingCanceled();
  }

  protected void customizeComponent(T value, JTable table, boolean isSelected) {
    setOpaque(true);
    setText(value == null ? "" : getTextFor(value));
    setIcon(value == null ? null : getIconFor(value));
    setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
    setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
  }

  @Override
  public Object getCellEditorValue() {
    return myValue;
  }

  @Override
  public boolean isCellEditable(EventObject event) {
    return !(event instanceof MouseEvent) || ((MouseEvent)event).getClickCount() >= myClickCount;
  }

  @Override
  public boolean shouldSelectCell(EventObject event) {
    return true;
  }

  private void stopCellEditing(T value) {
    myValue = value;
    stopCellEditing();
  }

  @Override
  public boolean stopCellEditing() {
    fireEditingStopped();
    hidePopup();
    return true;
  }

  @Override
  public void cancelCellEditing() {
    fireEditingCanceled();
    hidePopup();
  }

  protected void fireEditingStopped() {
    fireEditingEvent(false);
  }

  protected void fireEditingCanceled() {
    fireEditingEvent(true);
  }

  protected void fireEditingEvent(boolean cancelled) {
    Object[] listeners = myListenerList.getListenerList();
    // process the listeners last to first, notifying those that are interested in this event
    for (int i = listeners.length - 2; i >= 0; i -= 2) {
      if (listeners[i] == CellEditorListener.class) {
        // Lazily create the event:
        if (myChangeEvent == null) {
          myChangeEvent = new ChangeEvent(this);
        }
        CellEditorListener listener = (CellEditorListener)listeners[i + 1];
        if (cancelled) {
          listener.editingCanceled(myChangeEvent);
        }
        else {
          listener.editingStopped(myChangeEvent);
        }
      }
    }
  }

  private void hidePopup() {
    if (myPopupRef != null) {
      ListPopup popup = myPopupRef.get();
      if (popup != null && popup.isVisible()) {
        popup.cancel();
      }

      myPopupRef = null;
    }
  }

  @Override
  public void addCellEditorListener(CellEditorListener l) {
    myListenerList.add(CellEditorListener.class, l);
  }

  @Override
  public void removeCellEditorListener(CellEditorListener l) {
    myListenerList.remove(CellEditorListener.class, l);
  }

  private static final class ListStep<T> implements ListPopupStep<T>, SpeedSearchFilter<T> {
    private final ComboBoxTableRenderer<T> myHost;
    private final List<T> myValues;
    private final T mySelected;

    ListStep(ComboBoxTableRenderer<T> host, List<T> values, T selected) {
      myHost = host;
      myValues = values;
      mySelected = selected;
    }

    @Override
    public String getTitle() {
      return null;
    }

    @Override
    public @Nullable PopupStep<?> onChosen(T selectedValue, boolean finalChoice) {
      myHost.myFinalRunnable = myHost.onChosen(selectedValue);
      return FINAL_CHOICE;
    }

    @Override
    public boolean hasSubstep(T selectedValue) {
      return false;
    }

    @Override
    public void canceled() {
      myHost.cancelCellEditing();
    }

    @Override
    public boolean isMnemonicsNavigationEnabled() {
      return false;
    }

    @Override
    public boolean isSpeedSearchEnabled() {
      return true;
    }

    @Override
    public boolean isAutoSelectionEnabled() {
      return false;
    }

    @Override
    public @Nullable Runnable getFinalRunnable() {
      return myHost.myFinalRunnable;
    }

    @Override
    public @NotNull List<T> getValues() {
      return myValues;
    }

    @Override
    public boolean isSelectable(T value) {
      return true;
    }

    @Override
    public Icon getIconFor(T value) {
      return myHost.getIconFor(value);
    }

    @Override
    public @NlsContexts.ListItem @NotNull String getTextFor(T value) {
      return myHost.getTextFor(value);
    }

    @Override
    public @Nullable ListSeparator getSeparatorAbove(T value) {
      return myHost.getSeparatorAbove(value);
    }

    @Override
    public int getDefaultOptionIndex() {
      return mySelected == null ? 0 : myValues.indexOf(mySelected);
    }

    @Override
    public MnemonicNavigationFilter<T> getMnemonicNavigationFilter() {
      return null;
    }

    @Override
    public SpeedSearchFilter<T> getSpeedSearchFilter() {
      return this;
    }

    @Override
    public String getIndexedString(T value) {
      return getTextFor(value);
    }
  }
}
