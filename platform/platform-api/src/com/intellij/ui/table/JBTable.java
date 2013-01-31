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
package com.intellij.ui.table;

import com.intellij.Patches;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.components.JBViewport;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EventObject;

public class JBTable extends JTable implements ComponentWithEmptyText, ComponentWithExpandableItems<TableCell> {
  private StatusText myEmptyText;
  private ExpandableItemsHandler<TableCell> myExpandableItemsHandler;

  private MyCellEditorRemover myEditorRemover;
  private boolean myEnableAntialiasing;

  private int myRowHeight = -1;
  private boolean myRowHeightIsExplicitlySet;
  private boolean myRowHeightIsComputing;

  private Integer myMinRowHeight;
  private boolean myStriped;
  private boolean isTypeAhead = true;

  private AsyncProcessIcon myBusyIcon;
  private boolean myBusy;


  public JBTable() {
    this(new DefaultTableModel());
  }

  public JBTable(final TableModel model) {
    super(model);
    myEmptyText = new StatusText(this) {
      @Override
      protected boolean isStatusVisible() {
        return JBTable.this.isEmpty();
      }
    };

    myExpandableItemsHandler = ExpandableItemsHandlerFactory.install(this);

    setFillsViewportHeight(true);

    addMouseListener(new MyMouseListener());
    getColumnModel().addColumnModelListener(new TableColumnModelListener() {
      public void columnMarginChanged(ChangeEvent e) {
        if (cellEditor != null) {
          cellEditor.stopCellEditing();
        }
      }

      public void columnSelectionChanged(ListSelectionEvent e) {
      }

      public void columnAdded(TableColumnModelEvent e) {
      }

      public void columnMoved(TableColumnModelEvent e) {
      }

      public void columnRemoved(TableColumnModelEvent e) {
      }
    });

    final TableModelListener modelListener = new TableModelListener() {
      @Override
      public void tableChanged(final TableModelEvent e) {
        if (!myRowHeightIsExplicitlySet) {
          myRowHeight = -1;
        }
        if ((e.getType() == TableModelEvent.DELETE && isEmpty())
            || (e.getType() == TableModelEvent.INSERT && !isEmpty())) {
          repaintViewport();
        }
      }
    };

    if (getModel() != null) getModel().addTableModelListener(modelListener);
    addPropertyChangeListener("model", new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        repaintViewport();

        if (evt.getOldValue() instanceof TableModel) {
          ((TableModel)evt.getOldValue()).removeTableModelListener(modelListener);
        }
        if (evt.getNewValue() instanceof TableModel) {
          ((TableModel)evt.getNewValue()).addTableModelListener(modelListener);
        }
      }
    });


    //noinspection UnusedDeclaration
    boolean marker = Patches.SUN_BUG_ID_4503845; // Don't remove. It's a marker for find usages
  }

  @Override
  public int getRowHeight() {
    if (myRowHeightIsComputing) {
      return super.getRowHeight();
    }

    if (myRowHeight < 0) {
      try {
        myRowHeightIsComputing = true;
        for (int row = 0; row < getRowCount(); row++) {
          for (int column = 0; column < getColumnCount(); column++) {
            final TableCellRenderer renderer = getCellRenderer(row, column);
            if (renderer != null) {
              final Object value = getValueAt(row, column);
              final Component component = renderer.getTableCellRendererComponent(this, value, true, true, row, column);
              if (component != null) {
                final Dimension size = component.getPreferredSize();
                myRowHeight = Math.max(size.height, myRowHeight);
              }
            }
          }
        }
      }
      finally {
        myRowHeightIsComputing = false;
      }
    }

    if (myMinRowHeight == null) {
      myMinRowHeight = getFontMetrics(UIManager.getFont("Label.font")).getHeight();
    }

    return Math.max(myRowHeight, myMinRowHeight);
  }

  public void setShowColumns(boolean value) {
    JTableHeader tableHeader = getTableHeader();
    tableHeader.setVisible(value);
    tableHeader.setPreferredSize(value ? null : new Dimension());
  }

  @Override
  public void setRowHeight(int rowHeight) {
    myRowHeight = rowHeight;
    myRowHeightIsExplicitlySet = true;
  }

  @Override
  public void updateUI() {
    super.updateUI();
    myMinRowHeight = null;
  }

  private void repaintViewport() {
    if (!isDisplayable() || !isVisible()) return;

    Container p = getParent();
    if (p instanceof JBViewport) {
      p.repaint();
    }
  }

  @Override
  protected JTableHeader createDefaultTableHeader() {
    return new JTableHeader(columnModel) {
      @Override
      public void paint(Graphics g) {
        if (myEnableAntialiasing) {
          GraphicsUtil.setupAntialiasing(g);
        }
        super.paint(g);
      }

      @Override
      public String getToolTipText(final MouseEvent event) {
        final TableModel model = getModel();
        if (model instanceof SortableColumnModel) {
          final int i = columnAtPoint(event.getPoint());
          final int infoIndex = i >= 0 ? convertColumnIndexToModel(i) : -1;
          final ColumnInfo[] columnInfos = ((SortableColumnModel)model).getColumnInfos();
          final String tooltipText = infoIndex >= 0 && infoIndex < columnInfos.length ? columnInfos[infoIndex].getTooltipText() : null;
          if (tooltipText != null) {
            return tooltipText;
          }
        }
        return super.getToolTipText(event);
      }
    };
  }

  public boolean isEmpty() {
    return getRowCount() == 0;
  }

  @Override
  public void setModel(final TableModel model) {
    super.setModel(model);

    if (model instanceof SortableColumnModel) {
      final SortableColumnModel sortableModel = (SortableColumnModel)model;
      if (sortableModel.isSortable()) {
        final TableRowSorter<TableModel> rowSorter = createRowSorter(model);
        rowSorter.setSortsOnUpdates(isSortOnUpdates());
        setRowSorter(rowSorter);
        final RowSorter.SortKey sortKey = sortableModel.getDefaultSortKey();
        if (sortKey != null && sortKey.getColumn() >= 0 && sortKey.getColumn() < model.getColumnCount()) {
          if (sortableModel.getColumnInfos()[sortKey.getColumn()].isSortable()) {
            rowSorter.setSortKeys(Arrays.asList(sortKey));
          }
        }
      }
      else {
        final RowSorter<? extends TableModel> rowSorter = getRowSorter();
        if (rowSorter instanceof DefaultColumnInfoBasedRowSorter) {
          setRowSorter(null);
        }
      }
    }
  }

  protected boolean isSortOnUpdates() {
    return true;
  }

  @Override
  protected void paintComponent(Graphics g) {
    if (myEnableAntialiasing) {
      GraphicsUtil.setupAntialiasing(g);
    }
    super.paintComponent(g);
    myEmptyText.paint(this, g);
  }

  @Override
  protected void paintChildren(Graphics g) {
    if (myEnableAntialiasing) {
      GraphicsUtil.setupAntialiasing(g);
    }
    super.paintChildren(g);
  }

  public void setEnableAntialiasing(boolean flag) {
    myEnableAntialiasing = flag;
  }

  public static DefaultCellEditor createBooleanEditor() {
    return new DefaultCellEditor(new JCheckBox()) {
      {
        ((JCheckBox)getComponent()).setHorizontalAlignment(SwingConstants.CENTER);
      }

      @Override
      public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        Component component = super.getTableCellEditorComponent(table, value, isSelected, row, column);
        component.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
        return component;
      }
    };
  }

  public void resetDefaultFocusTraversalKeys() {
    KeyboardFocusManager m = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    for (Integer each : Arrays.asList(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,
                                      KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS,
                                      KeyboardFocusManager.UP_CYCLE_TRAVERSAL_KEYS,
                                      KeyboardFocusManager.DOWN_CYCLE_TRAVERSAL_KEYS)) {
      setFocusTraversalKeys(each, m.getDefaultFocusTraversalKeys(each));
    }
  }

  @NotNull
  @Override
  public StatusText getEmptyText() {
    return myEmptyText;
  }

  @Override
  @NotNull
  public ExpandableItemsHandler<TableCell> getExpandableItemsHandler() {
    return myExpandableItemsHandler;
  }

  @Override
  public void setExpandableItemsEnabled(boolean enabled) {
    myExpandableItemsHandler.setEnabled(enabled);
  }

  public void removeNotify() {
    if (ScreenUtil.isStandardAddRemoveNotify(this)) {
      final KeyboardFocusManager keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
      //noinspection HardCodedStringLiteral
      keyboardFocusManager.removePropertyChangeListener("permanentFocusOwner", myEditorRemover);
      //noinspection HardCodedStringLiteral
      keyboardFocusManager.removePropertyChangeListener("focusOwner", myEditorRemover);
      super.removeNotify();
      if (myBusyIcon != null) {
        remove(myBusyIcon);
        Disposer.dispose(myBusyIcon);
        myBusyIcon = null;
      }
    }
    else {
      super.removeNotify();
    }
  }

  @Override
  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    if (orientation == SwingConstants.VERTICAL) {
      return super.getScrollableUnitIncrement(visibleRect, orientation, direction);
    }
    else { // if orientation == SwingConstants.HORIZONTAL
      // use smooth editor-like scrolling
      return SwingUtilities.computeStringWidth(getFontMetrics(getFont()), " ");
    }
  }

  @Override
  public void doLayout() {
    super.doLayout();
    if (myBusyIcon != null) {
      myBusyIcon.updateLocation(this);
    }
  }

  @Override
  public void paint(Graphics g) {
    super.paint(g);
    if (myBusyIcon != null) {
      myBusyIcon.updateLocation(this);
    }
  }

  public void setPaintBusy(boolean paintBusy) {
    if (myBusy == paintBusy) return;

    myBusy = paintBusy;
    updateBusy();
  }

  private void updateBusy() {
    if (myBusy) {
      if (myBusyIcon == null) {
        myBusyIcon = new AsyncProcessIcon(toString()).setUseMask(false);
        myBusyIcon.setOpaque(false);
        myBusyIcon.setPaintPassiveIcon(false);
        add(myBusyIcon);
      }
    }

    if (myBusyIcon != null) {
      if (myBusy) {
        myBusyIcon.resume();
      }
      else {
        myBusyIcon.suspend();
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            if (myBusyIcon != null) {
              repaint();
            }
          }
        });
      }
      if (myBusyIcon != null) {
        myBusyIcon.updateLocation(this);
      }
    }
  }

  public boolean isStriped() {
    return myStriped;
  }

  public void setStriped(boolean striped) {
    myStriped = striped;
    if (striped) {
      getColumnModel().setColumnMargin(0);
      setIntercellSpacing(new Dimension(getIntercellSpacing().width, 0));
      setShowGrid(false);
    }
  }

  public boolean editCellAt(final int row, final int column, final EventObject e) {
    if (cellEditor != null && !cellEditor.stopCellEditing()) {
      return false;
    }

    if (row < 0 || row >= getRowCount() || column < 0 || column >= getColumnCount()) {
      return false;
    }

    if (!isCellEditable(row, column)) {
      return false;
    }

    if (e instanceof KeyEvent && UIUtil.isReallyTypedEvent((KeyEvent)e)) {
      SpeedSearchSupply supply = SpeedSearchSupply.getSupply(this);
      if (supply != null && supply.isPopupActive()) {
        return false;
      }
    }

    if (myEditorRemover == null) {
      final KeyboardFocusManager keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
      myEditorRemover = new MyCellEditorRemover();
      //noinspection HardCodedStringLiteral
      keyboardFocusManager.addPropertyChangeListener("focusOwner", myEditorRemover);
      //noinspection HardCodedStringLiteral
      keyboardFocusManager.addPropertyChangeListener("permanentFocusOwner", myEditorRemover);
    }

    final TableCellEditor editor = getCellEditor(row, column);
    if (editor != null && editor.isCellEditable(e)) {
      editorComp = prepareEditor(editor, row, column);
      //((JComponent)editorComp).setBorder(null);
      if (editorComp == null) {
        removeEditor();
        return false;
      }
      editorComp.setBounds(getCellRect(row, column, false));
      add(editorComp);
      editorComp.validate();

      IdeFocusManager.findInstanceByComponent(this).requestFocus(editorComp, false);

      setCellEditor(editor);
      setEditingRow(row);
      setEditingColumn(column);
      editor.addCellEditorListener(this);
      if (isTypeAhead) {
        JTableCellEditorHelper.typeAhead(this, e, row, column);
      }
      return true;
    }
    return false;
  }

  private static boolean isTableDecorationSupported() {
    return UIUtil.isUnderAlloyLookAndFeel()
           || UIUtil.isUnderNativeMacLookAndFeel()
           || UIUtil.isUnderDarcula()
           || UIUtil.isUnderNimbusLookAndFeel()
           || UIUtil.isUnderWindowsLookAndFeel();
  }

  public void disableTypeAheadInCellEditors() {
    isTypeAhead = false;
  }

  @Override
  public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
    Component result = super.prepareRenderer(renderer, row, column);

    // Fix GTK background
    if (UIUtil.isUnderGTKLookAndFeel()) {
      UIUtil.changeBackGround(this, UIUtil.getTreeTextBackground());
    }

    if (isTableDecorationSupported() && isStriped() && result instanceof JComponent) {
      final Color bg = row % 2 == 1 ? getBackground() : UIUtil.getDecoratedRowColor();
      final JComponent c = (JComponent)result;
      final boolean cellSelected = isCellSelected(row, column);
      if (!cellSelected) {
        c.setOpaque(true);
        c.setBackground(bg);
        for (Component child : c.getComponents()) {
          child.setBackground(bg);
        }
      }
    }

    if (myExpandableItemsHandler.getExpandedItems().contains(new TableCell(row, column))) {
      result = new ExpandedItemRendererComponentWrapper(result);
    }
    return result;
  }

  private final class MyCellEditorRemover implements PropertyChangeListener {
    private final IdeFocusManager myFocusManager;

    public MyCellEditorRemover() {
      myFocusManager = IdeFocusManager.findInstanceByComponent(JBTable.this);
    }

    public void propertyChange(final PropertyChangeEvent e) {
      if (!isEditing()) {
        return;
      }

      myFocusManager.doWhenFocusSettlesDown(new Runnable() {
        @Override
        public void run() {
          if (!isEditing()) {
            return;
          }
          Component c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
          while (c != null) {
            if (c instanceof JPopupMenu) {
              c = ((JPopupMenu)c).getInvoker();
            }
            if (c == JBTable.this) {
              // focus remains inside the table
              return;
            }
            else if (c instanceof Window) {
              if (c == SwingUtilities.getWindowAncestor(JBTable.this)) {
                getCellEditor().stopCellEditing();
              }
              break;
            }
            c = c.getParent();
          }
        }
      });
    }
  }

  private final class MyMouseListener extends MouseAdapter {
    public void mousePressed(final MouseEvent e) {
      if (SwingUtilities.isRightMouseButton(e)) {
        final int[] selectedRows = getSelectedRows();
        if (selectedRows.length < 2) {
          final int row = rowAtPoint(e.getPoint());
          if (row != -1) {
            getSelectionModel().setSelectionInterval(row, row);
          }
        }
      }
    }
  }

  @SuppressWarnings({"MethodMayBeStatic", "unchecked"})
  protected TableRowSorter<TableModel> createRowSorter(final TableModel model) {
    return new DefaultColumnInfoBasedRowSorter(model);
  }

  protected static class DefaultColumnInfoBasedRowSorter extends TableRowSorter<TableModel> {
    public DefaultColumnInfoBasedRowSorter(final TableModel model) {
      super(model);
      setModelWrapper(new TableRowSorterModelWrapper(model));
      setMaxSortKeys(1);
    }

    @Override
    public Comparator<?> getComparator(final int column) {
      final TableModel model = getModel();
      if (model instanceof SortableColumnModel) {
        final ColumnInfo[] columnInfos = ((SortableColumnModel)model).getColumnInfos();
        if (column >= 0 && column < columnInfos.length) {
          final Comparator comparator = columnInfos[column].getComparator();
          if (comparator != null) return comparator;
        }
      }

      return super.getComparator(column);
    }

    protected boolean useToString(int column) {
      return false;
    }

    @Override
    public boolean isSortable(final int column) {
      final TableModel model = getModel();
      if (model instanceof SortableColumnModel) {
        final ColumnInfo[] columnInfos = ((SortableColumnModel)model).getColumnInfos();
        if (column >= 0 && column < columnInfos.length) {
          return columnInfos[column].isSortable() && columnInfos[column].getComparator() != null;
        }
      }

      return false;
    }

    private class TableRowSorterModelWrapper extends ModelWrapper<TableModel, Integer> {
      private TableModel myModel;

      private TableRowSorterModelWrapper(@NotNull final TableModel model) {
        myModel = model;
      }

      public TableModel getModel() {
        return myModel;
      }

      public int getColumnCount() {
        return (myModel == null) ? 0 : myModel.getColumnCount();
      }

      public int getRowCount() {
        return (myModel == null) ? 0 : myModel.getRowCount();
      }

      public Object getValueAt(int row, int column) {
        if (myModel instanceof SortableColumnModel) {
          return ((SortableColumnModel)myModel).getRowValue(row);
        }

        return myModel.getValueAt(row, column);
      }

      public String getStringValueAt(int row, int column) {
        TableStringConverter converter = getStringConverter();
        if (converter != null) {
          // Use the converter
          String value = converter.toString(
            myModel, row, column);
          if (value != null) {
            return value;
          }
          return "";
        }

        // No converter, use getValueAt followed by toString
        Object o = getValueAt(row, column);
        if (o == null) {
          return "";
        }
        String string = o.toString();
        if (string == null) {
          return "";
        }
        return string;
      }

      public Integer getIdentifier(int index) {
        return index;
      }
    }
  }
}

