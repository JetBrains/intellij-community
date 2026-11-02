package com.intellij.database.run.ui.table;

import com.intellij.application.options.EditorFontsConstants;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridCellRequest;
import com.intellij.database.datagrid.GridCellRequestKt;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridColumnLayout;
import com.intellij.database.datagrid.GridHelper;
import com.intellij.database.datagrid.GridModel;
import com.intellij.database.datagrid.GridRequestSource;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.GridSortingModel;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.database.datagrid.GridUtilCore;
import com.intellij.database.datagrid.HierarchicalColumnsCollapseManager;
import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.HierarchicalGridColumn;
import com.intellij.database.datagrid.HierarchicalReader;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.datagrid.ModelIndexSet;
import com.intellij.database.datagrid.NestedTable;
import com.intellij.database.datagrid.RawIndexConverter;
import com.intellij.database.datagrid.ResultView.HoveredRowBgHighlightMode;
import com.intellij.database.datagrid.ViewIndex;
import com.intellij.database.datagrid.ViewIndexSet;
import com.intellij.database.remote.jdbc.LobInfo;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.database.run.ui.DataGridRequestPlace;
import com.intellij.database.run.ui.EditMaximizedView;
import com.intellij.database.run.ui.GridTableCellEditor;
import com.intellij.database.run.ui.ResultViewWithCells;
import com.intellij.database.run.ui.ValueTabInfoProvider;
import com.intellij.database.run.ui.grid.CacheComponent;
import com.intellij.database.run.ui.grid.CellAttributes;
import com.intellij.database.run.ui.grid.CellRenderingUtils;
import com.intellij.database.run.ui.grid.DummyGridColumnLayout;
import com.intellij.database.run.ui.grid.GridColorsScheme;
import com.intellij.database.run.ui.grid.GridSearchSession;
import com.intellij.database.run.ui.grid.JBTableWithResizableCells;
import com.intellij.database.run.ui.grid.ResizableCellEditorsSupport;
import com.intellij.database.run.ui.grid.TableCellImageCache;
import com.intellij.database.run.ui.grid.editors.GridCellEditorFactory;
import com.intellij.database.run.ui.grid.editors.GridCellEditorFactoryProvider;
import com.intellij.database.run.ui.grid.renderers.GridCellRenderer;
import com.intellij.database.settings.DataGridSettings;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.bookmark.BookmarksManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ide.ui.UISettingsUtils;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.ui.popup.ListPopup;
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
import com.intellij.ui.RelativeFont;
import com.intellij.ui.SideBorder;
import com.intellij.ui.TableCell;
import com.intellij.ui.TableExpandableItemsHandler;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.JBTable;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.SwingTextTrimmer;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.UpdateScaleHelper;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
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
import javax.swing.RowFilter;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
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
import java.util.Comparator;
import java.util.EventObject;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

import static com.intellij.database.datagrid.GridUtilKt.isArrayCell;
import static com.intellij.database.datagrid.GridUtilKt.setupDynamicRowHeight;
import static com.intellij.database.run.ui.DataAccessType.DATA_WITH_MUTATIONS;
import static com.intellij.database.run.ui.EditMaximizedViewKt.EDIT_MAXIMIZED_GRID_KEY;
import static com.intellij.database.run.ui.grid.GridColorSchemeUtil.doGetBackground;
import static com.intellij.database.run.ui.grid.GridColorSchemeUtil.doGetForeground;
import static com.intellij.database.run.ui.grid.GridColorSchemeUtil.doGetGridColor;
import static com.intellij.database.run.ui.grid.GridColorSchemeUtil.doGetSelectionBackground;
import static com.intellij.database.run.ui.grid.GridColorSchemeUtil.doGetSelectionForeground;
import static com.intellij.database.run.ui.grid.TableCellImageCache.MAX_COLUMNS;
import static com.intellij.database.run.ui.grid.TableCellImageCache.MAX_ROWS;
import static com.intellij.database.run.ui.table.UnparsedValueHoverListener.Companion.Place.CENTER;
import static com.intellij.util.containers.ContainerUtil.all;
import static com.intellij.util.containers.ContainerUtil.emptyList;
import static com.intellij.util.containers.ContainerUtil.exists;
import static com.intellij.util.containers.ContainerUtil.filter;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static com.intellij.util.ui.UIUtil.getFontWithFallback;
import static java.awt.event.InputEvent.ALT_DOWN_MASK;

/**
 * A Swing table that shows the cells of the grid columns in its slice of the view's column model. The view has a main table and, while
 * columns are pinned, a pinned table. Both share the view's data model, row order and row selection, so together they look like one grid.
 * A request that may affect both tables goes to the view through {@link GridTableHost}.
 *
 * @author gregsh
 */
