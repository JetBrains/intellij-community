package com.intellij.database.csv.ui;

import com.intellij.database.csv.ui.preview.CsvFormatPreview;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.ui.components.JBPanel;
import org.jetbrains.annotations.NotNull;

import java.awt.BorderLayout;

import static com.intellij.database.csv.CsvFormatEditor.CSV_FORMAT_EDITOR_KEY;

public class FormatsListAndPreviewPanel extends JBPanel<FormatsListAndPreviewPanel> implements UiDataProvider {
  private final CsvFormatsUI myList;

  public FormatsListAndPreviewPanel(@NotNull CsvFormatsUI formatsList, @NotNull CsvFormatPreview preview) {
    super(new BorderLayout());
    myList = formatsList;

    formatsList.attachPreview(preview);

    add(formatsList.getComponent(), BorderLayout.WEST);
    add(preview.getComponent(), BorderLayout.CENTER);
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(CSV_FORMAT_EDITOR_KEY, myList.getFormatForm());
  }
}
