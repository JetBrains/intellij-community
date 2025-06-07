package com.intellij.database.run.ui.treetable;

import com.intellij.database.datagrid.*;
import com.intellij.database.run.ui.ResultViewWithCells;
import com.intellij.database.run.ui.ResultViewWithRows;
import com.intellij.database.run.ui.grid.GridColorsScheme;
import com.intellij.database.run.ui.grid.TableCellImageCache;
import com.intellij.database.run.ui.grid.renderers.DefaultTextRendererFactory.TextRenderer;
import com.intellij.database.run.ui.grid.renderers.GridCellRenderer;
import com.intellij.database.run.ui.grid.renderers.GridCellRendererFactories;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.EditorTextFieldCellRenderer.AbbreviatingRendererComponent;
import com.intellij.ui.components.JBTreeTable;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.treeStructure.treetable.TreeTableModel;
import com.intellij.util.ObjectUtils;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Map;

import static com.intellij.database.run.ui.grid.GridColorSchemeUtil.*;
import static com.intellij.database.run.ui.grid.TableCellImageCache.MAX_ROWS;
import static com.intellij.database.run.ui.grid.renderers.DefaultTextRendererFactory.TextRenderer.*;
import static com.intellij.ui.render.RenderingUtil.CUSTOM_SELECTION_BACKGROUND;

/**
 * @author Liudmila Kornilova
 **/
public final class GridTreeTable extends JBTreeTable implements Disposable, EditorColorsListener, ResultViewWithRows {
  private final DataGrid myResultPanel;
  private final TreeTableResultView myView;
  private final TableCellImageCache myCellImageCache;
  private final FieldNameColumnRenderer myFieldNameRenderer;
  private final ValueColumnRenderer myValueColumnRenderer;

  public GridTreeTable(@NotNull TreeTableModel model, @NotNull DataGrid resultPanel, @NotNull TreeTableResultView view) {
    super(model);

    myResultPanel = resultPanel;
    myView = view;
    myCellImageCache = new TableCellImageCache(this, view);
    setColumnProportion(0.7f);
    myFieldNameRenderer = new FieldNameColumnRenderer();
    Disposer.register(this, myFieldNameRenderer);
    UIUtil.putClientProperty(getTable(), CUSTOM_SELECTION_BACKGROUND, () -> getTable().getSelectionBackground());
    setDefaultRenderer(TreeTableModel.class, myFieldNameRenderer);
    myValueColumnRenderer = new ValueColumnRenderer(resultPanel, this, view, getDefaultRenderer(Object.class));
    setDefaultRenderer(Object.class, myValueColumnRenderer);
    getTree().setLargeModel(true);
    getTable().getEmptyText().setText("");
    getTree().getEmptyText().setText("");
    getTable().setBorder(JBUI.Borders.emptyBottom(150));
    getTree().setBorder(JBUI.Borders.emptyBottom(150));
    hideColumnHeader();
    getTable().setShowVerticalLines(true);

    colorSchemeUpdated();
    MessageBusConnection connection = resultPanel.getProject().getMessageBus().connect(this);
  }

  private int getTextLineHeight() {
    return (int)Math.ceil(getFontMetrics(getFont()).getHeight() * getColorsScheme().getLineSpacing());
  }

  @Override
  public Font getFont() {
    return getColorsScheme().getFont(EditorFontType.PLAIN);
  }

  private void hideColumnHeader() {
    getTable().setTableHeader(null);
    JScrollPane scrollPane = getScrollPane();
    if (scrollPane != null) scrollPane.setColumnHeaderView(null);
  }

  @Override
  protected void paintComponent(Graphics g) {
    adjustCacheSize();
    super.paintComponent(g);
  }

  private void adjustCacheSize() {
    if (!myCellImageCache.isCacheEnabled()) return;
    int rowCount = getTable().getRowCount();
    Rectangle visibleRect = getVisibleRect();
    if (rowCount == 0 || visibleRect.isEmpty()) return;

    int rowHeight = getRowHeight();
    int rowsMax = rowHeight == 0 ? rowCount : Math.min(rowCount, (int)Math.ceil(visibleRect.height / (float)rowHeight));
    rowsMax = Math.min(MAX_ROWS, rowsMax);
    int columnsMax = 2;
    int factor = Math.max(1, Registry.intValue("database.grid.cache.factor"));
    int treeTableFactor = 3;
    myCellImageCache.adjustCacheSize(rowsMax * columnsMax * factor * treeTableFactor);
  }

