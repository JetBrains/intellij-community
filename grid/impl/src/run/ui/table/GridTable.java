package com.intellij.database.run.ui.table;

import com.intellij.application.options.EditorFontsConstants;
import com.intellij.database.datagrid.DataGridListModel;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridColumnLayout;
import com.intellij.database.datagrid.GridModel;
import com.intellij.database.datagrid.GridRequestSource;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.HierarchicalGridColumn;
import com.intellij.database.datagrid.HierarchicalReader;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.datagrid.ModelIndexSet;
import com.intellij.database.datagrid.NestedTable;
import com.intellij.database.datagrid.RawIndexConverter;
import com.intellij.database.datagrid.ResultView.HoveredRowBgHighlightMode;
import com.intellij.database.datagrid.RowSortOrder;
import com.intellij.database.datagrid.ViewIndex;
import com.intellij.database.run.ui.GridTableCellEditor;
import com.intellij.database.run.ui.grid.CacheComponent;
import com.intellij.database.run.ui.grid.CellAttributes;
import com.intellij.database.run.ui.grid.CellRenderingUtils;
import com.intellij.database.run.ui.grid.DummyGridColumnLayout;
import com.intellij.database.run.ui.grid.GridColorsScheme;
import com.intellij.database.run.ui.grid.JBTableWithResizableCells;
import com.intellij.database.run.ui.grid.ResizableCellEditorsSupport;
import com.intellij.database.run.ui.grid.TableCellImageCache;
import com.intellij.icons.AllIcons;
import com.intellij.ide.bookmark.BookmarksManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ide.ui.UISettingsUtils;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.CellRendererPanel;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.ComponentWithExpandableItems;
import com.intellij.ui.ExpandableItemsHandler;
import com.intellij.ui.IconManager;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBAutoScroller;
import com.intellij.ui.RelativeFont;
import com.intellij.ui.SideBorder;
import com.intellij.ui.TableCell;
import com.intellij.ui.TableExpandableItemsHandler;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.SwingTextTrimmer;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.UpdateScaleHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BoxLayout;
import javax.swing.CellRendererPane;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.TableModelEvent;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.IntUnaryOperator;

import static com.intellij.database.datagrid.GridUtilKt.setupDynamicRowHeight;
import static com.intellij.database.run.ui.grid.GridColorSchemeUtil.doGetBackground;
import static com.intellij.database.run.ui.grid.GridColorSchemeUtil.doGetForeground;
import static com.intellij.database.run.ui.grid.GridColorSchemeUtil.doGetGridColor;
import static com.intellij.database.run.ui.grid.GridColorSchemeUtil.doGetSelectionBackground;
import static com.intellij.database.run.ui.grid.GridColorSchemeUtil.doGetSelectionForeground;
import static com.intellij.database.run.ui.grid.TableCellImageCache.MAX_COLUMNS;
import static com.intellij.database.run.ui.grid.TableCellImageCache.MAX_ROWS;
import static com.intellij.database.run.ui.table.UnparsedValueHoverListener.Companion.Place.CENTER;
import static com.intellij.util.containers.ContainerUtil.all;
import static com.intellij.util.containers.ContainerUtil.exists;
import static com.intellij.util.containers.ContainerUtil.filter;
import static com.intellij.util.ui.UIUtil.getFontWithFallback;

/**
 * A Swing table that shows the cells of the grid columns in its slice of the view's column model. The view has a main table and, while
 * columns are pinned, a pinned table. Both share the view's data model, row order and row selection, so together they look like one grid.
 * A request that may affect both tables goes to the view through {@link GridTableHost}.
 *
 * @author gregsh
 */
