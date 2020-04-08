// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.table;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ExpirableRunnable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.ui.treeStructure.treetable.TreeTableModel;
import com.intellij.util.ui.*;
import com.intellij.util.ui.accessibility.ScreenReader;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.MouseInputListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.plaf.basic.BasicTableHeaderUI;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EventObject;

import static com.intellij.ui.components.JBViewport.FORCE_VISIBLE_ROW_COUNT_KEY;

public class JBTable extends JTable implements ComponentWithEmptyText, ComponentWithExpandableItems<TableCell> {
  public static final int PREFERRED_SCROLLABLE_VIEWPORT_HEIGHT_IN_ROWS = 7;
  public static final int COLUMN_RESIZE_AREA_WIDTH = 3; // same as in BasicTableHeaderUI
  private static final int DEFAULT_MIN_COLUMN_WIDTH = 15; // see TableColumn constructor javadoc

  private final StatusText myEmptyText;
  private final ExpandableItemsHandler<TableCell> myExpandableItemsHandler;

  private boolean myEnableAntialiasing;

  private int myVisibleRowCount = 4;
  private int myRowHeight = -1;
  private boolean myRowHeightIsExplicitlySet;
  private boolean myRowHeightIsComputing;
  private boolean myUiUpdating = true;

  private Integer myMinRowHeight;
  private boolean myStriped;

  protected AsyncProcessIcon myBusyIcon;
  private boolean myBusy;

  private int myMaxItemsForSizeCalculation = Integer.MAX_VALUE;

  private TableCell rollOverCell;

  private final Color disabledForeground = JBColor.namedColor("Table.disabledForeground", JBColor.gray);

  public JBTable() {
    this(new DefaultTableModel());
  }

  public JBTable(TableModel model) {
    this(model, null);
  }

  public JBTable(final TableModel model, final TableColumnModel columnModel) {
    super(model, columnModel);
    // By default, tables only allow Ctrl-TAB/Ctrl-Shift-TAB to navigate to the
    // next/previous component, while TAB/Shift-TAB is used to navigate through
    // cells. This behavior is somewhat counter-intuitive, as visually impaired
    // users are used to use TAB to navigate between components. By resetting
    // the default traversal keys, we add TAB/Shift-TAB as focus navigation keys.
    // Notes:
    // * Navigating through cells can still be done using the cursor keys
    // * One could argue that resetting to the default behavior should be
    //   done in all case, i.e. not only when a screen reader is active,
    //   but we leave it as is to favor backward compatibility.
    if (ScreenReader.isActive()) {
      resetDefaultFocusTraversalKeys();
    }

    setSurrendersFocusOnKeystroke(true);

    myEmptyText = new StatusText(this) {
      @Override
      protected boolean isStatusVisible() {
        return isEmpty();
      }
    };

    myExpandableItemsHandler = ExpandableItemsHandlerFactory.install(this);

    setFillsViewportHeight(true);

    addMouseListener(new MyMouseListener());

    if (UIUtil.isUnderWin10LookAndFeel()) {
      addMouseMotionListener(new MouseMotionAdapter() {
        @Override
        public void mouseMoved(MouseEvent e) {
          Point point = e.getPoint();
          int column = columnAtPoint(point);
          int row = rowAtPoint(point);

          resetRollOverCell();

          if (row >= 0 && row < getRowCount() && column >= 0 && column < getColumnCount()) {
            TableCellRenderer cellRenderer = getCellRenderer(row, column);
            if (cellRenderer != null) {
              Component rc = cellRenderer.getTableCellRendererComponent(JBTable.this,
                                                                        getValueAt(row, column),
                                                                        isCellSelected(row, column),
                                                                        hasFocus(),
                                                                        row, column);
              if (rc instanceof JCheckBox && (rollOverCell == null || !rollOverCell.at(row, column))) {
                Rectangle cellRect = getCellRect(row, column, false);
                rollOverCell = new TableCell(row, column);
                ((JCheckBox)rc).putClientProperty(UIUtil.CHECKBOX_ROLLOVER_PROPERTY, cellRect);

                if (getModel() instanceof AbstractTableModel) {
                  ((AbstractTableModel)getModel()).fireTableCellUpdated(row, column);
                }
              }
            }
          }
        }
      });
    }

    final TableModelListener modelListener = new TableModelListener() {
      @Override
      public void tableChanged(@NotNull final TableModelEvent e) {
        onTableChanged(e);
      }
    };

    if (getModel() != null) getModel().addTableModelListener(modelListener);
    addPropertyChangeListener("model", new PropertyChangeListener() {
      @Override
      public void propertyChange(@NotNull PropertyChangeEvent evt) {
        UIUtil.repaintViewport(JBTable.this);

        if (evt.getOldValue() instanceof TableModel) {
          ((TableModel)evt.getOldValue()).removeTableModelListener(modelListener);
        }
        if (evt.getNewValue() instanceof TableModel) {
          ((TableModel)evt.getNewValue()).addTableModelListener(modelListener);
        }
      }
    });

    myUiUpdating = false;

    new MyCellEditorRemover();
  }