  private void colorSchemeUpdated() {
    getTable().setGridColor(doGetGridColor(getColorsScheme()));

    updateInnerComponentsBackground(doGetBackground(getColorsScheme()));
    getTable().setSelectionBackground(doGetSelectionBackground(getColorsScheme()));
    getTable().setSelectionForeground(doGetSelectionForeground(getColorsScheme()));
    getTree().setRowHeight(getTextLineHeight() + getTable().getRowMargin());
  }

  private void updateInnerComponentsBackground(Color bg) {
    getTree().setBackground(bg);
    getTable().setBackground(bg);
  }

  private @NotNull GridColorsScheme getColorsScheme() {
    return myResultPanel.getColorsScheme();
  }

  @Override
  public Color getBackground() {
    return doGetBackground(getColorsScheme());
  }

  @Override
  public void setBackground(@NotNull Color bg) {
    // prevent background change events from being fired
  }

  @Override
  public void resetRowHeights() {
    int defaultRowHeight = getRowHeight();
    JBTable table = getTable();
    for (int i = 0; i < table.getRowCount(); i++) {
      if (table.getRowHeight(i) != defaultRowHeight) {
        table.setRowHeight(i, defaultRowHeight);
      }
    }
  }

  @Override
  public GridTreeTableModel getModel() {
    return (GridTreeTableModel)super.getModel();
  }

  @Override
  public @Nullable Color getPathBackground(@NotNull TreePath path, int row) {
    ViewIndex<GridRow> rowIdx = ViewIndex.forRow(myResultPanel, row);
    ViewIndex<GridColumn> colIdx = ViewIndex.forColumn(myResultPanel, 0);
    SelectionModel<GridRow, GridColumn> selectionModel = SelectionModelUtil.get(myResultPanel, myView);

    boolean selected = selectionModel.isSelected(rowIdx, colIdx);
    return myView.getCellBackground(rowIdx, colIdx, selected);
  }

  @Override
  public void dispose() {
    getTable().removeEditor();
  }

  @Override
  public void globalSchemeChange(@Nullable EditorColorsScheme scheme) {
    myFieldNameRenderer.myComponent.getEditor().reinitSettings();
    colorSchemeUpdated();
  }

  @Override
  public @NotNull JComponent getComponent() {
    return this;
  }

  private @Nullable JScrollPane getScrollPane() {
    if (getComponentCount() < 1) return null;
    Splitter split = ObjectUtils.tryCast(getComponent(0), Splitter.class);
    return split == null ? null : ObjectUtils.tryCast(split.getFirstComponent(), JScrollPane.class);
  }

  @Override
  public @Nullable JScrollBar getVerticalScrollBar() {
    JScrollPane scrollPane = getScrollPane();
    return scrollPane == null ? null : scrollPane.getVerticalScrollBar();
  }

  @Override
  public @Nullable JScrollBar getHorizontalScrollBar() {
    JScrollPane scrollPane = getScrollPane();
    return scrollPane == null ? null : scrollPane.getHorizontalScrollBar();
  }

  @Override
  public int getRowHeight() {
    return getTable().getRowHeight();
  }

  public void clearCache() {
    myCellImageCache.reset();
    revalidate();
    repaint();
  }

  public void reinitSettings() {
    myCellImageCache.reset();
    GridCellRendererFactories.get(myResultPanel).reinitSettings();
    myValueColumnRenderer.reinitSettings();
    myFieldNameRenderer.reinitSettings();
  }

  private class FieldNameColumnRenderer implements TableCellRenderer, Disposable {
    final AbbreviatingRendererComponent myComponent = createComponent(myResultPanel.getProject(), null);

    @Override
    public Component getTableCellRendererComponent(JTable table,
                                                   Object value,
                                                   boolean isSelected,
                                                   boolean hasFocus,
                                                   int row,
                                                   int column) {
      EditorEx editor = myComponent.getEditor();
      if (editor.isDisposed()) return myComponent;
      configureEditor(editor, myResultPanel);
      TextAttributes attributes = getAttributes(value, editor.getColorsScheme(), isSelected, false);
      myComponent.setText(((Node)value).getName(), attributes, isSelected);
      return ResultViewWithCells.prepareComponent(myComponent, myResultPanel, myView, ViewIndex.forRow(myResultPanel, row),
                                                  ViewIndex.forColumn(myResultPanel, column), true);
    }