class GridTable extends JBTableWithResizableCells
  implements Disposable, CacheComponent, EditorColorsListener, UISettingsListener, UiDataProvider {

  /** The grid's data with the user's unsubmitted changes, which this table shows. */
  private final GridModel<GridRow, GridColumn> myGridModel;
  /** The grid's data as loaded, without unsubmitted changes; the first column layout waits for its columns. */
  private final GridModel<GridRow, GridColumn> myDatabaseModel;
  /** The grid's colors and fonts; null only while JTable's constructor runs. */
  private final GridColorsScheme myColorsScheme;
  /** Keeps a selection change from scrolling the table; null only while JTable's constructor runs. */
  private final JBAutoScroller.AutoscrollLocker myAutoscrollLocker;
  private final ViewColumnCache myColumnCache;
  /** The view's adapters of the grid's cell renderers and its cell colors, which every table of the view shares. */
  private final ViewCellRenderers myCellRenderers;
  /** The view's access to the grid's cell editors, which every table of the view shares. */
  private final ViewCellEditors myCellEditors;
  /** What the view's column headers show of the grid's state, which this table's header reads as it paints. */
  private final ColumnHeaderAppearance myHeaderAppearance;
  private final TableRawIndexConverter myRawIndexConverter;
  private GridColumnLayout<GridRow, GridColumn> myColumnLayout = new DummyGridColumnLayout();

  private double myFontSizeIncrement = 0.0d;
  private double myFontSizeScale = 1.0d;
  private final UpdateScaleHelper myUpdateScaleHelper = new UpdateScaleHelper();

  /**
   * Identity hash of the grid's columns when the first column layout finished; 0 before. Only zero versus non-zero is read, so the layout
   * runs once.
   */
  private int myLaidOutColumnsHash;
  private PaintingSession myPaintingSession;

  private ColumnHeaderBackgroundListener myColumnHeaderBgListener;
  private Boolean myIsTransparentColumnHeaderBg;
  private boolean myAllowMultilineColumnLabel = false;
  /** The view's block on column reordering, which this table's header reads, as the other table's header does. */
  private final ColumnReorderingBlock myColumnReorderingBlock;
  private HoveredRowBgHighlightMode myHoveredRowMode = HoveredRowBgHighlightMode.AUTO;

  private static final Logger LOG = Logger.getInstance(GridTable.class);

  private GridTableHost myHost;
  /** The view's row heights, which this table applies to itself; null until the host is installed. */
  private @Nullable RowHeightModel myRowHeights;
  /** The view's cell image cache, which every table of the view paints through; null until the view installs the table. */
  private @Nullable TableCellImageCache myCellImageCache;
  /** The value of the view's open multi-cell edit, which both tables share; null until the view installs the table. */
  private @Nullable MultiCellEditValue myEditedValue;
  /** The view's row sorter, which every table of the view shares; null before the view installs the table and after it drops it. */
  private @Nullable RowSorter<? extends TableModel> mySharedRowSorter;
  private ColumnHeaderPainter myColumnHeaderPainter;

  /**
   * Creates a table over the view's data model, which another table of the view may already show. The model is passed here rather
   * than set later, because a later {@link #setModel} would clear the shared row selection and create another row sorter. The table
   * takes its columns from the view's cache and shows those of {@code columnModel}. JTable's constructor clears the selection of
   * {@code columnModel}, so a table added later starts on a placeholder. The row selection is set after that constructor for the same
   * reason.
   */
  GridTable(@NotNull SharedTableParts sharedParts, @NotNull TableColumnModel columnModel) {
    super(sharedParts.getDataModel(), columnModel);
    setSelectionModel(sharedParts.getRowSelection());
    myGridModel = sharedParts.getGridModel();
    myDatabaseModel = sharedParts.getDatabaseModel();
    myColorsScheme = sharedParts.getColorsScheme();
    myAutoscrollLocker = sharedParts.getAutoscrollLocker();
    myRawIndexConverter = new TableRawIndexConverter(this);
    myColumnCache = sharedParts.getColumnCache();
    myCellRenderers = sharedParts.getCellRenderers();
    myCellEditors = sharedParts.getCellEditors();
    myHeaderAppearance = sharedParts.getHeaderAppearance();
    myColumnReorderingBlock = sharedParts.getColumnReorderingBlock();
    getTableHeader().setDefaultRenderer(createHeaderRenderer());
    ComponentWithExpandableItems<?> tableHeader = ObjectUtils.tryCast(getTableHeader(), ComponentWithExpandableItems.class);
    if (tableHeader != null) tableHeader.setExpandableItemsEnabled(false);
    putClientProperty(BookmarksManager.ALLOWED, true);

    ResizableCellEditorsSupport.install(this);

    setupFocusListener();
    setEnableAntialiasing(true);
    setShowLastHorizontalLine(true);

    setFont(getColorsScheme().getFont(EditorFontType.PLAIN));
    updateFonts();

    adjustDefaultActions();

    new UnparsedValueHoverListener(CENTER, this).addTo(this);

    setShowHorizontalLines(false);
  }

  /** Where the hint for an expanded cell may reach; a table that clips its cells narrows it. */
  protected @NotNull Rectangle narrowExpandedCellHint(@NotNull Rectangle visible, @NotNull TableCell key) {
    return visible;
  }

  /**
   * Paints nothing here; the pinned table draws its divider to the main table. {@code bottom} is where the painted rows or the header
   * end.
   */
  protected void paintTrailingDivider(@NotNull Graphics g, int bottom) {
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    if (myHost != null) myHost.uiDataSnapshot(sink);
  }

  @Override
  public void setShowHorizontalLines(boolean v) {
    if (v) {
      setIntercellSpacing(new Dimension(getIntercellSpacing().width, 1));
    }
    super.setShowHorizontalLines(v);
  }

  @Override
  public void setShowVerticalLines(boolean showVerticalLines) {
    if (showVerticalLines) {
      setIntercellSpacing(new Dimension(1, getIntercellSpacing().height));
    }
    super.setShowVerticalLines(showVerticalLines);
  }

  @Override
  public void setStriped(boolean striped) {
    super.setStriped(striped);
    if (striped) {
      setShowHorizontalLines(false);
      setShowVerticalLines(true);
    }
  }

  @Override
  protected @Nullable Color getStripeColor() {
    return myCellRenderers.stripeRowBackground();
  }

  @Override
  public @NotNull JComponent getComponent() {
    return this;
  }

  /**
   * Before Swing changes the selection, the host commits an editor open in either table while columns are pinned; a refusal keeps the
   * selection.
   * <p>
   * An extend from an anchor column that this table doesn't show goes to the table's slice, which extends in displayed order. Swing reads
   * that anchor in the slice as -1, so it would extend from this table's first column. Swing handles everything else, as without pins.
   */
  @Override
  public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend) {
    if (!myHost.stopEditingBeforeSelectionChange(modelCellAt(rowIndex, columnIndex))) return;
    if (extend && getColumnModel() instanceof GridColumnSlice slice && slice.omitsAnchor() &&
        rowIndex >= 0 && rowIndex < getRowCount() && columnIndex >= 0 && columnIndex < getColumnCount()) {
      extendFromAnchorInOtherSlice(slice, rowIndex, columnIndex, toggle);
      return;
    }
    super.changeSelection(rowIndex, columnIndex, toggle, extend);
  }

  /**
   * Extends the selection to the cell at {@code row} and {@code localIndex} from an anchor column that {@code slice} doesn't show. It
   * follows JTable: without an anchor row in the table, the rows start at the first one, and the anchor cell counts as unselected. Like
   * JTable, it scrolls to the cell only when the table autoscrolls; a table with a JBAutoScroller scrolls on the selection change instead.
   */
  private void extendFromAnchorInOtherSlice(@NotNull GridColumnSlice slice, int row, int localIndex, boolean toggle) {
    ListSelectionModel rows = getSelectionModel();
    int anchorRow = rows.getAnchorSelectionIndex();
    boolean hasAnchorRow = anchorRow >= 0 && anchorRow < getRowCount();
    boolean anchorSelected = hasAnchorRow && rows.isSelectedIndex(anchorRow) && slice.isAnchorColumnSelected();
    RangeChange change = RangeChange.forExtend(toggle, anchorSelected);
    change.applyTo(rows, hasAnchorRow ? anchorRow : 0, row);
    slice.extendColumnSelection(localIndex, change);
    if (getAutoscrolls()) scrollRectToVisible(getCellRect(row, localIndex, false));
  }

  @Override
  protected void processMouseEvent(MouseEvent e) {
    if (!myHost.beforeMouseEvent(e)) return;
    processSwingMouseEvent(e);
  }

  /** Swing's own handling of a mouse event in this table, after the view's check before it. */
  protected void processSwingMouseEvent(@NotNull MouseEvent e) {
    super.processMouseEvent(e);
  }

  /** The grid's autoscroll lock, for a subclass that changes the selection without scrolling the tables. */
  protected final @NotNull JBAutoScroller.AutoscrollLocker getAutoscrollLocker() {
    return myAutoscrollLocker;
  }

  @Override
  protected void processMouseMotionEvent(MouseEvent e) {
    super.processMouseMotionEvent(e);
    myHost.processMouseMotionEvent(e);
  }

  public void setTransparentColumnHeaderBackground(boolean v) {
    myIsTransparentColumnHeaderBg = v;
    if (myColumnHeaderBgListener != null) myColumnHeaderBgListener.transparencyChanged(myIsTransparentColumnHeaderBg);
  }

  /** Sets the one listener told whether the column header background is transparent, now and on each change. */
  public void setColumnHeaderBackgroundListener(@NotNull ColumnHeaderBackgroundListener listener) {
    myColumnHeaderBgListener = listener;
    if (myIsTransparentColumnHeaderBg != null) myColumnHeaderBgListener.transparencyChanged(myIsTransparentColumnHeaderBg);
  }

  public void setAllowMultilineLabel(boolean v) {
    myAllowMultilineColumnLabel = v;
  }

  @Override
  protected @NotNull ExpandableItemsHandler<TableCell> createExpandableItemsHandler() {
    return new TableExpandableItemsHandler(this) {
      @Override
      public Rectangle getVisibleRect(TableCell key) {
        return narrowExpandedCellHint(super.getVisibleRect(key), key);
      }

      @Override
      protected void handleSelectionChange(TableCell selected, boolean processIfUnfocused) {
        if (selected == null) {
          super.handleSelectionChange(null, processIfUnfocused);
          return;
        }
        Object val = myGridModel.getValueAt(modelRowAt(selected.row, selected.column), modelColumnAt(selected.row, selected.column));
        if (val instanceof NestedTable) {
          super.handleSelectionChange(null, processIfUnfocused);
          return;
        }

        boolean isSelected = isRowSelected(selected.row) && isColumnSelected(selected.column);
        boolean suppress = isSelected && myCellEditors.valueEditorShowsSelectedValue();
        super.handleSelectionChange(suppress ? null : selected, processIfUnfocused);
      }
    };
  }

  @Override
  public @Nullable JScrollBar getVerticalScrollBar() {
    JScrollPane scrollPane = getScrollPane();
    return scrollPane == null ? null : scrollPane.getVerticalScrollBar();
  }

  private @Nullable JScrollPane getScrollPane() {
    return ComponentUtil.getParentOfType((Class<? extends JScrollPane>)JScrollPane.class, this);
  }

  @Override
  public @Nullable JScrollBar getHorizontalScrollBar() {
    JScrollPane scrollPane = getScrollPane();
    return scrollPane == null ? null : scrollPane.getHorizontalScrollBar();
  }

  private @NotNull GridColorsScheme getColorsScheme() {
    return myColorsScheme;
  }

  /** Renders the cell at {@code row} and local index {@code localIndex} of this table. */
  @Nullable JComponent getCellRendererComponent(int row, int localIndex, boolean forDisplay) {
    TableCellRenderer renderer = getCellRenderer(row, localIndex);
    if (renderer == null) {
      return null;
    }

    return (JComponent)prepareRenderer(renderer, row, localIndex, forDisplay);
  }

  private @NotNull MyCellRenderer createHeaderRenderer() {
    return new MyCellRenderer(this);
  }

  public void startPaintingSession() {
    myPaintingSession = new PaintingSession();
  }

  public void endPaintingSession() {
    myPaintingSession = null;
  }

  public void orderingAndVisibilityChanged() {
    getModel().fireTableDataChanged();
  }

  /**
   * Converts between model indices and this table's rows and local indices. The grid's converter works in positions of the view's column
   * model instead.
   */
  public @NotNull RawIndexConverter getRawIndexConverter() {
    return myRawIndexConverter;
  }

  /** Shows the view's data model for a transpose. The view resets its columns before and rebuilds them after. */
  void setDataModel(@NotNull GridTableModel model) {
    myColumnLayout.invalidateCache();
    setModel(model);
  }

  @Override
  public void addNotify() {
    super.addNotify();
    layoutColumnsIfNeeded();
  }

  @Override
  public void doLayout() {
    super.doLayout();
    if (getWidth() != 0) {
      layoutColumnsIfNeeded();
    }
  }

  private void layoutColumnsIfNeeded() {
    if (myLaidOutColumnsHash != 0) return;
    int hash = computeColumnsHashCode();
    if (hash == 0) return;
    layoutColumns().doWhenDone(() -> myLaidOutColumnsHash = hash);
  }

  @Override
  protected void paintComponent(@NotNull Graphics g) {
    adjustCacheSize();
    super.paintComponent(g);
    paintCellsEffects(g);
  }

  @Override
  public void paint(@NotNull Graphics g) {
    super.paint(g);
    paintTrailingDivider(g, getLastRowBottom());
  }

  private int getLastRowBottom() {
    if (getRowCount() == 0) return 0;
    Rectangle lastRow = getCellRect(getRowCount() - 1, 0, true);
    return lastRow.y + lastRow.height;
  }

  private void adjustCacheSize() {
    if (myHost != null && myCellImageCache != null && myCellImageCache.isCacheEnabled()) myHost.aboutToPaint();
  }

  /** How many cell images this table shows at most, which the view's cache must hold for it; 0 while nothing is visible. */
  int requestedCellImageCacheSize() {
    TableColumnModel columnModel = getColumnModel();
    int columnCount = columnModel == null ? 0 : columnModel.getColumnCount();
    int rowCount = getRowCount();
    Rectangle visibleRect = getVisibleRect();
    if (columnCount == 0 || rowCount == 0 || visibleRect.isEmpty()) return 0;

    int minColumnWidth = Integer.MAX_VALUE;
    for (int i = 0; i < columnCount; i++) {
      TableColumn column = columnModel.getColumn(i);
      minColumnWidth = Math.min(minColumnWidth, column.getMinWidth());
    }
    int rowHeight = getRowHeight();

    int rowsMax = rowHeight == 0 ? rowCount : Math.min(rowCount, (int)Math.ceil(visibleRect.height / (float)rowHeight));
    rowsMax = Math.min(MAX_ROWS, rowsMax);
    int colsMax = minColumnWidth == 0 ? columnCount : Math.min(columnCount, (int)Math.ceil(visibleRect.width / (float)minColumnWidth));
    colsMax = Math.min(MAX_COLUMNS, colsMax);
    int factor = Math.max(1, Registry.intValue("database.grid.cache.factor"));
    return rowsMax * colsMax * factor;
  }

  @Override
  public int rowAtPoint(@NotNull Point point) {
    int y = point.y;

    var result = super.rowAtPoint(point);
    if (result == -1 && y >= 0) { // we detect that the point is below all rows, not above
      return getRowCount() - 1;
    }
    return result;
  }

  private void paintCellsEffects(Graphics g) {
    Rectangle visibleArea = g.getClipBounds();
    int fromRow, fromColumn, toRow, toColumn;
    Point at = new Point((int)visibleArea.getMinX(), (int)visibleArea.getMinY());
    fromRow = rowAtPoint(at);
    fromColumn = columnAtPoint(at);
    at.setLocation(visibleArea.getMaxX(), visibleArea.getMaxY());
    toRow = rowAtPoint(at);
    toColumn = columnAtPoint(at);
    if (fromColumn == -1) fromColumn = 0;
    if (toRow == -1) toRow = 0;
    if (toColumn == -1) toColumn = getColumnCount() - 1;
    if (fromRow == -1) toRow = getRowCount() - 1;

    for (int row = fromRow; row <= toRow; ++row) {
      for (int column = fromColumn; column <= toColumn; ++column) {
        paintCellEffects(g, row, column);
      }
    }
  }

  private void paintCellEffects(Graphics g, int row, int column) {
    CellAttributes attributes = myCellRenderers.cellEffect(modelRowAt(row, column), modelColumnAt(row, column));
    if (attributes != null) {
      CellRenderingUtils.paintCellEffect(g, getCellRect(row, column, true), attributes);
    }
  }

  private int computeColumnsHashCode() {
    int hash = 0;
    List<GridColumn> columns = myDatabaseModel.getColumns();
    for (GridColumn column : columns) {
      hash = System.identityHashCode(column) + 31 * hash;
    }
    return hash;
  }

  private void setupFocusListener() {
    addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(@NotNull FocusEvent e) {
        if (isEditing()) {
          Component component = getEditorComponent();
          if (component != null) {
            IdeFocusManager.getGlobalInstance()
              .doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(component, true));
          }
        }
      }
    });
  }

  private void adjustDefaultActions() {
    ActionMap actionMap = getActionMap();
    Action selectPreviousRowCell = actionMap.get("selectPreviousRowCell");
    actionMap.put("selectPreviousRowCell", new AbstractAction() {
      @Override
      public boolean isEnabled() {
        return false;
      }

      @Override
      public void actionPerformed(ActionEvent e) {
        selectPreviousRowCell.actionPerformed(e);
      }
    });
    Action startEditing = actionMap.get("startEditing");
    actionMap.put("startEditing", new AbstractAction() {
      @Override
      public boolean isEnabled() {
        return false;
      }

      @Override
      public void actionPerformed(ActionEvent e) {
        startEditing.actionPerformed(e);
      }
    });
  }

  @Override
  public void columnMarginChanged(ChangeEvent e) {
    // same as JTable#columnMarginChanged except for it doesn't stop editing
    JTableHeader tableHeader = getTableHeader();
    TableColumn resizingColumn = tableHeader != null ? tableHeader.getResizingColumn() : null;
    if (resizingColumn != null && autoResizeMode == AUTO_RESIZE_OFF) {
      if (resizingColumn instanceof TableResultViewColumn column) {
        int width = myHost == null
                    ? resizingColumn.getWidth()
                    : myHost.constrainColumnResize(column, resizingColumn.getWidth());
        if (resizingColumn.getWidth() != width) resizingColumn.setWidth(width);
        column.setColumnWidthByUser(width);
      }
      else {
        resizingColumn.setPreferredWidth(resizingColumn.getWidth());
      }
    }
    resizeAndRepaint();
    if (myHost != null) myHost.columnMarginChanged();
  }

  @Override
  public String getToolTipText(@NotNull MouseEvent event) {
    return "";
  }

  /** Stores the height in the view's row heights, which every table of the view then applies to itself. */
  @Override
  public void setRowHeight(int row, int rowHeight) {
    if (row < 0 || row >= getRowCount()) {
      return;
    }
    if (myRowHeights == null) {
      applyRowHeight(row, rowHeight);
      return;
    }
    // A dropped pinned table can still restore an editor's row height late, but its rows no longer match the view's.
    if (!sharesTheViewRowOrder()) return;
    // An int table-model row: while transposed, a table row is a grid column, and the row heights go by table-model rows.
    myRowHeights.setHeight(convertRowIndexToModel(row), rowHeight);
  }

  /** Setting the uniform height drops the per-row heights of this table, so it applies the view's row heights again. */
  @Override
  public void setRowHeight(int rowHeight) {
    super.setRowHeight(rowHeight);
    applyStoredRowHeights();
  }

  private void applyStoredRowHeights() {
    if (myRowHeights == null) return;
    for (Map.Entry<Integer, Integer> height : myRowHeights.heightsByModelRow().entrySet()) {
      applyHeightToModelRow(height.getKey(), height.getValue());
    }
  }

  private void applyStoredRowHeight(int modelRow) {
    Integer height = Objects.requireNonNull(myRowHeights).heightOf(modelRow);
    applyHeightToModelRow(modelRow, height != null ? height : getRowHeight());
  }

  private void applyHeightToModelRow(int modelRow, int rowHeight) {
    if (modelRow >= getModel().getRowCount()) return;
    int viewRow = convertRowIndexToView(modelRow);
    if (viewRow >= 0) applyRowHeight(viewRow, rowHeight);
  }

  private void applyRowHeight(int viewRow, int rowHeight) {
    super.setRowHeight(viewRow, rowHeight);

    Container parent = getParent();
    if (parent instanceof JViewport) {
      Container grandParent = parent.getParent();
      if (grandParent instanceof JScrollPane scrollPane) {
        JViewport rowHeader = scrollPane.getRowHeader();
        if (rowHeader != null) {
          rowHeader.revalidate();
          rowHeader.repaint();
        }
      }
    }
  }

  @Override
  protected @NotNull JTableHeader createDefaultTableHeader() {
    return new MyTableHeader();
  }

  /** Tells the data model about the new columns; the view has rebuilt its columns for them already. */
  public void columnsAdded(ModelIndexSet<GridColumn> columnIndices) {
    getModel().columnsAdded(columnIndices);
    myColumnLayout.newColumnsAdded(columnIndices);
  }

  /** Tells the data model about the removed columns; the view rebuilds its columns after. */
  public void columnsRemoved(ModelIndexSet<GridColumn> columns) {
    getModel().columnsRemoved(columns);
  }

  /** Tells the data model about the new rows; a transposed view rebuilds its columns after. */
  public void rowsAdded(ModelIndexSet<GridRow> rows) {
    getModel().rowsAdded(rows);
  }

  public void expandMultilineRows(boolean v) {
    if (v) {
      setupDynamicRowHeight(this);
    }
  }

  /** Tells the data model about the removed rows; a transposed view rebuilds its columns after. */
  public void rowsRemoved(ModelIndexSet<GridRow> rows) {
    getModel().rowsRemoved(rows);
  }

  public void cellsUpdated(ModelIndexSet<GridRow> rows, ModelIndexSet<GridColumn> columns, @Nullable GridRequestSource.RequestPlace place) {
    getModel().cellsUpdated(rows, columns, place);
    myColumnLayout.newRowsAdded(rows);
  }

  @Override
  public void dispose() {
    removeEditor();
  }

  /**
   * Aligns the leftmost requested column with the visible area in an untransposed table.
   * Skips the columns this table doesn't show, and keeps the current vertical scroll position.
   */
  public void scrollColumnsIntoView(@NotNull List<ModelIndex<GridColumn>> columns) {
    if (isTransposed()) return;
    IntUnaryOperator column2View = getRawIndexConverter().column2View();
    int target = -1;
    for (ModelIndex<GridColumn> column : columns) {
      int viewColumn = column2View.applyAsInt(column.value);
      if (viewColumn >= 0 && (target < 0 || viewColumn < target)) target = viewColumn;
    }
    if (target < 0) return;
    Rectangle visible = getParent() instanceof JViewport viewport ? viewport.getViewRect() : getVisibleRect();
    Rectangle cell = getCellRect(Math.max(0, getSelectionModel().getLeadSelectionIndex()), target, true);
    // Use the viewport width to show the start of a column wider than the viewport.
    cell.setBounds(cell.x, visible.y, visible.width, visible.height);
    scrollRectToVisible(cell);
  }

  /**
   * The table header's renderer: each header cell through {@link MyHeaderCellComponent}; anything else through the header's original
   * renderer.
   */
  static class MyCellRenderer extends MyHeaderCellComponent implements TableCellRenderer {
    private final TableCellRenderer myOriginalRenderer;

    MyCellRenderer(@NotNull GridTable table) {
      super(table);
      myOriginalRenderer = table.getTableHeader().getDefaultRenderer();
    }

    @Override
    public @NotNull Component getTableCellRendererComponent(@NotNull JTable table,
                                                            Object value,
                                                            boolean isSelected,
                                                            boolean hasFocus,
                                                            int row,
                                                            int column) {
      return row == -1 ?
             getHeaderCellRendererComponent(table.convertColumnIndexToModel(column), true) :
             myOriginalRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    }

    /** {@code tableModelColumn} is a grid column, or a grid row while the table is transposed. */
    public Component getHeaderCellRendererComponent(int tableModelColumn, boolean forDisplay) {
      HierarchicalReader reader = myTable.myGridModel.getHierarchicalReader();

      return prepare(tableModelColumn, forDisplay, reader);
    }
  }

  protected class MyTableHeader extends JBTableHeader {
    private int myLastPositiveHeight = 0;
    /** The column whose filter icon is under the pointer, or null. */
    private @Nullable ModelIndex<GridColumn> myHoveredFilterColumn = null;
    /** The column whose sort icon is under the pointer, or null. */
    private @Nullable ModelIndex<GridColumn> myHoveredSortColumn = null;

    @Override
    public void setExpandableItemsEnabled(boolean enabled) {
      super.setExpandableItemsEnabled(false); // we never want this
    }

    /** Highlights the filter icon or the sort arrow under the pointer, which the header's hit test finds, as it does for a click. */
    private void onColumnHeaderMouseMoved(@NotNull ColumnHeaderHit.ColumnCell hit) {
      var previousFilterColumn = myHoveredFilterColumn;
      myHoveredFilterColumn = hit.getPart() instanceof ColumnHeaderPart.FilterIcon ? hit.getColumn() : null;

      var previousSortColumn = myHoveredSortColumn;
      myHoveredSortColumn = hit.getPart() instanceof ColumnHeaderPart.SortArrow ? hit.getColumn() : null;

      if (!Objects.equals(previousFilterColumn, myHoveredFilterColumn) ||
          !Objects.equals(previousSortColumn, myHoveredSortColumn)) {
        tableHeader.repaint();
      }
    }

    {
      setOpaque(false);
      setFocusable(false);
      // Clicks on the header are the view's, which asks headerHitAt; this listener only ends the hover.
      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseExited(MouseEvent e) {
          if (isTransposed()) {
            return;
          }

          if (MyTableHeader.this.myHoveredFilterColumn != null || MyTableHeader.this.myHoveredSortColumn != null) {
            MyTableHeader.this.myHoveredFilterColumn = null;
            MyTableHeader.this.myHoveredSortColumn = null;
            MyTableHeader.this.repaint();
          }
        }
      });
      addMouseMotionListener(new MouseMotionAdapter() {
        @Override
        public void mouseMoved(MouseEvent e) {
          // A transposed header and the space past the last column keep the hover as it is.
          if (headerHitAt(e.getPoint()) instanceof ColumnHeaderHit.ColumnCell hit) {
            onColumnHeaderMouseMoved(hit);
          }
        }
      });
    }

    @Override
    public boolean getReorderingAllowed() {
      if (isTransposed() || myColumnReorderingBlock.isBlocked()) return false;

      List<GridColumn> columns = myGridModel.getColumns();

      return !exists(
        columns,
        c -> c instanceof HierarchicalGridColumn hierarchicalColumn && !hierarchicalColumn.isTopLevelColumn()
      );
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension d = super.getPreferredSize();
      if (!isPreferredSizeSet() && d.height > 0 && myAllowMultilineColumnLabel) {
        // DS-4208
        // Here we need to calculate maximum height of column header because
        // BasicTableHeaderUI.getHeaderHeight returns first column's height when
        // columns use default renderer
        int maxHeight = d.height;
        for (int idx = 0; idx < columnModel.getColumnCount(); ++idx) {
          Component comp = getHeaderRenderer(idx);
          int rendererHeight = comp.getPreferredSize().height;
          maxHeight = Math.max(maxHeight, rendererHeight);
        }
        d.height = maxHeight;
      }
      else if (d.height <= 0) {
        d.height = myLastPositiveHeight;
      }
      return d;
    }

    // similar to javax.swing.plaf.basic.BasicTableHeaderUI.getHeaderRenderer
    private Component getHeaderRenderer(int idx) {
      TableColumn column = columnModel.getColumn(idx);
      TableCellRenderer renderer = column.getHeaderRenderer();
      if (renderer == null) {
        renderer = getDefaultRenderer();
      }

      return renderer.getTableCellRendererComponent(getTable(), column.getHeaderValue(), false, false, -1, idx);
    }

    @Override
    public void setSize(int width, int height) {
      super.setSize(width, height);
      if (height > 0) {
        myLastPositiveHeight = height;
      }
    }

    @Override
    public void paint(@NotNull Graphics g) {
      Rectangle clip = g.getClipBounds();
      if (clip == null) {
        return;
      }
      clip.width = Math.max(0, Math.min(clip.width, getTable().getWidth() - clip.x));
      g.setClip(clip);
      super.paint(g);
      paintTrailingDivider(g, getHeight());
    }
  }

  /** The tables of a view share its sorter, which only the view tells about data changes; see {@link TableRowOrder}. */
  @Override
  public @Nullable RowSorter<? extends TableModel> getRowSorter() {
    return mySharedRowSorter;
  }

  /** Replaces the view's row sorter that this table reads, as on a transpose. */
  void setSharedRowSorter(@NotNull RowSorter<? extends TableModel> sorter) {
    mySharedRowSorter = sorter;
  }

  /**
   * Whether this table reads the view's shared sorter, from {@link #installHost} until {@link #detachModel}. Meanwhile the view tells it of
   * row changes.
   */
  private boolean sharesTheViewRowOrder() {
    return mySharedRowSorter != null;
  }

  @Override
  public @NotNull Component prepareRenderer(@NotNull TableCellRenderer renderer, int row, int column) {
    return prepareRenderer(renderer, row, column, true);
  }

  public @NotNull Component prepareRenderer(@NotNull TableCellRenderer renderer, int row, int column, boolean forDisplay) {
    Component component = super.prepareRenderer(renderer, row, column);
    if (!forDisplay) return component;

    boolean selected = isRowSelected(row) && isColumnSelected(column);
    return myCellRenderers.prepare(component, this, row, column, selected);
  }

  /** Lets {@code action} handle Escape while this table has focus. */
  void registerEscapeAction(@NotNull AbstractAction action) {
    String actionId = "grid.escape";
    getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), actionId);
    getActionMap().put(actionId, action);
  }

  /**
   * The grid's view index of this table's column at {@code localIndex}: its position in the view's column model, or -1 when the slice
   * lacks it. A table on a column model of its own keeps the index. The view's renderer adapter and cell colors build the grid's
   * {@link ViewIndex} from it in Swing callbacks, which pass Swing positions.
   */
  int toGridViewColumn(int localIndex) {
    return getColumnModel() instanceof GridColumnSlice slice ? slice.toMaster(localIndex) : localIndex;
  }

  public @NotNull Color getCellForeground(boolean selected) {
    return selected ? getSelectionForeground() : getForeground();
  }

  /** Applies the font again; the view first reinitializes the renderer factories and resets the cell image cache every table shares. */
  public void reinitSettings() {
    updateFonts();
  }

  @Override
  public boolean isCellEditable(int row, int column) {
    return myCellEditors.isCellEditingAllowed();
  }

  @Override
  public boolean editCellAt(int row, int column, EventObject e) {
    EditStart start = myHost == null ? EditStart.IN_CELL_EDITOR : myHost.startEdit(modelCellAt(row, column), e);
    if (start != EditStart.IN_CELL_EDITOR) return false;
    ClientProperty.put(this, GridTableCellEditor.EDITING_STARTER_CLIENT_PROPERTY_KEY, e);
    try {
      return WriteIntentReadAction.compute(() -> super.editCellAt(row, column, e));
    }
    finally {
      ClientProperty.put(this, GridTableCellEditor.EDITING_STARTER_CLIENT_PROPERTY_KEY, null);
    }
  }

  @Override
  public int getScrollableUnitIncrement(@NotNull Rectangle visibleRect, int orientation, int direction) {
    if (orientation == SwingConstants.VERTICAL) {
      // use original font size and ignore magnification!
      return getColorsScheme().getEditorFontSize();
    }
    return super.getScrollableUnitIncrement(visibleRect, orientation, direction);
  }

  @Override
  public Font getFont() {
    return myPaintingSession != null ? myPaintingSession.getFont() :
           myColorsScheme == null ? super.getFont() :
           doGetFont();
  }

  @Override
  public void globalSchemeChange(@Nullable EditorColorsScheme scheme) {
    updateFonts();
  }

  @Override
  public void updateUI() {
    super.updateUI();
    if (getParent() != null) {
      myUpdateScaleHelper.saveScaleAndRunIfChanged(() -> {
        updateFonts();
      });
    }
  }

  public void changeFontSize(int increment, double scale) {
    double newIncrement = myFontSizeIncrement * scale + increment;
    double newScale = myFontSizeScale * scale;
    int newFontSize = fontSize(newIncrement, newScale);

    int oldFontSize = getFont().getSize();
    if (newFontSize == oldFontSize) return;

    myFontSizeIncrement = newIncrement;
    myFontSizeScale = newScale;

    updateFonts();
  }

  private void updateFonts() {
    updateFonts(true);
  }

  /** A font change resets the grid's per-row heights, which were set for the old font; setting up another table of the view does not. */
  private void updateFonts(boolean resetRowHeights) {
    if (resetRowHeights && myRowHeights != null) myRowHeights.clear();
    Font font = getFont();
    setFont(font);
    setRowHeight(getRowHeight());
    JTableHeader tableHeader = getTableHeader();
    if (tableHeader != null) tableHeader.setFont(getScaledFont(font));
    layoutColumns();
  }

  @Override
  public void uiSettingsChanged(@NotNull UISettings uiSettings) {
    if (changeHeaderFont(uiSettings)) layoutColumns();
  }

  private boolean changeHeaderFont(UISettings settings) {
    JTableHeader header = getTableHeader();
    if (settings == null || header == null || header.getFont() == null) return false;
    int fontSize = header.getFont().getSize();
    float scaledFontSize = UISettingsUtils.with(settings).getPresentationModeFontSize();
    boolean presentationMode = settings.getPresentationMode() && fontSize != scaledFontSize;
    if (presentationMode) header.setFont(header.getFont().deriveFont(scaledFontSize));
    boolean normalMode = !settings.getPresentationMode() && fontSize == scaledFontSize;
    if (normalMode) header.setFont(getScaledFont(header.getFont()));
    return presentationMode || normalMode;
  }

  private int fontSize(double fontSizeIncrement, double fontSizeScale) {
    int baseFontSize = Math.round(UISettingsUtils.getInstance().scaleFontSize(getColorsScheme().getEditorFontSize()));
    int newFontSize = (int)Math.max(fontSizeScale * baseFontSize + fontSizeIncrement, 8);
    return Math.min(Math.max(EditorFontsConstants.getMaxEditorFontSize(), baseFontSize), newFontSize);
  }

  @Override
  public int getRowHeight() {
    return myPaintingSession != null ? myPaintingSession.getRowHeight() : doGetRowHeight();
  }

  public int getTextLineHeight() {
    return (int)Math.ceil(getFontMetrics(getFont()).getHeight() * getColorsScheme().getLineSpacing());
  }

  @Override
  public Color getSelectionForeground() {
    return myPaintingSession != null ? myPaintingSession.getSelectionForeground() :
           myColorsScheme == null ? super.getSelectionForeground() :
           doGetSelectionForeground(getColorsScheme());
  }

  @Override
  public Color getSelectionBackground() {
    return myPaintingSession != null ? myPaintingSession.getSelectionBackground() :
           myColorsScheme == null ? super.getSelectionBackground() :
           doGetSelectionBackground(getColorsScheme());
  }

  @Override
  public Color getForeground() {
    return myPaintingSession != null ? myPaintingSession.getForeground() :
           myColorsScheme == null ? super.getForeground() :
           doGetForeground(getColorsScheme());
  }

  @Override
  public Color getBackground() {
    return myPaintingSession != null ? myPaintingSession.getBackground() :
           myColorsScheme == null ? super.getBackground() :
           doGetBackground(getColorsScheme());
  }

  @Override
  public void setBackground(@NotNull Color bg) {
    // prevent background change events from being fired
  }

  @Override
  public Color getGridColor() {
    return myPaintingSession != null ? myPaintingSession.getGridColor() :
           myColorsScheme == null ? super.getGridColor() :
           doGetGridColor(getColorsScheme());
  }

  @Override
  public void tableChanged(TableModelEvent e) {
    if (myAutoscrollLocker == null) {
      super.tableChanged(e);
      return;
    }
    // The view updates every table for a change of rows, after it has updated the shared sorter and selection.
    if (sharesTheViewRowOrder() && e != null && e.getFirstRow() != TableModelEvent.HEADER_ROW) return;
    // The lock covers only this table's own Swing update; the view tells the grid of the change.
    myAutoscrollLocker.runWithLock(() -> super.tableChanged(e));
  }

  /**
   * Moves a dropped table off the data model it shared with the main table onto an empty one. The shared model then no longer holds it. The
   * private model listeners of {@link JBTable} and {@link TableExpandableItemsHandler} leave a model only when the model property changes.
   * The empty model has no rows and no columns, so the table reads no grid data while it is disposed. Call it after the table has its own
   * row selection model again: a model change clears the row selection.
   */
  void detachModel() {
    mySharedRowSorter = null;
    super.setModel(new RegularGridTableModel(new DataGridListModel(Objects::equals)));
  }

  @Override
  public GridTableModel getModel() {
    return (GridTableModel)super.getModel();
  }

  @Override
  public Object getValueAt(int row, int column) {
    Ref<Object> commonValue = getSharedCommonValue();
    boolean useCommonValue =
      commonValue != null && isEditing() && isCellSelected(row, column) && myCellEditors.allowsMultiEdit(getCellEditor());
    return useCommonValue ? commonValue.get() : super.getValueAt(row, column);
  }

  @Override
  public void removeEditor() {
    try {
      super.removeEditor();
    }
    finally {
      if (myEditedValue != null) myEditedValue.setValue(null);
    }
  }

  private @Nullable Ref<Object> getSharedCommonValue() {
    return myEditedValue == null ? null : myEditedValue.getValue();
  }

  public boolean isTransposed() {
    return getModel() instanceof TransposedGridTableModel;
  }

  /**
   * What {@code point} of this table's header, in the header's coordinates, falls on. A transposed header's cells are grid rows. The parts
   * of a cell are where the header paints them.
   */
  @NotNull ColumnHeaderHit headerHitAt(@NotNull Point point) {
    int localIndex = columnAtPoint(point);
    if (isTransposed()) {
      int modelRow = myRawIndexConverter.row2Model().applyAsInt(localIndex);
      return modelRow < 0 ? ColumnHeaderHit.Outside.INSTANCE : new ColumnHeaderHit.RowCell(ModelIndex.forRow(myGridModel, modelRow));
    }
    int modelColumn = myRawIndexConverter.column2Model().applyAsInt(localIndex);
    if (modelColumn < 0) return ColumnHeaderHit.Outside.INSTANCE;
    ModelIndex<GridColumn> column = ModelIndex.forColumn(myGridModel, modelColumn);
    return new ColumnHeaderHit.ColumnCell(column, headerPartAt(column, localIndex, point));
  }

  private @NotNull ColumnHeaderPart headerPartAt(@NotNull ModelIndex<GridColumn> column, int localIndex, @NotNull Point point) {
    if (myGridModel.getColumn(column) instanceof HierarchicalGridColumn hierarchicalColumn) {
      OptionalInt line = getIndexOfClickedHeaderLine(localIndex, point);
      if (line.isEmpty()) return ColumnHeaderPart.BlankLine.INSTANCE;
      return isLeafColumnHeaderLineClicked(hierarchicalColumn, line.getAsInt())
             ? ColumnHeaderPart.LeafLine.INSTANCE
             : new ColumnHeaderPart.GroupLine(line.getAsInt());
    }
    Rectangle headerRect = tableHeader.getHeaderRect(localIndex);
    MyHeaderCellComponent currentHeader = renderWithActualBounds(tableHeader, localIndex);
    Point relativePoint = new Point(point.x - headerRect.x, point.y);
    JLabel sortLabel = currentHeader.mySortLabels.isEmpty() ? null : currentHeader.mySortLabels.getLast();
    if (currentHeader.filterLabel.getBounds().contains(relativePoint)) return ColumnHeaderPart.FilterIcon.INSTANCE;
    if (sortLabel != null && sortLabel.getBounds().contains(relativePoint)) return ColumnHeaderPart.SortArrow.INSTANCE;
    return ColumnHeaderPart.Name.INSTANCE;
  }

  private boolean isLeafColumnHeaderLineClicked(@NotNull HierarchicalGridColumn hierarchicalColumn, int clickedLineIdx) {
    int leafColumnHeaderLineIdx = hierarchicalColumn.getPathFromRoot().length - 1;
    return leafColumnHeaderLineIdx == clickedLineIdx;
  }

  private OptionalInt getIndexOfClickedHeaderLine(int localIndex, @NotNull Point point) {
    if (localIndex < 0 || localIndex >= getColumnModel().getColumnCount()) return OptionalInt.empty();
    Component headerRenderer = getHeaderRenderer(this, localIndex);

    if (!(headerRenderer instanceof MyHeaderCellComponent customHeaderComponent)) {
      return OptionalInt.empty();
    }

    Rectangle headerRect = getTableHeader().getHeaderRect(localIndex);

    return findIndexOfClickedHeaderLineWithNonEmptyLabel(point, customHeaderComponent, headerRect.x, headerRect.width);
  }

  private static Component getHeaderRenderer(@NotNull GridTable table, int localIndex) {
    TableColumn column = table.getColumnModel().getColumn(localIndex);
    return table
      .getTableHeader()
      .getDefaultRenderer()
      .getTableCellRendererComponent(table, column.getHeaderValue(), false, false, -1, localIndex);
  }

  private MyHeaderCellComponent renderWithActualBounds(JTableHeader header, int localIndex) {

    MyHeaderCellComponent currentHeaderCell = (MyHeaderCellComponent)getHeaderRenderer(this, localIndex);
    var cellRendererPane = new CellRendererPane();
    cellRendererPane.add(currentHeaderCell);
    header.add(cellRendererPane);
    // The same rectangle `BasicTableHeaderUI` gives the renderer when it paints. Another width moves the
    // right-anchored sort and filter labels away from where the user sees them.
    Rectangle headerRect = header.getHeaderRect(localIndex);
    currentHeaderCell.setBounds(0, 0, headerRect.width, headerRect.height);
    currentHeaderCell.validate();
    header.remove(cellRendererPane);

    return currentHeaderCell;
  }

  private OptionalInt findIndexOfClickedHeaderLineWithNonEmptyLabel(@NotNull Point point,
                                                                    @NotNull MyHeaderCellComponent customHeaderComponent,
                                                                    int clickedColumnX,
                                                                    int columnWidth) {
    for (int idx = 0; idx < customHeaderComponent.myNameLabels.size(); ++idx) {
      JLabel nameLabel = customHeaderComponent.myNameLabels.get(idx);
      if (isColumnNameLabelNotEmpty(nameLabel) && isClickedWithinHeaderLine(point, clickedColumnX, columnWidth, idx)) {
        return OptionalInt.of(idx);
      }
    }
    return OptionalInt.empty();
  }

  private static boolean isColumnNameLabelNotEmpty(@NotNull JLabel nameLabel) {
    String labelText = nameLabel.getText();
    return labelText != null && !labelText.isBlank();
  }

  private boolean isClickedWithinHeaderLine(@NotNull Point point, int clickedColumnX, int columnWidth, int idx) {
    Rectangle bounds = getHeaderLineBounds(clickedColumnX, columnWidth, idx);
    return bounds.contains(point);
  }

  private Rectangle getHeaderLineBounds(int clickedColumnX, int columnWidth, int idx) {
    Rectangle headerLineBounds = new Rectangle();
    int rowHeight = getRowHeight();
    headerLineBounds.setLocation(clickedColumnX, rowHeight * idx);
    headerLineBounds.height = rowHeight;
    headerLineBounds.width = columnWidth;

    return headerLineBounds;
  }

  public int getViewRowCount() {
    return isTransposed() ? getColumnCount() : getRowCount();
  }

  public void cancelEditing() {
    TableCellEditor editor = getCellEditor();
    if (editor != null) {
      editor.cancelCellEditing();
    }
  }

  @Override
  public TableCellEditor getCellEditor(int row, int column) {
    Object currentValue = ClientProperty.get(this, GridTableCellEditor.CURRENT_VALUE_CLIENT_PROPERTY_KEY);
    return myCellEditors.editorFor(modelRowAt(row, column), modelColumnAt(row, column), currentValue);
  }

  @Override
  public TableCellRenderer getCellRenderer(int row, int column) {
    TableCellRenderer renderer = myCellRenderers.rendererFor(modelRowAt(row, column), modelColumnAt(row, column));
    return myCellImageCache == null ? renderer : myCellImageCache.wrapCellRenderer(renderer);
  }

  /** Hands a value committed by a cell editor of this table to the view, which writes it to the cell or every selected cell. */
  @Override
  public void setValueAt(Object value, int row, int localIndex) {
    ModelCell cell = modelCellAt(row, localIndex);
    if (cell == null) return;
    myHost.commitEditedValue(value, cell);
  }

  /** The grid row of the cell at {@code row} and {@code localIndex} of this table, or an index of -1 outside the table. */
  @NotNull ModelIndex<GridRow> modelRowAt(int row, int localIndex) {
    // While transposed, a table row is a grid column, and a table column is a grid row.
    return ModelIndex.forRow(myGridModel, myRawIndexConverter.row2Model().applyAsInt(isTransposed() ? localIndex : row));
  }

  /** The grid column of the cell at {@code row} and {@code localIndex} of this table, or an index of -1 outside the table. */
  @NotNull ModelIndex<GridColumn> modelColumnAt(int row, int localIndex) {
    return ModelIndex.forColumn(myGridModel, myRawIndexConverter.column2Model().applyAsInt(isTransposed() ? row : localIndex));
  }

  /** The cell at {@code row} and {@code localIndex} of this table in model indices, or null outside the table. */
  @Nullable ModelCell modelCellAt(int row, int localIndex) {
    ModelIndex<GridRow> modelRow = modelRowAt(row, localIndex);
    ModelIndex<GridColumn> modelColumn = modelColumnAt(row, localIndex);
    return modelRow.asInteger() >= 0 && modelColumn.asInteger() >= 0 ? new ModelCell(modelRow, modelColumn) : null;
  }

  private Font doGetFont() {
    return getScaledFont(getColorsScheme().getFont(EditorFontType.PLAIN));
  }

  private Font getScaledFont(Font font) {
    return UISettings.getInstance().getPresentationMode() ? font :
           font == null ? null : font.deriveFont((float)fontSize(myFontSizeIncrement, myFontSizeScale));
  }

  private int doGetRowHeight() {
    return getTextLineHeight() + getRowMargin() + (isStriped() || !showHorizontalLines ? 1 : 0) + JBUI.scale(4);
  }

  public static Rectangle getLabelTextRect(JLabel label) {
    Dimension pref = label.getPreferredSize();
    Rectangle bounds = label.getBounds();
    Insets insets = label.getInsets();
    int w = Math.min(pref.width, bounds.width);
    bounds.setSize(w - insets.left - insets.right, bounds.height - insets.top - insets.bottom);
    bounds.translate(insets.left, insets.top);
    return bounds;
  }

  private @Nullable GridColumn getColumn(int modelColumnIdx) {
    return myGridModel.getColumn(ModelIndex.forColumn(myGridModel, modelColumnIdx));
  }

  /** The sort arrow that a column header, or the gutter of a transposed grid, shows for a column sorted in {@code direction}. */
  static @NotNull Icon getSortOrderIcon(@NotNull RowSortOrder.Type direction) {
    return switch (direction) {
      case ASC -> AllIcons.General.ArrowUp;
      case DESC -> AllIcons.General.ArrowDown;
      case UNSORTED -> AllIcons.General.ArrowSplitCenterV;
    };
  }

  /** The place in the sort that a header shows next to the arrow of {@code sortMark}, or an empty text. */
  static @NlsSafe @NotNull String getSortOrderText(@NotNull ColumnSortMark sortMark) {
    Integer place = sortMark.getPlace();
    return sortMark.isNumbered() && place != null ? String.valueOf(place) : "";
  }

  private @NotNull ActionCallback layoutColumns() {
    ActionCallback callback = new ActionCallback();
    SwingUtilities.invokeLater(() -> {
      if (SwingUtilities.getWindowAncestor(this) != null && myColumnLayout.resetLayout()) callback.setDone();
    });
    return callback;
  }

  /** The font, colors and row height, read once per painting session rather than for every cell. */
  private class PaintingSession {
    private Font myFont;
    private Color myGridColor;
    private Color myForeground;
    private Color myBackground;
    private Color mySelectionForeground;
    private Color mySelectionBackground;
    private int myRowHeight = -1;

    public Font getFont() {
      return myFont != null ? myFont : (myFont = doGetFont());
    }

    public int getRowHeight() {
      return myRowHeight != -1 ? myRowHeight : (myRowHeight = doGetRowHeight());
    }

    public Color getGridColor() {
      return myGridColor != null ? myGridColor : (myGridColor = doGetGridColor(getColorsScheme()));
    }

    public Color getForeground() {
      return myForeground != null ? myForeground : (myForeground = doGetForeground(getColorsScheme()));
    }

    public Color getBackground() {
      return myBackground != null ? myBackground : (myBackground = doGetBackground(getColorsScheme()));
    }

    public Color getSelectionForeground() {
      return mySelectionForeground != null ? mySelectionForeground : (mySelectionForeground = doGetSelectionForeground(getColorsScheme()));
    }

    public Color getSelectionBackground() {
      return mySelectionBackground != null ? mySelectionBackground : (mySelectionBackground = doGetSelectionBackground(getColorsScheme()));
    }
  }

  /**
   * The header cell of one column. It stacks one line per level of a hierarchical column. Each line has a name and a sort label; the last
   * also has a filter label.
   */
  static class MyHeaderCellComponent extends CellRendererPanel {
    private static final String HEADER_PLACEHOLDER = "    ";

    final GridTable myTable;
    private final List<JLabel> myNameLabels;
    private final JPanel myLinesPanel;
    /** One per line, in the sort slot: the last line shows the sort order, the others a group's expand or collapse arrow. */
    private final List<JLabel> mySortLabels;
    private JLabel filterLabel;
    private final Icon myActiveFilterIcon =
      IconManager.getInstance().withIconBadge(AllIcons.General.Filter, JBUI.CurrentTheme.IconBadge.SUCCESS);

    private final List<JPanel> myHeaderLinePanels;
    private TableResultViewColumn myCurrentColumn;

    private boolean isInitialized = false;

    MyHeaderCellComponent(@NotNull GridTable table) {
      myTable = table;
      myLinesPanel = new JPanel();
      myLinesPanel.setLayout(new BoxLayout(myLinesPanel, BoxLayout.Y_AXIS));
      myNameLabels = new ArrayList<>();
      mySortLabels = new ArrayList<>();
      myHeaderLinePanels = new ArrayList<>();
      setLayout(new BorderLayout());
      add(myLinesPanel, BorderLayout.CENTER);
    }

    @Override
    protected void paintComponent(Graphics g) {
      UISettings.setupAntialiasing(g);
      ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
      setSelected(true);
      super.paintComponent(g);
      if (myTable.myColumnHeaderPainter != null) {
        myTable.myColumnHeaderPainter.paint(g, getNameRect(), getModelIdx());
      }
    }

    protected Rectangle getNameRect() {
      return getLabelTextRect(myNameLabels.getFirst());
    }

    protected int getModelIdx() {
      return myCurrentColumn.getModelIndex();
    }

    /** Roughly four visible column-name characters. */
    private int headerNameMinWidth() {
      var fm = myTable.getFontMetrics(myTable.getFont());
      return fm.charWidth('W') * 4 + JBUI.scale(6);
    }

    /** Label that fades overflowing content into the background of the header cell that holds it. */
    private static class FadingLabel extends LabelWithFallbackFont {
      private static final SwingTextTrimmer NO_TRIM =
        SwingTextTrimmer.createCustomTrimmer((text, _, _) -> text);

      FadingLabel(@NotNull GridTable table) {
        super(table);
        putClientProperty(SwingTextTrimmer.KEY, NO_TRIM);
      }

      /** Icon and text width, excluding insets. */
      private int contentWidth() {
        int width = 0;
        Icon icon = getIcon();
        if (icon != null) width += icon.getIconWidth();
        String text = getText();
        if (text != null && !text.isEmpty() && !text.startsWith("<html")) {
          if (icon != null) width += getIconTextGap();
          width += getFontMetrics(getFont()).stringWidth(text);
        }
        return width;
      }

      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        String text = getText();
        if (text != null && text.startsWith("<html")) return;
        Insets insets = getInsets();
        int available = getWidth() - insets.left - insets.right;
        // Avoid premature blur on tiny overflows.
        if (available <= 0 || contentWidth() <= available + JBUI.scale(4)) return;
        // Keep hover highlights crisp.
        if (isOpaque()) return;
        // The cell's background as it paints now, which changes with the column it renders.
        MyHeaderCellComponent cell = ComponentUtil.getParentOfType(MyHeaderCellComponent.class, this);
        Color bg = cell == null ? null : cell.getHeaderCellBackground();
        if (bg == null) return;
        // Fade to the true right edge; JLabel icons may paint into the right inset.
        int fadeWidth = Math.min(JBUI.scale(12), getWidth() - insets.left);
        int x = getWidth() - fadeWidth;
        Graphics2D g2 = (Graphics2D)g.create();
        try {
          g2.setPaint(new GradientPaint(x, 0, ColorUtil.withAlpha(bg, 0), x + fadeWidth, 0, bg));
          g2.fillRect(x, insets.top, fadeWidth, getHeight() - insets.top - insets.bottom);
        }
        finally {
          g2.dispose();
        }
      }
    }

    /** Lays out one header line, shrinking sort first, then filter, to preserve the name. */
    private final class HeaderLineLayout implements LayoutManager {
      private final JLabel myName;
      private final JLabel mySort;
      private final @Nullable JLabel myFilter;

      private HeaderLineLayout(@NotNull JLabel name, @NotNull JLabel sort, @Nullable JLabel filter) {
        myName = name;
        mySort = sort;
        myFilter = filter;
      }

      @Override public void addLayoutComponent(String name, Component comp) { }
      @Override public void removeLayoutComponent(Component comp) { }

      private boolean hasSort() { return mySort.getIcon() != null || !StringUtil.isEmpty(mySort.getText()); }
      private boolean hasFilter() { return myFilter != null && myFilter.getIcon() != null; }

      private int sortPrefWidth() { return hasSort() ? mySort.getPreferredSize().width : 0; }
      private int filterPrefWidth() { return myFilter != null && myFilter.getIcon() != null ? myFilter.getPreferredSize().width : 0; }
      private int nameFilterGap() { return hasFilter() ? JBUI.scale(2) : 0; }

      @Override
      public Dimension preferredLayoutSize(Container parent) {
        int width = myName.getPreferredSize().width + JBUI.scale(6) + sortPrefWidth();
        if (hasFilter()) width += nameFilterGap() + filterPrefWidth();
        return new Dimension(width, myTable.getRowHeight());
      }

      @Override
      public Dimension minimumLayoutSize(Container parent) {
        return new Dimension(JBUI.scale(10), myTable.getRowHeight());
      }

      @Override
      public void layoutContainer(Container parent) {
        int total = parent.getWidth();
        int height = parent.getHeight();
        int[] d = distribute(total);
        int sortW = d[0], filterW = d[1], trail = d[2];
        int gap = nameFilterGap();
        int nameW = Math.max(0, total - gap - filterW - sortW - trail);

        myName.setBounds(0, 0, nameW, height);
        if (myFilter != null) myFilter.setBounds(nameW + gap, 0, filterW, height);
        // Pin the sort by its kept width (the flexible space sits to its left), so its slot never overlaps the filter.
        mySort.setBounds(total - trail - sortW, 0, sortW, height);
      }

      /** Name slot left after trailing gap and icon slots. */
      int nameWidth(int total) {
        int[] d = distribute(total);
        return Math.max(0, total - nameFilterGap() - d[1] - d[0] - d[2]);
      }

      /** {sortWidth, filterWidth, trailingGap}, shrunk in priority order. */
      private int[] distribute(int total) {
        int gap = nameFilterGap();
        int sortW = sortPrefWidth();
        int filterW = filterPrefWidth();
        int trail = JBUI.scale(6);

        // Remove trailing padding first, so sort can sit flush right.
        int trailDeficit = myName.getPreferredSize().width + gap + filterW + sortW + trail - total;
        if (trailDeficit > 0) trail -= Math.min(trailDeficit, trail);

        int deficit = headerNameMinWidth() - (total - gap - filterW - sortW - trail);
        if (deficit > 0) { int d = Math.min(deficit, sortW); sortW -= d; deficit -= d; }
        if (deficit > 0) { int d = Math.min(deficit, filterW); filterW -= d; }
        return new int[]{sortW, filterW, trail};
      }
    }

    private static int getHeaderLineNum(@Nullable HierarchicalReader reader) {
      return reader == null ? 1 : reader.getDepthOfHierarchy();
    }

    /** {@code tableModelColumn} is a grid column, or a grid row while the table is transposed. */
    public MyHeaderCellComponent prepare(int tableModelColumn, boolean forDisplay, @Nullable HierarchicalReader reader) {
      myCurrentColumn = myTable.myColumnCache.getOrCreateColumn(tableModelColumn);

      if (!isInitialized) {
        initializeLabelsForEachHeaderLine(getHeaderLineNum(reader));
        isInitialized = true;
      }

      resetLabelsValues(myNameLabels);
      resetLabelsValues(mySortLabels);
      resetHeaderLinesBg();
      setBorder(null);

      List<String> headerLineValues = myCurrentColumn.getMultilineHeaderValues();
      if (headerLineValues.size() > myNameLabels.size()) {
        clearAllLabelsAndPanels();
        initializeLabelsForEachHeaderLine(getHeaderLineNum(reader));
      }
      else if (myNameLabels.size() != getHeaderLineNum(reader)) {
        clearAllLabelsAndPanels();
        initializeLabelsForEachHeaderLine(getHeaderLineNum(reader));
      }

      if (headerLineValues.isEmpty()) return this;

      if (myTable.getColumn(tableModelColumn) instanceof HierarchicalGridColumn hierarchicalColumn) {
        boolean isCollapsedSubtree = myTable.myHeaderAppearance.isCollapsedSubtree(hierarchicalColumn);

        for (int i = 0; i < myHeaderLinePanels.size(); ++i) {
          if (isCollapsedSubtree) {
            prepareHeaderLineForCollapsedColumn(i, headerLineValues, hierarchicalColumn, forDisplay);
          }
          else {
            prepareHeaderLineForExpandedColumn(i, headerLineValues, hierarchicalColumn, forDisplay);
          }

          myHeaderLinePanels.get(i).setBackground(getHeaderCellBackground(i));
        }
      }
      else {
        setValueForLastNonEmptyHeaderLine(myCurrentColumn.getHeaderValue().trim(), 0, tableModelColumn, forDisplay);
        setBorder(IdeBorderFactory.createBorder(SideBorder.RIGHT));
      }

      myLinesPanel.revalidate();
      return this;
    }

    private void clearAllLabelsAndPanels() {
      myLinesPanel.removeAll();
      mySortLabels.clear();
      myNameLabels.clear();
      myHeaderLinePanels.clear();
    }

    private void prepareHeaderLineForCollapsedColumn(int headerLineIdx,
                                                     @NotNull List<String> headerLineValues,
                                                     @NotNull HierarchicalGridColumn column,
                                                     boolean forDisplay) {
      setHeaderLineContentForCollapsedColumn(headerLineIdx, headerLineValues, forDisplay, column);
      setHeaderLineBorderForCollapsedColumn(headerLineIdx, headerLineValues, column);
    }

    private void setHeaderLineContentForCollapsedColumn(int headerLineIdx,
                                                        @NotNull List<String> headerLineValues,
                                                        boolean forDisplay,
                                                        @NotNull HierarchicalGridColumn column) {
      boolean isLeafColumnName = headerLineIdx == headerLineValues.size() - 1;
      boolean isResidualLine = headerLineIdx >= headerLineValues.size();

      setPlaceholderIntoHeaderLine(headerLineIdx);

      HierarchicalGridColumn ancestorCorrespondingToHeaderLine = column.getAncestorAtDepth(headerLineIdx);
      if (ancestorCorrespondingToHeaderLine == null || ancestorCorrespondingToHeaderLine.isLeaf()) {
        return;
      }
      List<HierarchicalGridColumn> siblings =
        filter(ancestorCorrespondingToHeaderLine.getSiblings(), (col) -> col != ancestorCorrespondingToHeaderLine);
      boolean allSiblingsCollapsed =
        !siblings.isEmpty() && all(siblings, (col) -> myTable.myHeaderAppearance.isHiddenByCollapse(col));
      if (allSiblingsCollapsed) {
        setPlaceholderIntoHeaderLine(headerLineIdx);
        return;
      }

      if (!isLeafColumnName && !isResidualLine) {
        String headerLineValue = headerLineValues.get(headerLineIdx);
        setHeaderLineValueExceptLast(headerLineValue, headerLineIdx, forDisplay);
        if (!headerLineValue.isBlank()) {
          mySortLabels.get(headerLineIdx).setIcon(forDisplay ? AllIcons.Actions.ArrowExpand : null);
        }
      }
    }

    private void setHeaderLineBorderForCollapsedColumn(int headerLineIdx,
                                                       @NotNull List<String> headerLineValues,
                                                       @NotNull HierarchicalGridColumn column) {
      boolean isLeafColumnName = headerLineIdx == headerLineValues.size() - 1;
      boolean isResidualLine = headerLineIdx >= headerLineValues.size();
      boolean isLastLine = headerLineIdx == myHeaderLinePanels.size() - 1;

      if (isLeafColumnName || isResidualLine || isLastLine) {
        myHeaderLinePanels.get(headerLineIdx).setBorder(IdeBorderFactory.createBorder(SideBorder.RIGHT));
        return;
      }

      int border = SideBorder.NONE;
      boolean nextLineIsLeafColumnName = headerLineIdx == headerLineValues.size() - 2;
      if (!nextLineIsLeafColumnName) {
        border |= SideBorder.BOTTOM;
      }

      HierarchicalGridColumn ancestorCorrespondingToHeaderLine = column.getAncestorAtDepth(headerLineIdx);
      ColumnHeaderAppearance appearance = myTable.myHeaderAppearance;
      if (column.isRightMostChildOfAncestor(ancestorCorrespondingToHeaderLine,
                                            (col) -> !appearance.isEnabled(col) || appearance.isHiddenByCollapse(col))) {
        border |= SideBorder.RIGHT;
      }

      myHeaderLinePanels.get(headerLineIdx).setBorder(IdeBorderFactory.createBorder(border));
    }

    private void prepareHeaderLineForExpandedColumn(int headerLineIdx,
                                                    @NotNull List<String> headerLineValues,
                                                    @NotNull HierarchicalGridColumn column,
                                                    boolean forDisplay) {
      setHeaderLineContentForExpandedColumn(headerLineIdx, headerLineValues, column, forDisplay);
      setHeaderLineBorderForExpandedColumn(headerLineIdx, headerLineValues, column);
    }

    private void setHeaderLineContentForExpandedColumn(int headerLineIdx,
                                                       @NotNull List<String> headerLineValues,
                                                       @NotNull HierarchicalGridColumn column,
                                                       boolean forDisplay) {
      boolean isLeafColumnName = headerLineIdx == headerLineValues.size() - 1;
      boolean isResidualLine = headerLineIdx >= headerLineValues.size();

      if (isLeafColumnName) {
        setValueForLastNonEmptyHeaderLine(headerLineValues.get(headerLineIdx), headerLineIdx, column.getColumnNumber(), forDisplay);
        return;
      }

      if (isResidualLine) {
        setPlaceholderIntoHeaderLine(headerLineIdx);
        return;
      }

      String value = headerLineValues.get(headerLineIdx);
      setHeaderLineValueExceptLast(headerLineValues.get(headerLineIdx), headerLineIdx, forDisplay);
      if (!value.isBlank()) {
        mySortLabels.get(headerLineIdx).setIcon(forDisplay ? AllIcons.Actions.ArrowCollapse : null);
      }
    }

    private void setHeaderLineBorderForExpandedColumn(int headerLineIdx,
                                                      @NotNull List<String> headerLineValues,
                                                      @NotNull HierarchicalGridColumn column) {
      boolean isLeafColumnName = headerLineIdx == headerLineValues.size() - 1;
      boolean isResidualLine = headerLineIdx >= headerLineValues.size();

      if (isLeafColumnName || isResidualLine) {
        myHeaderLinePanels.get(headerLineIdx).setBorder(IdeBorderFactory.createBorder(SideBorder.RIGHT));
        return;
      }

      int border = SideBorder.BOTTOM;
      HierarchicalGridColumn ancestorCorrespondingToHeaderLine = column.getAncestorAtDepth(headerLineIdx);
      ColumnHeaderAppearance appearance = myTable.myHeaderAppearance;
      if (column.isRightMostChildOfAncestor(ancestorCorrespondingToHeaderLine,
                                            (col) -> !appearance.isEnabled(col) || appearance.isHiddenByCollapse(col))) {
        border |= SideBorder.RIGHT;
      }

      int[] path = column.getPathFromRoot();
      int nextSiblingIndex = path[path.length - 1] + 1;
      if (ancestorCorrespondingToHeaderLine == column.getParent() &&
          hasAllSiblingsFromIndexDisabled(column, nextSiblingIndex)) {
        border |= SideBorder.RIGHT;
      }

      myHeaderLinePanels.get(headerLineIdx).setBorder(IdeBorderFactory.createBorder(border));
    }

    private boolean hasAllSiblingsFromIndexDisabled(@NotNull HierarchicalGridColumn column, int startIdx) {
      List<HierarchicalGridColumn> siblings = column.getSiblings();

      if (startIdx < 0 || startIdx >= siblings.size()) return false;

      for (int i = startIdx; i < siblings.size(); ++i) {
        HierarchicalGridColumn sibling = siblings.get(i);

        if (sibling.isLeaf()) {
          if (myTable.myHeaderAppearance.isEnabled(sibling)) return false;
        }
        else {
          HierarchicalGridColumn leftMostChildOfSibling = sibling.getChildren().getFirst();
          if (myTable.myHeaderAppearance.isEnabled(leftMostChildOfSibling)) return false;
          // We do not check every descendant down to the leaf level.
          // It is not possible to disable all child columns without disabling the leftmost column, and vice versa,
          // disabling the leftmost column automatically disables all sibling columns.
        }
      }

      return true;
    }

    private void setPlaceholderIntoHeaderLine(int headerLineIdx) {
      if (headerLineIdx < 0 || headerLineIdx >= myNameLabels.size()) {
        LOG.error("Header line index out of bounds: idx=" + headerLineIdx +
                  ", myNameLabels.size()=" + myNameLabels.size() +
                  ", myHeaderLinePanels.size()=" + myHeaderLinePanels.size());
        return;
      }
      myNameLabels.get(headerLineIdx).setText(HEADER_PLACEHOLDER);
    }

    private void initializeLabelsForEachHeaderLine(int headerLineNum) {
      filterLabel = new FadingLabel(myTable);
      filterLabel.setVerticalAlignment(SwingConstants.CENTER);
      filterLabel.setHorizontalAlignment(SwingConstants.LEFT);
      filterLabel.setBorder(JBUI.Borders.empty(0, 5));

      for (int i = 0; i < headerLineNum; ++i) {
        JLabel nameLabel = new FadingLabel(myTable);
        nameLabel.setHorizontalAlignment(SwingConstants.LEFT);
        nameLabel.setBorder(IdeBorderFactory.createEmptyBorder(CellRenderingUtils.NAME_LABEL_INSETS));
        myNameLabels.add(nameLabel);

        JLabel sortLabel = new FadingLabel(myTable) {
          @Override
          public Font getFont() {
            return RelativeFont.TINY.derive(super.getFont());
          }
        };
        sortLabel.setVerticalAlignment(SwingConstants.CENTER);
        sortLabel.setHorizontalAlignment(SwingConstants.LEFT);
        sortLabel.setIconTextGap(JBUI.scale(0));
        sortLabel.setBorder(IdeBorderFactory.createEmptyBorder(CellRenderingUtils.SORT_LABEL_INSETS));
        mySortLabels.add(sortLabel);

        JLabel filterForLine = i + 1 == headerLineNum ? filterLabel : null;
        JPanel panel = new JPanel(new HeaderLineLayout(nameLabel, sortLabel, filterForLine));
        panel.add(nameLabel);
        panel.add(sortLabel);
        if (filterForLine != null) panel.add(filterForLine);

        myHeaderLinePanels.add(panel);
        myLinesPanel.add(panel);
      }
    }

    private void setHeaderLineValueExceptLast(@NlsSafe String value, int lineIdx, boolean forDisplay) {
      myNameLabels.get(lineIdx).setText(myTable.myAllowMultilineColumnLabel && StringUtil.containsLineBreak(value)
                                        ? "<html>" + StringUtil.replace(value, "\n", "<br>") + "</html>"
                                        : value);
      if (!value.isBlank()) {
        myNameLabels.get(lineIdx).setIcon(forDisplay ? AllIcons.Json.Object : null);
        myNameLabels.get(lineIdx).setHorizontalTextPosition(SwingConstants.RIGHT);
        myNameLabels.get(lineIdx).setVerticalTextPosition(SwingConstants.CENTER);
        myNameLabels.get(lineIdx).setHorizontalAlignment(SwingConstants.LEFT);
      }
    }

    /** {@code tableModelColumn} is a grid column, or a grid row while the table is transposed. */
    private void setValueForLastNonEmptyHeaderLine(@NlsSafe String value, int lineIdx, int tableModelColumn, boolean forDisplay) {
      JLabel nameLabel = myNameLabels.get(lineIdx);
      JLabel sortLabel = mySortLabels.get(lineIdx);

      Icon columnIcon = myCurrentColumn.getIcon(forDisplay);
      nameLabel.setIcon(columnIcon);
      nameLabel.setForeground(getHeaderCellForeground());
      nameLabel.setText(myTable.myAllowMultilineColumnLabel && StringUtil.containsLineBreak(value)
                        ? "<html>" + StringUtil.replace(value, "\n", "<br>") + "</html>"
                        : value);

      Icon sortLabelIcon = null;
      String sortLabelText = "";
      Icon filterLabelIcon = null;
      GridColumn column = myTable.getColumn(tableModelColumn);
      if (!myTable.isTransposed()) {
        if (column != null) {
          ModelIndex<GridColumn> columnIdx = ModelIndex.forColumn(myTable.myGridModel, tableModelColumn);
          ColumnSortMark sortMark = myTable.myHeaderAppearance.sortMark(columnIdx);
          if (sortMark != null) {
            sortLabelIcon = getSortOrderIcon(sortMark.getDirection());
            sortLabelText = getSortOrderText(sortMark);
          }

          filterLabelIcon = switch (myTable.myHeaderAppearance.filterMark(columnIdx)) {
            case NONE -> null;
            case UNFILTERED -> AllIcons.General.Filter;
            case FILTERED -> myActiveFilterIcon;
          };
        }
      }

      nameLabel.setHorizontalAlignment(SwingConstants.LEFT);
      sortLabel.setIcon(sortLabelIcon);
      sortLabel.setText(sortLabelText);
      if (forDisplay) {
        myHeaderLinePanels.get(lineIdx).setBackground(getHeaderCellBackground());
        if (myTable.tableHeader instanceof MyTableHeader tableHeader &&
            tableModelColumn == (tableHeader.myHoveredSortColumn != null ? tableHeader.myHoveredSortColumn.value : -1)) {
          sortLabel.setOpaque(true);
          sortLabel.setBackground(UIUtil.getTableSelectionBackground(false));
        }
        else {
          sortLabel.setOpaque(false);
        }
      }

      boolean filterLabelNeeded = !(column instanceof HierarchicalGridColumn) && (filterLabelIcon != null);
      filterLabel.setVisible(filterLabelNeeded);
      if (filterLabelNeeded) {
        filterLabel.setIcon(filterLabelIcon);
        if (myTable.tableHeader instanceof MyTableHeader tableHeader &&
            tableModelColumn == (tableHeader.myHoveredFilterColumn != null ? tableHeader.myHoveredFilterColumn.value : -1)) {
          filterLabel.setOpaque(true);
          filterLabel.setBackground(UIUtil.getTableSelectionBackground(false));
        }
        else {
          filterLabel.setOpaque(false);
        }
      }
      else {
        filterLabel.setIcon(null);
      }

      // Drop the type icon once the name slot falls below its minimum width.
      if (forDisplay && columnIcon != null &&
          ((HeaderLineLayout)myHeaderLinePanels.get(lineIdx).getLayout()).nameWidth(myCurrentColumn.getWidth()) < headerNameMinWidth()) {
        nameLabel.setIcon(null);
      }
    }

    private void resetHeaderLinesBg() {
      for (JPanel panel : myHeaderLinePanels) {
        panel.setBackground(null);
        panel.setBorder(null);
      }
    }

    private static void resetLabelsValues(List<JLabel> labels) {
      for (JLabel l : labels) {
        l.setText(null);
        l.setIcon(null);
      }
    }

    @Override
    public String getToolTipText(MouseEvent event) {
      return myCurrentColumn != null ? myCurrentColumn.getTooltipText() : super.getToolTipText(event);
    }

    /** The text color of the header cell this component renders. A transposed header shows grid rows, in the row header's colors. */
    private Color getHeaderCellForeground() {
      if (myCurrentColumn == null) return super.getForeground();
      int modelIndex = myCurrentColumn.getModelIndex();
      return myTable.isTransposed() ?
             myTable.myHeaderAppearance.rowHeaderForeground(ModelIndex.forRow(myTable.myGridModel, modelIndex)) :
             myTable.myHeaderAppearance.columnHeaderForeground(ModelIndex.forColumn(myTable.myGridModel, modelIndex));
    }

    /** The background of the first line of the header cell this component renders. */
    private Color getHeaderCellBackground() {
      return getHeaderCellBackground(0);
    }

    /**
     * The background of line {@code headerLine} of the header cell this component renders, or the table's background where the grid's
     * color model gives none. A transposed header shows grid rows, in the row header's colors.
     */
    private Color getHeaderCellBackground(int headerLine) {
      if (myCurrentColumn == null) return super.getBackground();
      int modelIndex = myCurrentColumn.getModelIndex();
      Color color = myTable.isTransposed() ?
                    myTable.myHeaderAppearance.rowHeaderBackground(ModelIndex.forRow(myTable.myGridModel, modelIndex)) :
                    myTable.myHeaderAppearance.columnHeaderBackground(ModelIndex.forColumn(myTable.myGridModel, modelIndex), headerLine);
      return color == null ? myTable.getBackground() : color;
    }
  }

  static class LabelWithFallbackFont extends JBLabel {
    private final JTable myTable;

    LabelWithFallbackFont(@NotNull JTable table) {
      myTable = table;
    }

    @Override
    public Font getFont() {
      return myTable == null ? super.getFont() : getFontWithFallback(myTable.getTableHeader().getFont());
    }
  }

  @Override
  protected @Nullable Color getHoveredRowBackground() {
    return myCellRenderers.hoveredRowBackground();
  }

  public void resetScroll() {
    JScrollBar horizontal = getHorizontalScrollBar();
    if (horizontal != null) horizontal.setValue(0);
    JScrollBar vertical = getVerticalScrollBar();
    if (vertical != null) vertical.setValue(0);
  }

  public boolean isHoveredRowBgHighlightingEnabled() {
    if (isStriped()) {
      return false;
    }

    return switch (myHoveredRowMode) {
      case AUTO -> !getShowHorizontalLines();
      case HIGHLIGHT -> true;
      case NOT_HIGHLIGHT -> false;
    };
  }

  public void setHoveredRowHighlightMode(HoveredRowBgHighlightMode mode) {
    myHoveredRowMode = mode;
  }

  /**
   * Makes the table part of a view. It reports to {@code host} and shares the view's row heights, cell image cache, edited value and
   * row sorter. The sorter comes first, since the row heights are applied through it.
   */
  final void installHost(@NotNull GridTableHost host,
                         @NotNull RowHeightModel rowHeights,
                         @NotNull TableCellImageCache cellImageCache,
                         @NotNull MultiCellEditValue editedValue,
                         @NotNull RowSorter<? extends TableModel> rowSorter) {
    mySharedRowSorter = rowSorter;
    myHost = host;
    myCellImageCache = cellImageCache;
    myEditedValue = editedValue;
    myRowHeights = rowHeights;
    myRowHeights.addListener(new RowHeightModel.Listener() {
      @Override
      public void rowHeightChanged(int modelRow) { applyStoredRowHeight(modelRow); }

      @Override
      public void rowHeightsCleared() { setRowHeight(getRowHeight()); }
    }, this);
    applyStoredRowHeights();
  }

  final void setColumnHeaderPainter(@Nullable ColumnHeaderPainter painter) {
    myColumnHeaderPainter = painter;
  }

  final void installColumnLayout(@NotNull GridColumnLayout<GridRow, GridColumn> layout) {
    myColumnLayout = layout;
  }

  /**
   * The main table's look, which the pinned table copies whenever the view updates it. It holds lines, stripes, header style, hover mode,
   * font zoom, row height, row counts and the header painter.
   */
  record Appearance(boolean striped, boolean horizontalLines, boolean verticalLines, Boolean transparentHeader,
                    boolean multilineLabel, HoveredRowBgHighlightMode hoverMode, double fontIncrement, double fontScale,
                    int rowHeight, int additionalRows, int visibleRows, ColumnHeaderPainter headerPainter) { }

  Appearance captureAppearance() {
    return new Appearance(isStriped(), getShowHorizontalLines(), getShowVerticalLines(), myIsTransparentColumnHeaderBg,
                          myAllowMultilineColumnLabel, myHoveredRowMode, myFontSizeIncrement, myFontSizeScale,
                          getRowHeight(), getAdditionalRowsCount(), getVisibleRowCount(), myColumnHeaderPainter);
  }

  void applyAppearance(Appearance appearance) {
    setStriped(appearance.striped());
    setShowHorizontalLines(appearance.horizontalLines());
    setShowVerticalLines(appearance.verticalLines());
    if (appearance.transparentHeader() != null) setTransparentColumnHeaderBackground(appearance.transparentHeader());
    setAllowMultilineLabel(appearance.multilineLabel());
    setHoveredRowHighlightMode(appearance.hoverMode());
    myFontSizeIncrement = appearance.fontIncrement();
    myFontSizeScale = appearance.fontScale();
    updateFonts(false);
    setRowHeight(appearance.rowHeight());
    setAdditionalRowsCount(appearance.additionalRows());
    setVisibleRowCount(appearance.visibleRows());
    setColumnHeaderPainter(appearance.headerPainter());
  }
}
