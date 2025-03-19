package com.intellij.database.actions;

import com.intellij.database.DataGridBundle;
import com.intellij.database.DatabaseDataKeys;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Predicate;

import static com.intellij.database.datagrid.GridUtil.*;

public class GotoReferencingResultAction extends GotoResultAction {

  @Override
  protected boolean isEnabled(@NotNull DataGrid grid, @NotNull AnActionEvent e) {
    return IS_REFERENCED.get(grid, false);
  }

  @Override
  protected @Nullable Predicate<Content> getResultPredicate(@NotNull DataGrid grid, @NotNull AnActionEvent e) {
      Content curContent = e.getData(DatabaseDataKeys.DATA_GRID_CONTENT_KEY);
      if (curContent == null) return null;
      Object inReference = IN_REFERENCE.get(curContent);
      if (inReference == null) return null;
      return content -> {
        Set<Object> outReferences = OUT_REFERENCES.get(content);
        return outReferences != null && outReferences.contains(inReference);
      };
  }

  @Override
  protected @NotNull String getErrorMessage() {
    return DataGridBundle.message("action.Console.TableResult.GotoReferencingResult.error.message");
  }
}