    @Override
    public void dispose() {
      Disposer.dispose(myComponent);
    }

    public void reinitSettings() {
      myComponent.getEditor().reinitSettings();
    }
  }

  private static final class ValueColumnRenderer implements TableCellRenderer {
    final Renderers myRenderers;
    final DataGrid myDataGrid;
    final GridTreeTable myTreeTable;
    final TreeTableResultView myView;
    final TableCellRenderer myDefaultRenderer;

    private ValueColumnRenderer(DataGrid dataGrid, GridTreeTable treeTable, TreeTableResultView view, TableCellRenderer defaultRenderer) {
      myDataGrid = dataGrid;
      myTreeTable = treeTable;
      myView = view;
      myDefaultRenderer = defaultRenderer;
      myRenderers = new Renderers(dataGrid, treeTable);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table,
                                                   @Nullable Object value,
                                                   boolean isSelected,
                                                   boolean hasFocus,
                                                   int row,
                                                   int column) {
      TableCellRenderer renderer = myRenderers.getRenderer(row, 1);
      renderer = renderer != null ? renderer : myDefaultRenderer;
      renderer = myTreeTable.myCellImageCache.wrapCellRenderer(renderer);
      Component component = renderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      ViewIndex<GridRow> rowIdx = ViewIndex.forRow(myDataGrid, row);
      ViewIndex<GridColumn> columnIdx = ViewIndex.forColumn(myDataGrid, column);
      return ResultViewWithCells.prepareComponent(component, myDataGrid, myView, rowIdx, columnIdx, true);
    }

    public void reinitSettings() {
      myRenderers.reinitSettings();
    }
  }

  private static class Renderers {
    final GridCellRenderer myJsonRenderer;
    final GridCellRenderer myEmptyRenderer;
    private final DataGrid myGrid;
    final GridTreeTable myTreeTable;
    final Map<GridCellRenderer, TableCellRenderer> myTableCellRenderers = new Reference2ObjectOpenHashMap<>();

    Renderers(DataGrid grid, GridTreeTable treeTable) {
      myGrid = grid;
      myTreeTable = treeTable;
      myEmptyRenderer = new TextRenderer(grid) {
        @Override
        protected @NotNull String getValueText(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull Object value) {
          return "";
        }
      };
      myJsonRenderer = new TextRenderer(grid, "JSON5", false);
      Disposer.register(grid, myEmptyRenderer);
      Disposer.register(grid, myJsonRenderer);
    }

    public @Nullable TableCellRenderer getRenderer(int viewRow, int viewColumn) {
      Pair<Integer, Integer> rowAndColumn = myGrid.getRawIndexConverter().rowAndColumn2Model().fun(viewRow, viewColumn);
      int modelColumnIdx = rowAndColumn.second;
      GridCellRenderer gridCellRenderer = myTreeTable.getTree().isExpanded(viewRow) ? myEmptyRenderer :
                                          modelColumnIdx == -1 ? myJsonRenderer :
                                          GridCellRenderer.getRenderer(myGrid, ModelIndex.forRow(myGrid, rowAndColumn.first), ModelIndex.forColumn(myGrid, modelColumnIdx));

      TableCellRenderer renderer = myTableCellRenderers.get(gridCellRenderer);
      if (renderer == null) {
        renderer = new GridCellRendererWrapper(gridCellRenderer);
        myTableCellRenderers.put(gridCellRenderer, renderer);
      }
      return renderer;
    }

    public void reinitSettings() {
      myJsonRenderer.reinitSettings();
      myEmptyRenderer.reinitSettings();
    }
  }

  private static class GridCellRendererWrapper implements TableCellRenderer {
    final GridCellRenderer delegate;

    GridCellRendererWrapper(GridCellRenderer renderer) {
      delegate = renderer;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      DataGrid grid = delegate.myGrid;
      ViewIndex<GridRow> rowIdx = ViewIndex.forRow(grid, row);
      ViewIndex<GridColumn> columnIdx = ViewIndex.forColumn(grid, column);
      return delegate.getComponent(rowIdx, columnIdx, value);
    }
  }
}
