package com.intellij.database.vfs.fragment;

import com.intellij.database.csv.CsvFormat;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class CsvTableDataFragmentFile extends TableDataFragmentFile {
  private final CsvFormat myFormat;

  public CsvTableDataFragmentFile(@NotNull VirtualFile originalFile, @NotNull TextRange range, @NotNull CsvFormat format) {
    super(originalFile, range);
    myFormat = format;
  }

  public @NotNull CsvFormat getFormat() {
    return myFormat;
  }
}
