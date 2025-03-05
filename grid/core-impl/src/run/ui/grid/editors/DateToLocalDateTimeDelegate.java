package com.intellij.database.run.ui.grid.editors;

import com.intellij.database.extractors.FormatterCreator;
import com.intellij.database.extractors.ObjectFormatterConfig;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.Date;

public class DateToLocalDateTimeDelegate extends DateAndTimeFormatterDelegate<Date, LocalDateTime> {
  private final FormatsCache myFormatsCache;
  private final FormatterCreator myFormatterCreator;
  private final ObjectFormatterConfig myConfig;

  public DateToLocalDateTimeDelegate(
    @NotNull FormatsCache formatsCache,
    @NotNull FormatterCreator formatterCreator,
    ObjectFormatterConfig config
  ) {
    super(LocalDateTime::from);
    myFormatsCache = formatsCache;
    myFormatterCreator = formatterCreator;
    myConfig = config;
  }

  @Override
  protected Date createFromTemporal(@NotNull LocalDateTime value) {
    return DataGridFormattersUtilCore.fromLocalDateTime(value, myFormatsCache, myFormatterCreator, myConfig);
  }

  @Override
  protected LocalDateTime toTemporalAccessor(@NotNull Object value) {
    if (!(value instanceof Date)) throw new IllegalArgumentException("Value must be of type Date");
    return DataGridFormattersUtilCore.fromDateToLocalDateTime((Date)value, myFormatsCache, myFormatterCreator, myConfig);
  }
}
