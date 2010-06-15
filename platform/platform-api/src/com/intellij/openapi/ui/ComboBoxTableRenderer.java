/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.EventListenerList;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EventObject;
import java.util.List;

/**
 * @author spleaner
 */
public class ComboBoxTableRenderer<T> extends JLabel implements TableCellRenderer, TableCellEditor, JBPopupListener {
  private static final Icon ARROW_ICON = IconLoader.getIcon("/general/comboArrow.png");
  private T[] myValues;
  private WeakReference<ListPopup> myPopupRef;
  private ChangeEvent myChangeEvent = null;

  private T myValue;

  protected EventListenerList myListenerList = new EventListenerList();

  private Runnable myFinalRunnable;

  public ComboBoxTableRenderer(final T[] values) {
    myValues = values;
    setFont(UIUtil.getButtonFont());
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
    return new Dimension(d.width + ARROW_ICON.getIconWidth() + 2, Math.max(d.height, ARROW_ICON.getIconHeight()));
  }

  protected String getTextFor(@NotNull T value) {
    return value.toString();
  }



  protected Runnable onChosen(@NotNull final T value) {
    stopCellEditing(value);

    return new Runnable() {
      public void run() {
        stopCellEditing(value);
      }
    };
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    final Rectangle r = getBounds();
    final Insets i = getInsets();

    ARROW_ICON.paintIcon(this, g, r.width - i.right - ARROW_ICON.getIconWidth(), i.top);
  }

  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    if (value != null) customizeComponent((T) value, isSelected);
    return this;
  }

  public Component getTableCellEditorComponent(JTable table, final Object value, boolean isSelected, final int row, final int column) {
    myValue = (T) value;
    customizeComponent((T) value, isSelected);

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        showPopup((T) value);
      }
    });

    return this;
  }

  private void showPopup(final T value) {
    final ListPopup popup = JBPopupFactory.getInstance().createListPopup(new ListStep<T>(myValues, value) {
      @NotNull
      public String getTextFor(T value) {
        return ComboBoxTableRenderer.this.getTextFor(value);
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

    myPopupRef = new WeakReference<ListPopup>(popup);
    popup.showUnderneathOf(this);
  }

  public void beforeShown(LightweightWindowEvent event) {
  }

  public void onClosed(LightweightWindowEvent event) {
    event.asPopup().removeListener(this);
    fireEditingCanceled();
  }

  protected void customizeComponent(T value, boolean isSelected) {
    setOpaque(true);
    setText(getTextFor(value));
    setBackground(isSelected ? UIUtil.getTableSelectionBackground() : UIUtil.getTableBackground());
    setForeground(isSelected ? UIUtil.getTableSelectionForeground() : UIUtil.getTableForeground());
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
      for (int i = listeners.length-2; i>=0; i-=2) {
          if (listeners[i]==CellEditorListener.class) {
              // Lazily create the event:
              if (myChangeEvent == null)
                  myChangeEvent = new ChangeEvent(this);
              ((CellEditorListener)listeners[i+1]).editingStopped(myChangeEvent);
          }
      }
  }

  protected void fireEditingCanceled() {
      // Guaranteed to return a non-null array
      Object[] listeners = myListenerList.getListenerList();
      // Process the listeners last to first, notifying
      // those that are interested in this event
      for (int i = listeners.length-2; i>=0; i-=2) {
          if (listeners[i]==CellEditorListener.class) {
              // Lazily create the event:
              if (myChangeEvent == null)
                  myChangeEvent = new ChangeEvent(this);
              ((CellEditorListener)listeners[i+1]).editingCanceled(myChangeEvent);
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
    private List<T> myValues;
    private T mySelected;

    protected ListStep(final T[] values, final T selected) {
      myValues = new ArrayList<T>(Arrays.asList(values));
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
