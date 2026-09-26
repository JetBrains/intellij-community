package com.intellij.database.run.ui.treetable;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.RawIndexConverter;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

public final class TreeTableRawIndexConverter implements RawIndexConverter {
  private final DataGrid myResultPanel;
  private final GridTreeTable myTreeTable;

  public TreeTableRawIndexConverter(@NotNull DataGrid panel, @NotNull GridTreeTable treeTable) {
    myResultPanel = panel;
    myTreeTable = treeTable;
  }

  @Override
  public boolean isValidViewRowIdx(int viewRowIdx) {
    return viewRowIdx >= 0 && viewRowIdx < (myTreeTable.getTree().getRowCount());
  }

  @Override
  public boolean isValidViewColumnIdx(int viewColumnIdx) {
    return viewColumnIdx >= 0 && viewColumnIdx < (myTreeTable.getTable().getColumnCount() + 1);
  }

  @Override
  public @NotNull IntUnaryOperator row2View() {
    return index -> {
      if (!isValidModelRowIdx(index)) return -1;
      GridTreeTableModel model = myTreeTable.getModel();
      TreePath rowPath = new TreePath(new Object[]{model.getRoot(), model.getChild(model.getRoot(), index)});
      return myTreeTable.getTree().getRowForPath(rowPath);
    };
  }

  @Override
  public @NotNull IntUnaryOperator column2View() {
    return index -> {
      if (!isValidModelColumnIdx(index)) return -1;
      return 0;
    };
  }

  @Override
  public @NotNull PairPairFunction<Integer> rowAndColumn2Model() {
    return (row, column) -> {
      if (!isValidViewRowIdx(row) || !isValidViewColumnIdx(column)) return new Pair<>(-1, -1);
      TreePath path = myTreeTable.getTree().getPathForRow(row);
      if (path == null) return new Pair<>(-1, -1);
      ColumnNode columnNode = (ColumnNode)ContainerUtil.find(path.getPath(), node -> node instanceof ColumnNode);
      return new Pair<>(row2Model().applyAsInt(row), columnNode == null ? -1 : columnNode.getColumnIdx().asInteger());
    };
  }

  @Override
  public @NotNull PairPairFunction<Integer> rowAndColumn2View() {
    return (row, column) -> {
      int viewColumn = 0;
      if (!isValidModelColumnIdx(column)) viewColumn = -1;
      if (!isValidModelRowIdx(row)) return new Pair<>(-1, viewColumn);

      GridTreeTableModel model = myTreeTable.getModel();
      RowNode rowNode = ObjectUtils.tryCast(model.getChild(model.getRoot(), row), RowNode.class);
      if (rowNode == null) return new Pair<>(-1, viewColumn);

      kotlin.Pair<List<Node>, Node> pathAndNode = NodeKt.dfs(rowNode, (path, node) -> node instanceof ColumnNode && ((ColumnNode)node).getColumnIdx().asInteger() == column);

      List<Object> l = new ArrayList<>();
      l.add(model.getRoot());
      if (pathAndNode == null) l.add(rowNode);
      else l.addAll(pathAndNode.getFirst());

      int viewRow = myTreeTable.getTree().getRowForPath(new TreePath(l.toArray()));
      return new Pair<>(viewRow, viewColumn);
    };
  }

  @Override
  public @NotNull IntUnaryOperator row2Model() {
    return index -> {
      if (!isValidViewRowIdx(index)) return -1;
      TreePath path = myTreeTable.getTree().getPathForRow(index);
      for (Object node : path.getPath()) {
        if (node instanceof RowNode) {
          return ((RowNode)node).getRowIdx().asInteger();
        }
      }
      return -1;
    };
  }

  @Override
  public @NotNull IntUnaryOperator column2Model() {
    return index -> {
      if (!isValidViewColumnIdx(index)) return -1;
      return index;
    };
  }

  private boolean isValidModelRowIdx(int modelRowIdx) {
    return modelRowIdx >= 0 && modelRowIdx < myResultPanel.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getRowCount();
  }

  private boolean isValidModelColumnIdx(int modelColumnIdx) {
    return modelColumnIdx >= 0 && modelColumnIdx < myResultPanel.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumnCount();
  }
}
