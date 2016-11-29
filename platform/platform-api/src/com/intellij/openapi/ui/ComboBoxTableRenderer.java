/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.openapi.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
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
  private boolean myPaintArrow = true;

  protected EventListenerList myListenerList = new EventListenerList();

  private Runnable myFinalRunnable;

  public ComboBoxTableRenderer(final T[] values) {
    myValues = values;
    setFont(UIUtil.getButtonFont());
    setBorder(new EmptyBorder(0, 5, 0, 5));
  }

  @Override
  public Dimension getPreferredSize() {
    return addIconSize(super.getPreferredSize());
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  private static Dimension addIconSize(final Dimension d) {
    return new Dimension(d.width + AllIcons.General.ArrowDown.getIconWidth() + 2, Math.max(d.height, AllIcons.General.ArrowDown
      .getIconHeight()));
  }

  protected String getTextFor(@NotNull T value) {
    return value.toString();
  }

  protected Icon getIconFor(@NotNull T value) {
    return null;
  }

  public void setPaintArrow(final boolean paintArrow) {
    myPaintArrow = paintArrow;
  }

  protected Runnable onChosen(@NotNull final T value) {
    stopCellEditing(value);

    return () -> stopCellEditing(value);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    if (!StringUtil.isEmpty(getText()) && myPaintArrow) {
      final Rectangle r = getBounds();
      final Insets i = getInsets();
      final int x = r.width - i.right - AllIcons.General.ArrowDown.getIconWidth();
      final int y = i.top + (r.height - i.top - i.bottom - AllIcons.General.ArrowDown.getIconHeight()) / 2;
      AllIcons.General.ArrowDown.paintIcon(this, g, x, y);
    }
  }

  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    @SuppressWarnings("unchecked") final T t = (T)value;
    customizeComponent(t, table, isSelected);
    return this;
  }

  public Component getTableCellEditorComponent(JTable table, final Object value, boolean isSelected, final int row, final int column) {
    @SuppressWarnings("unchecked") final T t = (T)value;
    myValue = t;
    customizeComponent(t, table, true);

    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(() -> showPopup(t, row));

    return this;
  }

  protected boolean isApplicable(final T value, final int row) {
    return true;
  }

  private void showPopup(final T value, final int row) {
    List<T> filtered = ContainerUtil.findAll(myValues, t -> isApplicable(t, row));
    final ListPopup popup = JBPopupFactory.getInstance().createListPopup(new ListStep<T>(filtered, value) {
      @NotNull
      public String getTextFor(T value) {
        return ComboBoxTableRenderer.this.getTextFor(value);
      }

      @Override
      public Icon getIconFor(T value) {
        return ComboBoxTableRenderer.this.getIconFor(value);
      }

      public PopupStep onChosen(T selectedValue, boolean finalChoice) {
        myFinalRunnable = ComboBoxTableRenderer.this.onChosen(selectedValue);
        return FINAL_CHOICE;
      }

      public void canceled() {
        ComboBoxTableRenderer.this.cancelCellEditing();
      }

      public Runnable getFinalRunnable() {
        return myFinalRunnable;
      }
    });

    popup.addListener(this);
    popup.setRequestFocus(false);

    myPopupRef = new WeakReference<>(popup);
    popup.showUnderneathOf(this);
  }

  public void beforeShown(LightweightWindowEvent event) {
  }

  public void onClosed(LightweightWindowEvent event) {
    event.asPopup().removeListener(this);
    fireEditingCanceled();
  }

  protected void customizeComponent(final T value, final JTable table, final boolean isSelected) {
    setOpaque(true);
    setText(value == null ? "" : getTextFor(value));
    if (value != null) {
      setIcon(getIconFor(value));
    }
    setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
    setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
  }

  public Object getCellEditorValue() {
    return myValue;
  }

  public boolean isCellEditable(EventObject event) {
    if (event instanceof MouseEvent) {
      return ((MouseEvent)event).getClickCount() >= 2;
    }

    return true;
  }

  public boolean shouldSelectCell(EventObject event) {
    return true;
  }

  private void stopCellEditing(final T value) {
    myValue = value;
    stopCellEditing();
  }

  public boolean stopCellEditing() {
    fireEditingStopped();
    hidePopup();
    return true;
  }

  public void cancelCellEditing() {
    fireEditingCanceled();
    hidePopup();
  }

  protected void fireEditingStopped() {
    // Guaranteed to return a non-null array
    Object[] listeners = myListenerList.getListenerList();
    // Process the listeners last to first, notifying
    // those that are interested in this event
    for (int i = listeners.length - 2; i >= 0; i -= 2) {
      if (listeners[i] == CellEditorListener.class) {
        // Lazily create the event:
        if (myChangeEvent == null) {
          myChangeEvent = new ChangeEvent(this);
        }
        ((CellEditorListener)listeners[i + 1]).editingStopped(myChangeEvent);
      }
    }
  }

  protected void fireEditingCanceled() {
    // Guaranteed to return a non-null array
    Object[] listeners = myListenerList.getListenerList();
    // Process the listeners last to first, notifying
    // those that are interested in this event
    for (int i = listeners.length - 2; i >= 0; i -= 2) {
      if (listeners[i] == CellEditorListener.class) {
        // Lazily create the event:
        if (myChangeEvent == null) {
          myChangeEvent = new ChangeEvent(this);
        }
        ((CellEditorListener)listeners[i + 1]).editingCanceled(myChangeEvent);
      }
    }
  }

  private void hidePopup() {
    if (myPopupRef != null) {
      final ListPopup popup = myPopupRef.get();
      if (popup != null && popup.isVisible()) {
        popup.cancel();
      }

      myPopupRef = null;
    }
  }

  public void addCellEditorListener(CellEditorListener l) {
    myListenerList.add(CellEditorListener.class, l);
  }

  public void removeCellEditorListener(CellEditorListener l) {
    myListenerList.remove(CellEditorListener.class, l);
  }

  private abstract static class ListStep<T> implements ListPopupStep<T> {
    private final List<T> myValues;
    private final T mySelected;

    protected ListStep(List<T> values, T selected) {
      myValues = values;
      mySelected = selected;
    }

    public String getTitle() {
      return null;
    }

    public boolean hasSubstep(T selectedValue) {
      return false;
    }

    public boolean isMnemonicsNavigationEnabled() {
      return false;
    }

    public boolean isSpeedSearchEnabled() {
      return false;
    }

    public boolean isAutoSelectionEnabled() {
      return false;
    }

    @NotNull
    public List<T> getValues() {
      return myValues;
    }

    public boolean isSelectable(T value) {
      return true;
    }

    public Icon getIconFor(T aValue) {
      return null;
    }

    public ListSeparator getSeparatorAbove(T value) {
      return null;
    }

    public int getDefaultOptionIndex() {
      return mySelected == null ? 0 : myValues.indexOf(mySelected);
    }

    public MnemonicNavigationFilter<T> getMnemonicNavigationFilter() {
      return null;
    }

    public SpeedSearchFilter<T> getSpeedSearchFilter() {
      return null;
    }
  }
}
