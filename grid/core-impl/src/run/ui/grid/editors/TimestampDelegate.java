package com.intellij.database.run.ui.grid.editors;

import com.intellij.database.extractors.FormatterCreator;
import com.intellij.database.extractors.ObjectFormatterConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static com.intellij.database.run.ui.grid.editors.DataGridFormattersUtilCore.getDefaultOffset;

public class TimestampDelegate extends DateAndTimeFormatterDelegate<Timestamp, OffsetDateTime> {
  private final FormatsCache myFormatsCache;
  private final FormatterCreator myFormatterCreator;
  private final ObjectFormatterConfig myConfig;

  public TimestampDelegate(
    @NotNull FormatsCache formatsCache,
    @NotNull FormatterCreator creator,
    @Nullable ObjectFormatterConfig config
  ) {
    super(OffsetDateTime::from,
          temporal -> LocalDateTime.from(temporal).atOffset(getDefaultOffset()),
          temporal -> LocalDate.from(temporal).atStartOfDay().atOffset(getDefaultOffset()));
    myFormatsCache = formatsCache;
    myFormatterCreator = creator;
    myConfig = config;
  }

  @Override
  protected OffsetDateTime toTemporalAccessor(@NotNull Object value) {
    if (!(value instanceof Timestamp)) throw new IllegalArgumentException("Value must be of type Timestamp");
    return DataGridFormattersUtilCore.fromTimestamp((Timestamp)value, myFormatsCache, myFormatterCreator, myConfig);
  }

  @Override
  protected Timestamp createFromTemporal(@NotNull OffsetDateTime value) {
    return DataGridFormattersUtilCore.fromOffsetDateTime(value, myFormatsCache, myFormatterCreator, myConfig);
  }
}
