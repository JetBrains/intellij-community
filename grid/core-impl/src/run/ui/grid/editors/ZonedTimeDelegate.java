package com.intellij.database.run.ui.grid.editors;

import com.intellij.database.DataGridBundle;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;

public abstract class ZonedTimeDelegate<T> extends DateAndTimeFormatterDelegate<T, TemporalAccessor> {
  private static final ZoneOffset MAX_ZONE_OFFSET = ZoneOffset.of("+14:00");
  private static final ZoneOffset MIN_ZONE_OFFSET = ZoneOffset.of("-12:00");

  public ZonedTimeDelegate() {
    super(ZonedTimeDelegate::extractOffsetTime, ZonedTimeDelegate::extractWithLocalOffset);
  }

  private static TemporalAccessor extractOffsetTime(TemporalAccessor temporal) {
    OffsetTime offsetTime = OffsetTime.from(temporal);
    ZoneOffset offset = offsetTime.getOffset();
    if (MAX_ZONE_OFFSET.compareTo(offset) > 0 || MIN_ZONE_OFFSET.compareTo(offset) < 0) {
      throw new DateTimeException(DataGridBundle.message("zoned.time.out.of.range"));
    }
    return offsetTime;
  }

  private static TemporalAccessor extractWithLocalOffset(TemporalAccessor temporal) {
    if (temporal.isSupported(ChronoField.OFFSET_SECONDS)) {
      throw new DateTimeException(DataGridBundle.message("zoned.time.out.of.range"));
    }
    return LocalTime.from(temporal).atOffset(DataGridFormattersUtilCore.getDefaultOffset());
  }
}
