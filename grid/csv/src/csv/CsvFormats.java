package com.intellij.database.csv;

import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface CsvFormats {
  NotNullLazyValue<CsvFormat> TSV_FORMAT = NotNullLazyValue.lazy(() -> {
    return xsvFormat(GridCsvBundle.message("csv.format.tsv.default.name"), "Tab-separated (TSV)_id", "\t");
  });
  NotNullLazyValue<CsvFormat> CSV_FORMAT = NotNullLazyValue.lazy(() -> {
    return xsvFormat(GridCsvBundle.message("csv.format.csv.default.name"), "Comma-separated (CSV)_id", ",");
  });
  NotNullLazyValue<CsvFormat> PIPE_SEPARATED_FORMAT = NotNullLazyValue.lazy(() -> {
    return xsvFormat(GridCsvBundle.message("csv.format.pipe.separated.default.name"), "Pipe-separated_id", "|");
  });
  NotNullLazyValue<CsvFormat> SEMICOLON_SEPARATED_FORMAT = NotNullLazyValue.lazy(() -> {
    return xsvFormat(GridCsvBundle.message("csv.format.semicolon.separated.default.name"), "Semicolon-separated_id", ";");
  });

  private static CsvFormat xsvFormat(@NotNull String name, @NonNls @NotNull String id, @NotNull String valueSeparator) {
    List<CsvRecordFormat.Quotes> quotes = List.of(new CsvRecordFormat.Quotes("\"", "\"", "\"\"", "\"\""),
                                                  new CsvRecordFormat.Quotes("'", "'", "''", "''"));
    CsvRecordFormat.QuotationPolicy quotationPolicy = CsvRecordFormat.QuotationPolicy.AS_NEEDED;
    CsvRecordFormat dataFormat = new CsvRecordFormat("", "", "", quotes, quotationPolicy, valueSeparator, "\n", false);
    return new CsvFormat(name, dataFormat, null, id, false);
  }
}
