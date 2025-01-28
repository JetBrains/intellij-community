package com.intellij.database.run.ui.grid;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.*;
import com.intellij.database.extractors.DisplayType;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.find.*;
import com.intellij.find.editorHeaderActions.*;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.platform.util.coroutines.CoroutineScopeKt;
import com.intellij.ui.ClientProperty;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.UIUtil;
import kotlin.Unit;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Dispatchers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.Iterator;

import static com.intellij.database.datagrid.GridUtil.getConfigProvider;

public class DataGridSearchSession implements GridSearchSession<GridRow, GridColumn>,
                                              SearchReplaceComponent.Listener,
                                              FindModel.FindModelObserver,
                                              Disposable {
  private static final String FILTERING_ENABLED_PROPERTY = "grid.search.filter.rows";

  private final Project myProject;
  private final DataGrid myGrid;
  private final FindModel myFindModel;
  private final SearchReplaceComponent mySearchComponent;
  private final Component myPreviousFilterComponent;
  private final EventDispatcher<Listener> myDispatcher = EventDispatcher.create(Listener.class);
  private boolean myFilteringEnabled;
  private final DataGridSearchSessionWorker worker;
  private final CoroutineScope myCoroutineScope;

  public DataGridSearchSession(@NotNull Project project,
                               @NotNull DataGrid grid,
                               @NotNull FindModel findModel,
                               @Nullable Component previousFilterComponent) {
    myProject = project;
    myGrid = grid;
    myFindModel = findModel;
    myPreviousFilterComponent = previousFilterComponent;
    myFindModel.addObserver(this);
    mySearchComponent = createSearchComponent();
    applyFindModel(mySearchComponent, myFindModel);
    myCoroutineScope = CoroutineScopeKt.childScope(grid.getCoroutineScope(), getClass().getName(), Dispatchers.getDefault(), true);
    Disposer.register(grid, this);

    worker = new DataGridSearchSessionWorker(myCoroutineScope, grid, FindManager.getInstance(myProject), myFindModel);
    worker.submitStartSearch();
    worker.subscribeOnUpdateEDT((selectOccurence) -> {
      FindUtil.updateFindInFileModel(myProject, myFindModel, true);
      fireSessionUpdated();

      String stringToFind = myFindModel.getStringToFind();
      boolean incorrectRegex = myFindModel.isRegularExpressions() && myFindModel.compileRegExp() == null;
      boolean hasMatches = hasMatches();
      if (incorrectRegex || StringUtil.isNotEmpty(stringToFind) && !hasMatches) {
        mySearchComponent.setNotFoundBackground();
      }
      else {
        mySearchComponent.setRegularBackground();
      }
      mySearchComponent.setStatusText(incorrectRegex ? FindBundle.message(INCORRECT_REGEXP_MESSAGE_KEY) : "");
      mySearchComponent.update(stringToFind, "", false, myFindModel.isMultiline());
      if (hasMatches && selectOccurence) {
          selectOccurrence(true, DataGridSearchSessionWorker.SearchDirection.FORWARD);
      }
      return Unit.INSTANCE;
    }, myCoroutineScope);

    grid.addDataGridListener(new DataGridListener() {
      @Override
      public void onContentChanged(DataGrid dataGrid, GridRequestSource.@Nullable RequestPlace place) {
        worker.submitStartSearchWithoutSelection();
      }

      @Override
      public void onCellDisplayTypeChanged(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull DisplayType type) {
        worker.submitStartSearchWithoutSelection();
      }

      @Override
      public void onCellLanguageChanged(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull Language language) {
        worker.submitStartSearchWithoutSelection();
      }
    }, this);
    myFilteringEnabled = PropertiesComponent.getInstance(myProject).getBoolean(FILTERING_ENABLED_PROPERTY);

    myGrid.getPanel().setSecondTopComponent(mySearchComponent);
    myGrid.searchSessionStarted(this);
    ClientProperty.put(mySearchComponent, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, new Iterable<>() {
      @Override
      public @NotNull Iterator<Component> iterator() {
        Component component = myPreviousFilterComponent;
        if (component != null && component.getParent() == null) {
          return Collections.singleton(component).iterator();
        }
        return Collections.emptyIterator();
      }
    });
  }

  @Override
  public @Nullable Component getPreviousFilterComponent() {
    return myPreviousFilterComponent;
  }

  private static void applyFindModel(@NotNull SearchReplaceComponent component, @NotNull FindModel findModel) {
    component.getSearchTextComponent().setText(findModel.getStringToFind());
  }

  private @NotNull SearchReplaceComponent createSearchComponent() {
    SearchReplaceComponent searchReplaceComponent = SearchReplaceComponent
      .buildFor(myProject, myGrid.getPanel().getComponent(), this)
      .addPrimarySearchActions(new PrevOccurrenceAction(),
                               new NextOccurrenceAction())
      .addExtraSearchActions(new ToggleMatchCase(),
                             new ToggleRegex(),
                             new ToggleWholeWordsOnlyAction(),
                             new ToggleFilteringAction(),
                             new StatusTextAction())
      .withCloseAction(this::close)
      .build();
    searchReplaceComponent.addListener(this);
    return searchReplaceComponent;
  }

  @Override
  public @NotNull SearchReplaceComponent getComponent() {
    return mySearchComponent;
  }

  @Override
  public boolean isMatchedCell(@NotNull ModelIndex<GridRow> rowIdx, @NotNull ModelIndex<GridColumn> columnIdx) {
    return worker.isMatchedCell(rowIdx, columnIdx);
  }

  @Override
  public boolean isFilteringEnabled() {
    return myFilteringEnabled && isFilteringAvailable();
  }

  @Override
  public void addListener(@NotNull Listener listener, @NotNull Disposable parent) {
    myDispatcher.addListener(listener, parent);
  }

  @Override
  public void searchFieldDocumentChanged() {
    String textToFind = mySearchComponent.getSearchTextComponent().getText();
    myFindModel.setStringToFind(textToFind);
    myFindModel.setMultiline(textToFind.contains("\n"));
  }

  @Override
  public void multilineStateChanged() {
    myFindModel.setMultiline(mySearchComponent.isMultiline());
  }

  @Override
  public @NotNull FindModel getFindModel() {
    return myFindModel;
  }

  @Override
  public boolean hasMatches() {
    return worker.getOccurence(true, DataGridSearchSessionWorker.SearchDirection.FORWARD) != null;
  }

  @Override
  public void searchForward() {
    selectOccurrence(false, DataGridSearchSessionWorker.SearchDirection.FORWARD);
  }

  @Override
  public void searchBackward() {
    selectOccurrence(false, DataGridSearchSessionWorker.SearchDirection.BACKWARD);
  }

  @Override
  public boolean isSearchInProgress() {
    return !worker.hasInfo();
  }

  @Override
  public void findModelChanged(FindModel findModel) {
    worker.submitStartSearch();
  }

  @Override
  public void close() {
    IdeFocusManager.getInstance(myProject).requestFocus(myGrid.getPreferredFocusedComponent(), false);
    myGrid.getPanel().setSecondTopComponent(myPreviousFilterComponent);
    myGrid.searchSessionStopped(this);
    Disposer.dispose(this);
  }

  @Override
  public void dispose() {
    kotlinx.coroutines.CoroutineScopeKt.cancel(myCoroutineScope, null);
  }

  private void fireSessionUpdated() {
    myDispatcher.getMulticaster().searchSessionUpdated();
  }

  private boolean isFilteringAvailable() {
    return myGrid.getPresentationMode() == GridPresentationMode.TABLE && !myGrid.getResultView().isTransposed();
  }

  private void selectOccurrence(boolean selectCurrent, DataGridSearchSessionWorker.SearchDirection direction) {
    var cell = worker.getOccurence(selectCurrent, direction);
    if (cell != null) {
      var selectionModel = myGrid.getSelectionModel();
      selectionModel.setSelection(cell.getFirst(), cell.getSecond());
    }
  }

  public static void configureFindModel(@NotNull DataGrid grid, @NotNull FindModel model) {
    FindUtil.configureFindModel(false, model, false, getSelectedText(grid));
  }

  private static String getSelectedText(@NotNull DataGrid grid) {
    ModelIndex<GridRow> row = grid.getSelectionModel().getLeadSelectionRow();
    ModelIndex<GridColumn> column = grid.getSelectionModel().getLeadSelectionColumn();
    if (!row.isValid(grid) || !column.isValid(grid)) return null;
    GridModel<GridRow, GridColumn> model = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS);
    GridColumn c = model.getColumn(column);
    if (c == null) return null;
    return grid.getObjectFormatter().objectToString(model.getValueAt(row, column), c, getConfigProvider(grid).apply(c.getColumnNumber()));
  }


  private class ToggleFilteringAction extends EditorHeaderToggleAction {
    protected ToggleFilteringAction() {
      super(DataGridBundle.message("checkbox.filter.rows"));
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(isFilteringAvailable());
      super.update(e);
    }

    @Override
    protected boolean isSelected(@NotNull SearchSession session) {
      return myFilteringEnabled && isFilteringAvailable();
    }

    @Override
    protected void setSelected(@NotNull SearchSession session, boolean selected) {
      boolean wasEnabled = myFilteringEnabled;
      myFilteringEnabled = selected;
      if (wasEnabled != selected) {
        fireSessionUpdated();
      }
      if (myProject != null) PropertiesComponent.getInstance(myProject).setValue(FILTERING_ENABLED_PROPERTY, myFilteringEnabled);
    }
  }
}
