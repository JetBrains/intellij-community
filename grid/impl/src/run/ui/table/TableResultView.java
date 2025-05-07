package com.intellij.database.run.ui.table;

import com.intellij.application.options.EditorFontsConstants;
import com.intellij.database.actions.ShowEditMaximizedAction;
import com.intellij.database.datagrid.*;
import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.HierarchicalGridColumn;
import com.intellij.database.extractors.DisplayType;
import com.intellij.database.remote.jdbc.LobInfo;
import com.intellij.database.run.actions.ColumnLocalFilterAction;
import com.intellij.database.run.ui.*;
import com.intellij.database.run.ui.grid.*;
import com.intellij.database.run.ui.grid.editors.GridCellEditorFactory;
import com.intellij.database.run.ui.grid.editors.GridCellEditorFactoryProvider;
import com.intellij.database.run.ui.grid.renderers.GridCellRenderer;
import com.intellij.database.run.ui.grid.renderers.GridCellRendererFactories;
import com.intellij.database.run.ui.table.statisticsPanel.StatisticsPanelMode;
import com.intellij.database.run.ui.table.statisticsPanel.StatisticsTableHeader;
import com.intellij.database.settings.DataGridSettings;
import com.intellij.database.settings.DataGridSettings.AutoTransposeMode;
import com.intellij.find.FindModel;
import com.intellij.find.SearchSession;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.bookmark.BookmarksManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ide.ui.UISettingsUtils;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.Magnificator;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.SwingTextTrimmer;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.UpdateScaleHelper;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableModelEvent;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;

import static com.intellij.database.datagrid.GridUtilKt.setupDynamicRowHeight;
import static com.intellij.database.run.ui.DataAccessType.DATA_WITH_MUTATIONS;
import static com.intellij.database.run.ui.EditMaximizedViewKt.EDIT_MAXIMIZED_GRID_KEY;
import static com.intellij.database.run.ui.GridTableCellEditor.TABLE_CELL_EDITOR_PROPERTY;
import static com.intellij.database.run.ui.grid.GridColorSchemeUtil.*;
import static com.intellij.database.run.ui.grid.TableCellImageCache.MAX_COLUMNS;
import static com.intellij.database.run.ui.grid.TableCellImageCache.MAX_ROWS;
import static com.intellij.database.run.ui.table.UnparsedValueHoverListener.Companion.Place.CENTER;
import static com.intellij.util.containers.ContainerUtil.*;
import static com.intellij.util.ui.SwingTextTrimmer.ELLIPSIS_AT_RIGHT;
import static com.intellij.util.ui.UIUtil.getFontWithFallback;
import static java.awt.event.InputEvent.ALT_DOWN_MASK;

/**
 * @author gregsh
 */