class GridTable extends JBTableWithResizableCells
  implements Disposable, CacheComponent, EditorColorsListener, UISettingsListener, UiDataProvider {

  private final DataGrid myGrid;
  private final ViewColumnCache myColumnCache;
  private final Renderers myRenderers;
  private final TableRawIndexConverter myRawIndexConverter;
  private final ActionGroup myColumnHeaderPopupActions;
  private final ActionGroup myRowHeaderPopupActions;
  private GridColumnLayout<GridRow, GridColumn> myColumnLayout = new DummyGridColumnLayout();

  private Point myClickedHeaderPoint;
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
  /**
   * How many column moves the grid is writing to its source; while any is, this table's header allows no column reordering. The view
   * blocks only its main table's header.
   */
  private final AtomicInteger myColumnReorderingBlocks = new AtomicInteger(0);
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
   * takes its columns from the view's cache and shows those of {@code columnModel}. The view's setup of a table clears the selection,
   * so a table added later starts on placeholder selection and column models.
   */
  GridTable(@NotNull DataGrid grid,
            @NotNull GridTableModel model,
            @NotNull ListSelectionModel rowSelection,
            @NotNull TableColumnModel columnModel,
            @NotNull ViewColumnCache columnCache,
            @NotNull ActionGroup columnHeaderPopupActions,
            @NotNull ActionGroup rowHeaderPopupActions) {
    super(model, columnModel);
    setSelectionModel(rowSelection);
    myGrid = grid;
    myColumnHeaderPopupActions = columnHeaderPopupActions;
    myRowHeaderPopupActions = rowHeaderPopupActions;
    myRawIndexConverter = new TableRawIndexConverter(this);
    myColumnCache = columnCache;
    myRenderers = new Renderers(myGrid, this);
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

    repaintScrollPaneOnSelectionChange();
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
    sink.set(PlatformDataKeys.CONTEXT_MENU_POINT, myClickedHeaderPoint);
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
    return myGrid.getStripeRowBackground();
  }

  @Override
  public @NotNull JComponent getComponent() {
    return this;
  }

  /**
   * Before Swing changes the selection, the host commits an open editor; a refusal keeps the selection. While columns are pinned, the host
   * selects an extended range across both tables.
   */
  @Override
  public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend) {
    if (!myHost.stopEditingBeforeSelectionChange(rowIndex, columnIndex)) return;
    if (extend && myHost.selectCellRange(rowIndex, columnIndex, toggle)) return;
    super.changeSelection(rowIndex, columnIndex, toggle, extend);
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
        ViewIndex<GridRow> row = ViewIndex.forRow(myGrid, isTransposed() ? selected.column : selected.row);
        ViewIndex<GridColumn> column = ViewIndex.forColumn(myGrid, isTransposed() ? selected.row : selected.column);

        Object val = myGrid.getDataModel(DATA_WITH_MUTATIONS)
          .getValueAt(row.toModel(myGrid), toModelColumn(column.asInteger()));
        if (val instanceof NestedTable) {
          super.handleSelectionChange(null, processIfUnfocused);
          return;
        }

        boolean isSelected = isRowSelected(selected.row) && isColumnSelected(selected.column);
        EditMaximizedView view = myGrid.getUserData(EDIT_MAXIMIZED_GRID_KEY);
        boolean suppress = isSelected && view != null && view.getCurrentTabInfoProvider() instanceof ValueTabInfoProvider;
        super.handleSelectionChange(suppress ? null : selected, processIfUnfocused);
      }
    };
  }

  public boolean isColumnReorderingBlocked() {
    return myColumnReorderingBlocks.get() > 0;
  }

  /**
   * Blocks column reordering in this table's header while the grid writes a column move to its source. The view blocks only the main
   * table, so the pinned table's header still allows a drag. Calls nest: each true needs a false.
   */
  public void setColumnReorderingBlocked(boolean blocked) {
    if (blocked) {
      myColumnReorderingBlocks.incrementAndGet();
    }
    else {
      myColumnReorderingBlocks.decrementAndGet();
    }
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

  /** Repaints the scroll pane when the row or column selection changes, also after the view replaces the table's column model. */
  private void repaintScrollPaneOnSelectionChange() {
    ListSelectionListener l = ignored -> {
      JScrollPane scrollPane = getScrollPane();
      if (scrollPane != null) scrollPane.repaint();
    };
    getColumnModel().getSelectionModel().addListSelectionListener(l);
    getSelectionModel().addListSelectionListener(l);
    addPropertyChangeListener("columnModel", e -> {
      ((TableColumnModel)e.getOldValue()).getSelectionModel().removeListSelectionListener(l);
      ((TableColumnModel)e.getNewValue()).getSelectionModel().addListSelectionListener(l);
    });
  }

  /** The model column at 1-based {@code columnNumber} among the visible columns, as Go to Row takes it; -1 when out of range. */
  public @NotNull ModelIndex<GridColumn> modelColumnOfNumber(int columnNumber) {
    int modelIndex = columnNumber < 1 || columnNumber >= myGrid.getVisibleColumnCount() + 1
                     ? -1
                     : myGrid.getRawIndexConverter().column2Model().applyAsInt(columnNumber - 1);
    return ModelIndex.forColumn(myGrid, modelIndex);
  }

  /** The 0-based row index of 1-based {@code rowNumber}, or -1. The row may sit on another page. */
  public int rowIndexOfNumber(int rowNumber) {
    return rowNumber < 1 ? -1 : rowNumber - 1;
  }

  private @NotNull GridColorsScheme getColorsScheme() {
    return myGrid.getColorsScheme();
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

  private void dropCaches() {
    if (myCellImageCache != null) myCellImageCache.reset();
  }

  public ViewColumnCache getColumnCache() {
    return myColumnCache;
  }

  /** The keys the view's sorter sorts by locally, or null when the grid doesn't sort locally. */
  @Nullable List<RowSorter.SortKey> sortKeysFromColumnAttributes() {
    return isTransposed() || myGrid.isSortViaOrderBy() ? null : createSortKeys();
  }

  public void orderingAndVisibilityChanged() {
    getModel().fireTableDataChanged();
  }

  private @NotNull List<RowSorter.SortKey> createSortKeys() {
    if (isTransposed()) return emptyList();

    TreeMap<Integer, GridColumn> sortOrderMap = myGrid.getSortOrderMap();
    int orderIdx = 0;
    RowSorter.SortKey[] keys = new RowSorter.SortKey[sortOrderMap.size()];
    for (GridColumn column : sortOrderMap.values()) {
      RowSorter.SortKey key = new RowSorter.SortKey(column.getColumnNumber(),
                                                    myGrid.getSortOrder(column) < 0
                                                    ? SortOrder.ASCENDING
                                                    : SortOrder.DESCENDING);
      keys[orderIdx++] = key;
    }

    return List.of(keys);
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
    if (myHost != null && myCellImageCache != null && myCellImageCache.isCacheEnabled()) myHost.cellImageCacheSizeChanged();
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
    CellAttributes attributes = myGrid.getMarkupModel().getCellAttributes(
      ViewIndex.forRow(myGrid, isTransposed() ? column : row).toModel(myGrid),
      toModelColumn(isTransposed() ? row : column),
      getColorsScheme());
    if (attributes != null) {
      CellRenderingUtils.paintCellEffect(g, getCellRect(row, column, true), attributes);
    }
  }

  private int computeColumnsHashCode() {
    int hash = 0;
    List<GridColumn> columns = myGrid.getDataModel(DataAccessType.DATABASE_DATA).getColumns();
    for (GridColumn column : columns) {
      hash = System.identityHashCode(column) + 31 * hash;
    }
    return hash;
  }

  /**
   * After a column's content language or display type changed, drops its renderer's caches and the view's whole cell image cache. A table
   * that doesn't show the column does nothing.
   */
  void clearCache(@NotNull ModelIndex<GridColumn> columnIdx) {
    if (isTransposed() ? getColumnCount() == 0 : getRowCount() == 0) return;
    int viewRow = isTransposed() ? columnIdx.toView(myGrid).asInteger() : 0;
    int localIndex = isTransposed() ? 0 : localIndexOf(columnIdx.value);

    if (viewRow == -1 || localIndex == -1) {
      // -1 when the other table shows the column, or while createDefaultColumnsFromModel sets a binary display type before creating it
      // (DBE-16996).
      return;
    }

    TableCellImageCache.CachingCellRendererWrapper renderer =
      ObjectUtils.tryCast(getCellRenderer(viewRow, localIndex), TableCellImageCache.CachingCellRendererWrapper.class);
    if (renderer != null) {
      renderer.clearCache();
      GridCellRendererWrapper rendererWrapper = ObjectUtils.tryCast(renderer.getDelegate(), GridCellRendererWrapper.class);
      if (rendererWrapper != null) rendererWrapper.delegate.clearCache();
    }
    dropCaches();
    revalidate();
    repaint();
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
    if (myHost != null) {
      myHost.setRowHeight(row, rowHeight);
    }
    else {
      applyRowHeight(row, rowHeight);
    }
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
      if (grandParent instanceof JScrollPane) {
        JViewport rowHeader = ((JScrollPane)grandParent).getRowHeader();
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
    dropCaches();
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

  public @NotNull LocalFilterState getLocalFilterState() {
    return myGrid.getLocalFilterState();
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

    public Component getHeaderCellRendererComponent(int modelColumn, boolean forDisplay) {
      GridModel<?, GridColumn> model = myTable.myGrid.getDataModel(DATA_WITH_MUTATIONS);
      HierarchicalReader reader = model.getHierarchicalReader();

      return prepare(modelColumn, forDisplay, reader);
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

    private void onColumnHeaderMouseMoved(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull MouseEvent e) {
      var gridColumn = myGrid.getDataModel(DATA_WITH_MUTATIONS).getColumn(columnIdx);
      var isHierarchicalColumn = gridColumn instanceof HierarchicalGridColumn;

      // Map to this table's own column index; a grid index would overrun its column model, which holds only part of the columns.
      var localIndex = myRawIndexConverter.column2View().applyAsInt(columnIdx.value);
      if (localIndex < 0 || localIndex >= columnModel.getColumnCount()) return;
      var headerRect = tableHeader.getHeaderRect(localIndex);

      MyHeaderCellComponent currentHeader = renderWithActualBounds(tableHeader, localIndex);

      var point = e.getPoint();
      var relativePoint = new Point(point.x - headerRect.x, point.y);
      var filterLabel = currentHeader.filterLabel;
      var sortLabel = currentHeader.mySortLabels.isEmpty() ?
                      null :
                      currentHeader.mySortLabels.getLast();

      var previousFilterColumn = myHoveredFilterColumn;
      if (filterLabel.getBounds().contains(relativePoint)) {
        myHoveredFilterColumn = columnIdx;
      }
      else {
        myHoveredFilterColumn = null;
      }

      var previousSortColumn = myHoveredSortColumn;
      if (!isHierarchicalColumn && sortLabel != null && sortLabel.getBounds().contains(relativePoint)) {
        myHoveredSortColumn = columnIdx;
      }
      else {
        myHoveredSortColumn = null;
      }

      if (!Objects.equals(previousFilterColumn, myHoveredFilterColumn) ||
          !Objects.equals(previousSortColumn, myHoveredSortColumn)) {
        tableHeader.repaint();
      }
    }

    {
      setOpaque(false);
      setFocusable(false);
      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(final @NotNull MouseEvent e) {
          processEvent(e);
        }

        @Override
        public void mousePressed(final @NotNull MouseEvent e) {
          processEvent(e);
        }

        @Override
        public void mouseReleased(final @NotNull MouseEvent e) {
          processEvent(e);
        }

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

        private void processEvent(MouseEvent e) {
          if (isTransposed()) {
            int modelRow = myRawIndexConverter.row2Model().applyAsInt(columnAtPoint(e.getPoint()));
            if (modelRow >= 0) {
              onRowHeaderClicked(ModelIndex.forRow(myGrid, modelRow), e);
            }
          }
          else {
            int modelColumn = myRawIndexConverter.column2Model().applyAsInt(columnAtPoint(e.getPoint()));
            if (modelColumn >= 0) {
              onColumnHeaderClicked(ModelIndex.forColumn(myGrid, modelColumn), e);
            }
          }
        }
      });
      addMouseMotionListener(new MouseMotionAdapter() {
        @Override
        public void mouseMoved(MouseEvent e) {
          if (isTransposed()) {
            return;
          }

          int modelColumn = myRawIndexConverter.column2Model().applyAsInt(columnAtPoint(e.getPoint()));
          if (modelColumn >= 0) {
            onColumnHeaderMouseMoved(ModelIndex.forColumn(myGrid, modelColumn), e);
          }
        }
      });
    }

    @Override
    public boolean getReorderingAllowed() {
      if (isTransposed() || isColumnReorderingBlocked()) return false;

      List<GridColumn> columns = myGrid
        .getDataModel(DATA_WITH_MUTATIONS)
        .getColumns();

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

  /** A sorter over this table's model, configured the way the grid sorts rows. The view's {@link TableRowOrder} owns it. */
  @NotNull TableRowSorter<TableModel> createGridRowSorter() {
    TableRowSorter<TableModel> rowSorter = createRowSorter(getModel());
    rowSorter.setMaxSortKeys(1);
    rowSorter.setSortsOnUpdates(isSortOnUpdates());
    return rowSorter;
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

  @NotNull RowFilter<TableModel, Integer> createFilter() {
    RowFilter<TableModel, Integer> baseFilter = isTransposed() ?
                                                new MyTransposedViewColumnFilter(myGrid) :
                                                new MySearchRowFilter(myGrid, getLocalFilterState());
    return new RowFilter<>() {
      @Override
      public boolean include(Entry<? extends TableModel, ? extends Integer> entry) {
        if (!baseFilter.include(entry)) return false;
        int intIdx = entry.getIdentifier().intValue();
        ModelIndex<?> rowIdx = isTransposed() ? ModelIndex.forColumn(myGrid, intIdx) : ModelIndex.forRow(myGrid, intIdx);
        return !myGrid.isRowFilteredOut(rowIdx);
      }
    };
  }

  @Override
  public @NotNull Component prepareRenderer(@NotNull TableCellRenderer renderer, int row, int column) {
    return prepareRenderer(renderer, row, column, true);
  }

  public @NotNull Component prepareRenderer(@NotNull TableCellRenderer renderer, int row, int column, boolean forDisplay) {
    Component component = super.prepareRenderer(renderer, row, column);
    if (!forDisplay) return component;

    ViewIndex<GridRow> rowIdx = ViewIndex.forRow(myGrid, isTransposed() ? column : row);
    ViewIndex<GridColumn> columnIdx = ViewIndex.forColumn(myGrid, isTransposed() ? row : toGridViewColumn(column));
    ModelIndex<GridColumn> modelColumn = toModelColumn(isTransposed() ? row : column);
    boolean selected = isRowSelected(row) && isColumnSelected(column);
    return ResultViewWithCells.prepareComponent(component, myGrid, selected, getCellBackground(rowIdx, modelColumn, selected),
                                                getCellForeground(selected), getBackground(), rowIdx, columnIdx);
  }

  /** Lets {@code action} handle Escape while this table has focus. */
  void registerEscapeAction(@NotNull AbstractAction action) {
    String actionId = "grid.escape";
    getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), actionId);
    getActionMap().put(actionId, action);
  }

  @Nullable Color getCellBackground(@NotNull ViewIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column, boolean selected) {
    return selected ?
           getSelectionBackground() :
           myGrid.getColorModel().getCellBackground(row.toModel(myGrid), column);
  }

  /** The model column at {@code columnAxisIndex}: a local index of this table, or a row of it when transposed. */
  private @NotNull ModelIndex<GridColumn> toModelColumn(int columnAxisIndex) {
    return ModelIndex.forColumn(myGrid, myRawIndexConverter.column2Model().applyAsInt(columnAxisIndex));
  }

  /** The local index of model column {@code modelColumn} in this table, or -1 when the table doesn't show it. */
  int localIndexOf(int modelColumn) {
    return myRawIndexConverter.column2View().applyAsInt(modelColumn);
  }

  /**
   * The grid's view index of this table's column at {@code localIndex}: its position in the view's column model, or -1 when the slice
   * lacks it. A table on a column model of its own keeps the index.
   */
  private int toGridViewColumn(int localIndex) {
    return getColumnModel() instanceof GridColumnSlice slice ? slice.toMaster(localIndex) : localIndex;
  }

  public @NotNull Color getCellForeground(boolean selected) {
    return selected ? getSelectionForeground() : getForeground();
  }

  /** Drops the cached cell images and applies the font again; the view first reinitializes the renderer factories every table shares. */
  public void reinitSettings() {
    dropCaches();
    updateFonts();
  }

  @Override
  public boolean isCellEditable(int row, int column) {
    return myGrid.isCellEditingAllowed();
  }

  /**
   * Runs {@code r}; a cell editor it opens in this table starts with {@code value} rather than the cell's value. That editor moves the
   * focus on commit only if {@code shouldMoveFocus}.
   */
  public void withCurrentValue(@Nullable Object value, boolean shouldMoveFocus, @NotNull Runnable r) {
    ClientProperty.put(this, GridTableCellEditor.CURRENT_VALUE_CLIENT_PROPERTY_KEY, value);
    ClientProperty.put(this, GridTableCellEditor.SUPPRESS_MOVE_FOCUS_CLIENT_PROPERTY_KEY, !shouldMoveFocus);
    try {
      r.run();
    }
    finally {
      ClientProperty.remove(this, GridTableCellEditor.CURRENT_VALUE_CLIENT_PROPERTY_KEY);
      ClientProperty.remove(this, GridTableCellEditor.SUPPRESS_MOVE_FOCUS_CLIENT_PROPERTY_KEY);
    }
  }

  @Override
  public boolean editCellAt(int row, int column, EventObject e) {
    // JTable finishes only its own editor; the host first commits an editor open in another table of the view.
    if (!stopEditingBeforeEdit()) return false;
    ClientProperty.put(this, GridTableCellEditor.EDITING_STARTER_CLIENT_PROPERTY_KEY, e);
    if (shouldDisplayValueEditor(row, column)) {
      showValueEditor(e);
      return false;
    }
    try {
      return WriteIntentReadAction.compute(() -> super.editCellAt(row, column, e));
    }
    finally {
      ClientProperty.put(this, GridTableCellEditor.EDITING_STARTER_CLIENT_PROPERTY_KEY, null);
    }
  }

  /** Commits an editor open in the view's other table. False when it refuses, and no edit starts in this table. */
  private boolean stopEditingBeforeEdit() {
    return myHost == null || myHost.stopEditingBeforeEdit();
  }

  private boolean shouldDisplayValueEditor(int row, int column) {
    var cellValue = super.getValueAt(row, column);
    if ((cellValue instanceof LobInfo.ClobInfo clob && clob.isFullyReloaded()) ||
        (cellValue instanceof LobInfo.BlobInfo blob && blob.isFullyReloaded()) ||
        isArrayViewCell(row, column)) {
      return true;
    }
    return false;
  }

  private boolean isArrayViewCell(int row, int column) {
    var settings = GridUtil.getSettings(myGrid);
    if (Registry.is("database.new.arrays.editor", false) && (settings != null && !settings.isEditArrayAsText())) {
      int modelColumnIdx = convertColumnIndexToModel(column);
      int modelRowIdx = convertRowIndexToModel(row);
      var columnIndex = ModelIndex.forColumn(myGrid, modelColumnIdx);
      var rowIndex = ModelIndex.forRow(myGrid, modelRowIdx);
      return isArrayCell(GridCellRequestKt.request(myGrid, rowIndex, columnIndex));
    }
    return false;
  }

  private void showValueEditor(EventObject e) {
    if (e instanceof MouseEvent mouseEvent && mouseEvent.getClickCount() < 2) return;
    if (e instanceof KeyEvent && !UIUtil.isReallyTypedEvent((KeyEvent)e)) return;

    myHost.openValueEditor();
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
           myGrid == null ? super.getFont() :
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
           myGrid == null ? super.getSelectionForeground() :
           doGetSelectionForeground(getColorsScheme());
  }

  @Override
  public Color getSelectionBackground() {
    return myPaintingSession != null ? myPaintingSession.getSelectionBackground() :
           myGrid == null ? super.getSelectionBackground() :
           doGetSelectionBackground(getColorsScheme());
  }

  @Override
  public Color getForeground() {
    return myPaintingSession != null ? myPaintingSession.getForeground() :
           myGrid == null ? super.getBackground() :
           doGetForeground(getColorsScheme());
  }

  @Override
  public Color getBackground() {
    return myPaintingSession != null ? myPaintingSession.getBackground() :
           myGrid == null ? super.getForeground() :
           doGetBackground(getColorsScheme());
  }

  @Override
  public void setBackground(@NotNull Color bg) {
    // prevent background change events from being fired
  }

  @Override
  public Color getGridColor() {
    return myPaintingSession != null ? myPaintingSession.getGridColor() :
           myGrid == null ? super.getGridColor() :
           doGetGridColor(getColorsScheme());
  }

  @Override
  public void tableChanged(TableModelEvent e) {
    if (myGrid == null) {
      super.tableChanged(e);
      return;
    }
    // The view updates every table for a change of rows, after it has updated the shared sorter and selection.
    if (sharesTheViewRowOrder() && e != null && e.getFirstRow() != TableModelEvent.HEADER_ROW) return;
    // The lock covers only this table's own Swing update; the view tells the grid of the change.
    myGrid.getAutoscrollLocker().runWithLock(() -> super.tableChanged(e));
  }

  /**
   * Moves a dropped table off the data model it shared with the main table, so the model no longer holds it. The private model
   * listeners of {@link JBTable} and {@link TableExpandableItemsHandler} leave a model only when the model property changes.
   * Call it after the table has its own row selection model again: a model change clears the row selection.
   */
  void detachModel() {
    mySharedRowSorter = null;
    super.setModel(new RegularGridTableModel(myGrid));
  }

  @Override
  public GridTableModel getModel() {
    return (GridTableModel)super.getModel();
  }

  @Override
  public Object getValueAt(int row, int column) {
    Ref<Object> commonValue = getSharedCommonValue();
    boolean useCommonValue = commonValue != null && isEditing() && isCellSelected(row, column) && isMultiEditingAllowed();
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

  private void onRowHeaderClicked(ModelIndex<GridRow> rowIdx, MouseEvent e) {
    if (e.getID() != MouseEvent.MOUSE_PRESSED) return;

    if (myGrid.isHeaderSelecting() && isTransposed()) {
      int localIndex = myRawIndexConverter.row2View().applyAsInt(rowIdx.value);
      selectHeaderColumn(localIndex, e);
    }

    if (e.isPopupTrigger()) {
      invokeRowPopup(e.getComponent(), e.getX(), e.getY());
    }
  }

  // A popup changes no state of the grid, so it may stay in the table until DBE-27115 moves both header popups.
  void invokeRowPopup(@NotNull Component component, int x, int y) {
    if (myRowHeaderPopupActions != ActionGroup.EMPTY_GROUP) {
      ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu("TableResultViewHeader", myRowHeaderPopupActions);
      popupMenu.getComponent().show(component, x, y);
    }
  }

  private void onColumnHeaderClicked(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull MouseEvent e) {
    if (e.getID() == MouseEvent.MOUSE_CLICKED && e.getButton() == MouseEvent.BUTTON1) {
      handleClickOnColumnHeader(columnIdx, e);
      return;
    }
    if (e.getID() != MouseEvent.MOUSE_CLICKED && e.getButton() == MouseEvent.BUTTON2 && e.getClickCount() == 1) {
      if (columnIdx.value >= 0) myHost.hideColumn(columnIdx);
      return;
    }
    if (e.isPopupTrigger()) {
      if (myGrid.isHeaderSelecting() && !isTransposed()) {
        int localIndex = myRawIndexConverter.column2View().applyAsInt(columnIdx.value);
        // Right-clicking a column already in the selection keeps it; only a click outside collapses to that column.
        if (!isColumnSelected(localIndex)) selectHeaderColumn(localIndex, e);
      }

      invokeColumnPopup(columnIdx, e.getComponent(), new Point(e.getX(), e.getY()));
    }
  }

  private void handleClickOnColumnHeader(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull MouseEvent e) {
    GridColumn column = myGrid.getDataModel(DATA_WITH_MUTATIONS).getColumn(columnIdx);
    if (column instanceof HierarchicalGridColumn hierarchicalColumn) {
      handleClickOnHierarchicalColumnHeader(hierarchicalColumn, columnIdx, e);
    }
    else {
      // Map to this table's own column index; a grid index would overrun its column model, which holds only part of the columns.
      var localIndex = myRawIndexConverter.column2View().applyAsInt(columnIdx.value);
      if (localIndex < 0 || localIndex >= columnModel.getColumnCount()) return;
      var headerRect = tableHeader.getHeaderRect(localIndex);

      MyHeaderCellComponent currentHeader = renderWithActualBounds(tableHeader, localIndex);

      var point = e.getPoint();
      var relativePoint = new Point(point.x - headerRect.x, point.y);
      var filterLabel = currentHeader.filterLabel;
      var sortLabel = currentHeader.mySortLabels.isEmpty() ?
                      null :
                      currentHeader.mySortLabels.getLast();

      if (filterLabel.getBounds().contains(relativePoint)) {
        myHost.showLocalFilter(columnIdx, new RelativePoint(e.getComponent(), point));
      }
      else if (sortLabel != null && sortLabel.getBounds().contains(relativePoint)) {
        handleClickToSortColumn(columnIdx, e);
      }
      else {
        selectHeaderColumn(localIndex, e);
      }
    }
  }

  private void handleClickOnHierarchicalColumnHeader(@NotNull HierarchicalGridColumn hierarchicalColumn,
                                                     @NotNull ModelIndex<GridColumn> columnIdx,
                                                     @NotNull MouseEvent e) {
    OptionalInt clickedHeaderLineIndex = getIndexOfClickedHeaderLine(columnIdx, e);
    if (clickedHeaderLineIndex.isEmpty()) return;

    if (isLeafColumnHeaderLineClicked(hierarchicalColumn, clickedHeaderLineIndex.getAsInt())) {
      // Handle sorting if the clicked header line corresponds to a leaf column.
      handleClickToSortColumn(columnIdx, e);
    }
    else {
      // Collapse the columns subtree for any other clicked header line.
      myHost.collapseColumnGroup(columnIdx, clickedHeaderLineIndex.getAsInt());
    }
  }

  static boolean isColumnDisabledOrCollapsed(@NotNull HierarchicalGridColumn column, @NotNull DataGrid grid) {
    return !isColumnEnabled(column, grid) || isSubtreeHiddenDueToCollapse(column, grid);
  }

  private static boolean isColumnEnabled(@NotNull HierarchicalGridColumn column, @NotNull DataGrid grid) {
    if (column.isLeaf()) {
      return grid.isColumnEnabled(modelIndexOf(column, grid));
    }

    return grid.isColumnEnabled(modelIndexOf(getFirstItem(column.getChildren()), grid));
  }

  private static @NotNull ModelIndex<GridColumn> modelIndexOf(@NotNull GridColumn column, @NotNull DataGrid grid) {
    return ModelIndex.forColumn(grid, column.getColumnNumber());
  }

  private static boolean isSubtreeHiddenDueToCollapse(@NotNull HierarchicalGridColumn column, @NotNull DataGrid grid) {
    HierarchicalColumnsCollapseManager collapseManager = grid.getHierarchicalColumnsCollapseManager();
    if (collapseManager == null) return false;

    if (column.isLeaf()) {
      return collapseManager.isColumnHiddenDueToCollapse(column);
    }

    return collapseManager.isColumnHiddenDueToCollapse(getFirstItem(column.getLeaves()));
  }

  private boolean isLeafColumnHeaderLineClicked(@NotNull HierarchicalGridColumn hierarchicalColumn, int clickedLineIdx) {
    int leafColumnHeaderLineIdx = hierarchicalColumn.getPathFromRoot().length - 1;
    return leafColumnHeaderLineIdx == clickedLineIdx;
  }

  private void handleClickToSortColumn(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull MouseEvent e) {
    if (!GridHelper.get(myGrid).isSortingApplicable(columnIdx)) return;
    boolean alt = e.getModifiersEx() == ALT_DOWN_MASK;
    DataGridSettings settings = GridUtil.getSettings(myGrid);
    GridSortingModel<GridRow, GridColumn> model = myGrid.getDataHookup().getSortingModel();
    boolean supportsAdditiveSorting = model == null || !model.isSortingEnabled() || model.supportsAdditiveSorting();
    myHost.toggleSortOrder(columnIdx, supportsAdditiveSorting && (settings == null || settings.isAddToSortViaAltClick()) == alt);
  }

  private OptionalInt getIndexOfClickedHeaderLine(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull MouseEvent e) {
    int localIndex = myRawIndexConverter.column2View().applyAsInt(columnIdx.value);
    if (localIndex < 0 || localIndex >= getColumnModel().getColumnCount()) return OptionalInt.empty();
    Component headerRenderer = getHeaderRenderer(this, localIndex);

    if (!(headerRenderer instanceof MyHeaderCellComponent customHeaderComponent)) {
      return OptionalInt.empty();
    }

    Rectangle headerRect = getTableHeader().getHeaderRect(localIndex);

    return findIndexOfClickedHeaderLineWithNonEmptyLabel(e, customHeaderComponent, headerRect.x, headerRect.width);
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

  private OptionalInt findIndexOfClickedHeaderLineWithNonEmptyLabel(@NotNull MouseEvent e,
                                                                    @NotNull MyHeaderCellComponent customHeaderComponent,
                                                                    int clickedColumnX,
                                                                    int columnWidth) {
    for (int idx = 0; idx < customHeaderComponent.myNameLabels.size(); ++idx) {
      JLabel nameLabel = customHeaderComponent.myNameLabels.get(idx);
      if (isColumnNameLabelNotEmpty(nameLabel) && isClickedWithinHeaderLine(e, clickedColumnX, columnWidth, idx)) {
        return OptionalInt.of(idx);
      }
    }
    return OptionalInt.empty();
  }

  private static boolean isColumnNameLabelNotEmpty(@NotNull JLabel nameLabel) {
    String labelText = nameLabel.getText();
    return labelText != null && !labelText.isBlank();
  }

  private boolean isClickedWithinHeaderLine(@NotNull MouseEvent e, int clickedColumnX, int columnWidth, int idx) {
    Rectangle bounds = getHeaderLineBounds(clickedColumnX, columnWidth, idx);
    return bounds.contains(e.getPoint());
  }

  private Rectangle getHeaderLineBounds(int clickedColumnX, int columnWidth, int idx) {
    Rectangle headerLineBounds = new Rectangle();
    int rowHeight = getRowHeight();
    headerLineBounds.setLocation(clickedColumnX, rowHeight * idx);
    headerLineBounds.height = rowHeight;
    headerLineBounds.width = columnWidth;

    return headerLineBounds;
  }

  // Suppressed until DBE-27115 splits the grid module into frontend and backend parts.
  @SuppressWarnings("SplitModeApiUsage")
  void invokeColumnPopup(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull Component component, @NotNull Point point) {
    if (myColumnHeaderPopupActions != ActionGroup.EMPTY_GROUP) {
      myHost.setContextColumn(columnIdx);
      myClickedHeaderPoint = point;
      ListPopup popupMenu = JBPopupFactory.getInstance()
        .createActionGroupPopup(null, myColumnHeaderPopupActions, DataManager.getInstance().getDataContext(getComponent()),
                                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true);
      popupMenu.addListener(new JBPopupListener() {
        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          SwingUtilities.invokeLater(() -> {
            // if not clicked on another column
            if (myHost.columnPopupClosed(columnIdx)) myClickedHeaderPoint = null;
          });
        }
      });
      popupMenu.show(new RelativePoint(component, point));
    }
  }

  void selectHeaderColumn(int localIndex, @NotNull MouseEvent e) {
    myHost.selectHeaderColumn(localIndex, e);
  }

  public @NotNull ModelIndexSet<GridRow> getVisibleRows() {
    int rowCount = isTransposed() ? getModel().getColumnCount() : getModel().getRowCount();
    int[] viewRowIndices = new int[rowCount];
    for (int i = 0; i < rowCount; i++) {
      viewRowIndices[i] = i;
    }
    ModelIndexSet<GridRow> rowIndices = ViewIndexSet.forRows(myGrid, viewRowIndices).toModel(myGrid);
    return validIndexSet(rowIndices, rowIndices1 -> ModelIndexSet.forRows(myGrid, rowIndices1));
  }

  public int getViewRowCount() {
    return isTransposed() ? getColumnCount() : getRowCount();
  }

  private @NotNull <T> ModelIndexSet<T> validIndexSet(@NotNull ModelIndexSet<T> indexSet,
                                                      @NotNull Function<int[], ModelIndexSet<T>> factory) {
    IntList validIndices = new IntArrayList(indexSet.size());
    for (ModelIndex<T> idx : indexSet.asIterable()) {
      if (idx.isValid(myGrid)) {
        validIndices.add(idx.asInteger());
      }
    }
    return factory.fun(validIndices.toIntArray());
  }

  public boolean stopEditing() {
    TableCellEditor editor = getCellEditor();
    if (editor == null) return true;

    ModelIndexSet<GridRow> editedRows;
    ModelIndexSet<GridColumn> editedColumns;
    if (isMultiEditingAllowed()) {
      // The grid's selection covers both tables of the view, in model indices.
      editedRows = myGrid.getSelectionModel().getSelectedRows();
      editedColumns = myGrid.getSelectionModel().getSelectedColumns();
    }
    else {
      int tableModelColumn = convertColumnIndexToModel(getEditingColumn());
      int tableModelRow = convertRowIndexToModel(getEditingRow());
      editedRows = ModelIndexSet.forRows(myGrid, isTransposed() ? tableModelColumn : tableModelRow);
      editedColumns = ModelIndexSet.forColumns(myGrid, isTransposed() ? tableModelRow : tableModelColumn);
    }

    // Only asks the grid, which may ask the user to drop unsubmitted changes; the commit itself writes through the host.
    return myGrid.isSafeToUpdate(editedRows, editedColumns, editor.getCellEditorValue()) && editor.stopCellEditing();
  }

  public void cancelEditing() {
    TableCellEditor editor = getCellEditor();
    if (editor != null) {
      editor.cancelCellEditing();
    }
  }

  public boolean isMultiEditingAllowed() {
    // The grid's selection covers both tables of the view.
    ModelIndexSet<GridColumn> indexSet = myGrid.getSelectionModel().getSelectedColumns();
    int selectedRowCount = myGrid.getSelectionModel().getSelectedRowCount();
    List<GridColumn> columns = myGrid.getDataModel(DataAccessType.DATABASE_DATA).getColumns(indexSet);
    GridColumn uniqueColumn = GridHelper.get(myGrid).findUniqueColumn(myGrid, columns);
    GridTableCellEditor editor = ObjectUtils.tryCast(getCellEditor(), GridTableCellEditor.class);
    boolean uniqueOk = uniqueColumn == null || selectedRowCount == 1 || (editor != null && editor.allowsUniqueMultiEdit());
    return getSharedCommonValue() != null &&
           uniqueOk &&
           GridHelper.get(myGrid).canEditTogether(myGrid, columns);
  }

  @Override
  protected TableRowSorter<TableModel> createRowSorter(final TableModel model) {
    final GridTableModel m = getModel();
    return new TableRowSorter<>(m) {
      {
        setModelWrapper(new ModelWrapper<>() {
          @Override
          public TableModel getModel() {
            return m;
          }

          @Override
          public int getColumnCount() {
            return m.getColumnCount();
          }

          @Override
          public int getRowCount() {
            return m.getRowCount();
          }

          @Override
          public Object getValueAt(int row, int column) {
            return getRow(row);
          }

          @Override
          public Integer getIdentifier(int row) {
            return row;
          }
        });
      }

      @Override
      public void toggleSortOrder(int modelColumn) {
        // Does nothing: Swing calls this only once for a double click, so header clicks sort in onColumnHeaderClicked.
      }

      @Override
      protected boolean useToString(int column) {
        return false;
      }

      @Override
      public Comparator<?> getComparator(int modelColumnIdx) {
        Comparator<?> comparator = null;
        if (!isTransposed()) {
          comparator = myGrid.getComparator(ModelIndex.forColumn(myGrid, modelColumnIdx));
        }
        return comparator != null ? comparator : super.getComparator(modelColumnIdx);
      }

      @Override
      public boolean isSortable(int modelColumn) {
        return !isTransposed() && myGrid.getComparator(ModelIndex.forColumn(myGrid, modelColumn)) != null;
      }
    };
  }

  @Override
  public TableCellEditor getCellEditor(int row, int column) {
    ModelIndex<GridRow> rowIdx = ViewIndex.forRow(myGrid, isTransposed() ? column : row).toModel(myGrid);
    ModelIndex<GridColumn> columnIdx = toModelColumn(isTransposed() ? row : column);
    GridCellRequest<GridRow, GridColumn> request = GridCellRequestKt.request(myGrid, rowIdx, columnIdx);
    Object currentValue = ClientProperty.get(this, GridTableCellEditor.CURRENT_VALUE_CLIENT_PROPERTY_KEY);
    if (currentValue != null) {
      request = GridCellRequestKt.overrideValue(request, currentValue);
    }
    GridCellEditorFactory editorFactory = GridCellEditorFactoryProvider.provideEditorFactory(request);
    GridColumn dataColumn = request.getColumn();
    return dataColumn != null && !GridUtilCore.isRowId(dataColumn) && !GridUtilCore.isVirtualColumn(dataColumn) && editorFactory != null ?
           new GridTableCellEditor(myGrid, rowIdx, columnIdx, editorFactory) :
           null;
  }

  @Override
  public TableCellRenderer getCellRenderer(int row, int column) {
    TableCellRenderer renderer = myRenderers.getRenderer(row, column);
    return myCellImageCache == null ? renderer : myCellImageCache.wrapCellRenderer(renderer);
  }

  /** Hands a value committed by a cell editor of this table to the view, which writes it to the cell or every selected cell. */
  @Override
  public void setValueAt(Object value, int row, int localIndex) {
    int modelRow = myRawIndexConverter.row2Model().applyAsInt(isTransposed() ? localIndex : row);
    int modelColumn = myRawIndexConverter.column2Model().applyAsInt(isTransposed() ? row : localIndex);
    if (modelRow < 0 || modelColumn < 0) return;
    myHost.setValueAt(value, ModelIndex.forRow(myGrid, modelRow), ModelIndex.forColumn(myGrid, modelColumn),
                      true, new GridRequestSource(new DataGridRequestPlace(myGrid)), null);
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

  private @Nullable GridRow getRow(int modelRowIdx) {
    return myGrid.getDataModel(DATA_WITH_MUTATIONS).getRow(ModelIndex.forRow(myGrid, modelRowIdx));
  }

  private @Nullable GridColumn getColumn(int modelColumnIdx) {
    return myGrid.getDataModel(DATA_WITH_MUTATIONS).getColumn(ModelIndex.forColumn(myGrid, modelColumnIdx));
  }

  public static @NotNull Icon getSortOrderIcon(int sortOrder) {
    return sortOrder < 0 ? AllIcons.General.ArrowUp :
           sortOrder > 0 ? AllIcons.General.ArrowDown :
           AllIcons.General.ArrowSplitCenterV;
  }

  private @NotNull ActionCallback layoutColumns() {
    ActionCallback callback = new ActionCallback();
    SwingUtilities.invokeLater(() -> {
      if (SwingUtilities.getWindowAncestor(this) != null && myColumnLayout.resetLayout()) callback.setDone();
    });
    return callback;
  }

  public static @NlsSafe String getSortOrderText(int sortOrder) {
    return sortOrder != 0 ? String.valueOf(Math.abs(sortOrder)) : "";
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


  private static final class Renderers {
    private final DataGrid myGrid;
    final GridTable myTable;
    final Map<GridCellRenderer, TableCellRenderer> myTableCellRenderers = new Reference2ObjectOpenHashMap<>();

    Renderers(DataGrid grid, GridTable table) {
      myGrid = grid;
      myTable = table;
    }

    public @NotNull TableCellRenderer getRenderer(int row, int column) {
      ModelIndex<GridRow> rowIdx = ViewIndex.forRow(myGrid, myTable.isTransposed() ? column : row).toModel(myGrid);
      ModelIndex<GridColumn> columnIdx = myTable.toModelColumn(myTable.isTransposed() ? row : column);
      GridCellRequest<GridRow, GridColumn> request = GridCellRequestKt.request(myGrid, rowIdx, columnIdx);
      GridCellRenderer gridCellRenderer = GridCellRenderer.getRenderer(request);

      TableCellRenderer renderer = myTableCellRenderers.get(gridCellRenderer);
      if (renderer == null) {
        renderer = new GridCellRendererWrapper(gridCellRenderer, myTable);
        myTableCellRenderers.put(gridCellRenderer, renderer);
      }
      return renderer;
    }
  }

  private static final class GridCellRendererWrapper implements TableCellRenderer {
    final GridCellRenderer delegate;
    final GridTable myTable;

    private GridCellRendererWrapper(GridCellRenderer renderer, GridTable table) {
      delegate = renderer;
      myTable = table;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      DataGrid grid = delegate.myGrid;
      ViewIndex<GridRow> rowIdx = ViewIndex.forRow(grid, myTable.isTransposed() ? column : row);
      // The renderer works in the grid's column space, not this table's. In a transposed table its row is the grid column.
      ViewIndex<GridColumn> columnIdx =
        ViewIndex.forColumn(grid, myTable.isTransposed() ? row : myTable.toGridViewColumn(column));
      return delegate.getComponent(rowIdx, columnIdx, value);
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

    /** Label that fades overflowing content into the header background. */
    private static class FadingLabel extends LabelWithFallbackFont {
      private static final SwingTextTrimmer NO_TRIM =
        SwingTextTrimmer.createCustomTrimmer((text, _, _) -> text);

      private final Supplier<Color> myBackground;

      FadingLabel(@NotNull GridTable table, @NotNull Supplier<Color> background) {
        super(table);
        myBackground = background;
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
        Color bg = myBackground.get();
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

    public MyHeaderCellComponent prepare(int modelColumn, boolean forDisplay, @Nullable HierarchicalReader reader) {
      myCurrentColumn = myTable.getColumnCache().getOrCreateColumn(modelColumn);

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

      GridColumn column = myTable.getColumn(modelColumn);
      boolean isHierarchicalColumn = column instanceof HierarchicalGridColumn;

      if (isHierarchicalColumn) {
        HierarchicalColumnsCollapseManager collapseManager =
          myTable.myGrid.getHierarchicalColumnsCollapseManager();
        boolean isCollapsedSubtree = collapseManager != null
                                     && collapseManager.isColumnCollapsedSubtree(column);

        for (int i = 0; i < myHeaderLinePanels.size(); ++i) {
          if (isCollapsedSubtree) {
            prepareHeaderLineForCollapsedColumn(i, headerLineValues, (HierarchicalGridColumn)column, forDisplay);
          }
          else {
            prepareHeaderLineForExpandedColumn(i, headerLineValues, (HierarchicalGridColumn)column, forDisplay);
          }

          myHeaderLinePanels.get(i).setBackground(getHeaderCellBackground(i));
        }
      }
      else {
        setValueForLastNonEmptyHeaderLine(myCurrentColumn.getHeaderValue().trim(), 0, modelColumn, forDisplay);
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
        !siblings.isEmpty() && all(siblings, (col) -> isSubtreeHiddenDueToCollapse(col, myTable.myGrid));
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
      if (column.isRightMostChildOfAncestor(ancestorCorrespondingToHeaderLine,
                                            (col) -> isColumnDisabledOrCollapsed(col, myTable.myGrid))) {
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
      if (column.isRightMostChildOfAncestor(ancestorCorrespondingToHeaderLine,
                                            (col) -> isColumnDisabledOrCollapsed(col, myTable.myGrid))) {
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
          if (isColumnEnabled(sibling, myTable.myGrid)) return false;
        }
        else {
          HierarchicalGridColumn leftMostChildOfSibling = sibling.getChildren().getFirst();
          if (isColumnEnabled(leftMostChildOfSibling, myTable.myGrid)) return false;
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
      filterLabel = new FadingLabel(myTable, this::getHeaderCellBackground);
      filterLabel.setVerticalAlignment(SwingConstants.CENTER);
      filterLabel.setHorizontalAlignment(SwingConstants.LEFT);
      filterLabel.setBorder(JBUI.Borders.empty(0, 5));

      for (int i = 0; i < headerLineNum; ++i) {
        JLabel nameLabel = new FadingLabel(myTable, this::getHeaderCellBackground);
        nameLabel.setHorizontalAlignment(SwingConstants.LEFT);
        nameLabel.setBorder(IdeBorderFactory.createEmptyBorder(CellRenderingUtils.NAME_LABEL_INSETS));
        myNameLabels.add(nameLabel);

        JLabel sortLabel = new FadingLabel(myTable, this::getHeaderCellBackground) {
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

    private void setValueForLastNonEmptyHeaderLine(@NlsSafe String value, int lineIdx, int modelColumn, boolean forDisplay) {
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
      GridColumn column = myTable.getColumn(modelColumn);
      if (!myTable.isTransposed()) {
        if (column != null) {
          ModelIndex<GridColumn> columnIdx = ModelIndex.forColumn(myTable.myGrid, modelColumn);
          if (GridHelper.get(myTable.myGrid).isSortingApplicable(columnIdx)) {
            if (myTable.myGrid.getComparator(columnIdx) != null) {
              int sortOrder = myTable.myGrid.getSortOrder(column);
              sortLabelIcon = getSortOrderIcon(sortOrder);
              sortLabelText = myTable.myGrid.countSortedColumns() > 1 ? getSortOrderText(sortOrder) : "";
            }
          }

          LocalFilterState localFilterState = myTable.myGrid.getLocalFilterState();
          if (localFilterState.isEnabled()) {
            if (localFilterState.columnFilterEnabled(columnIdx)) {
              filterLabelIcon = myActiveFilterIcon;
            }
            else {
              filterLabelIcon = AllIcons.General.Filter;
            }
          }
        }
      }

      nameLabel.setHorizontalAlignment(SwingConstants.LEFT);
      sortLabel.setIcon(sortLabelIcon);
      sortLabel.setText(sortLabelText);
      if (forDisplay) {
        myHeaderLinePanels.get(lineIdx).setBackground(getHeaderCellBackground());
        if (myTable.tableHeader instanceof MyTableHeader tableHeader &&
            modelColumn == (tableHeader.myHoveredSortColumn != null ? tableHeader.myHoveredSortColumn.value : -1)) {
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
            modelColumn == (tableHeader.myHoveredFilterColumn != null ? tableHeader.myHoveredFilterColumn.value : -1)) {
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

    private Color getHeaderCellForeground() {
      if (myCurrentColumn == null) return super.getForeground();
      return myTable.isTransposed() ?
             myTable.myGrid.getColorModel().getRowHeaderForeground(
               ModelIndex.forRow(myTable.myGrid, myCurrentColumn.getModelIndex())
             ) :
             myTable.myGrid.getColorModel().getColumnHeaderForeground(
               ModelIndex.forColumn(myTable.myGrid, myCurrentColumn.getModelIndex())
             );
    }

    private Color getHeaderCellBackground() {
      return getHeaderCellBackground(0);
    }

    private Color getHeaderCellBackground(int headerLine) {
      if (myCurrentColumn == null) return super.getBackground();
      Color color = myTable.isTransposed() ?
                    myTable.myGrid.getColorModel().getRowHeaderBackground(
                      ModelIndex.forRow(myTable.myGrid, myCurrentColumn.getModelIndex())
                    ) :
                    myTable.myGrid.getColorModel().getColumnHeaderBackground(
                      ModelIndex.forColumn(myTable.myGrid, myCurrentColumn.getModelIndex()),
                      headerLine
                    );
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

  private static class MyTransposedViewColumnFilter extends RowFilter<TableModel, Integer> {
    final DataGrid myGrid;

    MyTransposedViewColumnFilter(@NotNull DataGrid grid) {
      myGrid = grid;
    }

    @Override
    public boolean include(Entry<? extends TableModel, ? extends Integer> entry) {
      ModelIndex<GridColumn> column = ModelIndex.forColumn(myGrid, entry.getIdentifier().intValue());
      return column.isValid(myGrid) && myGrid.isColumnEnabled(column);
    }
  }

  private static class MySearchRowFilter extends RowFilter<TableModel, Integer> {
    private final DataGrid myGrid;
    private final LocalFilterState myLocalFilterState;

    MySearchRowFilter(@NotNull DataGrid grid, @NotNull LocalFilterState state) {
      myGrid = grid;
      myLocalFilterState = state;
    }

    @Override
    public boolean include(Entry<? extends TableModel, ? extends Integer> entry) {
      final ModelIndex<GridRow> row = ModelIndex.forRow(myGrid, entry.getIdentifier().intValue());
      if (!myLocalFilterState.include(myGrid, row, null)) {
        return false;
      }
      //noinspection unchecked
      GridSearchSession<GridRow, GridColumn> searchSession = ObjectUtils.tryCast(myGrid.getSearchSession(), GridSearchSession.class);
      if (searchSession == null || !searchSession.isFilteringEnabled() ||
          StringUtil.isEmpty(searchSession.getFindModel().getStringToFind()) || searchSession.isSearchInProgress()) {
        return true;
      }

      return !myGrid.getVisibleColumns().asIterable().filter(column -> searchSession.isMatchedCell(row, column)).isEmpty();
    }
  }

  @Override
  protected @Nullable Color getHoveredRowBackground() {
    return myGrid.getHoveredRowBackground();
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
   * font zoom, row height and row counts.
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
