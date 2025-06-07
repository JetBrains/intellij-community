package com.intellij.database.datagrid;

import com.intellij.database.DataGridBundle;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.database.run.ui.grid.GridScrollPositionManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.pom.NavigatableWithText;
import com.intellij.pom.PomRenameableTarget;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author gregsh
 */
public abstract class DataGridPomTarget implements PomRenameableTarget<DataGridPomTarget>, NavigatableWithText {
  public final DataGrid dataGrid;

  public static @NotNull PsiElement wrapDataGrid(@NotNull Project project,
                                                 @NotNull DataGrid dataGrid) {
    return PomService.convertToPsi(project, new Grid(dataGrid));
  }

  public static @NotNull PsiElement wrapCell(@NotNull Project project,
                                             @NotNull DataGrid dataGrid,
                                             @NotNull ModelIndexSet<GridRow> rows,
                                             @NotNull ModelIndexSet<GridColumn> columns) {
    return PomService.convertToPsi(project, new Cell(dataGrid, rows, columns));
  }

  public static @NotNull PsiElement wrapColumn(@NotNull Project project,
                                               @NotNull DataGrid dataGrid,
                                               @NotNull ModelIndex<GridColumn> column) {
    return PomService.convertToPsi(project, new Column(dataGrid, column));
  }

  public static @Nullable DataGrid unwrapDataGrid(@Nullable PsiElement element) {
    DataGridPomTarget t = unwrap(element);
    return t != null ? t.dataGrid : null;
  }

  public static @Nullable Cell unwrapCell(@Nullable PsiElement element) {
    DataGridPomTarget t = unwrap(element);
    return t instanceof Cell ? (Cell)t : null;
  }

  public static @Nullable Column unwrapColumn(@Nullable PsiElement element) {
    DataGridPomTarget t = unwrap(element);
    return t instanceof Column ? (Column)t : null;
  }

  private static @Nullable DataGridPomTarget unwrap(@Nullable PsiElement element) {
    if (!(element instanceof PomTargetPsiElement)) return null;
    PomTarget target = ((PomTargetPsiElement)element).getTarget();
    return target instanceof DataGridPomTarget ? (DataGridPomTarget)target : null;
  }


  DataGridPomTarget(@NotNull DataGrid dataGrid) {
    this.dataGrid = dataGrid;
  }

  @Override
  public abstract @Nls String getName();

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void navigate(boolean requestFocus) {
    GridScrollPositionManager.get(dataGrid.getResultView(), dataGrid).scrollSelectionToVisible();
    if (requestFocus) {
      VirtualFile file = GridHelper.get(dataGrid).getVirtualFile(dataGrid);
      if (file != null) {
        FileEditorManager.getInstance(dataGrid.getProject()).openFile(file, true);
      }
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(dataGrid.getPreferredFocusedComponent(), true));
    }
  }

  @Override
  public boolean canNavigate() {
    return true;
  }

  @Override
  public boolean canNavigateToSource() {
    return true;
  }

  @Override
  public int hashCode() {
    return dataGrid.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    return dataGrid.equals(((DataGridPomTarget)o).dataGrid);
  }

  @Override
  public @Nullable String getNavigateActionText(boolean focusEditor) {
    return DataGridBundle.message("action.jump.to.data.grid.text");
  }

  @Override
  public String toString() {
    return getName();
  }

  @Override
  public boolean isWritable() {
    return true;
  }

  @Override
  public DataGridPomTarget setName(@NotNull String newName) {
    return null;
  }

  public static class Grid extends DataGridPomTarget {

    public final @Nls String title;

    Grid(@NotNull DataGrid dataGrid) {
      super(dataGrid);
      title = dataGrid.getDisplayName();
    }

    @Override
    public String getName() {
      return StringUtil.isNotEmpty(title) ? title : DataGridBundle.message("table.data");
    }
  }

  public static class Column extends DataGridPomTarget {
    public final ModelIndex<GridColumn> column;

    Column(@NotNull DataGrid dataGrid, @NotNull ModelIndex<GridColumn> column) {
      super(dataGrid);
      this.column = column;
    }

    public @Nullable GridColumn getColumn() {
      return dataGrid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumn(column);
    }

    @Override
    public void navigate(boolean requestFocus) {
      dataGrid.getSelectionModel().setColumnSelection(column, true);
      super.navigate(requestFocus);
    }

    @Override
    public String getName() {
      GridColumn c = getColumn();
      return c != null ? c.getName() : DataGridBundle.message("column", column.asInteger());
    }

    @Override
    public int hashCode() {
      return super.hashCode() + 31 * column.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      return super.equals(o) && column.equals(((Column)o).column);
    }
  }

  public static class Cell extends DataGridPomTarget {
    public final ModelIndexSet<GridRow> rows;
    public final ModelIndexSet<GridColumn> columns;

    Cell(DataGrid dataGrid, ModelIndexSet<GridRow> rows, ModelIndexSet<GridColumn> columns) {
      super(dataGrid);
      this.rows = rows;
      this.columns = columns;
    }

    @Override
    public void navigate(boolean requestFocus) {
      dataGrid.getSelectionModel().setSelection(rows, columns);
      super.navigate(requestFocus);
    }

    @Override
    public @NlsSafe String getName() {
      ModelIndex<?> r0 = rows.first();
      ModelIndex<?> c0 = columns.last();
      ModelIndex<?> r1 = rows.last();
      ModelIndex<?> c1 = columns.last();
      if (!r0.isValid(dataGrid) || !c0.isValid(dataGrid) || !r1.isValid(dataGrid) || !c1.isValid(dataGrid)) return "";

      String s = (r0.toView(dataGrid).asInteger() + 1) + "x" + (c0.toView(dataGrid).asInteger() + 1);
      return "[" + (r0.equals(r1) && c0.equals(c1) ? s :
                    s + ".." + (r1.toView(dataGrid).asInteger() + 1) + "x" + (c1.toView(dataGrid).asInteger() + 1)) + "]";
    }

    @Override
    public int hashCode() {
      return super.hashCode() + 31 * rows.hashCode() + 31 * 31 * columns.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      return super.equals(o) &&
             rows.equals(((Cell)o).rows) &&
             columns.equals(((Cell)o).columns);
    }
  }
}
