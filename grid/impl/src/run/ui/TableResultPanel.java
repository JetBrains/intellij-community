package com.intellij.database.run.ui;

import com.intellij.database.DataGridBundle;
import com.intellij.database.DatabaseDataKeys;
import com.intellij.database.connection.throwable.info.ErrorInfo;
import com.intellij.database.datagrid.*;
import com.intellij.database.datagrid.GridDataRequest.GridDataRequestOwner;
import com.intellij.database.datagrid.color.GridColorModel;
import com.intellij.database.datagrid.color.GridColorModelImpl;
import com.intellij.database.editor.DataGridColors;
import com.intellij.database.extractors.*;
import com.intellij.database.extractors.DatabaseObjectFormatterConfig.DatabaseDisplayObjectFormatterConfig;
import com.intellij.database.remote.jdbc.LobInfo;
import com.intellij.database.run.actions.DeleteRowsAction;
import com.intellij.database.run.ui.grid.*;
import com.intellij.database.run.ui.table.FormatterConfigCache;
import com.intellij.database.run.ui.table.LocalFilterState;
import com.intellij.database.run.ui.table.TableResultRowHeader;
import com.intellij.database.run.ui.table.TableResultView;
import com.intellij.database.settings.DataGridAppearanceSettings;
import com.intellij.database.settings.DataGridAppearanceSettings.BooleanMode;
import com.intellij.database.settings.DataGridSettings;
import com.intellij.find.FindModel;
import com.intellij.find.SearchSession;
import com.intellij.ide.ActivityTracker;
import com.intellij.ide.ui.UISettings;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme;
import com.intellij.openapi.fileEditor.impl.EditorEmptyTextPainter;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.JBAutoScroller;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.ui.components.JBLoadingPanelListener;
import com.intellij.util.Alarm;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.GlobalScope;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static com.intellij.database.datagrid.GridPresentationMode.TABLE;
import static com.intellij.database.datagrid.GridUtil.*;
import static com.intellij.database.datagrid.mutating.ColumnDescriptor.Attribute;
import static com.intellij.database.editor.TableFileEditorState.*;
import static com.intellij.database.extractors.ObjectFormatterUtil.isValidUUIDWithKnownVersion;
import static com.intellij.database.run.actions.ChangeColumnDisplayTypeAction.isBinary;
import static com.intellij.database.run.actions.ChangeColumnDisplayTypeAction.isIntegerOrBigInt;
import static com.intellij.database.run.ui.DataAccessType.DATA_WITH_MUTATIONS;


/**
 * @author Gregory.Shrago
 */