public final class TableResultView extends JBTableWithResizableCells
  implements ResultView, ResultViewWithCells, ResultViewWithColumns, ResultViewWithRows, EditorColorsListener, UISettingsListener, UiDataProvider {

  private final DataGrid myResultPanel;
  private final MyTableColumnCache myColumnCache;
  private final Renderers myRenderers;
  private final TableCellImageCache myCellImageCache;
  private final TableRawIndexConverter myRawIndexConverter;
  private final ActionGroup myColumnHeaderPopupActions;
  private final ActionGroup myRowHeaderPopupActions;
  private final GridColumnLayout<GridRow, GridColumn> myColumnLayout;
  private final GridSelectionGrower myGrower;
  private final List<Consumer<Boolean>> mySelectionListeners = new ArrayList<>();

  private ModelIndex<GridColumn> myClickedHeaderColumnIdx;
  private Point myClickedHeaderPoint;
  private double myFontSizeIncrement = 0.0d;
  private double myFontSizeScale = 1.0d;
  private final UpdateScaleHelper myUpdateScaleHelper = new UpdateScaleHelper();

  private int myColumnsHashCode;
  private PaintingSession myPaintingSession;

  private Ref<Object> myCommonValue;
  private boolean myWasAutomaticallyTransposed = false;
  private boolean myDisableSelectionListeners = false;
  private boolean myIsShowRowNumbers;
  private Consumer<Boolean> myColumnHeaderBgListener;
  private Boolean myIsTransparentColumnHeaderBg;
  private boolean myAllowMultilineColumnLabel = false;
  private final AtomicInteger editingBlocked = new AtomicInteger(0); // TODO: currently only locks column reordering
  private HoveredRowBgHighlightMode myHoveredRowMode = HoveredRowBgHighlightMode.AUTO;
  private final TableFloatingToolbar myFloatingToolbar;

  private StatisticsTableHeader myStatisticsHeader;

  public TableResultView(@NotNull DataGrid resultPanel,
                         @NotNull ActionGroup columnHeaderPopupActions,
                         @NotNull ActionGroup rowHeaderPopupActions) {
    super(new RegularGridTableModel(resultPanel), new MyTableColumnModel());
    myResultPanel = resultPanel;
    myCellImageCache = new TableCellImageCache(this, this);
    myColumnHeaderPopupActions = columnHeaderPopupActions;
    myRowHeaderPopupActions = rowHeaderPopupActions;
    myRawIndexConverter = new TableRawIndexConverter(this, () -> isTransposed());
    myColumnCache = new MyTableColumnCache();
    myRenderers = new Renderers(myResultPanel, this);
    myClickedHeaderColumnIdx = ModelIndex.forColumn(myResultPanel, -1);
    myColumnLayout = ApplicationManager.getApplication().isUnitTestMode()
                     ? new DummyGridColumnLayout()
                     : GridHelper.get(myResultPanel).createColumnLayout(this, myResultPanel);
    myGrower = new GridSelectionGrower(myResultPanel, this, this);
    getTableHeader().setDefaultRenderer(createHeaderRenderer());
    ComponentWithExpandableItems<?> tableHeader = ObjectUtils.tryCast(getTableHeader(), ComponentWithExpandableItems.class);
    if (tableHeader != null) tableHeader.setExpandableItemsEnabled(false);
    putClientProperty(BookmarksManager.ALLOWED, true);

    new ResizableCellEditorsSupport(this);
    setupFocusListener();
    setupMagnificator();
    setEnableAntialiasing(true);
    setShowLastHorizontalLine(true);

    setFont(getColorsScheme().getFont(EditorFontType.PLAIN));
    updateFonts();

    adjustDefaultActions();
    addPropertyChangeListener(TABLE_CELL_EDITOR_PROPERTY, e -> GridUtil.activeGridChanged(resultPanel));
    new TableSelectionModel(this, myResultPanel);
    new TableGoToRowHelper(this, myResultPanel);
    new TableAggregatorWidgetHelper(this, myResultPanel);
    new TablePositionWidgetHelper(this, myResultPanel);
    new TableScrollPositionManager(this, myResultPanel);
    myFloatingToolbar = new TableFloatingToolbar(this, myResultPanel, myResultPanel.getCoroutineScope());

    getColumnModel().getSelectionModel().addListSelectionListener(e -> myGrower.reset());

    myResultPanel.addDataGridListener(new DataGridListener() {
      @Override
      public void onValueEdited(DataGrid dataGrid, @Nullable Object object) {
        myCommonValue = Ref.create(object);
      }
    }, this);

    addSelectionChangedListener(isAdjusting -> {
      getScrollPane().repaint();
    });
    new UnparsedValueHoverListener(CENTER, this).addTo(this);
    MessageBusConnection connection = resultPanel.getProject().getMessageBus().connect(this);
    setShowHorizontalLines(false);

    var moveColumnListener = new MoveColumnListener(myResultPanel, this);
    getTableHeader().addMouseListener(moveColumnListener);
    columnModel.addColumnModelListener(moveColumnListener);

    if (myResultPanel instanceof DataGridWithNestedTables dataGridWithNestedTables &&
        dataGridWithNestedTables.isNestedTableSupportEnabled()) {
      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          if (e.getButton() == MouseEvent.BUTTON3) return;
          ModelIndex<GridRow> row = ViewIndex.forRow(myResultPanel, rowAtPoint(e.getPoint())).toModel(myResultPanel);
          ModelIndex<GridColumn> col = ViewIndex.forColumn(myResultPanel, columnAtPoint(e.getPoint())).toModel(myResultPanel);
          if (dataGridWithNestedTables.onCellClick(row, col)) {
            e.consume();
          }
        }
      });
    }
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(PlatformDataKeys.CONTEXT_MENU_POINT, myClickedHeaderPoint);
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
    return myResultPanel.getStripeRowBackground();
  }

  @Override
  public @NotNull JComponent getComponent() {
    return this;
  }

  @Override
  public void showRowNumbers(boolean v) {
    myIsShowRowNumbers = v;
    TableScrollPane parent = ComponentUtil.getParentOfType(TableScrollPane.class, this);
    if (parent == null) return;
    if (isTransposed()) {
      parent.setRowHeaderView(myResultPanel.createRowHeader(this));
      return;
    }
    if (myIsShowRowNumbers) parent.setRowHeaderView(myResultPanel.createRowHeader(this));
    else parent.setRowHeader(null);
  }

  @Override
  public void setTransparentColumnHeaderBackground(boolean v) {
    myIsTransparentColumnHeaderBg = v;
    if (myColumnHeaderBgListener != null) myColumnHeaderBgListener.accept(myIsTransparentColumnHeaderBg);
  }

  public void addColumnHeaderBackgroundChangedListener(@NotNull Consumer<Boolean> listener) {
    myColumnHeaderBgListener = listener;
    if (myIsTransparentColumnHeaderBg != null) myColumnHeaderBgListener.accept(myIsTransparentColumnHeaderBg);
  }

  @Override
  public void setAllowMultilineLabel(boolean v) {
    myAllowMultilineColumnLabel = v;
  }

  @Override
  protected @NotNull ExpandableItemsHandler<TableCell> createExpandableItemsHandler() {
    return new TableExpandableItemsHandler(this) {
      @Override
      protected void handleSelectionChange(TableCell selected, boolean processIfUnfocused) {
        if (selected == null) {
          super.handleSelectionChange(null, processIfUnfocused);
          return;
        }
        SelectionModel<GridRow, GridColumn> selectionModel = SelectionModelUtil.get(myResultPanel, TableResultView.this);
        ViewIndex<GridRow> row = ViewIndex.forRow(myResultPanel, isTransposed() ? selected.column : selected.row);
        ViewIndex<GridColumn> column = ViewIndex.forColumn(myResultPanel, isTransposed() ? selected.row : selected.column);

        Object val = myResultPanel.getDataModel(DATA_WITH_MUTATIONS)
          .getValueAt(row.toModel(myResultPanel), column.toModel(myResultPanel));
        if (val instanceof NestedTable) {
          super.handleSelectionChange(null, processIfUnfocused);
          return;
        }

        boolean isSelected = selectionModel.isSelected(row, column);
        EditMaximizedView view = myResultPanel.getUserData(EDIT_MAXIMIZED_GRID_KEY);
        boolean suppress = isSelected && view != null && view.getCurrentTabInfoProvider() instanceof ValueTabInfoProvider;
        super.handleSelectionChange(suppress ? null : selected, processIfUnfocused);
      }
    };
  }

  @Override
  public void setValueAt(@Nullable Object v,
                         @NotNull ModelIndex<GridRow> row,
                         @NotNull ModelIndex<GridColumn> column,
                         boolean allowImmediateUpdate,
                         @NotNull GridRequestSource source) {
    int viewRowIdx = isTransposed() ? column.toView(myResultPanel).asInteger() : row.toView(myResultPanel).asInteger();
    int viewColumnIdx = isTransposed() ? row.toView(myResultPanel).asInteger() : column.toView(myResultPanel).asInteger();
    setValueAt(v, viewRowIdx, viewColumnIdx, allowImmediateUpdate, source);
  }

  @Override
  public void setTransposed(boolean transposed) {
    myWasAutomaticallyTransposed = false;
    if (isTransposed() == transposed) return;
    GridUtil.saveAndRestoreSelection(myResultPanel, () -> {
      doTranspose();
      createDefaultColumnsFromModel();
    });
  }

  public boolean isEditingBlocked() {
    return editingBlocked.get() > 0;
  }

  public void setEditingBlocked(boolean editingBlocked) {
    if (editingBlocked) {
      this.editingBlocked.incrementAndGet();
    }
    else {
      this.editingBlocked.decrementAndGet();
    }
  }

  @Override
  public @NotNull JScrollBar getVerticalScrollBar() {
    return getScrollPane().getVerticalScrollBar();
  }

  private @NotNull JScrollPane getScrollPane() {
    return Objects.requireNonNull(ComponentUtil.getParentOfType((Class<? extends JScrollPane>)JScrollPane.class, this));
  }

  @Override
  public @NotNull JScrollBar getHorizontalScrollBar() {
    return getScrollPane().getHorizontalScrollBar();
  }

  @Override
  public void resetLayout() {
    myColumnLayout.resetLayout();
  }

  @Override
  public void growSelection() {
    myGrower.growSelection();
  }

  @Override
  public void shrinkSelection() {
    myGrower.shrinkSelection();
  }

  @Override
  public void addSelectionChangedListener(@NotNull Consumer<Boolean> listener) {
    ListSelectionListener l = e -> {
      if (!myDisableSelectionListeners) {
        listener.accept(e.getValueIsAdjusting());
      }
    };
    getColumnModel().getSelectionModel().addListSelectionListener(l);
    getSelectionModel().addListSelectionListener(l);
    mySelectionListeners.add(listener);
  }

  @Override
  public void restoreColumnsOrder(Map<Integer, ModelIndex<GridColumn>> expectedToModel) {
    if (isTransposed()) return;
    IntUnaryOperator column2View = getRawIndexConverter().column2View();
    int columnsCount = myResultPanel.getVisibleColumnCount();
    for (int expectedPos = 0; expectedPos < columnsCount; expectedPos++) {
      ModelIndex<GridColumn> modelIndex = expectedToModel.get(expectedPos);
      if (modelIndex == null) continue;
      int actualPos = column2View.applyAsInt(modelIndex.value);
      if (actualPos != -1 && actualPos != expectedPos) moveColumn(actualPos, expectedPos);
    }
  }

  private void removeViewColumnFromColumnModel(ViewIndex<?> viewColumnIdx) {
    getTableHeader().setDraggedColumn(null); // a workaround for JDK-6586009
    myResultPanel.runWithIgnoreSelectionChanges(
      () -> getColumnModel().removeColumn(getColumnModel().getColumn(viewColumnIdx.asInteger())));
  }

  private void addColumnAndMoveToTheCorrectPosition(ModelIndex<?> modelColumnIdx) {
    addColumn(getColumnCache().getOrCreateColumn(modelColumnIdx.asInteger()));

    int lastColumnIndex = getColumnCount() - 1;
    myResultPanel.runWithIgnoreSelectionChanges(() -> {
      for (int viewTargetColumnIdx = 0; viewTargetColumnIdx < lastColumnIndex; viewTargetColumnIdx++) {
        if (getColumnModel().getColumn(viewTargetColumnIdx).getModelIndex() > modelColumnIdx.asInteger()) {
          moveColumn(lastColumnIndex, viewTargetColumnIdx);
          break;
        }
      }
    });
  }

  public void setViewColumnVisible(ModelIndex<?> modelColumnIdx, boolean visible) {
    ViewIndex<?> viewColumnIdx = modelColumnIdx.toView(myResultPanel);
    if (visible && viewColumnIdx.asInteger() < 0) {
      boolean firstTimeShown = !getColumnCache().hasCachedColumn(modelColumnIdx.asInteger());
      addColumnAndMoveToTheCorrectPosition(modelColumnIdx);
      if (firstTimeShown) {
        myColumnLayout.columnsShown(
          isTransposed() ?
          ModelIndexSet.forRows(myResultPanel, modelColumnIdx.asInteger()) :
          ModelIndexSet.forColumns(myResultPanel, modelColumnIdx.asInteger()));
      }
    }
    else if (!visible && viewColumnIdx.asInteger() >= 0) {
      removeViewColumnFromColumnModel(viewColumnIdx);
    }
  }

  public @NotNull ModelIndex<GridColumn> uiColumn(int uiColumn) {
    int modelIndex = uiColumn < 1 || uiColumn >= myResultPanel.getVisibleColumnCount() + 1
                     ? -1
                     : getRawIndexConverter().column2Model().applyAsInt(uiColumn - 1);
    return ModelIndex.forColumn(myResultPanel, modelIndex);
  }

  public int fromRealRowIdx(int uiRow) {
    // produced model index can refer to pages other than current
    return uiRow < 1 ? -1 : uiRow - 1;
  }

  @Override
  public void showFirstCell(int rowNumOnCurrentPage) {
    myResultPanel.showCell(fromRealRowIdx(rowNumOnCurrentPage), uiColumn(0));
  }

  private @NotNull GridColorsScheme getColorsScheme() {
    return myResultPanel.getColorsScheme();
  }

  @Override
  public void searchSessionUpdated() {
    updateRowFilter();
    getComponent().repaint();
  }

  public @Nullable JComponent getCellRendererComponent(@NotNull ViewIndex<GridRow> viewRow,
                                                       @NotNull ViewIndex<GridColumn> viewColumn,
                                                       boolean forDisplay) {
    if (!viewRow.isValid(myResultPanel) || !viewColumn.isValid(myResultPanel)) {
      return null;
    }

    int row = (isTransposed() ? viewColumn : viewRow).asInteger();
    int column = (isTransposed() ? viewRow : viewColumn).asInteger();
    TableCellRenderer renderer = getCellRenderer(row, column);
    if (renderer == null) {
      return null;
    }

    return (JComponent)prepareRenderer(renderer, row, column, forDisplay);
  }

  @Override
  public void setColumnEnabled(@NotNull ModelIndex<GridColumn> columnIdx, boolean state) {
    if (isTransposed()) {
      getModel().fireTableDataChanged();
    }
    else {
      setViewColumnVisible(columnIdx, state);
    }
  }

  @Override
  public void setRowEnabled(@NotNull ModelIndex<GridRow> rowIdx, boolean state) {
    if (isTransposed()) {
      setViewColumnVisible(rowIdx, state);
    }
    else {
      getModel().fireTableDataChanged();
    }
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
    myCellImageCache.reset();
  }

  public MyTableColumnCache getColumnCache() {
    return myColumnCache;
  }

  @Override
  public @NotNull ModelIndex<GridColumn> getContextColumn() {
    return myClickedHeaderColumnIdx;
  }

  @Override
  public void updateSortKeysFromColumnAttributes() {
    RowSorter<? extends TableModel> rowSorter = getRowSorter();
    if (rowSorter != null) {
      rowSorter.setSortKeys(isTransposed() || myResultPanel.isSortViaOrderBy() ? null : createSortKeys());
    }
  }

  @Override
  public void orderingAndVisibilityChanged() {
    getModel().fireTableDataChanged();
  }

  private @NotNull List<RowSorter.SortKey> createSortKeys() {
    if (isTransposed()) return emptyList();

    TreeMap<Integer, GridColumn> sortOrderMap = myResultPanel.getSortOrderMap();
    int orderIdx = 0;
    RowSorter.SortKey[] keys = new RowSorter.SortKey[sortOrderMap.size()];
    for (GridColumn column : sortOrderMap.values()) {
      RowSorter.SortKey key = new RowSorter.SortKey(column.getColumnNumber(),
                                                    myResultPanel.getSortOrder(column) < 0
                                                    ? SortOrder.ASCENDING
                                                    : SortOrder.DESCENDING);
      keys[orderIdx++] = key;
    }

    return List.of(keys);
  }

  @Override
  public @NotNull RawIndexConverter getRawIndexConverter() {
    return myRawIndexConverter;
  }

  @Override
  public void addMouseListenerToComponents(@NotNull MouseListener listener) {
    getScrollPane().addMouseListener(listener);
    addMouseListener(listener);
  }

  @Override
  public boolean supportsCustomSearchSession() {
    return true;
  }

  @Override
  public @NotNull DataGridSearchSession createSearchSession(@Nullable FindModel findModel, @Nullable Component previousFilterComponent) {
    FindModel newFindModel = new FindModel();
    if (findModel != null) newFindModel.copyFrom(findModel);
    DataGridSearchSession.configureFindModel(myResultPanel, newFindModel);
    return new DataGridSearchSession(myResultPanel.getProject(), myResultPanel, newFindModel, previousFilterComponent);
  }

  @Override
  public void searchSessionStarted(@NotNull SearchSession searchSession) {
    if (!(searchSession instanceof GridSearchSession<?, ?>)) return;
    ((GridSearchSession<?, ?>)searchSession).addListener(this, this);
  }

  public void doTranspose() {
    myColumnCache.retainColumns(emptyList());
    myColumnLayout.invalidateCache();
    setModel(isTransposed() ? new RegularGridTableModel(myResultPanel) : new TransposedGridTableModel(myResultPanel));
    showRowNumbers(myIsShowRowNumbers);
    myResultPanel.updateSortKeysFromColumnAttributes();
    getModel().fireTableDataChanged();
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
    if (myColumnsHashCode != 0) return;
    int hash = computeColumnsHashCode();
    if (hash == 0) return;
    layoutColumns().doWhenDone(() -> myColumnsHashCode = hash);
  }

  @Override
  protected void paintComponent(@NotNull Graphics g) {
    adjustCacheSize();
    super.paintComponent(g);
    paintCellsEffects(g);
  }

  private void adjustCacheSize() {
    if (!myCellImageCache.isCacheEnabled()) return;
    TableColumnModel columnModel = getColumnModel();
    int columnCount = columnModel == null ? 0 : columnModel.getColumnCount();
    int rowCount = getRowCount();
    Rectangle visibleRect = getVisibleRect();
    if (columnCount == 0 || rowCount == 0 || visibleRect.isEmpty()) return;

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
    myCellImageCache.adjustCacheSize(rowsMax * colsMax * factor);
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
    CellAttributes attributes = myResultPanel.getMarkupModel().getCellAttributes(
      ViewIndex.forRow(myResultPanel, isTransposed() ? column : row).toModel(myResultPanel),
      ViewIndex.forColumn(myResultPanel, isTransposed() ? row : column).toModel(myResultPanel),
      getColorsScheme());
    if (attributes != null) {
      CellRenderingUtils.paintCellEffect(g, getCellRect(row, column, true), attributes);
    }
  }

  private int computeColumnsHashCode() {
    int hash = 0;
    List<GridColumn> columns = myResultPanel.getDataModel(DataAccessType.DATABASE_DATA).getColumns();
    for (GridColumn column : columns) {
      hash = System.identityHashCode(column) + 31 * hash;
    }
    return hash;
  }

  @Override
  public boolean isViewModified() {
    for (int viewColumnIdx = 0; viewColumnIdx < getColumnCount(); viewColumnIdx++) {
      ViewIndex<?> viewIndex =
        isTransposed() ? ViewIndex.forRow(myResultPanel, viewColumnIdx) : ViewIndex.forColumn(myResultPanel, viewColumnIdx);
      ModelIndex<?> modelIndex = viewIndex.toModel(myResultPanel);
      if (modelIndex.isValid(myResultPanel) && viewIndex.isValid(myResultPanel) && modelIndex.asInteger() != viewIndex.asInteger()) {
        return true;
      }
    }

    for (TableResultViewColumn tableColumn : myColumnCache) {
      if (!tableColumn.isWidthSetByLayout()) {
        return true;
      }
    }

    int defaultRowHeight = getRowHeight();
    for (int i = 0; i < getRowCount(); i++) {
      if (getRowHeight(i) != defaultRowHeight) {
        return true;
      }
    }

    return false;
  }

  @Override
  public void contentLanguageUpdated(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull Language language) {
    clearCache(columnIdx);
  }

  @Override
  public void displayTypeUpdated(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull DisplayType displayType) {
    clearCache(columnIdx);
  }

  private void clearCache(@NotNull ModelIndex<GridColumn> columnIdx) {
    if (isTransposed() ? getColumnCount() == 0 : getRowCount() == 0) return;
    int viewRow = isTransposed() ? columnIdx.toView(myResultPanel).asInteger() : 0;
    int viewColumn = isTransposed() ? 0 : columnIdx.toView(myResultPanel).asInteger();

    if (viewRow == -1 || viewColumn == -1) {
      // this can happen when column exist in grid model but is not yet created in table
      // when this method is called from createDefaultColumnsFromModel via methods updating binary display type
      // DBE-16996
      return;
    }

    TableCellImageCache.CachingCellRendererWrapper renderer =
      ObjectUtils.tryCast(getCellRenderer(viewRow, viewColumn), TableCellImageCache.CachingCellRendererWrapper.class);
    if (renderer != null) {
      renderer.clearCache();
      GridCellRendererWrapper rendererWrapper = ObjectUtils.tryCast(renderer.getDelegate(), GridCellRendererWrapper.class);
      if (rendererWrapper != null) rendererWrapper.delegate.clearCache();
    }
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

  private void setupMagnificator() {
    putClientProperty(Magnificator.CLIENT_PROPERTY_KEY, (Magnificator)(scale, at) -> {
      int column = columnAtPoint(at);
      int row = rowAtPoint(at);
      Rectangle r1 = column < 0 || row < 0 ? getBounds() : getCellRect(row, column, true);
      if (r1.width == 0 || r1.height == 0) return at;

      double xPerc = (at.x - r1.x) / (double)r1.width;
      double yPerc = (at.y - r1.y) / (double)r1.height;

      changeFontSize(0, scale);

      Rectangle r2 = column < 0 || row < 0 ? getBounds() : getCellRect(row, column, true);
      return new Point((int)(r2.x + r2.width * xPerc), (int)(r2.y + r2.height * yPerc));
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
      resizingColumn.setPreferredWidth(resizingColumn.getWidth());
    }
    resizeAndRepaint();
  }

  @Override
  public String getToolTipText(@NotNull MouseEvent event) {
    return "";
  }

  @Override
  public void setRowHeight(int row, int rowHeight) {
    if (row < 0 || row >= getRowCount()) {
      return;
    }

    super.setRowHeight(row, rowHeight);

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

  @Override
  public void columnsAdded(ModelIndexSet<GridColumn> columnIndices) {
    columnAttributesUpdated();
    getModel().columnsAdded(columnIndices);
    dropCaches();
    myColumnLayout.newColumnsAdded(columnIndices);
  }

  @Override
  public void columnAttributesUpdated() {
    List<GridColumn> columns = myResultPanel.getDataModel(DataAccessType.DATABASE_DATA).getColumns();
    if (!isTransposed()) {
      getColumnCache().retainColumns(columns);
      createDefaultColumnsFromModel();
    }
  }

  @Override
  public void columnsRemoved(ModelIndexSet<GridColumn> columns) {
    getModel().columnsRemoved(columns);
    if (!isTransposed()) createDefaultColumnsFromModel();
    dropCaches();
  }

  @Override
  public void rowsAdded(ModelIndexSet<GridRow> rows) {
    getModel().rowsAdded(rows);
    if (isTransposed()) createDefaultColumnsFromModel();
    myColumnLayout.newRowsAdded(rows);
  }

  @Override
  public void afterLastRowAdded() {
    DataGridSettings settings = GridUtil.getSettings(myResultPanel);
    if (!isTransposed() &&
        getViewRowCount() == 1 &&
        settings != null && settings.getAutoTransposeMode() == AutoTransposeMode.ONE_ROW) {
      setTransposed(true);
      myWasAutomaticallyTransposed = true;
      return;
    }
    if (myWasAutomaticallyTransposed && isTransposed() && getViewRowCount() != 1) {
      setTransposed(false);
      return;
    }

    SingleRowModeHelper.expandRowIfNeeded(myResultPanel);

    JComponent mainResultViewComponent = myResultPanel.getMainResultViewComponent();
    JViewport header = mainResultViewComponent instanceof JScrollPane ? ((JScrollPane)mainResultViewComponent).getRowHeader() : null;
    Component rowHeader = header == null ? null : header.getView();
    if (rowHeader == null) return;
    if (rowHeader instanceof TableResultRowHeader) {
      ((TableResultRowHeader)rowHeader).updatePreferredSize();
    }
    rowHeader.revalidate();
    rowHeader.repaint();
  }

  @Override
  public void addSpaceForHorizontalScrollbar(boolean v) {
    if (myResultPanel.getMainResultViewComponent() instanceof TableScrollPane scrollPane) {
      scrollPane.addSpaceForHorizontalScrollbar(v);
    }
  }

  @Override
  public void expandMultilineRows(boolean v) {
    if (v) {
      setupDynamicRowHeight(this);
    }
  }

  @Override
  public void rowsRemoved(ModelIndexSet<GridRow> rows) {
    getModel().rowsRemoved(rows);
    if (isTransposed()) createDefaultColumnsFromModel();
  }

  @Override
  public void cellsUpdated(ModelIndexSet<GridRow> rows, ModelIndexSet<GridColumn> columns, @Nullable GridRequestSource.RequestPlace place) {
    getModel().cellsUpdated(rows, columns, place);
    myColumnLayout.newRowsAdded(rows);
  }

  @Override
  public void dispose() {
    removeEditor();
  }

  @Override
  public void changeSelectedColumnsWidth(int delta) {
    int[] columns = getSelectedColumns();
    TableColumnModel columnModel = getColumnModel();
    for (int column : columns) {
      if (column < 0) continue;
      ResultViewColumn tableColumn = (ResultViewColumn)columnModel.getColumn(column);
      int width = tableColumn.getColumnWidth();
      tableColumn.setColumnWidth(Math.max(0, width + delta));
    }
  }

  @Override
  public void resetRowHeights() {
    int defaultRowHeight = getRowHeight();
    for (int i = 0; i < getRowCount(); i++) {
      if (getRowHeight(i) != defaultRowHeight) {
        setRowHeight(i, defaultRowHeight);
      }
    }
  }

  public void disableSelectionListeners(Runnable runnable) {
    try {
      myDisableSelectionListeners = true;
      runnable.run();
    }
    finally {
      myDisableSelectionListeners = false;
    }
  }

  public void fireSelectionChanged() {
    for (Consumer<Boolean> listener : mySelectionListeners) {
      listener.accept(false);
    }
  }

  public @NotNull LocalFilterState getLocalFilterState() {
    return myResultPanel.getLocalFilterState();
  }

  public static class MyCellRenderer extends MyHeaderCellComponent implements TableCellRenderer {
    private final TableCellRenderer myOriginalRenderer;

    public MyCellRenderer(@NotNull TableResultView table) {
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

    public Component getHeaderCellRendererComponent(int columnDataIdx, boolean forDisplay) {
      GridModel<?, GridColumn> model = myTable.myResultPanel.getDataModel(DATA_WITH_MUTATIONS);
      HierarchicalReader reader = model.getHierarchicalReader();

      return prepare(columnDataIdx, forDisplay, reader);
    }
  }

  static class MyTableColumnModel extends DefaultTableColumnModel {
    @Override
    public void addColumn(TableColumn aColumn) {
      if (!(aColumn instanceof TableResultViewColumn)) {
        throw new IllegalArgumentException("Unexpected column type");
      }
      super.addColumn(aColumn);
    }

    @Override
    public TableResultViewColumn getColumn(int columnIndex) {
      return (TableResultViewColumn)super.getColumn(columnIndex);
    }

    /**
     * Deletes all columns from this model.
     * <p>
     * Causes selection model to be changed only once as opposed to
     * multiple invocations of {@link #removeColumn}.
     *
     * @see #removeColumn
     */
    public void removeAllColumns() {
      int columnCount = tableColumns.size();
      if (columnCount == 0) return;

      if (selectionModel != null) {
        selectionModel.removeIndexInterval(0, columnCount - 1);
      }
      for (TableColumn column : tableColumns) {
        column.removePropertyChangeListener(this);
      }
      totalColumnWidth = -1; // javax.swing.table.DefaultTableColumnModel.invalidateWidthCache
      for (int i = columnCount - 1; i >= 0; i--) {
        tableColumns.remove(i);
        fireColumnRemoved(new TableColumnModelEvent(this, i, 0));
      }
    }
  }

  protected class MyTableHeader extends JBTableHeader {
    private int myLastPositiveHeight = 0;
    private @Nullable ModelIndex<GridColumn> hoveredFilterLabelIdx = null;
    private @Nullable ModelIndex<GridColumn> hoveredSortLabelIdx = null;

    @Override
    public void setExpandableItemsEnabled(boolean enabled) {
      super.setExpandableItemsEnabled(false); // we never want this
    }

    private void onColumnHeaderMouseMoved(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull MouseEvent e) {
      var gridColumn = myResultPanel.getDataModel(DATA_WITH_MUTATIONS).getColumn(columnIdx);
      var isHierarchicalColumn = gridColumn instanceof HierarchicalGridColumn;

      var currentViewIdx = columnIdx.toView(myResultPanel).value;
      TableResultViewColumn tableColumn = ((MyTableColumnModel)columnModel).getColumn(currentViewIdx);
      var offsetX = calculateClickedColumnX(TableResultView.this, currentViewIdx);

      MyHeaderCellComponent currentHeader = renderWithActualBounds(tableHeader, currentViewIdx, tableColumn);

      var point = e.getPoint();
      var relativePoint = new Point(point.x - offsetX, point.y);
      var filterLabel = currentHeader.filterLabel;
      var sortLabel = currentHeader.myIconLabels.isEmpty() ?
                      null :
                      currentHeader.myIconLabels.get(currentHeader.myIconLabels.size() - 1);

      var previousFilterLabelIdx = hoveredFilterLabelIdx;
      if (filterLabel.getBounds().contains(relativePoint)) {
        hoveredFilterLabelIdx = columnIdx;
      }
      else {
        hoveredFilterLabelIdx = null;
      }

      var previousSortLabelIdx = hoveredSortLabelIdx;
      if (!isHierarchicalColumn && sortLabel != null && sortLabel.getBounds().contains(relativePoint)) {
        hoveredSortLabelIdx = columnIdx;
      }
      else {
        hoveredSortLabelIdx = null;
      }

      if (!Objects.equals(previousFilterLabelIdx, hoveredFilterLabelIdx) ||
          !Objects.equals(previousSortLabelIdx, hoveredSortLabelIdx)) {
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

          if (MyTableHeader.this.hoveredFilterLabelIdx != null || MyTableHeader.this.hoveredSortLabelIdx != null) {
            MyTableHeader.this.hoveredFilterLabelIdx = null;
            MyTableHeader.this.hoveredSortLabelIdx = null;
            MyTableHeader.this.repaint();
          }
        }

        private void processEvent(MouseEvent e) {
          if (isTransposed()) {
            int modelRow = myRawIndexConverter.row2Model().applyAsInt(columnAtPoint(e.getPoint()));
            if (modelRow >= 0) {
              onRowHeaderClicked(ModelIndex.forRow(myResultPanel, modelRow), e);
            }
          }
          else {
            int modelColumn = myRawIndexConverter.column2Model().applyAsInt(columnAtPoint(e.getPoint()));
            if (modelColumn >= 0) {
              onColumnHeaderClicked(ModelIndex.forColumn(myResultPanel, modelColumn), e);
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
            onColumnHeaderMouseMoved(ModelIndex.forColumn(myResultPanel, modelColumn), e);
          }
        }
      });
    }

    @Override
    public boolean getReorderingAllowed() {
      if (isTransposed() || isEditingBlocked()) return false;

      List<GridColumn> columns = myResultPanel
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
    }
  }

  @Override
  public void setModel(@NotNull TableModel model) {
    super.setModel(model);

    final TableRowSorter<TableModel> rowSorter = createRowSorter(model);
    rowSorter.setMaxSortKeys(1);
    rowSorter.setSortsOnUpdates(isSortOnUpdates());

    SwingUtilities.invokeLater(() -> {
      if (getRowSorter() == rowSorter) {
        // row sorter will be set after the table is updated from passed model
        updateRowFilter();
      }
    });

    setRowSorter(rowSorter);
  }

  public void updateRowFilter() {
    DefaultRowSorter<? extends TableModel, Integer> sorter = (DefaultRowSorter<? extends TableModel, Integer>)getRowSorter();
    sorter.setRowFilter(createFilter());
  }

  private @NotNull RowFilter<TableModel, Integer> createFilter() {
    RowFilter<TableModel, Integer> baseFilter = isTransposed() ?
                                                new MyTransposedViewColumnFilter(myResultPanel) :
                                                new MySearchRowFilter(myResultPanel, getLocalFilterState());
    return new RowFilter<>() {
      @Override
      public boolean include(Entry<? extends TableModel, ? extends Integer> entry) {
        if (!baseFilter.include(entry)) return false;
        int intIdx = entry.getIdentifier().intValue();
        ModelIndex<?> rowIdx = isTransposed() ? ModelIndex.forColumn(myResultPanel, intIdx) : ModelIndex.forRow(myResultPanel, intIdx);
        return !myResultPanel.isRowFilteredOut(rowIdx);
      }
    };
  }

  @Override
  public @NotNull Component prepareRenderer(@NotNull TableCellRenderer renderer, int row, int column) {
    return prepareRenderer(renderer, row, column, true);
  }

  public @NotNull Component prepareRenderer(@NotNull TableCellRenderer renderer, int row, int column, boolean forDisplay) {
    Component component = super.prepareRenderer(renderer, row, column);

    ViewIndex<GridRow> rowIdx = ViewIndex.forRow(myResultPanel, isTransposed() ? column : row);
    ViewIndex<GridColumn> columnIdx = ViewIndex.forColumn(myResultPanel, isTransposed() ? row : column);
    return ResultViewWithCells.prepareComponent(component, myResultPanel, this, rowIdx, columnIdx, forDisplay);
  }

  @Override
  public @Nullable Color getCellBackground(@NotNull ViewIndex<GridRow> row, @NotNull ViewIndex<GridColumn> column, boolean selected) {
    return selected ?
           getSelectionBackground() :
           myResultPanel.getColorModel().getCellBackground(row.toModel(myResultPanel), column.toModel(myResultPanel));
  }

  @Override
  public @NotNull Color getCellForeground(boolean selected) {
    return selected ? getSelectionForeground() : getForeground();
  }

  @Override
  public void reinitSettings() {
    GridCellRendererFactories.get(myResultPanel).reinitSettings();
    dropCaches();
    updateFonts();
  }

  @Override
  public boolean isCellEditable(int row, int column) {
    return myResultPanel.isCellEditingAllowed();
  }

  @Override
  public boolean editCellAt(int row, int column, EventObject e) {
    ClientProperty.put(this, GridTableCellEditor.EDITING_STARTER_CLIENT_PROPERTY_KEY, e);
    if (shouldDisplayValueEditor(row, column)) {
      showValueEditor(e);
      return false;
    }
    try {
      return super.editCellAt(row, column, e);
    }
    finally {
      ClientProperty.put(this, GridTableCellEditor.EDITING_STARTER_CLIENT_PROPERTY_KEY, null);
    }
  }

  private boolean shouldDisplayValueEditor(int row, int column) {
    var tableModel = getModel();
    var cellValue = tableModel.getValueAt(row, column);
    return (cellValue instanceof LobInfo.ClobInfo clob && clob.isFullyReloaded()) || (cellValue instanceof LobInfo.BlobInfo blob && blob.isFullyReloaded());
  }

  private void showValueEditor(EventObject e) {
    if (e instanceof MouseEvent mouseEvent && mouseEvent.getClickCount() < 2) return;
    if (e instanceof KeyEvent && !UIUtil.isReallyTypedEvent((KeyEvent)e)) return;

    var action = ActionUtil.wrap("Console.TableResult.EditValueMaximized");
    var dataContext = DataManager.getInstance().getDataContext(this);
    AnActionEvent event = AnActionEvent.createEvent(action, dataContext, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null);
    var view = ShowEditMaximizedAction.getView(this.myResultPanel, event);
    view.open(tabInfoProvider -> tabInfoProvider instanceof ValueTabInfoProvider);
  }

  @Override
  public void createDefaultColumnsFromModel() {
    GridTableModel model = getModel();
    if (model == null) return;

    getTableHeader().setDraggedColumn(null); // EA-59152

    ((MyTableColumnModel)getColumnModel()).removeAllColumns();

    IntList newColumnIndices = new IntArrayList();
    for (int columnDataIdx = 0; columnDataIdx < model.getColumnCount(); columnDataIdx++) {
      boolean notShownEarlier = !myColumnCache.hasCachedColumn(columnDataIdx);
      myColumnCache.getOrCreateColumn(columnDataIdx);
      HierarchicalColumnsCollapseManager collapseManager = myResultPanel.getHierarchicalColumnsCollapseManager();
      // prevents myColumnLayout.columnsShown call in setColumnEnabled
      boolean enabled = isTransposed() ||
                        myResultPanel.isColumnEnabled(ModelIndex.forColumn(myResultPanel, columnDataIdx)) &&
                        !(collapseManager != null && collapseManager.isColumnHiddenDueToCollapse(ModelIndex.forColumn(myResultPanel, columnDataIdx)));
      ModelIndex<?> modelColumnIdx =
        isTransposed() ? ModelIndex.forRow(myResultPanel, columnDataIdx) : ModelIndex.forColumn(myResultPanel, columnDataIdx);
      setViewColumnVisible(modelColumnIdx, enabled);
      if (notShownEarlier && enabled) {
        newColumnIndices.add(columnDataIdx);
      }
    }

    if (!newColumnIndices.isEmpty()) {
      ModelIndexSet<?> dataIndices = isTransposed() ?
                                     ModelIndexSet.forRows(myResultPanel, newColumnIndices.toIntArray()) :
                                     ModelIndexSet.forColumns(myResultPanel, newColumnIndices.toIntArray());
      myColumnLayout.columnsShown(dataIndices);
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
           myResultPanel == null ? super.getFont() :
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
    Font font = getFont();
    setFont(font);
    setRowHeight(getRowHeight());
    JTableHeader tableHeader = getTableHeader();
    if (tableHeader != null) tableHeader.setFont(getScaledFont(font));
    myResultPanel.trueLayout();
    layoutColumns();
  }

  @Override
  public void uiSettingsChanged(@NotNull UISettings uiSettings) {
    if (changeHeaderFont(uiSettings)) {
      myResultPanel.trueLayout();
      layoutColumns();
    }
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
           myResultPanel == null ? super.getSelectionForeground() :
           doGetSelectionForeground(getColorsScheme());
  }

  @Override
  public Color getSelectionBackground() {
    return myPaintingSession != null ? myPaintingSession.getSelectionBackground() :
           myResultPanel == null ? super.getSelectionBackground() :
           doGetSelectionBackground(getColorsScheme());
  }

  @Override
  public Color getForeground() {
    return myPaintingSession != null ? myPaintingSession.getForeground() :
           myResultPanel == null ? super.getBackground() :
           doGetForeground(getColorsScheme());
  }

  @Override
  public Color getBackground() {
    return myPaintingSession != null ? myPaintingSession.getBackground() :
           myResultPanel == null ? super.getForeground() :
           doGetBackground(getColorsScheme());
  }

  @Override
  public void setBackground(@NotNull Color bg) {
    // prevent background change events from being fired
  }

  @Override
  public Color getGridColor() {
    return myPaintingSession != null ? myPaintingSession.getGridColor() :
           myResultPanel == null ? super.getGridColor() :
           doGetGridColor(getColorsScheme());
  }

  @Override
  public void tableChanged(TableModelEvent e) {
    if (myResultPanel == null) {
      super.tableChanged(e);
      return;
    }
    myResultPanel.getAutoscrollLocker().runWithLock(() -> super.tableChanged(e));
    myResultPanel.fireContentChanged(
      e instanceof GridTableModel.RequestedTableModelEvent ? ((GridTableModel.RequestedTableModelEvent)e).getPlace() : null);
  }

  @Override
  public GridTableModel getModel() {
    return (GridTableModel)super.getModel();
  }

  @Override
  public Object getValueAt(int row, int column) {
    boolean commonValue = isEditing() && isCellSelected(row, column) && isMultiEditingAllowed();
    return commonValue ? myCommonValue.get() : super.getValueAt(row, column);
  }

  @Override
  public void removeEditor() {
    try {
      super.removeEditor();
    }
    finally {
      myCommonValue = null;
    }
  }

  @Override
  public boolean isTransposed() {
    return getModel() instanceof TransposedGridTableModel;
  }

  private void onRowHeaderClicked(ModelIndex<GridRow> rowIdx, MouseEvent e) {
    if (e.getID() != MouseEvent.MOUSE_PRESSED) return;

    if (myResultPanel.isHeaderSelecting() && isTransposed()) {
      int tableViewColumnIdx = myRawIndexConverter.row2View().applyAsInt(rowIdx.value);
      selectViewColumnInterval(tableViewColumnIdx, e);
    }

    if (e.isPopupTrigger()) {
      invokeRowPopup(e.getComponent(), e.getX(), e.getY());
    }
  }

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
      if (columnIdx.value >= 0) {
        myResultPanel.setColumnEnabled(columnIdx, false);
      }
      return;
    }
    if (e.isPopupTrigger()) {
      if (myResultPanel.isHeaderSelecting() && !isTransposed()) {
        int tableViewColumnIdx = myRawIndexConverter.column2View().applyAsInt(columnIdx.value);
        selectViewColumnInterval(tableViewColumnIdx, e);
      }

      invokeColumnPopup(columnIdx, e.getComponent(), new Point(e.getX(), e.getY()));
    }
  }

  private void handleClickOnColumnHeader(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull MouseEvent e) {
    GridColumn column = myResultPanel.getDataModel(DATA_WITH_MUTATIONS).getColumn(columnIdx);
    if (column instanceof HierarchicalGridColumn hierarchicalColumn) {
      handleClickOnHierarchicalColumnHeader(hierarchicalColumn, columnIdx, e);
    }
    else {
      var currentViewIdx = columnIdx.toView(myResultPanel).value;
      TableResultViewColumn tableColumn = ((MyTableColumnModel)columnModel).getColumn(currentViewIdx);
      var offsetX = calculateClickedColumnX(this, currentViewIdx);

      MyHeaderCellComponent currentHeader = renderWithActualBounds(tableHeader, currentViewIdx, tableColumn);

      var point = e.getPoint();
      var relativePoint = new Point(point.x - offsetX, point.y);
      var filterLabel = currentHeader.filterLabel;
      var sortLabel = currentHeader.myIconLabels.isEmpty() ?
                      null :
                      currentHeader.myIconLabels.get(currentHeader.myIconLabels.size() - 1);

      if (filterLabel.getBounds().contains(relativePoint)) {
        var popup = ColumnLocalFilterAction.createFilterPopup(myResultPanel, columnIdx);
        if (popup != null)
          popup.show(new RelativePoint(e.getComponent(), point));
      }
      else if (sortLabel != null && sortLabel.getBounds().contains(relativePoint)) {
        handleClickToSortColumn(columnIdx, e);
      }
      else {
        myResultPanel.getSelectionModel().setColumnSelection(columnIdx, true);
        myResultPanel.getSelectionModel().selectWholeColumn();
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
      myResultPanel.getAutoscrollLocker()
        .runWithLock(() -> GridUtil.collapseColumnsSubtree(myResultPanel, columnIdx, clickedHeaderLineIndex.getAsInt(),
                                                           /* onCollapseCompleted */ () -> onColumnHierarchyChanged()));
    }
  }

  @Override
  public void onColumnHierarchyChanged() {
    GridModel<?, GridColumn> model = myResultPanel.getDataModel(DATA_WITH_MUTATIONS);
    HierarchicalReader hierarchicalReader = model.getHierarchicalReader();
    HierarchicalColumnsCollapseManager collapseManager = myResultPanel.getHierarchicalColumnsCollapseManager();
    if (hierarchicalReader != null && collapseManager != null) {
      hierarchicalReader.updateDepthOfHierarchy((column) -> {
        return isColumnDisabledOrCollapsed(column, myResultPanel) || collapseManager.isColumnCollapsedSubtree(column);
      });
    }
  }

  private static boolean isColumnDisabledOrCollapsed(@NotNull HierarchicalGridColumn column, @NotNull DataGrid grid) {
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
    if (!GridHelper.get(myResultPanel).isSortingApplicable(columnIdx)) return;
    boolean alt = e.getModifiersEx() == ALT_DOWN_MASK;
    DataGridSettings settings = GridUtil.getSettings(myResultPanel);
    GridSortingModel<GridRow, GridColumn> model = myResultPanel.getDataHookup().getSortingModel();
    boolean supportsAdditiveSorting = model == null || !model.isSortingEnabled() || model.supportsAdditiveSorting();
    toggleSortOrder(columnIdx, supportsAdditiveSorting && (settings == null || settings.isAddToSortViaAltClick()) == alt);
  }

  private OptionalInt getIndexOfClickedHeaderLine(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull MouseEvent e) {
    if (!(myResultPanel.getResultView() instanceof TableResultView table)) {
      return OptionalInt.empty();
    }

    int viewIdx = columnIdx.toView(myResultPanel).asInteger();
    Component headerRenderer = getHeaderRenderer(table, viewIdx);

    if (!(headerRenderer instanceof MyHeaderCellComponent customHeaderComponent)) {
      return OptionalInt.empty();
    }

    int clickedColumnX = calculateClickedColumnX(table, viewIdx);
    int columnWidth = table.getColumnModel().getColumn(viewIdx).getWidth();

    return findIndexOfClickedHeaderLineWithNonEmptyLabel(e, customHeaderComponent, clickedColumnX, columnWidth);
  }

  private Component getHeaderRenderer(@NotNull TableResultView table, int viewIdx) {
    TableColumn column = getColumnModel().getColumn(viewIdx);
    return table
      .getTableHeader()
      .getDefaultRenderer()
      .getTableCellRendererComponent(table, column.getHeaderValue(), false, false, -1, viewIdx);
  }

  private MyHeaderCellComponent renderWithActualBounds(JTableHeader header, int columnViewIndex, TableResultViewColumn tableColumn) {

    MyHeaderCellComponent currentHeaderCell = (MyHeaderCellComponent)getHeaderRenderer(this, columnViewIndex);
    var cellRendererPane = new CellRendererPane();
    cellRendererPane.add(currentHeaderCell);
    header.add(cellRendererPane);
    currentHeaderCell.setBounds(0, 0, tableColumn.getColumnWidth(), tableHeader.getHeight());
    currentHeaderCell.validate();
    header.remove(cellRendererPane);

    return currentHeaderCell;
  }

  private static int calculateClickedColumnX(@NotNull TableResultView table, int viewIdx) {
    int clickedColumnX = 0;
    for (int i = 0; i < viewIdx; ++i) {
      clickedColumnX += table.getColumnModel().getColumn(i).getWidth();
    }
    return clickedColumnX;
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

  void invokeColumnPopup(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull Component component, @NotNull Point point) {
    if (myColumnHeaderPopupActions != ActionGroup.EMPTY_GROUP) {
      myClickedHeaderColumnIdx = columnIdx;
      myClickedHeaderPoint = point;
      ListPopup popupMenu = JBPopupFactory.getInstance()
        .createActionGroupPopup(null, myColumnHeaderPopupActions, DataManager.getInstance().getDataContext(getComponent()),
                                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true);
      popupMenu.addListener(new JBPopupListener() {
        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          SwingUtilities.invokeLater(() -> {
            if (myClickedHeaderColumnIdx.equals(columnIdx)) {
              // if not clicked on another column
              myClickedHeaderColumnIdx = ModelIndex.forColumn(myResultPanel, -1);
              myClickedHeaderPoint = null;
            }
          });
        }
      });
      popupMenu.show(new RelativePoint(component, point));
    }
  }

  private void selectViewColumnInterval(int viewColumn, @NotNull MouseEvent e) {
    boolean interval = GridUtil.isIntervalModifierSet(e);
    boolean exclusive = GridUtil.isExclusiveModifierSet(e);
    TableSelectionModel selectionModel = ObjectUtils.tryCast(SelectionModelUtil.get(myResultPanel, this), TableSelectionModel.class);
    if (selectionModel == null) return;
    if (interval) {
      int lead = getColumnModel().getSelectionModel().getLeadSelectionIndex();
      if (exclusive) {
        selectionModel.addRowSelectionInterval(getRowCount() - 1, 0);
        selectionModel.addColumnSelectionInterval(viewColumn, lead);
      }
      else {
        selectionModel.setRowSelectionInterval(getRowCount() - 1, 0);
        selectionModel.setColumnSelectionInterval(viewColumn, lead);
      }
    }
    else if (exclusive) {
      removeColumnSelectionInterval(viewColumn, viewColumn);
    }
    else {
      selectionModel.setRowSelectionInterval(getRowCount() - 1, 0);
      selectionModel.setColumnSelectionInterval(viewColumn, viewColumn);
    }
  }

  @Override
  public @Nullable ResultViewColumn getLayoutColumn(@NotNull ModelIndex<?> column) {
    return getLayoutColumn(column, column.toView(myResultPanel));
  }

  public @Nullable ResultViewColumn getLayoutColumn(@NotNull ModelIndex<?> column, @NotNull ViewIndex<?> viewColumnIdx) {
    return viewColumnIdx.asInteger() != -1 ? getColumnCache().getOrCreateColumn(column.asInteger()) : null;
  }

  @Override
  public @NotNull ModelIndexSet<GridRow> getVisibleRows() {
    int rowCount = isTransposed() ? getModel().getColumnCount() : getModel().getRowCount();
    int[] viewRowIndices = new int[rowCount];
    for (int i = 0; i < rowCount; i++) {
      viewRowIndices[i] = i;
    }
    ModelIndexSet<GridRow> rowIndices = ViewIndexSet.forRows(myResultPanel, viewRowIndices).toModel(myResultPanel);
    return validIndexSet(rowIndices, rowIndices1 -> ModelIndexSet.forRows(myResultPanel, rowIndices1));
  }

  @Override
  public @NotNull ModelIndexSet<GridColumn> getVisibleColumns() {
    int visibleColumns = getViewColumnCount();
    int[] viewIndices = new int[visibleColumns];
    for (int i = 0; i < visibleColumns; i++) {
      viewIndices[i] = i;
    }
    ModelIndexSet<GridColumn> columnIndices = ViewIndexSet.forColumns(myResultPanel, viewIndices).toModel(myResultPanel);
    return validIndexSet(columnIndices, columnIndices1 -> ModelIndexSet.forColumns(myResultPanel, columnIndices1));
  }

  @Override
  public int getViewColumnCount() {
    return isTransposed() ? getRowCount() : getColumnCount();
  }

  @Override
  public int getViewRowCount() {
    return isTransposed() ? getColumnCount() : getRowCount();
  }

  private @NotNull <T> ModelIndexSet<T> validIndexSet(@NotNull ModelIndexSet<T> indexSet, @NotNull Function<int[], ModelIndexSet<T>> factory) {
    IntList validIndices = new IntArrayList(indexSet.size());
    for (ModelIndex<T> idx : indexSet.asIterable()) {
      if (idx.isValid(myResultPanel)) {
        validIndices.add(idx.asInteger());
      }
    }
    return factory.fun(validIndices.toIntArray());
  }

  @Override
  public boolean stopEditing() {
    TableCellEditor editor = getCellEditor();
    if (editor == null) return true;

    int[] columnDataIdx = Arrays.stream(
      !isMultiEditingAllowed() ? new int[]{getEditingColumn()} : getSelectedColumns()
    ).map(this::convertColumnIndexToModel).toArray();

    int[] rowDataIdx = Arrays.stream(
      !isMultiEditingAllowed() ? new int[]{getEditingRow()} : getSelectedRows()
    ).map(this::convertRowIndexToModel).toArray();

    ModelIndexSet<GridRow> myEditingRowIdx = ModelIndexSet.forRows(myResultPanel, isTransposed() ? columnDataIdx : rowDataIdx);
    ModelIndexSet<GridColumn> myEditingColumnIdx = ModelIndexSet.forColumns(myResultPanel, isTransposed() ? rowDataIdx : columnDataIdx);

    return myResultPanel.isSafeToUpdate(myEditingRowIdx, myEditingColumnIdx, editor.getCellEditorValue()) && editor.stopCellEditing();
  }

  @Override
  public void cancelEditing() {
    TableCellEditor editor = getCellEditor();
    if (editor != null) {
      editor.cancelCellEditing();
    }
  }

  @Override
  public boolean isCellEditingAllowed() {
    return true;
  }

  @Override
  public void editSelectedCell() {
    int leadRow = getSelectionModel().getLeadSelectionIndex();
    int leadColumn = getColumnModel().getSelectionModel().getLeadSelectionIndex();
    if (leadRow == -1 || leadColumn == -1) return;
    TableUtil.editCellAt(this, leadRow, leadColumn);
  }

  @Override
  public boolean isMultiEditingAllowed() {
    int[] selectedColumns = isTransposed() ? getSelectedRows() : getSelectedColumns();
    int[] selectedRows = isTransposed() ? getSelectedColumns() : getSelectedRows();
    ModelIndexSet<GridColumn> indexSet = ViewIndexSet.forColumns(myResultPanel, selectedColumns).toModel(myResultPanel);
    List<GridColumn> columns = myResultPanel.getDataModel(DataAccessType.DATABASE_DATA).getColumns(indexSet);
    GridColumn uniqueColumn = GridHelper.get(myResultPanel).findUniqueColumn(myResultPanel, columns);
    return myCommonValue != null &&
           (uniqueColumn == null || selectedRows.length == 1) &&
           GridHelper.get(myResultPanel).canEditTogether(myResultPanel, columns);
  }

  void toggleSortOrder(@NotNull ModelIndex<GridColumn> idx, boolean additive) {
    myResultPanel.getAutoscrollLocker().runWithLock(() -> myResultPanel.toggleSortColumns(Collections.singletonList(idx), additive));
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
      public void toggleSortOrder(int columnDataIdx) {
        // this method is triggered only once on double click
        // so I moved sorting toggle to onColumnHeaderClicked
      }

      @Override
      protected boolean useToString(int column) {
        return false;
      }

      @Override
      public Comparator<?> getComparator(int modelColumnIdx) {
        Comparator<?> comparator = null;
        if (!isTransposed()) {
          comparator = myResultPanel.getComparator(ModelIndex.forColumn(myResultPanel, modelColumnIdx));
        }
        return comparator != null ? comparator : super.getComparator(modelColumnIdx);
      }

      @Override
      public boolean isSortable(int columnDataIdx) {
        return !isTransposed() && myResultPanel.getComparator(ModelIndex.forColumn(myResultPanel, columnDataIdx)) != null;
      }
    };
  }

  @Override
  public TableCellEditor getCellEditor(int row, int column) {
    ModelIndex<GridRow> rowIdx = ViewIndex.forRow(myResultPanel, isTransposed() ? column : row).toModel(myResultPanel);
    ModelIndex<GridColumn> columnIdx = ViewIndex.forColumn(myResultPanel, isTransposed() ? row : column).toModel(myResultPanel);
    GridCellEditorFactoryProvider factoryProvider = GridCellEditorFactoryProvider.get(myResultPanel);
    GridCellEditorFactory editorFactory = factoryProvider == null ? null : factoryProvider.getEditorFactory(myResultPanel, rowIdx, columnIdx);
    GridColumn dataColumn = myResultPanel.getDataModel(DATA_WITH_MUTATIONS).getColumn(columnIdx);
    return dataColumn != null && !GridUtilCore.isRowId(dataColumn) && !GridUtilCore.isVirtualColumn(dataColumn) && editorFactory != null ?
           new GridTableCellEditor(myResultPanel, rowIdx, columnIdx, editorFactory) :
           null;
  }

  @Override
  public TableCellRenderer getCellRenderer(int row, int column) {
    TableCellRenderer renderer = myRenderers.getRenderer(row, column);
    return myCellImageCache.wrapCellRenderer(renderer);
  }

  @Override
  public void setValueAt(Object value, int viewRowIdx, int viewColumnIdx) {
    setValueAt(value, viewRowIdx, viewColumnIdx, true, new GridRequestSource(new DataGridRequestPlace(myResultPanel)));
  }

  private void setValueAt(Object value,
                          int viewRowIdx,
                          int viewColumnIdx,
                          boolean allowImmediateUpdate,
                          @NotNull GridRequestSource source) {
    boolean allowed = isMultiEditingAllowed();
    int[] rows = allowed ? getSelectedRows() : new int[]{viewRowIdx};
    int[] columns = allowed ? getSelectedColumns() : new int[]{viewColumnIdx};
    ViewIndexSet<GridRow> rowsSet = ViewIndexSet.forRows(myResultPanel, isTransposed() ? columns : rows);
    ViewIndexSet<GridColumn> columnsSet = ViewIndexSet.forColumns(myResultPanel, isTransposed() ? rows : columns);
    GridTableCellEditor editor = ObjectUtils.tryCast(getCellEditor(), GridTableCellEditor.class);
    Runnable moveToNextCellRunnable = editor != null && editor.shouldMoveFocus()
                                      ? () -> IdeFocusManager.findInstanceByComponent(this).doWhenFocusSettlesDown(
      // doWhenFocusSettlesDown causes "Write-unsafe context" so we need to call invokeLater
      () -> ApplicationManager.getApplication().invokeLater(() -> moveToNextCell(rowsSet.last(), columnsSet.last())))
                                      : null;
    myResultPanel.setValueAt(rowsSet.toModel(myResultPanel),
                             columnsSet.toModel(myResultPanel),
                             value,
                             allowImmediateUpdate,
                             moveToNextCellRunnable,
                             source);
  }

  private void moveToNextCell(@NotNull ViewIndex<GridRow> rowIndex, @NotNull ViewIndex<GridColumn> colIndex) {
    SelectionModel<GridRow, GridColumn> selectionModel = myResultPanel.getSelectionModel();
    if (selectionModel.getSelectedRowCount() != 1 || selectionModel.getSelectedColumnCount() != 1 ||
        !ApplicationManager.getApplication().isUnitTestMode() && (!UIUtil.isFocusAncestor(this) || isEditing())) {
      return;
    }

    // if selection has already been changed by Tab or Shift-Tab we don't need to change it
    if (!Comparing.equal(selectionModel.getSelectedRow(), rowIndex.toModel(myResultPanel)) ||
        !Comparing.equal(selectionModel.getSelectedColumn(), colIndex.toModel(myResultPanel))) {
      return;
    }

    if (GridUtil.isInsertedRow(myResultPanel, rowIndex.toModel(myResultPanel))) {
      ViewIndex<GridColumn> nextColumn = ViewIndex.forColumn(myResultPanel, colIndex.asInteger() + 1);
      colIndex = nextColumn.isValid(myResultPanel) ? nextColumn : colIndex;
    }
    else {
      ViewIndex<GridRow> nextRow = ViewIndex.forRow(myResultPanel, rowIndex.asInteger() + 1);
      rowIndex = nextRow.isValid(myResultPanel) ? nextRow : rowIndex;
    }

    GridUtil.scrollToLocally(myResultPanel, rowIndex, colIndex);
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
    return myResultPanel.getDataModel(DATA_WITH_MUTATIONS).getRow(ModelIndex.forRow(myResultPanel, modelRowIdx));
  }

  private @Nullable GridColumn getColumn(int modelColumnIdx) {
    return myResultPanel.getDataModel(DATA_WITH_MUTATIONS).getColumn(ModelIndex.forColumn(myResultPanel, modelColumnIdx));
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
    final TableResultView myResultView;
    final Map<GridCellRenderer, TableCellRenderer> myTableCellRenderers = new Reference2ObjectOpenHashMap<>();

    Renderers(DataGrid grid, TableResultView resultView) {
      myGrid = grid;
      myResultView = resultView;
    }

    public @NotNull TableCellRenderer getRenderer(int row, int column) {
      ModelIndex<GridRow> rowIdx = ViewIndex.forRow(myGrid, myResultView.isTransposed() ? column : row).toModel(myGrid);
      ModelIndex<GridColumn> columnIdx = ViewIndex.forColumn(myGrid, myResultView.isTransposed() ? row : column).toModel(myGrid);
      GridCellRenderer gridCellRenderer = GridCellRenderer.getRenderer(myGrid, rowIdx, columnIdx);

      TableCellRenderer renderer = myTableCellRenderers.get(gridCellRenderer);
      if (renderer == null) {
        renderer = new GridCellRendererWrapper(gridCellRenderer, myResultView);
        myTableCellRenderers.put(gridCellRenderer, renderer);
      }
      return renderer;
    }
  }

  private static final class GridCellRendererWrapper implements TableCellRenderer {
    final GridCellRenderer delegate;
    final TableResultView myResultView;

    private GridCellRendererWrapper(GridCellRenderer renderer, TableResultView resultView) {
      delegate = renderer;
      myResultView = resultView;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      DataGrid grid = delegate.myGrid;
      ViewIndex<GridRow> rowIdx = ViewIndex.forRow(grid, myResultView.isTransposed() ? column : row);
      ViewIndex<GridColumn> columnIdx = ViewIndex.forColumn(grid, myResultView.isTransposed() ? row : column);
      return delegate.getComponent(rowIdx, columnIdx, value);
    }
  }

  protected static class MyHeaderCellComponent extends CellRendererPanel {
    private static final String HEADER_PLACEHOLDER = "    ";

    protected final TableResultView myTable;
    private final List<JLabel> myNameLabels;
    private final JPanel myCompositeLabel;
    private final List<JLabel> myIconLabels;
    private JLabel filterLabel;
    private final Icon filterIconEnabled = new BadgeIcon(AllIcons.General.Filter, JBUI.CurrentTheme.IconBadge.SUCCESS);

    private final List<JPanel> myHeaderLinePanels;
    private TableResultViewColumn myCurrentColumn;

    private boolean isInitialized = false;

    public MyHeaderCellComponent(@NotNull TableResultView table) {
      myTable = table;
      myCompositeLabel = new JPanel();
      myCompositeLabel.setLayout(new BoxLayout(myCompositeLabel, BoxLayout.Y_AXIS));
      myNameLabels = new ArrayList<>();
      myIconLabels = new ArrayList<>();
      myHeaderLinePanels = new ArrayList<>();
      setLayout(new BorderLayout());
      add(myCompositeLabel, BorderLayout.CENTER);
    }

    @Override
    protected void paintComponent(Graphics g) {
      UISettings.setupAntialiasing(g);
      ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
      setSelected(true);
      super.paintComponent(g);
    }

    protected Rectangle getNameRect() {
      return getLabelTextRect(myNameLabels.get(0));
    }

    protected int getModelIdx() {
      return myCurrentColumn.getModelIndex();
    }

    private static int getHeaderLineNum(@Nullable HierarchicalReader reader) {
      return reader == null ? 1 : reader.getDepthOfHierarchy();
    }

    public MyHeaderCellComponent prepare(int columnDataIdx, boolean forDisplay, @Nullable HierarchicalReader reader) {
      myCurrentColumn = myTable.getColumnCache().getOrCreateColumn(columnDataIdx);

      if (!isInitialized) {
        initializeLabelsForEachHeaderLine(getHeaderLineNum(reader));
        isInitialized = true;
      }

      resetLabelsValues(myNameLabels);
      resetLabelsValues(myIconLabels);
      resetHeaderLinesBg();
      setBorder(null);

      List<String> headerLineValues = myCurrentColumn.getMultilineHeaderValues();
      if (headerLineValues.size() > myNameLabels.size()) {
        clearAllLabelsAndPanels();
        initializeLabelsForEachHeaderLine(getHeaderLineNum(reader));
      } else if (myNameLabels.size() != getHeaderLineNum(reader)) {
        clearAllLabelsAndPanels();
        initializeLabelsForEachHeaderLine(getHeaderLineNum(reader));
      }

      if (headerLineValues.isEmpty()) return this;

      GridColumn column = myTable.getColumn(columnDataIdx);
      boolean isHierarchicalColumn = column instanceof HierarchicalGridColumn;

      if (isHierarchicalColumn) {
        HierarchicalColumnsCollapseManager collapseManager =
          myTable.myResultPanel.getHierarchicalColumnsCollapseManager();
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
        setValueForLastNonEmptyHeaderLine(myCurrentColumn.getHeaderValue().trim(), 0, columnDataIdx, forDisplay);
        setBorder(IdeBorderFactory.createBorder(SideBorder.RIGHT));
      }

      myCompositeLabel.revalidate();
      return this;
    }

    private void clearAllLabelsAndPanels() {
      myCompositeLabel.removeAll();
      myIconLabels.clear();
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
        !siblings.isEmpty() && all(siblings, (col) -> isSubtreeHiddenDueToCollapse(col, myTable.myResultPanel));
      if (allSiblingsCollapsed) {
        setPlaceholderIntoHeaderLine(headerLineIdx);
        return;
      }

      if (!isLeafColumnName && !isResidualLine) {
        String headerLineValue = headerLineValues.get(headerLineIdx);
        setHeaderLineValueExceptLast(headerLineValue, headerLineIdx, forDisplay);
        if (!headerLineValue.isBlank()) {
          myIconLabels.get(headerLineIdx).setIcon(forDisplay ? AllIcons.Actions.ArrowExpand : null);
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
                                            (col) -> isColumnDisabledOrCollapsed(col, myTable.myResultPanel))) {
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
        myIconLabels.get(headerLineIdx).setIcon(forDisplay ? AllIcons.Actions.ArrowCollapse : null);
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
                                            (col) -> isColumnDisabledOrCollapsed(col, myTable.myResultPanel))) {
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
          if (isColumnEnabled(sibling, myTable.myResultPanel)) return false;
        }
        else {
          HierarchicalGridColumn leftMostChildOfSibling = sibling.getChildren().get(0);
          if (isColumnEnabled(leftMostChildOfSibling, myTable.myResultPanel)) return false;
          // We do not check every descendant down to the leaf level.
          // It is not possible to disable all child columns without disabling the leftmost column, and vice versa,
          // disabling the leftmost column automatically disables all sibling columns.
        }
      }

      return true;
    }

    private void setPlaceholderIntoHeaderLine(int headerLineIdx) {
      myNameLabels.get(headerLineIdx).setText(HEADER_PLACEHOLDER);
    }

    private void initializeLabelsForEachHeaderLine(int headerLineNum) {
      filterLabel = new LabelWithFallbackFont(myTable);
      filterLabel.setVerticalAlignment(SwingConstants.CENTER);
      filterLabel.setHorizontalAlignment(SwingConstants.LEFT);
      filterLabel.setBorder(JBUI.Borders.empty(0, 5));

      for (int i = 0; i < headerLineNum; ++i) {
        JLabel nameLabel = new LabelWithFallbackFont(myTable);
        nameLabel.putClientProperty(SwingTextTrimmer.KEY, ELLIPSIS_AT_RIGHT);
        nameLabel.setHorizontalAlignment(SwingConstants.LEFT);
        nameLabel.setBorder(IdeBorderFactory.createEmptyBorder(CellRenderingUtils.NAME_LABEL_INSETS));
        myNameLabels.add(nameLabel);

        JLabel sortLabel = new LabelWithFallbackFont(myTable);
        sortLabel.setVerticalAlignment(SwingConstants.CENTER);
        sortLabel.setBorder(IdeBorderFactory.createEmptyBorder(CellRenderingUtils.SORT_LABEL_INSETS));
        myIconLabels.add(sortLabel);

        var panel = new JPanel();
        var layout = new GroupLayout(panel);
        var tableRowHeight = myTable.getRowHeight();

        var hGroup = layout.createSequentialGroup();
        hGroup.addComponent(nameLabel, 0, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE);
        if (i + 1 == headerLineNum) {
          hGroup.addGap(JBUI.scale(2), JBUI.scale(2), JBUI.scale(2));
          hGroup.addComponent(filterLabel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE);
        }
        hGroup
          .addGap(0, 0, Short.MAX_VALUE)
          .addComponent(sortLabel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
          .addGap(0, JBUI.scale(6), JBUI.scale(6));

        var vGroup = layout.createParallelGroup();
        vGroup.addComponent(nameLabel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE);
        if (i + 1 == headerLineNum)
          vGroup.addComponent(filterLabel, tableRowHeight, tableRowHeight, Short.MAX_VALUE);
        vGroup.addComponent(sortLabel, tableRowHeight, tableRowHeight, Short.MAX_VALUE);

        layout.setHorizontalGroup(hGroup);
        layout.setVerticalGroup(vGroup);
        panel.setLayout(layout);

        myHeaderLinePanels.add(panel);

        myCompositeLabel.add(panel);
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
    
    private void setValueForLastNonEmptyHeaderLine(@NlsSafe String value, int lineIdx, int columnDataIdx, boolean forDisplay) {
      JLabel nameLabel = myNameLabels.get(lineIdx);
      JLabel sortLabel = myIconLabels.get(lineIdx);

      nameLabel.setIcon(myCurrentColumn.getIcon(forDisplay));
      nameLabel.setForeground(getHeaderCellForeground());
      nameLabel.setText(myTable.myAllowMultilineColumnLabel && StringUtil.containsLineBreak(value)
                          ? "<html>" + StringUtil.replace(value, "\n", "<br>") + "</html>"
                          : value);

      Icon sortLabelIcon = null;
      String sortLabelText = "";
      Icon filterLabelIcon = null;
      GridColumn column = myTable.getColumn(columnDataIdx);
      if (!myTable.isTransposed()) {
        if (column != null) {
          ModelIndex<GridColumn> columnIdx = ModelIndex.forColumn(myTable.myResultPanel, columnDataIdx);
          if (GridHelper.get(myTable.myResultPanel).isSortingApplicable(columnIdx)) {
            if (myTable.myResultPanel.getComparator(columnIdx) != null) {
              int sortOrder = myTable.myResultPanel.getSortOrder(column);
              sortLabelIcon = getSortOrderIcon(sortOrder);
              sortLabelText = myTable.myResultPanel.countSortedColumns() > 1 ? getSortOrderText(sortOrder) : "";
            }
          }

          myTable.myResultPanel.getLocalFilterState();
          if (myTable.myResultPanel.getLocalFilterState().isEnabled()) {
            if (myTable.myResultPanel.getLocalFilterState().columnFilterEnabled(columnIdx)) {
              filterLabelIcon = filterIconEnabled;
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
            columnDataIdx == (tableHeader.hoveredSortLabelIdx != null ? tableHeader.hoveredSortLabelIdx.value : -1)) {
          sortLabel.setOpaque(true);
          sortLabel.setBackground(UIUtil.getTableSelectionBackground(false));
        }
        else {
          sortLabel.setOpaque(false);
        }
      }

      boolean filterLabelNeeded = !(column instanceof HierarchicalGridColumn) && (filterLabelIcon != null);
      if (filterLabelNeeded) {
        filterLabel.setIcon(filterLabelIcon);
        filterLabel.setVisible(true);
        if (myTable.tableHeader instanceof MyTableHeader tableHeader &&
            columnDataIdx == (tableHeader.hoveredFilterLabelIdx != null ? tableHeader.hoveredFilterLabelIdx.value : -1)) {
          filterLabel.setOpaque(true);
          filterLabel.setBackground(UIUtil.getTableSelectionBackground(false));
        }
        else {
          filterLabel.setOpaque(false);
        }
      }
      else {
        filterLabel.setVisible(false);
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
             myTable.myResultPanel.getColorModel().getRowHeaderForeground(
               ModelIndex.forRow(myTable.myResultPanel, myCurrentColumn.getModelIndex())
             ) :
             myTable.myResultPanel.getColorModel().getColumnHeaderForeground(
               ModelIndex.forColumn(myTable.myResultPanel, myCurrentColumn.getModelIndex())
             );
    }

    private Color getHeaderCellBackground() {
      return getHeaderCellBackground(0);
    }

    private Color getHeaderCellBackground(int headerLine) {
      if (myCurrentColumn == null) return super.getBackground();
      Color color = myTable.isTransposed() ?
                    myTable.myResultPanel.getColorModel().getRowHeaderBackground(
                      ModelIndex.forRow(myTable.myResultPanel, myCurrentColumn.getModelIndex())
                    ) :
                    myTable.myResultPanel.getColorModel().getColumnHeaderBackground(
                      ModelIndex.forColumn(myTable.myResultPanel, myCurrentColumn.getModelIndex()),
                      headerLine
                    );
      return color == null ? myTable.getBackground() : color;
    }
  }

  public static class LabelWithFallbackFont extends JBLabel {
    private final TableResultView myTable;

    public LabelWithFallbackFont(@NotNull TableResultView table) {
      myTable = table;
    }

    @Override
    public Font getFont() {
      return myTable == null ? super.getFont() : getFontWithFallback(myTable.getTableHeader().getFont());
    }
  }

  public class MyTableColumnCache implements Iterable<TableResultViewColumn> {
    private static final class Entry {
      public final @NotNull Object myColumnData;
      public final @NotNull TableResultViewColumn myTableColumn;

      Entry(@NotNull Object columnData, @NotNull TableResultViewColumn tableColumn) {
        myColumnData = columnData;
        myTableColumn = tableColumn;
      }
    }

    private final Int2ObjectMap<Entry> myColumnDataIndicesToEntries = new Int2ObjectOpenHashMap<>();

    public boolean hasCachedColumn(int columnDataIdx) {
      boolean isCached = hasValidEntry(columnDataIdx);
      if (!isCached) {
        myColumnDataIndicesToEntries.remove(columnDataIdx);
        return false;
      }
      return true;
    }

    public TableResultViewColumn getOrCreateColumn(int columnDataIdx) {
      Entry e = myColumnDataIndicesToEntries.get(columnDataIdx);
      if (!hasCachedColumn(columnDataIdx) || e == null) {
        e = createEntry(columnDataIdx);
        myColumnDataIndicesToEntries.put(columnDataIdx, e);
      }
      return e.myTableColumn;
    }

    private @NotNull Entry createEntry(int columnDataIdx) {
      TableResultViewColumn tableColumn = getModel().createColumn(columnDataIdx);
      Object columnData = getColumnData(columnDataIdx);
      return new Entry(Objects.requireNonNull(columnData), tableColumn);
    }

    public void retainColumns(final Collection<? extends GridColumn> columnsToRetain) {
      myColumnDataIndicesToEntries.values().removeIf(entry -> !columnsToRetain.contains(entry.myColumnData));
    }

    @Override
    public Iterator<TableResultViewColumn> iterator() {
      return myColumnDataIndicesToEntries.values().stream().map(entry -> entry.myTableColumn).iterator();
    }

    private boolean hasValidEntry(int columnDataIdx) {
      Entry e = myColumnDataIndicesToEntries.get(columnDataIdx);
      if (e == null) return false;

      Object cachedColumnData = e.myColumnData;
      Object currentColumnData = getColumnData(columnDataIdx);
      if (Comparing.equal(cachedColumnData, currentColumnData)) return true;

      // let's attempt to detect if it's the same column data
      //TODO try using primary keys and ROWID columns for it
      if (cachedColumnData instanceof GridRow cachedRow && currentColumnData instanceof GridRow currentRow) {
        if (cachedRow.getRowNum() == currentRow.getRowNum() && cachedRow.getSize() == currentRow.getSize()) {
          int mismatchedValuesCount = 0;
          for (int i = 0; i < cachedRow.getSize(); i++) {
            if (!Comparing.equal(cachedRow.getValue(i), currentRow.getValue(i))) {
              mismatchedValuesCount++;
            }
          }
          if (mismatchedValuesCount < 2) return true;
        }
      }
      return false;
    }

    private Object getColumnData(int columnDataIdx) {
      return isTransposed() ? getRow(columnDataIdx) : getColumn(columnDataIdx);
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
    return myResultPanel.getHoveredRowBackground();
  }

  @Override
  public void resetScroll() {
    getHorizontalScrollBar().setValue(0);
    getVerticalScrollBar().setValue(0);
  }

  @Override
  public boolean isHoveredRowBgHighlightingEnabled() {
    if (isStriped()) { return false; }

    return switch (myHoveredRowMode) {
      case AUTO -> ResultView.super.isHoveredRowBgHighlightingEnabled();
      case HIGHLIGHT -> true;
      case NOT_HIGHLIGHT -> false;
    };
  }

  @Override
  public void setHoveredRowHighlightMode(HoveredRowBgHighlightMode mode) {
    myHoveredRowMode = mode;
  }

  public void setStatisticsHeader(StatisticsTableHeader statisticsHeader) {
    myStatisticsHeader = statisticsHeader;
  }

  public StatisticsTableHeader getStatisticsHeader() {
    return myStatisticsHeader;
  }

  public void setStatisticsPanelMode(StatisticsPanelMode newPanelMode) {
    StatisticsPanelMode previousPanelMode = getStatisticsPanelMode();
    if (myStatisticsHeader != null) {
      myStatisticsHeader.setStatisticsPanelMode(newPanelMode);

      if (previousPanelMode != null) {
        myColumnLayout.resetLayout();
      }
    }
  }

  public StatisticsPanelMode getStatisticsPanelMode() {
    if (myStatisticsHeader != null) {
      return myStatisticsHeader.getStatisticsPanelMode();
    }
    return null;
  }

  @Override
  public void onLocalFilterStateChanged() {
    updateRowFilter();
  }
}