  protected void onTableChanged(@NotNull TableModelEvent e) {
    if (!myRowHeightIsExplicitlySet) {
      myRowHeight = -1;
    }
    if (e.getType() == TableModelEvent.DELETE && isEmpty() ||
        e.getType() == TableModelEvent.INSERT && !isEmpty() ||
        e.getType() == TableModelEvent.UPDATE) {
      UIUtil.repaintViewport(this);
    }
  }

  public int getVisibleRowCount() {
    return myVisibleRowCount;
  }

  public void setVisibleRowCount(int visibleRowCount) {
    int oldValue = myVisibleRowCount;
    myVisibleRowCount = Math.max(0, visibleRowCount);
    firePropertyChange("visibleRowCount", oldValue, visibleRowCount);
  }

  @Override
  public int getRowHeight() {
    int height = super.getRowHeight();
    if (myRowHeightIsComputing) {
      return height;
    }

    if (myRowHeight < 0) {
      try {
        myRowHeightIsComputing = true;
        myRowHeight = calculateRowHeight();
      }
      finally {
        myRowHeightIsComputing = false;
      }
    }

    if (myMinRowHeight == null) {
      myMinRowHeight = getFontMetrics(UIManager.getFont("Label.font")).getHeight();
    }

    return Math.max(myRowHeight, Math.max(myMinRowHeight, height));
  }

  protected int calculateRowHeight() {
    int result = -1;

    for (int row = 0; row < Math.min(getRowCount(), myMaxItemsForSizeCalculation); row++) {
      for (int column = 0; column < Math.min(getColumnCount(), myMaxItemsForSizeCalculation); column++) {
        final TableCellRenderer renderer = getCellRenderer(row, column);
        if (renderer != null) {
          final Object value = getValueAt(row, column);
          final Component component = renderer.getTableCellRendererComponent(this, value, true, true, row, column);
          if (component != null) {
            Dimension size = component.getPreferredSize();
            result = Math.max(size.height, result);
            if (component instanceof JLabel && StringUtil.isEmpty(((JLabel)component).getText())) {
              String oldText = ((JLabel)component).getText();
              try {
                //noinspection HardCodedStringLiteral
                ((JLabel)component).setText("Jj");
                size = component.getPreferredSize();
                result = Math.max(size.height, result);
              } finally {
                ((JLabel)component).setText(oldText);
              }
            }
          }
        }
      }
    }

    return result;
  }

  public void setShowColumns(boolean value) {
    JTableHeader tableHeader = getTableHeader();
    tableHeader.setVisible(value);
    tableHeader.setPreferredSize(value ? null : new Dimension());
  }

  @Override
  public void setRowHeight(int rowHeight) {
    if (!myUiUpdating) {
      myRowHeight = rowHeight;
      myRowHeightIsExplicitlySet = true;
    }
    // call super to clean rowModel
    super.setRowHeight(rowHeight);
  }

  @Override
  public void updateUI() {
    myUiUpdating = true;
    try {
      super.updateUI();
      myMinRowHeight = null;
    }
    finally {
      myUiUpdating = false;
    }
  }

  @NotNull
  @Override
  protected JTableHeader createDefaultTableHeader() {
    return new JBTableHeader();
  }

  @Override
  protected void initializeLocalVars() {
    super.initializeLocalVars();
    setPreferredScrollableViewportSize(null);
  }

