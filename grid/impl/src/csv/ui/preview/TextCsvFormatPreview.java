package com.intellij.database.csv.ui.preview;

import com.intellij.database.csv.CsvFormat;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridRequestSource;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.database.extractors.FormatBasedExtractor;
import com.intellij.database.util.Out;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class TextCsvFormatPreview implements CsvFormatPreview {
  private final DataGrid myGrid;
  private final TextView myTextView;
  private final boolean mySelectionOnly;

  public TextCsvFormatPreview(@NotNull DataGrid grid, @NotNull Disposable parent, boolean selectionOnly) {
    this(grid, new TextView(parent), selectionOnly);
  }

  public TextCsvFormatPreview(@NotNull DataGrid grid, @NotNull TextView textView, boolean selectionOnly) {
    myGrid = grid;
    myTextView = textView;
    mySelectionOnly = selectionOnly;
  }

  @Override
  public void setFormat(@NotNull CsvFormat format, @NotNull GridRequestSource source) {
    FormatBasedExtractor extractor = new FormatBasedExtractor(format, myGrid.getObjectFormatter());
    var out = new Out.Readable();
    GridUtil.extractValues(myGrid, extractor, out, mySelectionOnly, false);
    String text = out.getString();
    myTextView.setText(text);
  }

  @Override
  public @NotNull JComponent getComponent() {
    return myTextView.getComponent();
  }
}
