package com.intellij.database.datagrid;

import com.intellij.database.datagrid.mutating.CellMutation;
import com.intellij.database.datagrid.mutating.ColumnDescriptor;
import com.intellij.database.datagrid.mutating.RowMutation;
import com.intellij.database.settings.DataGridSettings;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.testFramework.LightVirtualFileBase;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.sql.SQLWarning;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.intellij.database.datagrid.GridPagingModel.UNLIMITED_PAGE_SIZE;
import static com.intellij.database.settings.DataGridSettings.DEFAULT_PAGE_SIZE;


public class GridUtilCore {
  public static final String FAILED_TO_LOAD_PREFIX = "<failed to load>";
  public static final String COLUMN_NAME_PREFIX = "column ";
  public static final String OPEN_FILE_DESC = "Open file";

  protected GridUtilCore() {
  }

  @Contract("null,_->null;!null,_->_")
  public static <R, C, T extends GridDataHookUp<R, C>> T getHookUp(@Nullable CoreGrid<R, C> grid, Class<T> clazz) {
    GridDataHookUp<R, C> hookup = grid != null ? grid.getDataHookup() : null;
    return ObjectUtils.tryCast(hookup, clazz);
  }

  public static @NotNull List<RowMutation> mergeAll(@NotNull List<? extends CellMutation> mutations,
                                                    @NotNull GridModel<GridRow, GridColumn> model) {
    List<CellMutation> copy = new ArrayList<>(mutations);
    List<RowMutation> rowMutations = new ArrayList<>();
    while (!copy.isEmpty()) {
      rowMutations.add(merge(copy, model));
    }
    return rowMutations.stream()
      .filter(Objects::nonNull)
      .sorted()
      .collect(Collectors.toList());
  }

  private static @Nullable RowMutation merge(@NotNull List<? extends CellMutation> mutations,
                                             @NotNull GridModel<GridRow, GridColumn> model) {
    CellMutation item = ContainerUtil.getFirstItem(mutations);
    if (item == null) throw new IllegalStateException("Shouldn't call merge() when there is no pending changes");
    mutations.remove(item);
    List<CellMutation> toMerge = ContainerUtil.filter(mutations, item::canMergeByRowWith);
    mutations.removeAll(toMerge);
    RowMutation merged = item.createRowMutation(model);
    for (CellMutation mutation : toMerge) {
      if (merged == null) {
        merged = mutation.createRowMutation(model);
        continue;
      }
      merged = merged.merge(mutation.createRowMutation(model));
    }
    return merged;
  }

  public static @NotNull List<CellMutation> createMutations(@NotNull ModelIndexSet<GridRow> rows,
                                                            @NotNull ModelIndexSet<GridColumn> columns,
                                                            @Nullable Object value) {
    List<CellMutation> mutations = new ArrayList<>();
    for (ModelIndex<GridRow> rowIdx : rows.asIterable()) {
      for (ModelIndex<GridColumn> columnIdx : columns.asIterable()) {
        mutations.add(new CellMutation(rowIdx, columnIdx, value));
      }
    }
    return mutations;
  }

  public static @NotNull <R extends GridRow, C extends GridColumn> ModelIndex<C> findColumn(@NotNull GridModel<R, C> gridModel, @Nullable String name, boolean caseSensitive) {
    for (ModelIndex<C> c : gridModel.getColumnIndices().asIterable()) {
      C column = gridModel.getColumn(c);
      if (column != null && Comparing.strEqual(name, column.getName(), caseSensitive)) {
        return c;
      }
    }
    return ModelIndex.forColumn(gridModel, -1);
  }

  public static String generateColumnName(@NotNull GridModel<? extends GridRow, ? extends GridColumn> model) {
    int idx = model.getColumnCount() + 1;
    String name = COLUMN_NAME_PREFIX + idx;
    while (findColumn(model, name, false).asInteger() != -1) {
      idx++;
      name = COLUMN_NAME_PREFIX + idx;
    }
    return name;
  }

  public static boolean isRowId(@Nullable GridColumn column) {
    return column != null && column.getAttributes().contains(ColumnDescriptor.Attribute.ROW_ID);
  }

  public static boolean isVirtualColumn(@Nullable GridColumn column) {
    return column != null && column.getAttributes().contains(ColumnDescriptor.Attribute.VIRTUAL);
  }

  public static @NlsSafe @NotNull String getLongMessage(@NotNull Throwable e) {
    return getMessagePrefix(e) + getMessage(e);
  }

  public static @NlsSafe @NotNull String getMessage(@NotNull Throwable t) {
    String m = t.getMessage();
    return StringUtil.isNotEmpty(m) ? m.trim() : t.getClass().getName();
  }

  public static @NotNull String getMessagePrefix(@NotNull Throwable t) {
    if (isUserOutput(t)) return "";
    if (!(t instanceof SQLException e)) return "";
    String state = e.getSQLState();
    int code = e.getErrorCode();
    if (StringUtil.isEmpty(state)) {
      return code != 0 ? "[" + code + "] " : "";
    }
    else if (code != 0) {
      return "[" + state + "][" + code + "] ";
    }
    else if (!isZeroState(state)) {
      return "[" + state + "] ";
    }
    return "";
  }

  private static boolean isZeroState(@Nullable String state) {
    return "0".equals(state) || "00000".equals(state) || "S0000".equals(state);
  }

  public static boolean isUserOutput(@NotNull Throwable t) {
    return t instanceof SQLWarning && ((SQLWarning)t).getErrorCode() == 0 && isUserOutputState(((SQLWarning)t).getSQLState());
  }

  private static boolean isUserOutputState(@Nullable String state) {
    return StringUtil.isEmpty(state) || isZeroState(state) || /* MSSQL: PRINT */ "S0001".equals(state);
  }

  public static boolean isPageSizeUnlimited(int pageSize) {
    return pageSize < 1;
  }

  public static int getPageSize(@Nullable DataGridSettings settings) {
    return settings == null
           ? DEFAULT_PAGE_SIZE
           : settings.isLimitPageSize()
             ? settings.getPageSize()
             : UNLIMITED_PAGE_SIZE;
  }

  public static @NlsSafe @NotNull String wrapInOpenFileLink(@NotNull String path) {
    return "<a href=\"" + OPEN_FILE_DESC + "\">" + PathUtil.getFileName(path) + "</a>";
  }

  @RequiresWriteLock
  public static void associatePsiSafe(@NotNull Document document, @NotNull PsiFile psiFile) {
    var oldFile = FileDocumentManager.getInstance().getFile(document);
    if (oldFile instanceof LightVirtualFileBase lf) {
      lf.setValid(false);
    }
    ((PsiDocumentManagerBase)PsiDocumentManager.getInstance(psiFile.getProject())).associatePsi(document, psiFile);
  }
}