  @Override
  public Dimension getPreferredScrollableViewportSize() {
    Dimension base = super.getPreferredScrollableViewportSize();
    int visibleRows = myVisibleRowCount;
    if (visibleRows <= 0) return base;
    if (base != null && base.height > 0) return base;

    boolean addExtraSpace = Registry.is("ide.preferred.scrollable.viewport.extra.space");

    TableModel model = getModel();
    int modelRows = model == null ? 0 : model.getRowCount();
    boolean forceVisibleRowCount = Boolean.TRUE.equals(UIUtil.getClientProperty(this, FORCE_VISIBLE_ROW_COUNT_KEY));
    if (!forceVisibleRowCount) {
      visibleRows = Math.min(modelRows, visibleRows);
    }
    int fixedWidth = base != null && base.width > 0 ? base.width : JBUI.scale(100);
    Dimension size;
    if (modelRows == 0) {
      int fixedHeight = Registry.intValue("ide.preferred.scrollable.viewport.fixed.height");
      if (fixedHeight <= 0) fixedHeight = UIManager.getInt("Table.rowHeight");
      if (fixedHeight <= 0) fixedHeight = JBUIScale.scale(16); // scaled value from JDK

      size = new Dimension(fixedWidth, fixedHeight * visibleRows);
      if (addExtraSpace) size.height += fixedHeight / 2;
    }
    else {
      Rectangle rect = getCellRect(Math.min(visibleRows, modelRows) - 1, 0, true);
      size = new Dimension(fixedWidth, rect.y + rect.height);
      if (modelRows < visibleRows) {
        size.height += (visibleRows - modelRows) * rect.height;
      }
      else if (modelRows > visibleRows) {
        if (addExtraSpace) size.height += rect.height / 2;
      }
    }
    return size;
  }

  public boolean isEmpty() {
    return getRowCount() == 0;
  }

  @Override
  public void setModel(@NotNull TableModel model) {
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
            rowSorter.setSortKeys(Collections.singletonList(sortKey));
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
  protected void paintComponent(@NotNull Graphics g) {
    if (myEnableAntialiasing) {
      GraphicsUtil.setupAntialiasing(g);
    }
    super.paintComponent(g);
    getEmptyText().paint(this, g);
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

  @Override
  public void removeNotify() {
    super.removeNotify();
    if (ScreenUtil.isStandardAddRemoveNotify(this)) {
      if (myBusyIcon != null) {
        remove(myBusyIcon);
        Disposer.dispose(myBusyIcon);
        myBusyIcon = null;
      }
    }
  }

  @Override
  public int getScrollableUnitIncrement(@NotNull Rectangle visibleRect, int orientation, int direction) {
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
  protected Graphics getComponentGraphics(Graphics graphics) {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics));
  }

  @Override
  public void paint(@NotNull Graphics g) {
    super.paint(g);
    if (myBusyIcon != null) {
      myBusyIcon.updateLocation(this);
    }
  }

  @Override
  public Color getForeground() {
    return isEnabled() ? super.getForeground() : disabledForeground;
  }

  //@Override
  //public Color getSelectionBackground() {
  //  return isEnabled() ? super.getSelectionBackground() : UIUtil.getTableSelectionBackground(false);
  //}

  public void setPaintBusy(boolean paintBusy) {
    if (myBusy == paintBusy) return;

    myBusy = paintBusy;
    updateBusy();
  }

  private void updateBusy() {
    if (myBusy) {
      if (myBusyIcon == null) {
        myBusyIcon = createBusyIcon();
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
        SwingUtilities.invokeLater(() -> {
          if (myBusyIcon != null) {
            repaint();
          }
        });
      }
      if (myBusyIcon != null) {
        myBusyIcon.updateLocation(this);
      }
    }
  }

  @NotNull
  protected AsyncProcessIcon createBusyIcon() {
    return new AsyncProcessIcon(toString());
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

  @Override
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

    if (e instanceof KeyEvent) {
      // do not start editing in autoStartsEdit mode on Ctrl-Z and other non-typed events
      if (!UIUtil.isReallyTypedEvent((KeyEvent)e)) return false;

      SpeedSearchSupply supply = SpeedSearchSupply.getSupply(this);
      if (supply != null && supply.isPopupActive()) {
        return false;
      }
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

      if (surrendersFocusOnKeyStroke()) {
        // this replaces focus request in JTable.processKeyBinding
        final IdeFocusManager focusManager = IdeFocusManager.findInstanceByComponent(this);
        focusManager.setTypeaheadEnabled(false);
        focusManager.requestFocus(editorComp, true).doWhenProcessed(() -> focusManager.setTypeaheadEnabled(true));
      }

      setCellEditor(editor);
      setEditingRow(row);
      setEditingColumn(column);
      editor.addCellEditorListener(this);

      return true;
    }
    return false;
  }

  /**
   * Always returns false.
   * If you're interested in value of JTable.surrendersFocusOnKeystroke property, call JBTable.surrendersFocusOnKeyStroke()
   *
   * @return false
   * @see #surrendersFocusOnKeyStroke
   */
  @Override
  public boolean getSurrendersFocusOnKeystroke() {
    return false; // prevents JTable.processKeyBinding from requesting editor component to be focused
  }

  public boolean surrendersFocusOnKeyStroke() {
    return super.getSurrendersFocusOnKeystroke();
  }

  private static boolean isTableDecorationSupported() {
    return UIUtil.isUnderNativeMacLookAndFeel()
           || StartupUiUtil.isUnderDarcula()
           || UIUtil.isUnderIntelliJLaF();
  }

  @NotNull
  @Override
  public Component prepareRenderer(@NotNull TableCellRenderer renderer, int row, int column) {
    Component result = super.prepareRenderer(renderer, row, column);

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
      result = ExpandedItemRendererComponentWrapper.wrap(result);
    }

    if (renderer instanceof JCheckBox) {
      ((JCheckBox)renderer).getModel().setRollover(rollOverCell != null && rollOverCell.at(row, column));
    }
    return result;
  }

