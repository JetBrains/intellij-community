package com.intellij.database.datagrid;

import com.intellij.database.csv.CsvFormat;
import com.intellij.database.csv.CsvRecord;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CsvParserResult {
  private final CsvFormat myFormat;
  private final CharSequence mySequence;
  private final List<CsvRecord> myRecords;
  private final CsvRecord myHeader;
  private final int myColumnsCount;

  public CsvParserResult(@NotNull CsvFormat format,
                            @NotNull CharSequence sequence,
                            @NotNull List<CsvRecord> records,
                            @Nullable CsvRecord header,
                            int columnsCount) {
    myFormat = format;
    mySequence = sequence;
    myRecords = records;
    myHeader = header;
    myColumnsCount = columnsCount;
  }

  public List<CsvRecord> getRecords() {
    return myRecords;
  }

  public CsvRecord getHeader() {
    return myHeader;
  }

  public CsvFormat getFormat() {
    return myFormat;
  }

  public CharSequence getSequence() {
    return mySequence;
  }

  public int getColumnsCount() {
    return myColumnsCount;
  }
}
