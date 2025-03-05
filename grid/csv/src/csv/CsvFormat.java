package com.intellij.database.csv;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public final class CsvFormat {
  public final @NlsSafe String name;
  public final @NotNull CsvRecordFormat dataRecord;
  public final @Nullable CsvRecordFormat headerRecord;
  public final boolean rowNumbers;
  public final String id;

  public CsvFormat(@NotNull CsvRecordFormat dataRecord, @Nullable CsvRecordFormat headerRecord, boolean rowNumbers) {
    this("", dataRecord, headerRecord, rowNumbers);
  }

  public CsvFormat(@NotNull String name, @NotNull CsvRecordFormat dataRecord, @Nullable CsvRecordFormat headerRecord, boolean rowNumbers) {
    this(name, dataRecord, headerRecord, UUID.randomUUID().toString(), rowNumbers);
  }

  public CsvFormat(@NotNull String name,
                   @NotNull CsvRecordFormat dataRecord,
                   @Nullable CsvRecordFormat headerRecord,
                   @NotNull String id,
                   boolean rowNumbers) {
    this.name = name;
    this.dataRecord = dataRecord;
    this.headerRecord = headerRecord;
    this.rowNumbers = rowNumbers;
    this.id = id;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CsvFormat format)) return false;

    if (rowNumbers != format.rowNumbers) return false;
    if (!name.equals(format.name)) return false;
    if (!dataRecord.equals(format.dataRecord)) return false;
    if (headerRecord != null ? !headerRecord.equals(format.headerRecord) : format.headerRecord != null) return false;
    if (!id.equals(format.id)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = name.hashCode();
    result = 31 * result + dataRecord.hashCode();
    result = 31 * result + (headerRecord != null ? headerRecord.hashCode() : 0);
    result = 31 * result + (rowNumbers ? 1 : 0);
    result = 31 * result + id.hashCode();
    return result;
  }

  public static int indexOfFormatNamed(@NotNull List<CsvFormat> formats, final @Nullable String name) {
    return name == null ? -1 : ContainerUtil.indexOf(formats, format -> StringUtil.equals(name, format.name));
  }
}