  @Override
  protected void processMouseEvent(MouseEvent e) {
    MouseEvent e2 = e;

    if (SystemInfo.isMac) {
      e2 = MacUIUtil.fixMacContextMenuIssue(e);
    }

    super.processMouseEvent(e2);

    if (e != e2 && e2.isConsumed()) e.consume();
  }

  private final class MyCellEditorRemover implements PropertyChangeListener, Activatable {
    private boolean myIsActive = false;

    MyCellEditorRemover() {
      addPropertyChangeListener("tableCellEditor", this);
      new UiNotifyConnector(JBTable.this, this);
    }

    public void activate() {
      if (!myIsActive) {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("permanentFocusOwner", this);
      }
      myIsActive = true;
    }

    public void deactivate() {
      if (myIsActive) {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener("permanentFocusOwner", this);
      }
      myIsActive = false;
    }

    @Override
    public void hideNotify() {
      removeCellEditor();
    }

    @Override
    public void propertyChange(@NotNull final PropertyChangeEvent e) {
      if ("tableCellEditor".equals(e.getPropertyName())) {
        tableCellEditorChanged(e.getOldValue(), e.getNewValue());
      }
      else if ("permanentFocusOwner".equals(e.getPropertyName())) {
        permanentFocusOwnerChanged();
      }
    }

    private void tableCellEditorChanged(Object from, Object to) {
      boolean editingStarted = from == null && to != null;
      boolean editingStopped = from != null && to == null;

      if (editingStarted) {
        activate();
      }
      else if (editingStopped) {
        deactivate();
      }
    }

