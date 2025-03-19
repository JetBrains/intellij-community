package com.intellij.database.run.ui.treetable;

import com.intellij.database.datagrid.*;
import com.intellij.database.extractors.DisplayType;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.database.run.ui.ResultViewWithCells;
import com.intellij.database.run.ui.grid.DataGridSearchSession;
import com.intellij.database.run.ui.table.UnparsedValueHoverListener;
import com.intellij.find.FindModel;
import com.intellij.lang.Language;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseListener;
import java.util.Map;
import java.util.function.Consumer;

import static com.intellij.database.run.ui.grid.GridColorSchemeUtil.*;
import static com.intellij.database.run.ui.table.UnparsedValueHoverListener.Companion.Place.LEFT;

/**
 * @author Liudmila Kornilova
 **/
public class TreeTableResultView implements ResultView, ResultViewWithCells {
  private final DataGrid myResultPanel;
  private final GridTreeTable myTreeTable;
  private final TreeTableRawIndexConverter myRawIndexConverter;
  private boolean myTransposed;

  public TreeTableResultView(@NotNull DataGrid panel) {
    myResultPanel = panel;
    myTreeTable = new GridTreeTable(new GridTreeTableModel(panel), myResultPanel, this);
    myRawIndexConverter = new TreeTableRawIndexConverter(panel, myTreeTable);

    new TreeTableSelectionModel(myResultPanel, this);
    myResultPanel.addDataGridListener(new DataGridListener() {
      @Override
      public void onSelectionChanged(DataGrid dataGrid) {
        getComponent().repaint();
      }
    }, this);
    new UnparsedValueHoverListener(LEFT, this).addTo(myTreeTable.getTable());
  }

  @Override
  public void setValueAt(@Nullable Object v,
                         @NotNull ModelIndex<GridRow> row,
                         @NotNull ModelIndex<GridColumn> column,
                         boolean allowImmediateUpdate,
                         @NotNull GridRequestSource source) {
    ModelIndexSet<GridRow> rows = ModelIndexSet.forRows(myResultPanel, row.asInteger());
    ModelIndexSet<GridColumn> columns = ModelIndexSet.forColumns(myResultPanel, column.asInteger());
    myResultPanel.setValueAt(rows, columns, v, allowImmediateUpdate, null, source);
  }

  @Override
  public void setTransposed(boolean transposed) {
    myTransposed = transposed;
  }

  @Override
  public boolean isTransposed() {
    return myTransposed;
  }

  @Override
  public @NotNull GridTreeTable getComponent() {
    return myTreeTable;
  }

  @Override
  public @NotNull JComponent getPreferredFocusedComponent() {
    return myTreeTable.getTree();
  }

  @Override
  public void addMouseListenerToComponents(@NotNull MouseListener listener) {
    myTreeTable.getTable().addMouseListener(listener);
    myTreeTable.getTree().addMouseListener(listener);
  }