public class TableResultPanel extends UserDataHolderBase
  implements
  DataGrid,
  GridModel.Listener<GridRow, GridColumn>,
  DataGridAppearance,
  ColumnModelModification
{
  private static final ColorKey HOVER_BACKGROUND =
    ColorKey.createColorKey("Table.hoverBackground", JBUI.CurrentTheme.Table.Hover.background(true));
  private final String myUniqueId = UUID.randomUUID().toString();
  private static final int RESULT_VIEW_COMPONENT_Z_INDEX = JLayeredPane.DEFAULT_LAYER;
  private static final int LOAD_DATA_Z_INDEX = JLayeredPane.MODAL_LAYER;

  private final Alarm alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
  private ErrorNotificationPanel myErrorNotificationPanel;

  private final GridMainPanel myMainPanel;
  private final LayeredPaneWithSizer myLayeredPane;
  private ResultView myResultView;

  private final ActionGroup myPopupActionGroup;
  protected final ActionGroup myGutterPopupActions;
  private final ActionGroup myColumnHeaderActions;
  private final ActionGroup myRowHeaderActions;
  private final GridColorsScheme myColorsScheme;
  private final GridColorsScheme myEditorColorsScheme;

  private final ColumnAttributes myColumnAttributes;
  private final Project myProject;
  private Function<DataGrid, ObjectFormatter> myObjectFormatterProvider = null;

  protected final GridFilterAndSortingComponent myFilterComponent;

  private final EventDispatcher<DataGridListener> myEventDispatcher = EventDispatcher.create(DataGridListener.class);

  private final GridMarkupModel<GridRow, GridColumn> myMarkupModel;
  private final GridDataHookUp<GridRow, GridColumn> myDataHookUp;
  private final MyLoadDataPanel myLoadDataPanel;
  private JComponent myMainResultViewComponent;

  private final HiddenColumnsSelectionHolder myHiddenColumnSelectionHolder;
  private final JBAutoScroller.AutoscrollLocker myAutoscrollLocker;
  private GridColorModel myColorModel;

  private SearchSession mySearchSession;
  private GridPresentationMode myPresentationMode = TABLE;
  private ResultViewFactory myViewFactory;
  private final ResultViewSettings myResultViewSettings = new ResultViewSettings();
  private BooleanMode myBooleanMode = DataGridAppearanceSettings.BooleanMode.TEXT;
  private final LocalFilterState myLocalFilterState;
  private final CoroutineScope cs = com.intellij.platform.util.coroutines.CoroutineScopeKt
    .childScope(GlobalScope.INSTANCE, getClass().getName(), Dispatchers.getIO(), true);
  private final SimpleModificationTracker myModificationTracker = new SimpleModificationTracker();
  private final SimpleModificationTracker myColumnModificationTracker = new SimpleModificationTracker();
  private final CachedValue<Map<ModelIndex<GridColumn>, DatabaseDisplayObjectFormatterConfig>> myFormatterConfigCached;

  public TableResultPanel(@NotNull Project project,
                          @NotNull GridDataHookUp<GridRow, GridColumn> dataHookUp,
                          @NotNull ActionGroup popupActions,
                          @NotNull BiConsumer<DataGrid, DataGridAppearance> configurator) {
    this(project, dataHookUp, popupActions, null, getGridColumnHeaderPopupActions(), ActionGroup.EMPTY_GROUP, false, configurator);
  }

  public TableResultPanel(@NotNull Project project,
                          @NotNull GridDataHookUp<GridRow, GridColumn> dataHookUp,
                          @NotNull ActionGroup popupActions,
                          @Nullable ActionGroup gutterPopupActions,
                          @NotNull ActionGroup columnHeaderActions,
                          @NotNull ActionGroup rowHeaderActions,
                          boolean useConsoleFonts,
                          @NotNull BiConsumer<DataGrid, DataGridAppearance> configurator) {
    myProject = project;
    myPopupActionGroup = popupActions;
    myGutterPopupActions = gutterPopupActions;
    myColumnHeaderActions = columnHeaderActions;
    myRowHeaderActions = rowHeaderActions;
    myEditorColorsScheme = new GridColorsScheme(useConsoleFonts, null);
    myMarkupModel = new GridMarkupModelImpl<>();
    myHiddenColumnSelectionHolder = new HiddenColumnsSelectionHolder();
    myAutoscrollLocker = new JBAutoScroller.AutoscrollLocker();
    myDataHookUp = dataHookUp;
    myMainPanel = new GridMainPanel(this, sink -> uiDataSnapshot(sink));
    myMainPanel.getComponent().putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, Boolean.TRUE);
    myColumnAttributes = new ColumnAttributes();
    myLocalFilterState = new LocalFilterState(this, true);

    configurator.accept(this, this);
    var settings = getSettings(this);
    var isEnableLocalFilterByDefault = settings == null || settings.isEnableLocalFilterByDefault();
    myLocalFilterState.setEnabled(myLocalFilterState.isEnabled() && isEnableLocalFilterByDefault);
    myFormatterConfigCached = CachedValuesManager.getManager(myProject)
      .createCachedValue(FormatterConfigCache.getCacheValueProvider(this));

    myColorsScheme = new GridColorsScheme(useConsoleFonts, DataGridAppearanceSettings.getSettings());
    myFilterComponent = new GridFilterAndSortingComponentImpl(myProject, this);
    GridSortingModel<GridRow, GridColumn> sortingModel = myDataHookUp.getSortingModel();
    myFilterComponent.toggleSortingPanel(sortingModel != null && sortingModel.isSortingEnabled());
    myMainPanel.setLoadingText("");
    myLoadDataPanel = new MyLoadDataPanel(this);
    myLayeredPane = new LayeredPaneWithSizer();
    myMainPanel.setCenterComponent(myLayeredPane);

    myMainPanel.addListener(new JBLoadingPanelListener.Adapter() {
      @Override
      public void onLoadingStart() {
        DataGridStartupActivity.DataEditorConfigurator.disableLoadingDelay(TableResultPanel.this);
      }
    });

    myViewFactory = ResultViewFactory.of(myPresentationMode);
    createResultView();

    if (sortingModel != null) {
      sortingModel.addListener(new GridSortingModel.Listener() {
        @Override
        public void orderingChanged() {
          setOrderingFromModel();
          updateSortKeysFromColumnAttributes();
        }
      }, this);
    }

    installDataHookUpListeners();
    columnsAdded(getDataModel(DATA_WITH_MUTATIONS).getColumnIndices());
    addDataGridListener(myProject.getMessageBus().syncPublisher(DataGridListener.TOPIC), this);
    MessageBusConnection connection = myProject.getMessageBus().connect(this);
    connection.subscribe(DataGridAppearanceSettings.TOPIC, () -> {
      myColorsScheme.updateFromSettings(DataGridAppearanceSettings.getSettings());
      DataGridAppearanceSettings s = DataGridAppearanceSettings.getSettings();
      setBooleanMode(s.getBooleanMode());
      boolean striped = s.isStripedTable();
      setResultViewStriped(striped);
      if (!striped) {
        myResultView.setShowVerticalLines(true);
        myResultView.setShowHorizontalLines(myResultViewSettings.myShowHorizontalLines);
      }
      myResultView.reinitSettings();
    });

    connection.subscribe(EditorColorsManager.TOPIC, scheme -> {
      globalSchemeChange(this, scheme);
      myResultView.reinitSettings();
    });

    connection.subscribe(DataGridSettings.TOPIC, () -> {
      updateFloatingPaging();
    });
  }

  @Override
  public @NotNull String getUniqueId() {
    return myUniqueId;
  }

  @Override
  public void loadingDelayDisabled() {
    myLayeredPane.remove(myLoadDataPanel);
  }

  @Override
  public void loadingDelayed() {
    myLayeredPane.add(myLoadDataPanel);
    myLayeredPane.setLayer(myLoadDataPanel, LOAD_DATA_Z_INDEX);
  }

  @Override
  public void addResultViewMouseListener(@NotNull MouseListener listener) {
    myResultViewSettings.myMouseListener = listener;
    myResultView.getComponent().addMouseListener(myResultViewSettings.myMouseListener);
  }

  @Override
  public @NotNull DataGridAppearance getAppearance() {
    return this;
  }

  @Override
  public void setResultViewVisibleRowCount(int v) {
    myResultViewSettings.myVisibleRowCount = v;
    if (myResultView != null) myResultView.setVisibleRowCount(v);
  }

  @Override
  public void setResultViewShowRowNumbers(boolean v) {
    myResultViewSettings.myShowRowNumbers = v;
    if (myResultView != null) myResultView.showRowNumbers(v);
  }

  @Override
  public void setTransparentColumnHeaderBackground(boolean v) {
    myResultViewSettings.myTransparentColumnHeaderBg = v;
    myColorModel = new GridColorModelImpl(this, getDatabaseMutator(this), myResultViewSettings.myTransparentRowHeaderBg, v);
    if (myResultView != null) myResultView.setTransparentColumnHeaderBackground(v);
  }

  @Override
  public void setTransparentRowHeaderBackground(boolean v) {
    myResultViewSettings.myTransparentRowHeaderBg = v;
    myColorModel = new GridColorModelImpl(this, getDatabaseMutator(this), v, myResultViewSettings.myTransparentColumnHeaderBg);
  }

  @Override
  public void setResultViewAdditionalRowsCount(int v) {
    myResultViewSettings.myAdditionalRowsCount = v;
    if (myResultView != null) myResultView.setAdditionalRowsCount(v);
  }

  @Override
  public void setResultViewSetShowHorizontalLines(boolean v) {
    myResultViewSettings.myShowHorizontalLines = v;
    if (myResultView != null) myResultView.setShowHorizontalLines(v);
  }

  @Override
  public void setResultViewStriped(boolean v) {
    myResultViewSettings.myStriped = v;
    if (myResultView != null) myResultView.setStriped(v);
  }

  @Override
  public void addSpaceForHorizontalScrollbar(boolean v) {
    myResultViewSettings.myAddSpaceForHorizontalScrollbar = v;
    if (myResultView != null) myResultView.addSpaceForHorizontalScrollbar(v);
  }

  @Override
  public void expandMultilineRows(boolean v) {
    myResultViewSettings.myExpandMultilineRows = v;
    if (myResultView != null) myResultView.expandMultilineRows(v);
  }

  @Override
  public void setBooleanMode(@NotNull BooleanMode v) {
    myBooleanMode = v;
  }

  @Override
  public @NotNull BooleanMode getBooleanMode() {
    return myBooleanMode;
  }

  @Override
  public void setResultViewAllowMultilineColumnLabels(boolean v) {
    myResultViewSettings.myAllowMultilineColumnLabels = v;
    if (myResultView != null) myResultView.setAllowMultilineLabel(v);
  }

  @Override
  public void setHoveredRowBgHighlightingEnabled(boolean v) {
    myResultViewSettings.myHoveredRowBgHighlightMode =
      v ? ResultView.HoveredRowBgHighlightMode.HIGHLIGHT : ResultView.HoveredRowBgHighlightMode.NOT_HIGHLIGHT;

    if (myResultView != null) {
      myResultView.setHoveredRowHighlightMode(myResultViewSettings.myHoveredRowBgHighlightMode);
    }
  }

  @Override
  public void setAnonymousColumnName(@NotNull String name) {
    myColumnAttributes.myAnonymousColumnName = name;
  }

  private void createResultView() {
    myResultView = myViewFactory.createResultView(this, myColumnHeaderActions, myRowHeaderActions);

    myColorModel = new GridColorModelImpl(this, getDatabaseMutator(this), myResultViewSettings.myTransparentRowHeaderBg, myResultViewSettings.myTransparentColumnHeaderBg);
    myMainResultViewComponent = myViewFactory.wrap(this, myResultView);

    myLayeredPane.add(myMainResultViewComponent);
    myLayeredPane.setLayer(myMainResultViewComponent, RESULT_VIEW_COMPONENT_Z_INDEX);
    if (FloatingPagingManager.shouldBePresent(this)) {
      FloatingPagingManager.installOn(this, myLayeredPane);
    }

    registerEscapeAction(myResultView);
    myResultView.addSelectionChangedListener(isAdjusting -> {
      myEventDispatcher.getMulticaster().onSelectionChanged(this, isAdjusting);
      if (!isAdjusting) {
        // MergingUpdateQueue in DataGridUtil.createEDTSafeWrapper will cause "Write-unsafe context" exception
        // without this invokeLater
        ApplicationManager.getApplication().invokeLater(() -> activeGridChanged(this));
      }
    });
    myResultView.addMouseListenerToComponents(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        if (!stopEditing()) {
          cancelEditing();
        }
        if (myPopupActionGroup != ActionGroup.EMPTY_GROUP) {
          ActionManager.getInstance().createActionPopupMenu(ActionPlaces.EDITOR_POPUP, myPopupActionGroup).getComponent().show(comp, x, y);
        }
      }
    });
    if (myResultViewSettings.myMouseListener != null) {
      myResultView.getComponent().addMouseListener(myResultViewSettings.myMouseListener);
    }
    if (myResultViewSettings.myVisibleRowCount > 0) myResultView.setVisibleRowCount(myResultViewSettings.myVisibleRowCount);
    myResultView.showRowNumbers(myResultViewSettings.myShowRowNumbers);
    myResultView.setTransparentColumnHeaderBackground(myResultViewSettings.myTransparentColumnHeaderBg);
    myResultView.setAdditionalRowsCount(myResultViewSettings.myAdditionalRowsCount);
    myResultView.setShowHorizontalLines(myResultViewSettings.myShowHorizontalLines);
    myResultView.setAllowMultilineLabel(myResultViewSettings.myAllowMultilineColumnLabels);
    myResultView.setStriped(myResultViewSettings.myStriped);
    myResultView.addSpaceForHorizontalScrollbar(myResultViewSettings.myAddSpaceForHorizontalScrollbar);
    myResultView.expandMultilineRows(myResultViewSettings.myExpandMultilineRows);
    myResultView.setHoveredRowHighlightMode(myResultViewSettings.myHoveredRowBgHighlightMode);
  }

  @Override
  public @NotNull GridColorModel getColorModel() {
    return myColorModel;
  }

  @Override
  public @NotNull JBAutoScroller.AutoscrollLocker getAutoscrollLocker() {
    return myAutoscrollLocker;
  }

  @Override
  public HiddenColumnsSelectionHolder getHiddenColumnSelectionHolder() {
    return myHiddenColumnSelectionHolder;
  }

  @Override
  public @NotNull GridRowHeader createRowHeader(@NotNull TableResultView table) {
    return new TableResultRowHeader(this, table, myGutterPopupActions);
  }

  public void installDataHookUpListeners() {
    myDataHookUp.addRequestListener(new GridDataHookUp.RequestListener<>() {
      @Override
      public void error(@NotNull GridRequestSource source, @NotNull ErrorInfo errorInfo) {
        GridRequestSource.GridRequestPlace<?, ?> gridRequestPlace = ObjectUtils.tryCast(source.place, GridRequestSource.GridRequestPlace.class);
        if (gridRequestPlace == null || gridRequestPlace.getGrid() != TableResultPanel.this) return;
        handleError(source, errorInfo);
      }

      @Override
      public void updateCountReceived(@NotNull GridRequestSource source, int updateCount) {

      }

      @Override
      public void requestFinished(@NotNull GridRequestSource source, boolean success) {
        doRepaint(source);
        GridRequestSource.GridRequestPlace<?, ?> gridRequestPlace = ObjectUtils.tryCast(source.place, GridRequestSource.GridRequestPlace.class);
        if (gridRequestPlace == null || gridRequestPlace.getGrid() != TableResultPanel.this) return;

        if (!source.errorOccurred()) hideErrorPanel();
      }

      private void doRepaint(@NotNull GridRequestSource source) {
        if (!source.isMutatedDataLocally()) return;
        myResultView.getComponent().repaint(50);
        JViewport header =
          myMainResultViewComponent instanceof JScrollPane ? ((JScrollPane)myMainResultViewComponent).getRowHeader() : null;
        Component rowHeader = header == null ? null : header.getView();
        if (rowHeader != null) rowHeader.repaint(50);
      }
    }, this);
    getDataModel(DATA_WITH_MUTATIONS).addListener(this, this);
  }

  @Override
  public void columnsAdded(ModelIndexSet<GridColumn> columnIndices) {
    myColumnAttributes.newColumns(this, getDataModel(DATA_WITH_MUTATIONS).getColumns());
    setOrderingFromModel();
    updateSortKeysFromColumnAttributes();
    myResultView.columnsAdded(columnIndices);
    trueLayout();
    restoreColumnsOrder();
    myColumnModificationTracker.incModificationCount();
  }

  protected void columnAttributesUpdated() {
    myResultView.columnAttributesUpdated();
    restoreColumnsOrder();
  }

  public void restoreColumnsOrder() {
    Map<Integer, ModelIndex<GridColumn>> expectedToModel = new LinkedHashMap<>();
    GridModel<GridRow, GridColumn> model = getDataModel(DATA_WITH_MUTATIONS);
    JBIterable<ModelIndex<GridColumn>> modelIndices = model.getColumnIndices().asIterable();
    for (ModelIndex<GridColumn> modelIndex : modelIndices) {
      GridColumn column = model.getColumn(modelIndex);
      if (column == null) return;
      int initialPosition = getInitialPosition(column);
      if (initialPosition == UNKNOWN_COLUMN_POSITION) return;
      if (initialPosition == DEFAULT_OR_HIDDEN_COLUMN_POSITION) {
        initialPosition = modelIndex.value;
      }
      else {
        initialPosition = fromSerializedPosition(initialPosition);
      }
      expectedToModel.put(initialPosition, modelIndex);
    }
    myResultView.restoreColumnsOrder(expectedToModel);
  }

  @Override
  public void columnsRemoved(ModelIndexSet<GridColumn> columns) {
    myResultView.columnsRemoved(columns);
    trueLayout();
    myColumnModificationTracker.incModificationCount();
  }

  @Override
  public void rowsAdded(ModelIndexSet<GridRow> rows) {
    myResultView.rowsAdded(rows);
    ActivityTracker.getInstance().inc();
    trueLayout();
  }

  @Override
  public void afterLastRowAdded() {
    clearAllColumnsDisplayTypesAllowableCache();
    myResultView.afterLastRowAdded();
    ActivityTracker.getInstance().inc();
  }

  @Override
  public void fireValueEdited(@Nullable Object object) {
    myEventDispatcher.getMulticaster().onValueEdited(this, object);
  }

  @Override
  public @NotNull ResultView getResultView() {
    return myResultView;
  }

  @Override
  public void rowsRemoved(ModelIndexSet<GridRow> rows) {
    myResultView.rowsRemoved(rows);
    trueLayout();
  }

  @Override
  public void cellsUpdated(ModelIndexSet<GridRow> rows, ModelIndexSet<GridColumn> columns, @Nullable GridRequestSource.RequestPlace place) {
    for (ModelIndex<GridColumn> columnIdx : columns.asIterable()) {
      clearDisplayTypesAllowableCache(columnIdx);
    }
    myResultView.cellsUpdated(rows, columns, place);
  }

  private void registerEscapeAction(@NotNull ResultView resultView) {
    resultView.registerEscapeAction(new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myErrorNotificationPanel != null) {
          hideErrorPanel();
        }
        else if (mySearchSession != null) {
          mySearchSession.close();
        }
      }

      @Override
      public boolean isEnabled() {
        return super.isEnabled() && !isEditing() && (myErrorNotificationPanel != null || mySearchSession != null);
      }
    });
  }

  @Override
  public boolean isSortViaOrderBySupported() {
    return myDataHookUp.getSortingModel() != null;
  }

  @Override
  public boolean isSortViaOrderBy() {
    GridSortingModel<GridRow, GridColumn> sortingModel = myDataHookUp.getSortingModel();
    return sortingModel != null && sortingModel.isSortingEnabled();
  }

  @Override
  public void setSortViaOrderBy(boolean sortViaOrderBy) {
    GridSortingModel<GridRow, GridColumn> sortingModel = myDataHookUp.getSortingModel();
    if (sortingModel == null) return;

    boolean reload = updateDataOrdering(false) || sortingModel.isSortingEnabled() != sortViaOrderBy;
    sortingModel.setSortingEnabled(sortViaOrderBy);
    if (reload && isSafeToReload()) {
      myDataHookUp.getLoader().loadFirstPage(new GridRequestSource(new DataGridRequestPlace(this)));
    }
  }

  @Override
  public @NotNull RowSortOrder.Type getSortOrder(@NotNull ModelIndex<GridColumn> columnIdx) {
    int sortOrder = shouldExposeSortingInfo() ? getSortOrder(getDataModel().getColumn(columnIdx)) : 0;
    return sortOrder == 0 ? RowSortOrder.Type.UNSORTED : sortOrder < 0 ? RowSortOrder.Type.ASC : RowSortOrder.Type.DESC;
  }

  @Override
  public int getThenBySortOrder(@NotNull ModelIndex<GridColumn> columnIdx) {
    return shouldExposeSortingInfo() ? Math.abs(getSortOrder(getDataModel().getColumn(columnIdx))) : 0;
  }

  private boolean shouldExposeSortingInfo() {
    // we don't support local sorting in transposed mode
    return !getResultView().isTransposed() || isSortViaOrderBy();
  }

  @Override
  public int getSortOrder(@Nullable GridColumn column) {
    return column != null ? myColumnAttributes.getSortOrder(column) : 0;
  }

  @Override
  public void addDataGridListener(DataGridListener listener, Disposable disposable) {
    myEventDispatcher.addListener(listener, disposable);
  }

  @Override
  public @NotNull GridColorsScheme getColorsScheme() {
    return myColorsScheme;
  }

  @Override
  public @Nullable Color getHoveredRowBackground() {
    return myColorsScheme.getColor(HOVER_BACKGROUND);
  }

  @Override
  public @Nullable Color getStripeRowBackground() {
    if (myColorsScheme.isExplicitDefaultBackground()) {
      return myColorsScheme.getDelegate().getDefaultBackground(); // alternate background from editor colors scheme and datasource color
    }
    Color color = myColorsScheme.getColor(DataGridColors.GRID_STRIPE_COLOR);
    color = color != null ? color : myColorsScheme.getColor(EditorColors.CARET_ROW_COLOR);
    if (color == null) {
      AbstractColorsScheme delegate = ObjectUtils.tryCast(myColorsScheme.getDelegate(), AbstractColorsScheme.class);
      AbstractColorsScheme original = delegate == null ? null : delegate.getOriginal();
      color = original == null ? null : original.getColor(EditorColors.CARET_ROW_COLOR);
    }
    return color;
  }

  @Override
  public @NotNull GridColorsScheme getEditorColorsScheme() {
    return myEditorColorsScheme;
  }

  @Override
  public void searchSessionStarted(@NotNull SearchSession searchSession) {
    mySearchSession = searchSession;
    myResultView.searchSessionStarted(searchSession);
    getResultView().searchSessionUpdated();
  }

  @Override
  public void searchSessionStopped(@NotNull SearchSession searchSession) {
    assert mySearchSession == searchSession;
    mySearchSession = null;
    getResultView().searchSessionUpdated();
  }

  @Override
  public @Nullable SearchSession getSearchSession() {
    return mySearchSession;
  }

  @Override
  public TreeMap<Integer, GridColumn> getSortOrderMap() {
    return myColumnAttributes.getSortOrderMap();
  }

  @Override
  public int countSortedColumns() {
    return myColumnAttributes.countSortedColumns();
  }

  @Override
  public void setObjectFormatterProvider(@NotNull Function<DataGrid, ObjectFormatter> objectFormatterProvider) {
    myObjectFormatterProvider = objectFormatterProvider;
  }

  @Override
  public ObjectFormatter getObjectFormatter() {
    return myObjectFormatterProvider.apply(this);
  }

  @Override
  public boolean isEditable() {
    return GridHelper.get(this).isEditable(this);
  }

  @Override
  public boolean isCellEditingAllowed() {
    return myResultView instanceof ResultViewWithCells && ((ResultViewWithCells)myResultView).isCellEditingAllowed();
  }

  @Override
  public void setCells(@NotNull ModelIndexSet<GridRow> rows, @NotNull ModelIndexSet<GridColumn> columns, @Nullable Object value) {
    GridMutator<GridRow, GridColumn> mutator = myDataHookUp.getMutator();
    if (mutator != null && isSafeToUpdate(rows, columns, value)) {
      mutator.mutate(new GridRequestSource(new DataGridRequestPlace(this, rows, columns)), rows,
                     columns, value, true);
    }
  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public @NotNull ActionCallback submit() {
    GridMutator.DatabaseMutator<GridRow, GridColumn> mutator = getDatabaseMutator(this);
    if (mutator == null || !mutator.hasPendingChanges()) return ActionCallback.DONE;
    GridSelection<GridRow, GridColumn> selection = getSelectionModel().store();
    GridRequestSource source =
      new GridRequestSource(new DataGridRequestPlace(this, mutator.getAffectedRows(), ModelIndexSet.forColumns(this, -1)));
    mutator.submit(source, true);
    return source.getActionCallback().doWhenDone(() -> getSelectionModel().restore(getSelectionModel().fit(selection)));
  }

  @Override
  public @NotNull GridDataSupport getDataSupport() {
    return new GridDataSupportImpl(this, myDataHookUp.getMutator());
  }

  @Override
  public @NotNull SelectionModel<GridRow, GridColumn> getSelectionModel() {
    return SelectionModelUtil.get(this, myResultView);
  }

  @Override
  public @Nls @NotNull String getDisplayName() {
    GridDataRequestOwner owner = ObjectUtils.tryCast(getDataHookup(), GridDataRequestOwner.class);
    return owner != null ? StringUtil.notNullize(owner.getDisplayName()) : "";
  }

  @Override
  public @NotNull GridMarkupModel<GridRow, GridColumn> getMarkupModel() {
    return myMarkupModel;
  }

  @Override
  public @NotNull GridModel<GridRow, GridColumn> getDataModel(@NotNull DataAccessType reason) {
    return reason.getModel(myDataHookUp);
  }

  public @NotNull GridModel<GridRow, GridColumn> getDataModel() {
    return myDataHookUp.getDataModel();
  }

  @Override
  public @NotNull GridDataHookUp<GridRow, GridColumn> getDataHookup() {
    return myDataHookUp;
  }

  @Override
  public @NotNull RawIndexConverter getRawIndexConverter() {
    return myResultView.getRawIndexConverter();
  }

  @Override
  public @NotNull JComponent getPreferredFocusedComponent() {
    return myResultView.getPreferredFocusedComponent();
  }

  @Override
  public @NotNull JComponent getMainResultViewComponent() {
    return myMainResultViewComponent;
  }

  @Override
  public @NotNull GridMainPanel getPanel() {
    return myMainPanel;
  }

  @Override
  public @NotNull ModelIndexSet<GridColumn> getVisibleColumns() {
    return myResultView.getVisibleColumns();
  }

  @Override
  public @NotNull ModelIndexSet<GridRow> getVisibleRows() {
    return myResultView.getVisibleRows();
  }

  @Override
  public int getVisibleRowsCount() {
    return myResultView.getViewRowCount();
  }

  @Override
  public boolean isEditing() {
    return myResultView.isEditing();
  }

  @Override
  public boolean stopEditing() {
    return myResultView.stopEditing();
  }

  @Override
  public void cancelEditing() {
    myResultView.cancelEditing();
  }

  @Override
  public void editSelectedCell() {
    if (myResultView instanceof ResultViewWithCells) {
      ((ResultViewWithCells)myResultView).editSelectedCell();
    }
  }

  @Override
  public @NotNull String getUnambiguousColumnName(@NotNull ModelIndex<GridColumn> column) {
    GridColumn c = getDataModel(DATA_WITH_MUTATIONS).getColumn(column);
    return c == null ? "" : myColumnAttributes.getName(c).trim();
  }

  @Override
  public boolean isViewModified() {
    for (GridColumn column : getDataModel().getColumns()) {
      boolean shouldBeShown = !column.getAttributes().contains(Attribute.HIDDEN);
      boolean shown = isColumnEnabled(column);
      boolean sortOrderChanged = getSortOrder(column) != 0;
      if (shown != shouldBeShown || sortOrderChanged) {
        return true;
      }
    }
    return myResultView.isViewModified();
  }

  @Override
  public int getVisibleColumnCount() {
    return myResultView.getViewColumnCount();
  }

  void showError(@NotNull ErrorInfo errorInfo, final @Nullable DataGridRequestPlace source) {
    hideErrorPanel();
    ErrorNotificationPanel.Builder builder =
      ErrorNotificationPanel.create(errorInfo.getMessage(), errorInfo.getOriginalThrowable());
    List<ErrorInfo.Fix> fixes = errorInfo.getFixes();
    if (!fixes.isEmpty()) {
      for (ErrorInfo.Fix fix : fixes) {
        builder.addLink(fix.getName(), null, () -> GridHelper.get(this).applyFix(myProject, fix, null));
      }
      builder.addSpace();
    }
    else {
      builder.addDetailsButton();
    }
    if (source != null && source.getRows().size() == 1) {
      ModelIndex<GridRow> modelRowIdx = source.getRows().first();
      ModelIndex<GridColumn> modelColumnIdx = source.getColumns().first();
      final ViewIndex<GridRow> viewRowIdx = modelRowIdx.toView(this);
      final ViewIndex<GridColumn> viewColumnIdx = modelColumnIdx.toView(this);
      int r = viewRowIdx.asInteger() + 1;
      int c = viewColumnIdx.asInteger() + 1;
      //noinspection DialogTitleCapitalization
      String title = DataGridBundle.message("action.row.choice.col.text", r, c, c < 1 ? 0 : 1);
      builder.addLink(title, KeyEvent.VK_N, () -> {
        if (viewRowIdx.isValid(this) && viewColumnIdx.isValid(this)) {
          scrollToLocally(this, viewRowIdx, viewColumnIdx);
        }
      });
    }
    myErrorNotificationPanel = builder
      .addCloseButton(this::hideErrorPanel).build();
    myMainPanel.setBottomComponent(myErrorNotificationPanel);
    myErrorNotificationPanel.revalidate();
    myMainPanel.repaint();
  }

  void hideErrorPanel() {
    if (myErrorNotificationPanel == null) return;
    myMainPanel.setBottomComponent(null);
    myErrorNotificationPanel = null;
    myMainPanel.revalidate();
    myMainPanel.repaint();
  }

  private void handleError(@NotNull GridRequestSource requestSource, @NotNull ErrorInfo errorInfo) {
    final GridRequestSource.RequestPlace source = requestSource.place;
    ApplicationManager.getApplication().invokeLater(() -> {
      if (myFilterComponent.getFilterPanel().handleError(requestSource, errorInfo)) return;
      GridEditorPanel sortingPanel = myFilterComponent.getSortingPanel();
      if (sortingPanel != null && sortingPanel.handleError(requestSource, errorInfo)) return;
      showError(errorInfo, ObjectUtils.tryCast(source, DataGridRequestPlace.class));
    });
  }

  protected void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(CommonDataKeys.PROJECT, myProject);
    sink.set(PlatformDataKeys.COPY_PROVIDER, new GridCopyProvider(this));
    sink.set(PlatformDataKeys.PASTE_PROVIDER, new GridPasteProvider(this, GridUtil::retrieveDataFromText));
    sink.set(PlatformDataKeys.DELETE_ELEMENT_PROVIDER, new DeleteRowsAction());
    sink.set(DatabaseDataKeys.DATA_GRID_KEY, this);
    sink.set(LangDataKeys.NO_NEW_ACTION, Boolean.TRUE);

    sink.lazy(CommonDataKeys.PSI_FILE, () -> {
      //else PSI_ELEMENT.getContainingFile is taken which is null
      VirtualFile file = getVirtualFile(this);
      return file == null ? null : PsiManager.getInstance(getProject()).findFile(file);
    });
    sink.lazy(CommonDataKeys.PSI_ELEMENT, () -> {
      return getPsiElementForSelection(this);
    });
    sink.lazy(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY, () -> {
      return ContainerUtil.ar(getPsiElementForSelection(this));
    });
  }

  @Override
  public void toggleSortColumns(@NotNull List<ModelIndex<GridColumn>> columns, boolean additive) {
    if (columns.isEmpty()) return;
    GridModel<GridRow, GridColumn> model = getDataModel();
    int oldOrder = myColumnAttributes.getSortOrder(model.getColumn(columns.get(0)));
    boolean forceAscOrder = !additive && !areOnlySortedColumns(columns, this);
    RowSortOrder.Type order = forceAscOrder || oldOrder == 0 ? RowSortOrder.Type.ASC :
                              oldOrder < 0 ? RowSortOrder.Type.DESC :
                              RowSortOrder.Type.UNSORTED;
    sortColumns(columns, order, additive);
    activeGridListener().onColumnSortingToggled(myMainPanel.getGrid());
  }

  @Override
  public void sortColumns(@NotNull List<ModelIndex<GridColumn>> columns, @NotNull RowSortOrder.Type order, boolean additive) {
    if (columns.isEmpty()) return;
    GridSortingModel<GridRow, GridColumn> model = myDataHookUp.getSortingModel();
    if (additive && model != null) {
      List<ModelIndex<GridColumn>> ordering =
        ContainerUtil.map(model.getOrdering(), sortOrder -> sortOrder.getColumn());
      if (!GridHelper.get(this).canSortTogether(this, ordering, columns)) return;
    }
    if (sortingEquals(columns, order, !additive)) return;
    if (!additive) myColumnAttributes.resetOrdering();
    for (ModelIndex<GridColumn> column : columns) {
      changeSortOrder(column, order);
    }
    updateSortKeysFromColumnAttributes();
    updateDataOrderingIfNeeded();
  }

  @Override
  public ColumnAttributes getColumnAttributes() {
    return myColumnAttributes;
  }

  public boolean sortingEquals(@NotNull List<ModelIndex<GridColumn>> columns, @NotNull RowSortOrder.Type order, boolean checkOtherColumns) {
    GridModel<GridRow, GridColumn> model = getDataModel();
    Set<Integer> newColumns = ContainerUtil.map2Set(columns, c -> c.asInteger());
    for (ModelIndex<GridColumn> idx : model.getColumnIndices().asIterable()) {
      int oldOrder = myColumnAttributes.getSortOrder(model.getColumn(idx));
      if (newColumns.contains(idx.asInteger())) {
        if (order == RowSortOrder.Type.ASC ? oldOrder >= 0 :
            order == RowSortOrder.Type.DESC ? oldOrder <= 0 :
            oldOrder != 0) {
          return false;
        }
      }
      else if (checkOtherColumns && oldOrder != 0) return false;
    }
    return true;
  }

  @Override
  public @NotNull Language getContentLanguage(@NotNull ModelIndex<GridColumn> columnIdx) {
    GridColumn column = getDataModel().getColumn(columnIdx);
    return column != null ? getContentLanguage(column) : Language.ANY;
  }

  @NotNull
  Language getContentLanguage(@NotNull GridColumn column) {
    Language fromAttributes = myColumnAttributes.getContentLanguage(column);
    return fromAttributes != null ? fromAttributes : getInitialContentLanguage(column);
  }

  @Override
  public boolean isRowFilteredOut(@NotNull ModelIndex<?> rowIdx) {
    return false;
  }

  @Override
  public void setContentLanguage(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull Language language) {
    GridColumn column = getDataModel().getColumn(columnIdx);
    if (column != null) {
      myColumnAttributes.setContentLanguage(column, language);
      myResultView.contentLanguageUpdated(columnIdx, language);
      myEventDispatcher.getMulticaster().onCellLanguageChanged(columnIdx, language);
    }
  }

  @Override
  public void setDisplayType(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull DisplayType displayType) {
    GridColumn column = getDataModel().getColumn(columnIdx);
    if (column != null) {
      myColumnAttributes.setDisplayType(column, displayType);
      myResultView.displayTypeUpdated(columnIdx, displayType);
      myEventDispatcher.getMulticaster().onCellDisplayTypeChanged(columnIdx, displayType);
      myModificationTracker.incModificationCount();
    }
  }

  @Override
  public @NotNull DisplayType getPureDisplayType(@NotNull ModelIndex<GridColumn> columnIdx) {
    GridColumn column = getDataModel().getColumn(columnIdx);
    if (column == null) return BinaryDisplayType.HEX;
    DisplayType fromAttributes = myColumnAttributes.getDisplayType(column); // set by user
    if (fromAttributes != null) return fromAttributes;
    var defaultType = isIntegerOrBigInt(columnIdx, this) ? NumberDisplayType.NUMBER : BinaryDisplayType.DETECT;
    return ObjectUtils.notNull(getInitialDisplayType(column), defaultType);
  }

  /**
   * returns real type, not DETECT type
   */
  @Override
  public @NotNull DisplayType getDisplayType(@NotNull ModelIndex<GridColumn> columnIdx) {
    DisplayType displayType = getPureDisplayType(columnIdx);
    return displayType != BinaryDisplayType.DETECT
           ? displayType
           : getOptimalBinaryDisplayTypeForDetect(columnIdx);
  }

  @Override
  public @NotNull BinaryDisplayType getOptimalBinaryDisplayTypeForDetect(@NotNull ModelIndex<GridColumn> columnIdx) {
    GridColumn column = getDataModel(DATA_WITH_MUTATIONS).getColumn(columnIdx);
    if (column == null) {
      return BinaryDisplayType.HEX;
    }
    var columnAttributes = myColumnAttributes.myAttributesMap.get(column);
    if (columnAttributes == null) {
      return BinaryDisplayType.HEX;
    }
    if (columnAttributes.myDisplayTypesInfoCache == null) {
      updateDisplayTypesAllowable(columnIdx); // it ensures myDisplayTypesInfoCache becomes not null
    }

    boolean uuidAllowed = columnAttributes.myDisplayTypesInfoCache.allowedDisplayTypes().contains(BinaryDisplayType.UUID);
    boolean uuidKnownVersion = columnAttributes.myDisplayTypesInfoCache.uuidHasKnownVersion;
    boolean uuidSwapKnownVersion = columnAttributes.myDisplayTypesInfoCache.uuidHasSwapKnownVersion;
    boolean detectUUIDInBinaryColumns = isDetectUUIDInBinaryColumns(this);
    boolean hasRows = getDataModel(DATA_WITH_MUTATIONS).getRowCount() > 0;

    if (detectUUIDInBinaryColumns && uuidAllowed && hasRows) {
      if (!uuidKnownVersion && uuidSwapKnownVersion)
        return BinaryDisplayType.UUID_SWAP;
      return BinaryDisplayType.UUID;
    }

    if (isDetectTextInBinaryColumns(this) &&
        myColumnAttributes.getIsDisplayTypeAllowed(column, BinaryDisplayType.TEXT) &&
        hasRows) {
      return BinaryDisplayType.TEXT;
    }
    return BinaryDisplayType.HEX;
  }

  private void updateDisplayTypesAllowable(ModelIndex<GridColumn> columnIdx) {
    GridColumn column = getDataModel(DATA_WITH_MUTATIONS).getColumn(columnIdx);
    if (column == null) return;
    LinkedHashSet<BinaryDisplayType> newAllowedDisplayTypes = new LinkedHashSet<>();
    boolean uuidHasKnownVersion = false, uuidSwapHasKnownVersion = false;
    if (checkTextViewAllowed(columnIdx)) newAllowedDisplayTypes.add(BinaryDisplayType.TEXT);
    if (isBinary(columnIdx, this) && checkSomeUUIDViewAllowed(columnIdx)) {
      newAllowedDisplayTypes.add(BinaryDisplayType.UUID);
      newAllowedDisplayTypes.add(BinaryDisplayType.UUID_SWAP);
      uuidHasKnownVersion = checkUUIDHasKnownVersion(columnIdx, false);
      uuidSwapHasKnownVersion = checkUUIDHasKnownVersion(columnIdx, true);
    }
    myColumnAttributes.myAttributesMap.get(column).myDisplayTypesInfoCache =
      new ColumnAttributes.DisplayTypesInfo(newAllowedDisplayTypes, uuidHasKnownVersion, uuidSwapHasKnownVersion);
    myResultView.displayTypeUpdated(columnIdx, BinaryDisplayType.DETECT);
    myModificationTracker.incModificationCount();
  }

  private void clearDisplayTypesAllowableCache(ModelIndex<GridColumn> columnIdx) {
    GridColumn column = getDataModel(DATA_WITH_MUTATIONS).getColumn(columnIdx);
    if (column == null) return;
    myColumnAttributes.clearDisplayTypeAllowedCache(column);
  }

  private void clearAllColumnsDisplayTypesAllowableCache() {
    GridModel<GridRow, GridColumn> model = getDataModel(DATA_WITH_MUTATIONS);
    JBIterable<ModelIndex<GridColumn>> modelIndices = model.getColumnIndices().asIterable();
    for (ModelIndex<GridColumn> columnIdx : modelIndices) {
      GridColumn column = getDataModel(DATA_WITH_MUTATIONS).getColumn(columnIdx);
      if (column == null) return;
      myColumnAttributes.clearDisplayTypeAllowedCache(column);
    }
  }

  @Override
  public boolean isDisplayTypeApplicable(@NotNull BinaryDisplayType displayType, @NotNull ModelIndex<GridColumn> columnIdx) {
    GridColumn column = getDataModel(DATA_WITH_MUTATIONS).getColumn(columnIdx);
    if (column == null) return false;
    if (myColumnAttributes.myAttributesMap.get(column).myDisplayTypesInfoCache == null) updateDisplayTypesAllowable(columnIdx);
    return myColumnAttributes.getIsDisplayTypeAllowed(column, displayType) ||
           displayType == BinaryDisplayType.UUID && myColumnAttributes.getIsDisplayTypeAllowed(column, BinaryDisplayType.UUID_SWAP);
  }

  private boolean checkTextViewAllowed(@NotNull ModelIndex<GridColumn> columnIdx) {
    return isViewAllowed(columnIdx, (value) -> TextInfo.tryDetectString(value) != null);
  }

  private boolean checkSomeUUIDViewAllowed(@NotNull ModelIndex<GridColumn> columnIdx) {
    return isViewAllowed(columnIdx, (value) -> value.length == 16);
  }

  private boolean checkUUIDHasKnownVersion(@NotNull ModelIndex<GridColumn> columnIdx, boolean swapFlag) {
    return isViewAllowed(columnIdx, (value) -> isValidUUIDWithKnownVersion(ObjectFormatterUtil.toUUID(value, swapFlag)));
  }

  private boolean isViewAllowed(@NotNull ModelIndex<GridColumn> columnIdx, Function<byte[], Boolean> valueViewCheck) {
    boolean isViewAllowed = true;
    var dataModel = getDataModel(DATA_WITH_MUTATIONS);
    GridColumn column = dataModel.getColumn(columnIdx);
    if (column == null) return false;
    var rowsToInspect = Math.min(dataModel.getRowCount(), 1000);
    for (ModelIndex<GridRow> rowIdx : dataModel.getRowIndices().asIterable().take(rowsToInspect)) {
      GridRow row = dataModel.getRow(rowIdx);
      if (row == null) return false;
      Object value = column.getValue(row);
      if (value == null) continue;
      if (value instanceof byte[]) {
        isViewAllowed &= valueViewCheck.apply((byte[])value);
        continue;
      }
      if (value instanceof LobInfo.BlobInfo) {
        isViewAllowed &= valueViewCheck.apply(((LobInfo.BlobInfo)value).data);
        continue;
      }
      if (value instanceof TextInfo) {
        isViewAllowed &= valueViewCheck.apply(((TextInfo)value).bytes);
        continue;
      }
      isViewAllowed = false;
      break;
    }
    return isViewAllowed;
  }

  @Override
  public @NotNull ModelIndex<GridColumn> getContextColumn() {
    return myResultView.getContextColumn();
  }

  @Override
  public void setFilterText(@NotNull String filter, int caretPosition) {
    ThreadingAssertions.assertEventDispatchThread();

    if (!isFilteringComponentShown() && !StringUtil.isEmptyOrSpaces(filter)) {
      toggleFilteringComponent();
      getFilterComponent().getFilterPanel().getComponent().requestFocusInWindow();
    }
    GridFilterPanel.setFilterText(myFilterComponent.getFilterPanel(), this, filter, caretPosition);
  }

  @Override
  public @NotNull String getFilterText() {
    return myFilterComponent.getFilterPanel().getText();
  }

  @Override
  public boolean isReady() {
    return !getDataModel().isUpdatingNow();
  }

  @Override
  public boolean isEmpty() {
    return getDataModel(DATA_WITH_MUTATIONS).getRowCount() == 0;
  }

  @Override
  public void dispose() {
    if (myResultView != null) Disposer.dispose(myResultView);
    kotlinx.coroutines.CoroutineScopeKt.cancel(cs, null);
  }

  @Override
  public void showCell(final int absoluteRowIdx, final @NotNull ModelIndex<GridColumn> column) {
    ApplicationManager.getApplication().invokeLater(() -> {
      int rawRowIndex = adjustAbsoluteRowIdx(absoluteRowIdx) + 1;
      scrollTo(rawRowIndex, column);
    });
  }

  private int adjustAbsoluteRowIdx(int absoluteRowIdx) {
    final GridPagingModel<GridRow, GridColumn> pageModel = myDataHookUp.getPageModel();
    if (!pageModel.isTotalRowCountPrecise()) {
      return absoluteRowIdx;
    }
    long lastRowIdx = pageModel.getTotalRowCount() - 1;
    return Math.min(lastRowIdx > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)lastRowIdx, absoluteRowIdx);
  }

  private void scrollTo(final int dataRowIndex, ModelIndex<GridColumn> columnIdx) {
    final GridPagingModel<GridRow, GridColumn> pageModel = myDataHookUp.getPageModel();
    final Runnable localScrollRunnable = () -> {
      ModelIndex<GridRow> rowIdx = pageModel.findRow(dataRowIndex);
      Pair<Integer, Integer> rowAndColumn = getRawIndexConverter().rowAndColumn2View().fun(rowIdx.asInteger(), columnIdx.asInteger());
      ViewIndex<GridRow> row = ViewIndex.forRow(this, rowAndColumn.first);
      ViewIndex<GridColumn> column = ViewIndex.forColumn(this, rowAndColumn.second);
      scrollToLocally(this, row, column);
    };
    if (pageModel.findRow(dataRowIndex).isValid(this)) {
      localScrollRunnable.run();
    }
    else {
      GridLoader loader = myDataHookUp.getLoader();
      int offset = Math.max(0, dataRowIndex - pageModel.getPageSize() / 2);
      GridRequestSource source = new GridRequestSource(new DataGridRequestPlace(this));
      source.getActionCallback().doWhenDone(localScrollRunnable);
      loader.load(source, offset);
    }
  }

  @Override
  public boolean isFilteringSupported() {
    return getDataHookup().getFilteringModel() != null;
  }

  @Override
  public boolean isFilteringComponentShown() {
    return myFilterComponent.getComponent().isVisible();
  }

  @Override
  public void toggleFilteringComponent() {
    myFilterComponent.getComponent().setVisible(!myFilterComponent.getComponent().isVisible());
  }

  @Override
  public void resetLayout() {
    myResultView.resetLayout();
  }

  @Override
  public void fireContentChanged(@Nullable GridRequestSource.RequestPlace place) {
    JViewport header = myMainResultViewComponent instanceof JScrollPane ? ((JScrollPane)myMainResultViewComponent).getRowHeader() : null;
    Component rowHeader = header == null ? null : header.getView();
    if (rowHeader instanceof TableResultRowHeader) {
      ((TableResultRowHeader)rowHeader).updatePreferredSize();
      rowHeader.revalidate();
      rowHeader.repaint();
    }
    myModificationTracker.incModificationCount();
    myEventDispatcher.getMulticaster().onContentChanged(this, place);
  }

  @Override
  public ModificationTracker getModificationTracker() {
    return myModificationTracker;
  }

  @Override
  public @NotNull ModificationTracker getColumnModelModificationTracker() {
    return myColumnModificationTracker;
  }

  @Override
  public @Nullable DatabaseDisplayObjectFormatterConfig getFormatterConfig(@NotNull ModelIndex<GridColumn> columnIdx) {
    return myFormatterConfigCached.getValue().get(columnIdx);
  }

  public static class ColumnAttributes {
    private String myAnonymousColumnName = "<anonymous>";

    static final class Attributes {
      Language myContentLanguage;
      final Comparator<GridRow> myComparator;
      Boolean myEnabled;
      Integer mySortOrder;
      DisplayType myDisplayType;
      @Nullable DisplayTypesInfo myDisplayTypesInfoCache;

      Boolean myHiddenDueToCollapse;

      Boolean myIsCollapsedColumnSubtree;

      Attributes(@Nullable GridRowComparator comparator) {
        myComparator = comparator;
      }
    }

    record DisplayTypesInfo(@NotNull Set<BinaryDisplayType> allowedDisplayTypes,
                            boolean uuidHasKnownVersion, boolean uuidHasSwapKnownVersion) {
    }

    private final Map<GridColumn, Attributes> myAttributesMap = new HashMap<>();
    private List<String> myUnambiguousColumnNames = ContainerUtil.emptyList();

    public @NlsSafe @NotNull String getName(GridColumn column) {
      String name = column.getColumnNumber() < myUnambiguousColumnNames.size() ? myUnambiguousColumnNames.get(column.getColumnNumber()) : column.getName();
      return StringUtil.isNotEmpty(name) ? name : myAnonymousColumnName;
    }

    public @Nullable DisplayType getDisplayType(GridColumn column) {
      Attributes attributes = myAttributesMap.get(column);
      return attributes == null ? null : attributes.myDisplayType;
    }

    public void setDisplayType(GridColumn column, @NotNull DisplayType displayType) {
      myAttributesMap.get(column).myDisplayType = displayType;
    }

    public boolean getIsDisplayTypeAllowed(GridColumn column, @NotNull BinaryDisplayType displayType) {
      Attributes attributes = myAttributesMap.get(column);
      return attributes != null && attributes.myDisplayTypesInfoCache != null &&
             attributes.myDisplayTypesInfoCache.allowedDisplayTypes.contains(displayType);
    }

    public void clearDisplayTypeAllowedCache(GridColumn column) {
      var attributes = myAttributesMap.get(column);
      if (attributes != null) {
        attributes.myDisplayTypesInfoCache = null;
      }
    }

    public @Nullable Language getContentLanguage(GridColumn column) {
      Attributes attributes = myAttributesMap.get(column);
      return attributes == null ? null : attributes.myContentLanguage;
    }

    public void setContentLanguage(GridColumn column, @NotNull Language language) {
      myAttributesMap.get(column).myContentLanguage = language;
    }

    public @Nullable Comparator<GridRow> getComparator(GridColumn column) {
      Attributes attributes = myAttributesMap.get(column);
      return attributes == null ? null : attributes.myComparator;
    }

    @Nullable Boolean isEnabled(GridColumn column) {
      Attributes attributes = myAttributesMap.get(column);
      return attributes == null ? null : attributes.myEnabled;
    }

    public void setEnabled(GridColumn column, boolean enabled) {
      myAttributesMap.get(column).myEnabled = enabled;
    }

    public void setIsCollapsedSubtree(GridColumn column, boolean collapsed) {
      myAttributesMap.get(column).myIsCollapsedColumnSubtree = collapsed;
    }

    public void setHiddenDueToCollapse(GridColumn column, boolean hidden) {
      myAttributesMap.get(column).myHiddenDueToCollapse = hidden;
    }

    public @Nullable Boolean isCollapsedSubtree(GridColumn column) {
      Attributes attributes = myAttributesMap.get(column);
      return attributes == null ? null : attributes.myIsCollapsedColumnSubtree;
    }

    public @Nullable Boolean isHiddenDueToCollapse(GridColumn column) {
      Attributes attributes = myAttributesMap.get(column);
      return attributes == null ? null : attributes.myHiddenDueToCollapse;
    }

    public int getSortOrder(GridColumn column) {
      Attributes attributes = myAttributesMap.get(column);
      return attributes == null || attributes.mySortOrder == null ? 0 : attributes.mySortOrder;
    }

    public void resetOrdering() {
      for (Attributes attr : myAttributesMap.values()) {
        attr.mySortOrder = 0;
      }
    }

    public void resetVisibility() {
      for (GridColumn column : myAttributesMap.keySet()) {
        setEnabled(column, !column.getAttributes().contains(Attribute.HIDDEN));
      }
    }

    public void changeSortOrder(GridColumn targetColumn, @NotNull RowSortOrder.Type targetSortOrder) {
      if (getComparator(targetColumn) == null) return;

      int prevOrder = getSortOrder(targetColumn);
      if (prevOrder != 0 && targetSortOrder != RowSortOrder.Type.UNSORTED) {
        int prevOrderAbs = Math.abs(prevOrder);
        setSortOrder(targetColumn, targetSortOrder == RowSortOrder.Type.ASC ? -prevOrderAbs : prevOrderAbs);
        return;
      }
      int maxOrder = 0;
      for (GridColumn column : myAttributesMap.keySet()) {
        if (Comparing.equal(column, targetColumn)) continue;
        int order = getSortOrder(column);
        if (prevOrder != 0 && Math.abs(order) > Math.abs(prevOrder)) {
          setSortOrder(column, order = order > 0 ? order - 1 : order + 1);
        }
        maxOrder = Math.max(maxOrder, Math.abs(order));
      }

      int asc = -maxOrder - 1;
      int desc = maxOrder + 1;

      int newOrder = targetSortOrder == RowSortOrder.Type.ASC ? asc :
                     targetSortOrder == RowSortOrder.Type.DESC ? desc : 0;
      setSortOrder(targetColumn, newOrder);
    }

    public int countSortedColumns() {
      int count = 0;
      for (var attributes : myAttributesMap.values()) {
        if ((attributes == null || attributes.mySortOrder == null ? 0 : attributes.mySortOrder) != 0) {
          count++;
        }
      }
      return count;
    }

    public TreeMap<Integer, GridColumn> getSortOrderMap() {
      TreeMap<Integer, GridColumn> sortOrderMap = new TreeMap<>();
      for (GridColumn column : myAttributesMap.keySet()) {
        int sortOrder = getSortOrder(column);
        if (sortOrder != 0) {
          sortOrderMap.put(Math.abs(sortOrder), column);
        }
      }
      return sortOrderMap;
    }

    private void updateColumnNames(TableResultPanel resultPanel) {
      myUnambiguousColumnNames = GridHelper.get(resultPanel).getUnambiguousColumnNames(resultPanel);
    }

    public void newColumns(TableResultPanel resultPanel, Collection<GridColumn> columnsToRetain) {
      updateColumnNames(resultPanel);
      myAttributesMap.keySet().retainAll(columnsToRetain);
      GridHelper helper = GridHelper.get(resultPanel);
      columnsToRetain.stream()
        .filter(column -> !myAttributesMap.containsKey(column))
        .forEach(column -> myAttributesMap.put(column, new Attributes(helper.createComparator(column))));
    }

    public void setSortOrder(GridColumn column, int sortOrder) {
      myAttributesMap.get(column).mySortOrder = sortOrder;
    }
  }

  protected boolean isInitiallyDisabled(@NotNull GridColumn column) {
    return column.getAttributes().contains(Attribute.HIDDEN);
  }

  protected Language getInitialContentLanguage(@NotNull GridColumn column) {
    return Language.ANY;
  }

  protected int getInitialPosition(@NotNull GridColumn column) {
    return DEFAULT_OR_HIDDEN_COLUMN_POSITION;
  }

  protected @Nullable DisplayType getInitialDisplayType(@NotNull GridColumn column) {
    return null;
  }

  @Override
  public void setValueAt(@NotNull ModelIndexSet<GridRow> viewRows,
                         @NotNull ModelIndexSet<GridColumn> viewColumns,
                         @Nullable Object value,
                         boolean allowImmediateUpdate,
                         @Nullable Runnable moveToNextCellRunnable,
                         @NotNull GridRequestSource source) {
    final GridMutator<GridRow, GridColumn> mutator = getDataHookup().getMutator();

    int[] validRows = valid(viewRows);
    int[] validColumns = valid(viewColumns);
    ModelIndexSet<GridRow> rows = validRows.length > 0 ? ModelIndexSet.forRows(this, validRows) : null;
    ModelIndexSet<GridColumn> columns = validRows.length > 0 ? ModelIndexSet.forColumns(this, validColumns) : null;

    if (mutator == null || rows == null || getDataModel(DATA_WITH_MUTATIONS).allValuesEqualTo(rows, columns, value)) {
      if (moveToNextCellRunnable != null) ApplicationManager.getApplication().invokeLater(moveToNextCellRunnable);
      return;
    }

    ApplicationManager.getApplication().invokeLater(() -> {
      if (moveToNextCellRunnable != null) source.getActionCallback().doWhenDone(moveToNextCellRunnable);
      mutator.mutate(source, rows, columns, value, allowImmediateUpdate);
    });
  }

  private <T> int[] valid(ModelIndexSet<T> set) {
    return set.asList().stream()
      .filter(idx -> idx.isValid(this))
      .mapToInt(ModelIndex::asInteger)
      .toArray();
  }

  @Override
  public boolean isHeaderSelecting() {
    return false;
  }

  private void setOrderingFromModel() {
    if (!isSortViaOrderBy()) return;

    myColumnAttributes.resetOrdering();
    for (RowSortOrder<ModelIndex<GridColumn>> order : getOrderingFromModel()) {
      changeSortOrder(order.getColumn(), order.getOrder());
    }
  }

  private @NotNull List<RowSortOrder<ModelIndex<GridColumn>>> getOrderingFromModel() {
    GridSortingModel<GridRow, GridColumn> sortingModel = myDataHookUp.getSortingModel();
    return sortingModel != null ? sortingModel.getAppliedOrdering() :
           ContainerUtil.emptyList();
  }

  @Override
  public void updateSortKeysFromColumnAttributes() {
    myResultView.updateSortKeysFromColumnAttributes();
    // update structure view & popup
    fireContentChanged(null);
  }

  public void changeSortOrder(@NotNull ModelIndex<GridColumn> columnIdx,
                              @NotNull RowSortOrder.Type targetSortOrder) {
    GridColumn column = getDataModel().getColumn(columnIdx);
    if (column != null) {
      myColumnAttributes.changeSortOrder(column, targetSortOrder);
    }
  }

  private void updateDataOrderingIfNeeded() {
    if (isSortViaOrderBy()) updateDataOrdering(true);
  }

  private boolean updateDataOrdering(boolean reloadIfUpdated) {
    GridSortingModel<GridRow, GridColumn> sortingModel = myDataHookUp.getSortingModel();
    if (sortingModel == null) return false;

    List<RowSortOrder<ModelIndex<GridColumn>>> oldOrdering = getOrderingFromModel();
    List<RowSortOrder<ModelIndex<GridColumn>>> newOrdering = createOrdering();
    if (isSameOrdering(oldOrdering, newOrdering)) return false;

    if (reloadIfUpdated && !isSafeToReload()) return false;

    sortingModel.setOrdering(newOrdering);
    if (reloadIfUpdated) {
      alarm.cancelAllRequests();
      alarm.addRequest(() -> {
        myDataHookUp.getLoader().loadFirstPage(new GridRequestSource(new DataGridRequestPlace(this)));
      }, 300); // wait for double click
    }
    return true;
  }

  private @NotNull List<RowSortOrder<ModelIndex<GridColumn>>> createOrdering() {
    TreeMap<Integer, GridColumn> sortOrderMap = getSortOrderMap();
    ArrayList<RowSortOrder<ModelIndex<GridColumn>>> ordering = new ArrayList<>(sortOrderMap.size());
    for (GridColumn column : sortOrderMap.values()) {
      int dataColumnIdx = column.getColumnNumber();
      ModelIndex<GridColumn> columnIdx = ModelIndex.forColumn(this, dataColumnIdx);
      ordering.add(getSortOrder(column) < 0 ? RowSortOrder.asc(columnIdx) : RowSortOrder.desc(columnIdx));
    }
    return ordering;
  }

  private static boolean isSameOrdering(@NotNull List<RowSortOrder<ModelIndex<GridColumn>>> ordering1,
                                        @NotNull List<RowSortOrder<ModelIndex<GridColumn>>> ordering2) {
    if (ordering1.size() != ordering2.size()) return false;

    for (int i = 0; i < ordering1.size(); i++) {
      RowSortOrder<ModelIndex<GridColumn>> o1 = ordering1.get(i);
      RowSortOrder<ModelIndex<GridColumn>> o2 = ordering2.get(i);
      if (!Comparing.equal(o1.getOrder(), o2.getOrder()) ||
          !Comparing.equal(o1.getColumn(), o2.getColumn())) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean isColumnEnabled(@NotNull ModelIndex<GridColumn> columnIndex) {
    if (!columnIndex.isValid(this)) return false;
    return isColumnEnabled(getDataModel(DATA_WITH_MUTATIONS).getColumn(columnIndex));
  }

  public boolean isColumnEnabled(@Nullable GridColumn column) {
    if (column == null) return false;
    Boolean enabled = myColumnAttributes.isEnabled(column);
    return enabled != null ? enabled : !isInitiallyDisabled(column); // possible that columnsAdded hasn't been called yet
  }

  @Override
  public void setColumnEnabled(@NotNull ModelIndex<GridColumn> columnIdx, boolean state) {
    GridColumn column = getDataModel(DATA_WITH_MUTATIONS).getColumn(columnIdx);
    if (column == null || isColumnEnabled(column) == state) return;

    myColumnAttributes.setEnabled(column, state);

    GridSelection<GridRow, GridColumn> selection = getSelectionModel().store();
    ModelIndex<GridColumn> colIdx = ModelIndex.forColumn(this, column.getColumnNumber());
    storeOrRestoreSelection(colIdx, state, selection);
    myResultView.setColumnEnabled(colIdx, state);
    fireContentChanged(null); // update structure view
    runWithIgnoreSelectionChanges(() -> {
      getSelectionModel().restore(selection);
    });
  }

  @Override
  public void setRowEnabled(@NotNull ModelIndex<GridRow> rowIdx, boolean state) {
    myResultView.setRowEnabled(rowIdx, state);
  }

  public void storeOrRestoreSelection(@NotNull ModelIndex<GridColumn> columnIdx, boolean state, @NotNull GridSelection<GridRow, GridColumn> selection) {
    int modelIndex = columnIdx.asInteger();
    if (state && myHiddenColumnSelectionHolder.contains(modelIndex)) {
      selection.addSelectedColumns(this, ModelIndexSet.forColumns(this, columnIdx.value));
      myHiddenColumnSelectionHolder.columnShown(modelIndex);
    }
    else if (!state) {
      boolean selected = getSelectionModel().isSelectedColumn(columnIdx);
      if (selected) myHiddenColumnSelectionHolder.columnHidden(modelIndex);
    }
  }

  @Override
  public void setPresentationMode(@NotNull GridPresentationMode presentationMode) {
    if (myPresentationMode == presentationMode) return;

    saveAndRestoreSelection(this, () -> {
      myPresentationMode = presentationMode;
      ResultViewFactory newFactory = ResultViewFactory.of(presentationMode);
      boolean requestFocusInSearchField = mySearchSession != null &&
                                          IdeFocusManager.getInstance(getProject()).getFocusOwner() ==
                                          mySearchSession.getComponent().getSearchTextComponent();
      if (myViewFactory != newFactory) {
        boolean wasTransposed = myResultView.isTransposed();
        myViewFactory = newFactory;
        myLayeredPane.removeAll();
        Disposer.dispose(myResultView);
        createResultView();
        myResultView.setTransposed(wasTransposed);
        columnsAdded(getDataModel(DATA_WITH_MUTATIONS).getColumnIndices());
        if (mySearchSession != null) {
          FindModel findModel = mySearchSession.getFindModel();
          mySearchSession.close();
          mySearchSession = myResultView.createSearchSession(findModel, myMainPanel.getSecondTopComponent());
        }
      }
      myMainPanel.revalidate();
      myMainPanel.repaint();

      if (!DataGridStartupActivity.DataEditorConfigurator.isLoadingDelayed(this)) {
        IdeFocusManager.getInstance(getProject())
          .doWhenFocusSettlesDown(() -> IdeFocusManager.getInstance(getProject())
            .requestFocus(requestFocusInSearchField ?
                          mySearchSession.getComponent().getSearchTextComponent() :
                          getPreferredFocusedComponent(),
                          true));
      }
    });
  }

  @Override
  public @NotNull GridPresentationMode getPresentationMode() {
    return myPresentationMode;
  }

  @Override
  public void runWithIgnoreSelectionChanges(Runnable runnable) {
    try {
      myHiddenColumnSelectionHolder.startAdjusting();
      runnable.run();
    }
    finally {
      myHiddenColumnSelectionHolder.endAdjusting();
    }
  }

  @Override
  public boolean isSafeToReload() {
    GridMutator<GridRow, GridColumn> mutator = myDataHookUp.getMutator();
    return mutator == null || !mutator.hasPendingChanges() || showIgnoreUnsubmittedChangesYesNoDialog(this);
  }

  @Override
  public boolean isSafeToUpdate(@NotNull ModelIndexSet<GridRow> rows, @NotNull ModelIndexSet<GridColumn> columns, @Nullable Object newValue) {
    GridMutator<GridRow, GridColumn> mutator = myDataHookUp.getMutator();
    return mutator == null || mutator.isUpdateSafe(rows, columns, newValue) || showIgnoreUnsubmittedChangesYesNoDialog(this);
  }

  @Override
  public @NotNull GridFilterAndSortingComponent getFilterComponent() {
    return myFilterComponent;
  }

  @Override
  public void resetFilters() {
    setFilterText("", -1);
    resetOrderingAndVisibility();
  }

  @Override
  public void resetView() {
    if (isSortViaOrderBy() && !isSafeToReload()) {
      return;
    }

    HiddenColumnsSelectionHolder copy = myHiddenColumnSelectionHolder.copy();
    myHiddenColumnSelectionHolder.reset();
    GridSelection<GridRow, GridColumn> selection = getSelectionModel().store();
    resetOrderingAndVisibility();
    if (myResultView instanceof ResultViewWithRows) ((ResultViewWithRows)myResultView).resetRowHeights();
    if (myResultView instanceof ResultViewWithColumns) ((ResultViewWithColumns)myResultView).createDefaultColumnsFromModel();
    myResultView.resetLayout();
    int[] modelIndices = copy.selectedModelIndices(this);
    selection.addSelectedColumns(this, ModelIndexSet.forColumns(this, modelIndices));
    getSelectionModel().restore(selection);

    if (isSortViaOrderBy()) {
      myDataHookUp.getLoader().reloadCurrentPage(new GridRequestSource(new DataGridRequestPlace(this)));
    }
  }

  @Override
  public @Nullable Comparator<?> getComparator(@NotNull ModelIndex<GridColumn> columnIdx) {
    GridColumn column = getDataModel(DATA_WITH_MUTATIONS).getColumn(columnIdx);
    return myColumnAttributes.getComparator(column);
  }

  @Override
  public @NotNull @NlsSafe String getName(@NotNull GridColumn column) {
    return myColumnAttributes.getName(column);
  }

  @Override
  public void trueLayout() {
    Container parent = myResultView != null ? myResultView.getComponent().getParent() : null;
    if (parent == null) return;

    final Dimension size = parent.getSize();
    int colNum = getDataModel(DATA_WITH_MUTATIONS).getColumnCount();
    myResultView.getComponent().setPreferredSize(colNum == 0 ? size : null);
    myMainResultViewComponent.revalidate();
    myMainResultViewComponent.repaint(50);
  }

  @Override
  public @NotNull LocalFilterState getLocalFilterState() {
    return myLocalFilterState;
  }

  @Override
  public @NotNull CoroutineScope getCoroutineScope() {
    return cs;
  }

  @Override
  public void adaptForNewQuery() {
    getLocalFilterState().reset();
  }

  private void resetOrderingAndVisibility() {
    myColumnAttributes.resetOrdering();
    myColumnAttributes.resetVisibility();
    updateSortKeysFromColumnAttributes();
    updateDataOrderingIfNeeded();
    myResultView.orderingAndVisibilityChanged();
  }

  private void updateFloatingPaging() {
    var shouldFloatingPagingBePresent = FloatingPagingManager.shouldBePresent(this);
    var isFloatingPagingPresent = FloatingPagingManager.isPresent(this);
    if (isFloatingPagingPresent) {
      FloatingPagingManager.uninstallFrom(this, myLayeredPane);
    }
    if (shouldFloatingPagingBePresent) {
      FloatingPagingManager.installOn(this, myLayeredPane);
    }

    //if (shouldFloatingPagingBePresent && !isFloatingPagingPresent) {
    //  FloatingPagingManager.installOn(this, myLayeredPane);
    //}
    //if (!shouldFloatingPagingBePresent && isFloatingPagingPresent) {
    //  FloatingPagingManager.uninstallFrom(this, myLayeredPane);
    //}

    ActivityTracker.getInstance().inc();
  }

  public static class LayeredPaneWithSizer extends JBLayeredPane {
    public static final Key<Function1<LayeredPaneWithSizer, Unit>> SIZER = Key.create("LayeredPaneWithSizer.SIZER");

    @Override
    public void doLayout() {
      super.doLayout();
      for (int i = getComponentCount() - 1; i >= 0; i--) {
        var component = getComponent(i);
        var sizer = ClientProperty.get(component, SIZER);
        if (sizer == null) {
          component.setBounds(0, 0, getWidth(), getHeight());
        }
        else {
          sizer.invoke(this);
        }
      }
    }
  }

  private static class MyLoadDataPanel extends JPanel {
    final UIUtil.TextPainter myPainter = EditorEmptyTextPainter.createTextPainter();
    final TableResultPanel myGrid;

    MyLoadDataPanel(@NotNull TableResultPanel grid) {
      myGrid = grid;
      setOpaque(true);
      myPainter.appendLine(DataGridBundle.message("DataView.load.data") + " <shortcut>" + KeymapUtil.getFirstKeyboardShortcutText("Refresh") + "</shortcut>");
      addMouseListener(new LoadingMouseListener(this, grid));
    }

    @Override
    public Color getBackground() {
      return myGrid == null || myGrid.myResultView == null ? super.getBackground() : myGrid.myColorsScheme.getDefaultBackground();
    }

    @Override
    public void paint(Graphics g) {
      super.paint(g);
      UISettings.setupAntialiasing(g);
      myPainter.draw(g, (width, height) -> Couple.of((getWidth() - width) / 2, (getHeight() - height) / 2));
    }
  }

  private static class LoadingMouseListener extends MouseAdapter {
    final Component myComponent;
    final TableResultPanel myGrid;

    LoadingMouseListener(@NotNull Component component, @NotNull TableResultPanel grid) {
      myComponent = component;
      myGrid = grid;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      if (DataGridStartupActivity.DataEditorConfigurator.isLoadingDelayed(myGrid)) {
        myGrid.getDataHookup().getLoader().reloadCurrentPage(new GridRequestSource(new DataGridRequestPlace(myGrid)));
      }
    }

    @Override
    public void mousePressed(MouseEvent e) {
      if (DataGridStartupActivity.DataEditorConfigurator.isLoadingDelayed(myGrid)) {
        ResultView view = myGrid.myResultView;
        if (view != null) {
          IdeFocusManager.getGlobalInstance()
            .doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(view.getComponent(), true));
        }
      }
      else {
        myComponent.removeMouseListener(this);
      }
    }
  }

  private static class ResultViewSettings {
    MouseListener myMouseListener;
    int myVisibleRowCount;
    boolean myShowRowNumbers;
    boolean myTransparentRowHeaderBg;
    boolean myTransparentColumnHeaderBg;
    int myAdditionalRowsCount;
    boolean myShowHorizontalLines;
    boolean myStriped;
    boolean myAllowMultilineColumnLabels;
    boolean myAddSpaceForHorizontalScrollbar;
    boolean myExpandMultilineRows;
    ResultView.HoveredRowBgHighlightMode myHoveredRowBgHighlightMode = ResultView.HoveredRowBgHighlightMode.AUTO;
  }
}