    private void permanentFocusOwnerChanged() {
      if (!isEditing()) {
        return;
      }

      final IdeFocusManager focusManager = IdeFocusManager.findInstanceByComponent(JBTable.this);
      focusManager.doWhenFocusSettlesDown(new ExpirableRunnable() {
        @Override
        public boolean isExpired() {
          return !isEditing();
        }

        @Override
        public void run() {
          Component c = focusManager.getFocusOwner();
          if (UIUtil.isMeaninglessFocusOwner(c)) {
            // this allows using popup menus and menu bar without stopping cell editing
            return;
          }
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
                removeCellEditor();
              }
              break;
            }
            c = c.getParent();
          }
        }
      }, ModalityState.current());
    }

    private void removeCellEditor() {
      TableCellEditor cellEditor = getCellEditor();
      if (cellEditor != null && !cellEditor.stopCellEditing()) {
        cellEditor.cancelCellEditing();
      }
    }
  }

  private final class MyMouseListener extends MouseAdapter {
    @Override
    public void mousePressed(@NotNull final MouseEvent e) {
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

    @Override
    public void mouseExited(MouseEvent e) {
      if (e.getClickCount() == 0) {
        resetRollOverCell();
      }
    }
  }

  @SuppressWarnings({"unchecked"})
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

    @Override
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
      private final TableModel myModel;

      private TableRowSorterModelWrapper(@NotNull TableModel model) {
        myModel = model;
      }

      @Override
      public TableModel getModel() {
        return myModel;
      }

      @Override
      public int getColumnCount() {
        return myModel.getColumnCount();
      }

      @Override
      public int getRowCount() {
        return myModel.getRowCount();
      }

      @Override
      public Object getValueAt(int row, int column) {
        if (myModel instanceof SortableColumnModel) {
          return ((SortableColumnModel)myModel).getRowValue(row);
        }

        return myModel.getValueAt(row, column);
      }

      @NotNull
      @Override
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

      @Override
      public Integer getIdentifier(int index) {
        return index;
      }
    }
  }

  protected class JBTableHeader extends JTableHeader {

    private final Color disabledForeground = JBColor.namedColor("TableHeader.disabledForeground", JBColor.gray);

    public JBTableHeader() {
      super(JBTable.this.columnModel);
      JBTable.this.addPropertyChangeListener(new PropertyChangeListener() {
        @Override
        public void propertyChange(@NotNull PropertyChangeEvent evt) {
          if ("enabled".equals(evt.getPropertyName())) {
            JBTableHeader.this.repaint();
          }
        }
      });

      DefaultTableCellRenderer renderer = (DefaultTableCellRenderer)getDefaultRenderer();
      TableCellRenderer newRenderer = new TableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
          Component delegate = renderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          if (!(delegate instanceof JLabel)) return delegate;

          JLabel cmp = (JLabel)delegate;
          cmp.setHorizontalAlignment(SwingConstants.LEFT);
          Border border = cmp.getBorder();
          JBEmptyBorder indent = JBUI.Borders.emptyLeft(8);
          border = JBUI.Borders.merge(border, indent, true);
          cmp.setBorder(border);
          return cmp;
        }
      };
      setDefaultRenderer(newRenderer);
    }

    @Override
    public void paint(@NotNull Graphics g) {
      if (myEnableAntialiasing) {
        GraphicsUtil.setupAntialiasing(g);
      }
      super.paint(g);
    }

    @Override
    public Color getForeground() {
      return JBTable.this.isEnabled() ? super.getForeground() : disabledForeground;
    }

    @Override
    public String getToolTipText(@NotNull final MouseEvent event) {
      ColumnInfo[] columnInfos = getColumnInfos();
      if (columnInfos != null) {
        final int i = columnAtPoint(event.getPoint());
        final int infoIndex = i >= 0 ? convertColumnIndexToModel(i) : -1;
        final String tooltipText = infoIndex >= 0 && infoIndex < columnInfos.length ? columnInfos[infoIndex].getTooltipText() : null;
        if (tooltipText != null) {
          return tooltipText;
        }
      }
      return super.getToolTipText(event);
    }

    private ColumnInfo @Nullable [] getColumnInfos() {
      TableModel model = getModel();
      if (model instanceof SortableColumnModel) {
        return ((SortableColumnModel)model).getColumnInfos();
      }
      else if (getTable() instanceof TreeTable) {
        TreeTableModel treeTableModel = ((TreeTable)getTable()).getTableModel();
        if (treeTableModel instanceof SortableColumnModel) {
          return ((SortableColumnModel)treeTableModel).getColumnInfos();
        }
      }
      return null;
    }

    @Override
    protected void processMouseEvent(MouseEvent e) {
      if (e.getID() == MouseEvent.MOUSE_CLICKED && e.getButton() == MouseEvent.BUTTON1) {
        int columnToPack = getColumnToPack(e.getPoint());
        if (columnToPack != -1 && canResize(columnToPack)) {
          if (e.getClickCount() % 2 == 0) {
            packColumn(columnToPack);
          }
          return; // prevents click events in column resize area
        }
      }
      super.processMouseEvent(e);
    }

    protected void packColumn(int columnToPack) {
      TableColumn column = getColumnModel().getColumn(columnToPack);
      int currentWidth = column.getWidth();
      int expandedWidth = getExpandedColumnWidth(columnToPack);
      int newWidth = getColumnModel().getColumnMargin() +
                     (currentWidth >= expandedWidth ? getPreferredHeaderWidth(columnToPack) : expandedWidth);

      setResizingColumn(column);
      column.setWidth(newWidth);
      Dimension tableSize = JBTable.this.getSize();
      tableSize.width += newWidth - column.getWidth();
      JBTable.this.setSize(tableSize);
      // let the table update it's layout with resizing column set
      ApplicationManager.getApplication().invokeLater(() -> setResizingColumn(null));
    }

    private int getColumnToPack(Point p) {
      int viewColumnIdx = JBTable.this.columnAtPoint(p);
      if (viewColumnIdx == -1) return -1;

      Rectangle headerRect = getHeaderRect(viewColumnIdx);

      boolean atLeftBound = p.x - headerRect.x < COLUMN_RESIZE_AREA_WIDTH;
      if (atLeftBound) {
        return viewColumnIdx == 0 ? viewColumnIdx : viewColumnIdx - 1;
      }

      boolean atRightBound = headerRect.x + headerRect.width - p.x < COLUMN_RESIZE_AREA_WIDTH;
      return atRightBound ? viewColumnIdx : -1;
    }

    private boolean canResize(int columnIdx) {
      TableColumnModel columnModel = getColumnModel();
      return resizingAllowed && columnModel.getColumn(columnIdx).getResizable();
    }
  }

  public int getExpandedColumnWidth(int columnToExpand) {
    int expandedWidth = getPreferredHeaderWidth(columnToExpand);
    for (int row = 0; row < getRowCount(); row++) {
      TableCellRenderer cellRenderer = getCellRenderer(row, columnToExpand);
      if (cellRenderer != null) {
        Component c = prepareRenderer(cellRenderer, row, columnToExpand);
        expandedWidth = Math.max(expandedWidth, c.getPreferredSize().width);
      }
    }
    return expandedWidth;
  }

  private int getPreferredHeaderWidth(int columnIdx) {
    TableColumn column = getColumnModel().getColumn(columnIdx);
    TableCellRenderer renderer = column.getHeaderRenderer();
    if (renderer == null) {
      JTableHeader header = getTableHeader();
      if (header == null) {
        return DEFAULT_MIN_COLUMN_WIDTH;
      }
      renderer = header.getDefaultRenderer();
    }
    Object headerValue = column.getHeaderValue();
    Component headerCellRenderer = renderer.getTableCellRendererComponent(this, headerValue, false, false, -1, columnIdx);
    return headerCellRenderer.getPreferredSize().width;
  }

  protected class InvisibleResizableHeader extends JBTableHeader {
    @NotNull private final MyBasicTableHeaderUI myHeaderUI;
    @Nullable private Cursor myCursor = null;

    public InvisibleResizableHeader() {
      myHeaderUI = new MyBasicTableHeaderUI(this);
      // need a header to resize/drag columns, so use header that is not visible
      setDefaultRenderer(new EmptyTableCellRenderer());
      setReorderingAllowed(true);
    }

    @Override
    public void setTable(JTable table) {
      JTable oldTable = getTable();
      if (oldTable != null) {
        oldTable.removeMouseListener(myHeaderUI);
        oldTable.removeMouseMotionListener(myHeaderUI);
      }

      super.setTable(table);

      if (table != null) {
        table.addMouseListener(myHeaderUI);
        table.addMouseMotionListener(myHeaderUI);
      }
    }

    @Override
    public void setCursor(@Nullable Cursor cursor) {
      /* this method and the next one fixes cursor:
         BasicTableHeaderUI.MouseInputHandler behaves like nobody else sets cursor
         so we remember what it set last time and keep it unaffected by other cursor changes in the table
       */
      JTable table = getTable();
      if (table != null) {
        table.setCursor(UIUtil.cursorIfNotDefault(cursor));
        myCursor = cursor;
      }
      else {
        super.setCursor(cursor);
      }
    }

    @Override
    public Cursor getCursor() {
      if (myCursor == null) {
        JTable table = getTable();
        if (table == null) return super.getCursor();
        return table.getCursor();
      }
      return myCursor;
    }

    @NotNull
    @Override
    public Rectangle getHeaderRect(int column) {
      // if a header has zero height, mouse pointer can never be inside it, so we pretend it is one pixel high
      Rectangle headerRect = super.getHeaderRect(column);
      return new Rectangle(headerRect.x, headerRect.y, headerRect.width, 1);
    }

    protected boolean canMoveOrResizeColumn(int modelIndex) {
      return true;
    }
  }

  private static class EmptyTableCellRenderer extends CellRendererPanel implements TableCellRenderer {
    @NotNull
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      return this;
    }

    @Override
    public Dimension getMaximumSize() {
      return new Dimension(0, 0);
    }
  }

  // this class redirects events from the table to BasicTableHeaderUI.MouseInputHandler
  private static class MyBasicTableHeaderUI extends BasicTableHeaderUI implements MouseInputListener {
    private int myStartXCoordinate = 0;
    private int myStartYCoordinate = 0;

    MyBasicTableHeaderUI(@NotNull InvisibleResizableHeader tableHeader) {
      header = tableHeader;
      mouseInputListener = createMouseInputListener();
    }

    @NotNull
    private MouseEvent convertMouseEvent(@NotNull MouseEvent e) {
      // create a new event, almost exactly the same, but in the header
      return new MouseEvent(e.getComponent(), e.getID(), e.getWhen(), e.getModifiers(), e.getX(), 0, e.getXOnScreen(), header.getY(),
                            e.getClickCount(), e.isPopupTrigger(), e.getButton());
    }

    @Override
    public void mouseClicked(@NotNull MouseEvent e) {
    }

    @Override
    public void mousePressed(@NotNull MouseEvent e) {
      if (isOnBorder(e) || !canMoveOrResizeColumn(e)) return;
      myStartXCoordinate = e.getX();
      myStartYCoordinate = e.getY();
      mouseInputListener.mousePressed(convertMouseEvent(e));
    }

    @Override
    public void mouseReleased(@NotNull MouseEvent e) {
      mouseInputListener.mouseReleased(convertMouseEvent(e));
      if (header.getCursor() == Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)) {
        header.setCursor(null);
      }
    }

    @Override
    public void mouseEntered(@NotNull MouseEvent e) {
      mouseInputListener.mouseEntered(convertMouseEvent(e));
    }

    @Override
    public void mouseExited(@NotNull MouseEvent e) {
      mouseInputListener.mouseExited(convertMouseEvent(e));
    }

    @Override
    public void mouseDragged(@NotNull MouseEvent e) {
      if (!isDraggingEnabled(e)) {
        return;
      }

      mouseInputListener.mouseDragged(convertMouseEvent(e));
      // if I change cursor on mouse pressed, it will change on double-click as well
      // and I do not want that
      if (header.getDraggedColumn() != null) {
        if (header.getCursor() == Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)) {
          header.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        }
        int draggedColumn = header.getTable().convertColumnIndexToView(header.getDraggedColumn().getModelIndex());
        int targetColumn = draggedColumn + (header.getDraggedDistance() < 0 ? -1 : 1);
        if (targetColumn < 0 || targetColumn >= header.getTable().getColumnCount()) return;
        if (!canMoveOrResizeColumn(header.getTable().convertColumnIndexToModel(targetColumn))) {
          mouseReleased(e); //cancel dragging unmovable column
        }
      }
    }

    private boolean isDraggingEnabled(@NotNull MouseEvent e) {
      if (isOnBorder(e) || !SwingUtilities.isLeftMouseButton(e) || !canMoveOrResizeColumn(e)) return false;
      // can not check for getDragged/Resized column here since they can be set in mousePressed method
      // their presence does not necessarily means something is being dragged or resized
      if (header.getCursor() == Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR) ||
          header.getCursor() == Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)) {
        return true;
      }

      int deltaX = Math.abs(e.getX() - myStartXCoordinate);
      int deltaY = Math.abs(e.getY() - myStartYCoordinate);
      Point point = new Point(Math.min(Math.max(e.getX(), 0), header.getTable().getWidth() - 1), e.getY());
      boolean sameColumn;
      if (header.getDraggedColumn() == null) {
        sameColumn = true;
      }
      else {
        sameColumn = (header.getTable().getColumnModel().getColumn(header.getTable().columnAtPoint(point)) ==
                      header.getDraggedColumn());
      }
      // start dragging only if mouse moved horizontally
      // or if dragging was already started earlier (it looks weird to stop mid-dragging)
      return deltaX >= 3 * deltaY && sameColumn;
    }

    @Override
    public void mouseMoved(@NotNull MouseEvent e) {
      if (isOnBorder(e)) return;
      mouseInputListener.mouseMoved(convertMouseEvent(e));
    }

    private boolean isOnBorder(@NotNull MouseEvent e) {
      return Math.abs(header.getTable().getWidth() - e.getPoint().x) <= JBUIScale.scale(3);
    }

    private boolean canMoveOrResizeColumn(@NotNull MouseEvent e) {
      JTable table = header.getTable();
      return canMoveOrResizeColumn(table.getColumnModel().getColumnIndexAtX(e.getX()));
    }

    private boolean canMoveOrResizeColumn(int index) {
      return ((InvisibleResizableHeader)header).canMoveOrResizeColumn(index);
    }
  }

  /**
   * JTable gets table data from model lazily - only for a table part to be shown.
   * JBTable loads <i>all</i> the data on initialization to calculate cell size.
   * This methods provides possibility to calculate size without loading all the table data.
   *
   * @param maxItemsForSizeCalculation maximum number ot items in table to be loaded for size calculation
   */
  public void setMaxItemsForSizeCalculation(int maxItemsForSizeCalculation) {
    myMaxItemsForSizeCalculation = maxItemsForSizeCalculation;
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleJBTable();
    }
    return accessibleContext;
  }

  /**
   * Specialization of {@link AccessibleJTable} to ensure instances of
   * {@link AccessibleJBTableCell}, as opposed to {@link AccessibleJTableCell},
   * are created in all code paths.
   */
  protected class AccessibleJBTable extends AccessibleJTable {
    @Override
    public Accessible getAccessibleChild(int i) {
      if (i < 0 || i >= getAccessibleChildrenCount()) {
        return null;
      }
      else {
        int column = getAccessibleColumnAtIndex(i);
        int row = getAccessibleRowAtIndex(i);
        return new AccessibleJBTableCell(JBTable.this, row, column, getAccessibleIndexAt(row, column));
      }
    }

    @Override
    public Accessible getAccessibleAt(Point p) {
      int column = columnAtPoint(p);
      int row = rowAtPoint(p);

      if ((column != -1) && (row != -1)) {
        return getAccessibleChild(getAccessibleIndexAt(row, column));
      }
      return null;
    }

    /**
     * Specialization of {@link AccessibleJTableCell} to ensure the underlying cell renderer
     * is obtained by calling the virtual method {@link JTable#getCellRenderer(int, int)}.
     *
     * <p>
     * NOTE: The reason we need this class is that even though the documentation of the
     * {@link JTable#getCellRenderer(int, int)} method mentions that
     * </p>
     *
     * <pre>
     * Throughout the table package, the internal implementations always
     * use this method to provide renderers so that this default behavior
     * can be safely overridden by a subclass.
     * </pre>
     *
     * <p>
     * the {@link AccessibleJTableCell#getCurrentComponent()} and
     * {@link AccessibleJTableCell#getCurrentAccessibleContext()} methods do not
     * respect that contract, instead using a <strong>copy</strong> of the default
     * implementation of {@link JTable#getCellRenderer(int, int)}.
     * </p>
     *
     * <p>
     * There are a few derived classes of {@link JBTable}, e.g.
     * {@link com.intellij.ui.dualView.TreeTableView} that depend on the ability to
     * override {@link JTable#getCellRenderer(int, int)} method to behave correctly,
     * so we need to ensure we go through the same code path to ensure correct
     * accessibility behavior.
     * </p>
     */
    protected class AccessibleJBTableCell extends AccessibleJTableCell {
      private final int myRow;
      private final int myColumn;

      public AccessibleJBTableCell(JTable table, int row, int columns, int index) {
        super(table, row, columns, index);
        this.myRow = row;
        this.myColumn = columns;
      }

      @Override
      protected Component getCurrentComponent() {
        return JBTable.this
          .getCellRenderer(myRow, myColumn)
          .getTableCellRendererComponent(JBTable.this, getValueAt(myRow, myColumn), false, false, myRow, myColumn);
      }

      @Override
      protected AccessibleContext getCurrentAccessibleContext() {
        Component c = getCurrentComponent();
        if (c instanceof Accessible) {
          return c.getAccessibleContext();
        }
        // Note: don't call "super" as 1) we know for sure the cell is not accessible
        // and 2) the super implementation is incorrect anyways
        return null;
      }
    }
  }

  private void resetRollOverCell() {
    if (UIUtil.isUnderWin10LookAndFeel() && getModel() instanceof AbstractTableModel && rollOverCell != null) {
      TableCellRenderer cellRenderer = getCellRenderer(rollOverCell.row, rollOverCell.column);
      if (cellRenderer != null) {
        Object value = getValueAt(rollOverCell.row, rollOverCell.column);
        boolean selected = isCellSelected(rollOverCell.row, rollOverCell.column);

        Component rc = cellRenderer.getTableCellRendererComponent(this, value, selected, hasFocus(), rollOverCell.row, rollOverCell.column);
        if (rc instanceof JCheckBox) {
          ((JCheckBox)rc).putClientProperty(UIUtil.CHECKBOX_ROLLOVER_PROPERTY, null);
        }
      }

      if (getModel() instanceof AbstractTableModel) {
        ((AbstractTableModel)getModel()).fireTableCellUpdated(rollOverCell.row, rollOverCell.column);
      }
      rollOverCell = null;
    }
  }
}
