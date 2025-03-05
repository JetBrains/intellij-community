package com.intellij.database.csv.ui.preview;

import com.intellij.database.csv.CsvFormat;
import com.intellij.database.datagrid.CsvDocumentDataHookUp;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridRequestSource;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.database.run.ui.DataGridRequestPlace;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class TableCsvFormatPreview implements CsvFormatPreview {
  private final DataGrid myGrid;

  public TableCsvFormatPreview(@NotNull Project project,
                               @NotNull CsvFormat format,
                               @NotNull CharSequence text,
                               @NotNull Disposable parent,
                               @Nullable Runnable doAfterDataIsLoaded) {
    Document document = EditorFactory.getInstance().createDocument(text);
    document.setReadOnly(true);

    CsvDocumentDataHookUp hookup = new CsvDocumentDataHookUp(project, format, document, null);
    Disposer.register(parent, hookup);

    myGrid = GridUtil.createCsvPreviewDataGrid(project, hookup);
    Disposer.register(parent, myGrid);

    Disposer.register(parent, UiNotifyConnector.Once.installOn(myGrid.getPanel().getComponent(), new Activatable() {
      @Override
      public void showNotify() {
        ActionCallback loaded = loadPreview();
        if (doAfterDataIsLoaded != null) {
          loaded.doWhenDone(doAfterDataIsLoaded);
        }
      }
    }));
  }

  public @NotNull ActionCallback loadPreview() {
    GridRequestSource source = new GridRequestSource(new DataGridRequestPlace(myGrid));
    getHookUp().getLoader().loadFirstPage(source);
    return source.getActionCallback();
  }

  @Override
  public void setFormat(@NotNull CsvFormat format, @NotNull GridRequestSource source) {
    getHookUp().setFormat(format, source);
  }

  public @NotNull ActionCallback setFormat(@NotNull CsvFormat format) {
    GridRequestSource source = new GridRequestSource(new DataGridRequestPlace(myGrid));
    setFormat(format, source);
    return source.getActionCallback();
  }

  public @NotNull DataGrid getGrid() {
    return myGrid;
  }

  @Override
  public @NotNull JComponent getComponent() {
    return myGrid.getPanel().getComponent();
  }

  private @NotNull CsvDocumentDataHookUp getHookUp() {
    return (CsvDocumentDataHookUp)myGrid.getDataHookup();
  }
}
