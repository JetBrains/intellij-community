package com.intellij.database.csv.ui.preview;

import com.intellij.database.csv.CsvFormat;
import com.intellij.database.datagrid.GridRequestSource;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface CsvFormatPreview {
  void setFormat(@NotNull CsvFormat format, @NotNull GridRequestSource source);

  @NotNull
  JComponent getComponent();
}
