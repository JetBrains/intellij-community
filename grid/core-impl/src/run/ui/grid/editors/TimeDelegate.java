package com.intellij.database.run.ui.grid.editors;

import com.intellij.database.extractors.ObjectFormatterConfig;
import org.jetbrains.annotations.NotNull;

import java.sql.Time;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;

public class TimeDelegate extends DateAndTimeFormatterDelegate<Time, OffsetTime> {
  private final ObjectFormatterConfig myConfig;

  public TimeDelegate(ObjectFormatterConfig config) {
    super(OffsetTime::from, a -> LocalTime.from(a).atOffset(DataGridFormattersUtilCore.getDefaultOffset()));
    myConfig = config;
  }

  @Override
  protected Time createFromTemporal(@NotNull OffsetTime value) {
    return Time.valueOf(
      value.withOffsetSameInstant(DataGridFormattersUtilCore.getLocalTimeOffset(myConfig)).toLocalTime()
    );
  }

  @Override
  protected OffsetTime toTemporalAccessor(@NotNull Object value) {
    OffsetTime offsetTime;
    if (value instanceof Time time) {
      offsetTime = time.toLocalTime().atOffset(ZoneOffset.ofTotalSeconds(-time.getTimezoneOffset() * 60));
    }
    else if (value instanceof LocalTime localTime) {
      offsetTime = localTime.atOffset(ZoneOffset.UTC);
    }
    else {
      throw new IllegalArgumentException("Unsupported value type: " + value.getClass());
    }

    return offsetTime.withOffsetSameInstant(DataGridFormattersUtilCore.getZoneOffsetByEpochOrDefault(myConfig));
  }
}
