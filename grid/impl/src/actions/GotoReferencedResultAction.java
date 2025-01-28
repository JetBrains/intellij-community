package com.intellij.database.actions;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.run.ResultReference;
import com.intellij.database.util.DataGridUIUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

import static com.intellij.database.datagrid.GridUtil.IN_REFERENCE;

public class GotoReferencedResultAction extends GotoResultAction {

  @Override
  protected boolean isEnabled(@NotNull DataGrid grid, @NotNull AnActionEvent e) {
    return getOutResultReference(grid, e) != null;
  }

  @Override
  protected @Nullable Predicate<Content> getResultPredicate(@NotNull DataGrid grid, @NotNull AnActionEvent e) {
    ResultReference outResultReference = getOutResultReference(grid, e);
    if (outResultReference == null) return null;
    Object outReference = outResultReference.getReference();
    return content -> outReference == IN_REFERENCE.get(content);
  }

  private static @Nullable ResultReference getOutResultReference(@NotNull DataGrid grid, @NotNull AnActionEvent e) {
    if (!DataGridUIUtil.inCell(grid, e)) return null;
    Object value = DataGridUIUtil.getLeadSelectionCellValue(grid, e, true);
    return value instanceof ResultReference ? (ResultReference) value : null;
  }

  @Override
  protected @NotNull String getErrorMessage() {
    return DataGridBundle.message("action.Console.TableResult.GotoReferencedResult.error.message");
  }
}