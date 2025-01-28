package com.intellij.database.data.types;

import com.intellij.database.DataGridBundle;
import com.intellij.database.extractors.FormatterCreator;
import com.intellij.database.run.ui.grid.editors.*;
import org.jetbrains.annotations.NotNull;

import java.time.*;
import java.time.temporal.TemporalAccessor;

public final class ConverterSupport {

  private ConverterSupport() {
  }

  public static @NotNull Formatter createTimestampFormatter(@NotNull FormatterCreator formatterCreator) {
    return new CompositeFormatter(DataGridBundle.message("expected.timestamp"),
                                  formatterCreator.newZonedTimestampFormat(new TimestampDelegate(), BoundaryValueResolver.ALWAYS_NULL, 6, null),
                                  formatterCreator.newIsoFormatter(new TimestampDelegate())
    );
  }

  public static @NotNull Formatter getTimeFormatter(@NotNull FormatterCreator formatterCreator) {
    return new CompositeFormatter(DataGridBundle.message("expected.time"),
                                  formatterCreator.newZonedTimeFormat(new TimeDelegate(), 6, null),
                                  formatterCreator.newTimeFormat(null, 0, new TimeDelegate())
    );
  }

  public static @NotNull Formatter getDateFormatter(@NotNull FormatsCache formatsCache, @NotNull FormatterCreator formatterCreator) {
    return formatterCreator.newEraDateFormatter(null, new DateDelegate(formatsCache, formatterCreator), BoundaryValueResolver.ALWAYS_NULL);
  }

  private static class TimestampDelegate extends DateAndTimeFormatterDelegate<TemporalAccessor, TemporalAccessor> {
    TimestampDelegate() {
      super(OffsetDateTime::from,
            temporal -> LocalDateTime.from(temporal).atOffset(DataGridFormattersUtilCore.getDefaultOffset()),
            temporal -> LocalDate.from(temporal).atStartOfDay().atOffset(DataGridFormattersUtilCore.getDefaultOffset()));
    }

    @Override
    protected TemporalAccessor toTemporalAccessor(@NotNull Object value) {
      if (!(value instanceof TemporalAccessor)) throw new IllegalArgumentException(
        String.format("Value class is not TemporalAccessor: %s", value.getClass())
      );
      return value instanceof LocalDateTime ?
             ((LocalDateTime)value)
               .atZone(ZoneId.systemDefault())
               .toOffsetDateTime()
               .withOffsetSameInstant(DataGridFormattersUtilCore.getDefaultOffset()) :
             (TemporalAccessor)value;
    }

    @Override
    protected TemporalAccessor createFromTemporal(@NotNull TemporalAccessor value) {
      return value instanceof ZonedDateTime ? ((ZonedDateTime)value).toOffsetDateTime() : value;
    }
  }

  private static class TimeDelegate extends DateAndTimeFormatterDelegate<TemporalAccessor, TemporalAccessor> {
    TimeDelegate() {
      super(OffsetTime::from, temporal -> LocalTime.from(temporal).atOffset(DataGridFormattersUtilCore.getDefaultOffset()));
    }

    @Override
    protected TemporalAccessor toTemporalAccessor(@NotNull Object value) {
      if (!(value instanceof TemporalAccessor)) throw new IllegalArgumentException(
        String.format("Value class is not TemporalAccessor: %s", value.getClass())
      );
      return value instanceof LocalTime ?
             ((LocalTime)value)
               .atOffset(DataGridFormattersUtilCore.getLocalTimeOffset())
               .withOffsetSameInstant(DataGridFormattersUtilCore.getDefaultOffset()) :
             (TemporalAccessor)value;
    }

    @Override
    protected TemporalAccessor createFromTemporal(@NotNull TemporalAccessor value) {
      return value;
    }
  }
}
