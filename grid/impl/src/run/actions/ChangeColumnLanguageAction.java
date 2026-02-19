package com.intellij.database.run.actions;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.ide.scratch.LRUPopupBuilder;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

public class ChangeColumnLanguageAction extends SingleColumnHeaderAction {
  public ChangeColumnLanguageAction() {
    super(true);
  }

  @Override
  protected void actionPerformed(AnActionEvent e, @NotNull DataGrid grid, @NotNull ModelIndex<GridColumn> columnIdx) {
    Project project = e.getProject();
    if (project == null) return;

    Consumer<Language> chosenLanguageConsumer = language -> {
      if (language != null) {
        grid.setContentLanguage(columnIdx, language);
      }
    };
    Language oldLanguage = grid.getContentLanguage(columnIdx);
    LRUPopupBuilder.forFileLanguages(project, DataGridBundle.message("popup.title.set.language"), oldLanguage, chosenLanguageConsumer)
      .showInBestPositionFor(e.getDataContext());
  }
}