  @Override
  public void registerEscapeAction(@NotNull AbstractAction action) {
    String actionId = "grid.escape";
    KeyStroke keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
    myTreeTable.getTable().getInputMap(JComponent.WHEN_FOCUSED).put(keyStroke, actionId);
    myTreeTable.getTable().getActionMap().put(actionId, action);
    myTreeTable.getTree().getInputMap(JComponent.WHEN_FOCUSED).put(keyStroke, actionId);
    myTreeTable.getTree().getActionMap().put(actionId, action);
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
  public boolean isCellEditingAllowed() {
    return false;
  }

  @Override
  public void resetLayout() {
  }

  @Override
  public void setColumnEnabled(@NotNull ModelIndex<GridColumn> columnIdx, boolean state) {

  }

  @Override
  public void setRowEnabled(@NotNull ModelIndex<GridRow> rowIdx, boolean state) {

  }

  @Override
  public void showFirstCell(int rowNumOnCurrentPage) {

  }

  @Override
  public void growSelection() {

  }

  @Override
  public void shrinkSelection() {

  }

  @Override
  public void addSelectionChangedListener(@NotNull Consumer<Boolean> listener) {
    myTreeTable.getTree().getSelectionModel().addTreeSelectionListener(e -> listener.accept(false));  //TODO
  }

  @Override
  public void restoreColumnsOrder(Map<Integer, ModelIndex<GridColumn>> expectedToModel) {

  }

  @Override
  public boolean isEditing() {
    return false;
  }

  @Override
  public boolean isMultiEditingAllowed() {
    return false;
  }

  @Override
  public @NotNull ModelIndexSet<GridRow> getVisibleRows() {
    return myResultPanel.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getRowIndices();
  }

  @Override
  public @NotNull ModelIndexSet<GridColumn> getVisibleColumns() {
    return myResultPanel.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumnIndices();
  }

  @Override
  public int getViewColumnCount() {
    return myResultPanel.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumnCount();
  }

  @Override
  public int getViewRowCount() {
    return myResultPanel.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getRowCount();
  }

  @Override
  public boolean stopEditing() {
    return false;
  }

  @Override
  public void cancelEditing() {

  }

  @Override
  public void editSelectedCell() {

  }

  @Override
  public @Nullable TableCellEditor getCellEditor() {
    return null;
  }

  @Override
  public boolean isViewModified() {
    return false;
  }

  @Override
  public void contentLanguageUpdated(@NotNull ModelIndex<GridColumn> idx, @NotNull Language language) {
    myTreeTable.clearCache();
  }

  @Override
  public void displayTypeUpdated(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull DisplayType displayType) {
    myTreeTable.clearCache();
  }

  @Override
  public @NotNull ModelIndex<GridColumn> getContextColumn() {
    return ModelIndex.forColumn(myResultPanel, -1);
  }

  @Override
  public void updateSortKeysFromColumnAttributes() {

  }

  @Override
  public void orderingAndVisibilityChanged() {

  }

  @Override
  public @NotNull RawIndexConverter getRawIndexConverter() {
    return myRawIndexConverter;
  }

  @Override
  public void columnsAdded(ModelIndexSet<GridColumn> columns) {
    myTreeTable.getModel().columnsAdded(columns);
  }

  @Override
  public void columnAttributesUpdated() {

  }

  @Override
  public void columnsRemoved(ModelIndexSet<GridColumn> columns) {
    myTreeTable.getModel().columnsRemoved(columns);
  }

  @Override
  public void rowsAdded(ModelIndexSet<GridRow> rows) {
    myTreeTable.getModel().rowsAdded(rows);
  }

  @Override
  public void rowsRemoved(ModelIndexSet<GridRow> rows) {
    myTreeTable.getModel().rowsRemoved(rows);
  }

  @Override
  public void cellsUpdated(ModelIndexSet<GridRow> rows, ModelIndexSet<GridColumn> columns, @Nullable GridRequestSource.RequestPlace place) {
    myTreeTable.getModel().cellsUpdated(rows, columns, place);
  }

  @Override
  public void searchSessionUpdated() {
  }

  @Override
  public void dispose() {
    Disposer.dispose(myTreeTable);
  }

  @Override
  public @Nullable Color getCellBackground(@NotNull ViewIndex<GridRow> row, @NotNull ViewIndex<GridColumn> column, boolean selected) {
    if (selected) return doGetSelectionBackground(myResultPanel.getColorsScheme());
    Pair<Integer, Integer> rowAndColumn = getRawIndexConverter().rowAndColumn2Model().fun(row.asInteger(), column.asInteger());
    ModelIndex<GridRow> modelRow = ModelIndex.forRow(myResultPanel, rowAndColumn.first);
    ModelIndex<GridColumn> modelColumn = ModelIndex.forColumn(myResultPanel, rowAndColumn.second);
    return myResultPanel.getColorModel().getCellBackground(modelRow, modelColumn);
  }

  @Override
  public @NotNull Color getCellForeground(boolean selected) {
    return selected ? doGetSelectionForeground(myResultPanel.getColorsScheme()) : doGetForeground(myResultPanel.getColorsScheme());
  }

  @Override
  public void reinitSettings() {
    myTreeTable.reinitSettings();
  }

  @Override
  public void resetScroll() {
    JScrollBar horizontal = myTreeTable.getHorizontalScrollBar();
    if (horizontal != null) horizontal.setValue(0);
    JScrollBar vertical = myTreeTable.getVerticalScrollBar();
    if (vertical != null) vertical.setValue(0);
  }

  public void tryExpand(@NotNull ModelIndex<GridRow> rowIdx) {
    RawIndexConverter converter = myResultPanel.getRawIndexConverter();
    var rowAndColumn = converter.rowAndColumn2View().fun(rowIdx.asInteger(), -1);
    int viewRow = rowAndColumn.first;
    getComponent().getTree().expandRow(viewRow);
  }
}
