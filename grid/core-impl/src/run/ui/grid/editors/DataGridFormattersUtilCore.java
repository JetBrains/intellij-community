package com.intellij.database.run.ui.grid.editors;

import com.intellij.database.datagrid.CoreGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.extractors.FormatterCreator;
import com.intellij.database.extractors.ObjectFormatterConfig;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.database.settings.DataGridSettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.TimeZone;

import static com.intellij.database.extractors.FormatterCreator.getTimestampKey;
import static com.intellij.database.run.ui.grid.editors.FormatsCache.*;
import static java.time.temporal.ChronoField.*;

public final class DataGridFormattersUtilCore {
  public static final LocalDate START_DATE = LocalDate.of(1970, 1, 1);

  private static final Logger LOG = Logger.getInstance(DataGridFormattersUtilCore.class);
  private static final double START_JULIAN_DATE = 2440587.5;
  private static final int MS_PER_DAY = 60 * 60 * 24 * 1000;
  private static final int DEFAULT_ERA = 1;

  private DataGridFormattersUtilCore() {
  }

  public static int getEra(@NotNull TemporalAccessor date) {
    return date.isSupported(ERA) ? date.get(ERA) : DEFAULT_ERA;
  }

  public static synchronized @NotNull Timestamp fromOffsetDateTime(
    @NotNull OffsetDateTime offsetDateTime,
    @NotNull FormatsCache formatsCache,
    @NotNull FormatterCreator creator,
    @Nullable ObjectFormatterConfig config
  ) {
    DateFormat format = formatsCache.get(SIMPLE_TIMESTAMP_FORMAT_PROVIDER, creator);
    format.setTimeZone(getTimeZoneOrDefault(config));
    try {
      DateTimeFormatter offsetDateTimeFormatter = formatsCache.get(OFFSET_DATE_TIME_FORMAT_PROVIDER, creator);
      Date date = (Date)format.parseObject(offsetDateTimeFormatter.format(offsetDateTime));
      Timestamp timestamp = new Timestamp(date.getTime());
      timestamp.setNanos(offsetDateTime.getNano());
      return timestamp;
    }
    catch (ParseException e) {
      LOG.warn(e);
    }
    return Timestamp.valueOf(offsetDateTime.atZoneSameInstant(getZoneIdOrDefault(config)).toLocalDateTime());
  }

  public static @NotNull TimeZone getDefaultTimeZone() {
    return TimeZone.getTimeZone("UTC");
  }

  public static @NotNull ZoneOffset getLocalTimeOffset() {
    return ZonedDateTime.of(LocalDateTime.of(1970, 1, 1, 0, 0, 1), ZoneId.systemDefault()).getOffset();
  }

  public static @NotNull ZoneOffset getDefaultOffset() {
    return ZoneOffset.UTC;
  }

  public static synchronized @NotNull OffsetDateTime fromTimestamp(
    @NotNull Timestamp timestamp,
    @NotNull FormatsCache formatsCache,
    @NotNull FormatterCreator formatterCreator,
    @Nullable ObjectFormatterConfig config
  ) {
    DateFormat format = formatsCache.get(SIMPLE_TIMESTAMP_FORMAT_PROVIDER, formatterCreator);
    format.setTimeZone(getDefaultTimeZone());
    ZoneOffset zoneOffset = getZoneOffsetOrDefault(config, timestamp.toInstant());
    try {
      return formatsCache.get(OFFSET_DATE_TIME_FORMAT_PROVIDER, formatterCreator).parse(format.format(timestamp), OffsetDateTime::from)
        .withNano(timestamp.getNanos())
        .withOffsetSameInstant(zoneOffset);
    }
    catch (DateTimeParseException e) {
      LOG.warn(e);
    }
    return timestamp.toLocalDateTime()
      .atZone(ZoneId.systemDefault())
      .toOffsetDateTime()
      .withOffsetSameInstant(zoneOffset);
  }

  public static synchronized @NotNull LocalDateTime fromDateToLocalDateTime(
    @NotNull Date date,
    @NotNull FormatsCache formatsCache,
    @NotNull FormatterCreator formatterCreator,
    @Nullable ObjectFormatterConfig config
  ) {
    DateFormat format = formatsCache.get(TIMESTAMP_WITH_MILLI_FORMAT_PROVIDER, formatterCreator);
    format.setTimeZone(getTimeZoneOrDefault(config));
    try {
      return formatsCache.get(LOCAL_DATE_WITH_MILLI_FORMAT_PROVIDER, formatterCreator)
        .parse(formatsCache.get(TIMESTAMP_WITH_MILLI_FORMAT_PROVIDER, formatterCreator).format(date), LocalDateTime::from);
    }
    catch (DateTimeParseException e) {
      LOG.warn(e);
    }
    Instant instant = date.toInstant();
    return LocalDateTime.ofInstant(instant, ZoneOffset.UTC)
      .atOffset(getZoneOffsetOrDefault(config, instant))
      .toLocalDateTime();
  }

  public static boolean isEmptyTime(@NotNull TemporalAccessor temporalAccessor) {
    return (!temporalAccessor.isSupported(HOUR_OF_DAY) || temporalAccessor.get(HOUR_OF_DAY) == 0) &&
           (!temporalAccessor.isSupported(MINUTE_OF_HOUR) || temporalAccessor.get(MINUTE_OF_HOUR) == 0) &&
           (!temporalAccessor.isSupported(SECOND_OF_MINUTE) || temporalAccessor.get(SECOND_OF_MINUTE) == 0) &&
           (!temporalAccessor.isSupported(MILLI_OF_SECOND) || temporalAccessor.get(MILLI_OF_SECOND) == 0);
  }

  public static synchronized @NotNull LocalDate fromDate(@NotNull Date date, @NotNull FormatsCache formatsCache, @NotNull FormatterCreator formatterCreator) {
    DateFormat format = formatsCache.get(SIMPLE_DATE_FORMAT_PROVIDER, formatterCreator);
    format.setTimeZone(getDefaultTimeZone());
    try {
      return formatsCache.get(LOCAL_DATE_FORMAT_PROVIDER, formatterCreator).parse(format.format(date), LocalDate::from);
    }
    catch (DateTimeParseException e) {
      LOG.warn(e);
    }
    return date instanceof java.sql.Date ?
           ((java.sql.Date)date).toLocalDate() :
           LocalDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC)
             .atOffset(getDefaultOffset())
             .toLocalDate();
  }

  public static synchronized @NotNull Date fromLocalDateTime(
    @NotNull LocalDateTime dateTime,
    @NotNull FormatsCache formatsCache,
    @NotNull FormatterCreator formatterCreator,
    @Nullable ObjectFormatterConfig config
  ) {
    DateFormat format = formatsCache.get(TIMESTAMP_WITH_MILLI_FORMAT_PROVIDER, formatterCreator);
    format.setTimeZone(getTimeZoneOrDefault(config));
    try {
      return (Date)format.parseObject(formatsCache.get(LOCAL_DATE_WITH_MILLI_FORMAT_PROVIDER, formatterCreator).format(dateTime));
    }
    catch (ParseException e) {
      LOG.warn(e);
    }
    return Date.from(dateTime.atZone(getZoneIdOrDefault(config)).toInstant());
  }

  public static synchronized @NotNull Date fromLocalDate(@NotNull LocalDate date, @NotNull FormatsCache formatsCache, @NotNull FormatterCreator formatterCreator) {
    DateFormat format = formatsCache.get(SIMPLE_DATE_FORMAT_PROVIDER, formatterCreator);
    format.setTimeZone(getDefaultTimeZone());
    try {
      return (Date)format.parseObject(formatsCache.get(LOCAL_DATE_FORMAT_PROVIDER, formatterCreator).format(date));
    }
    catch (ParseException e) {
      LOG.warn(e);
    }
    return Date.from(date.atStartOfDay().toInstant(ZoneOffset.UTC));
  }

  private static @Nullable Date parseDateFromNumber(@NotNull String s) {
    try {
      return new Date(Long.parseLong(s));
    }
    catch (NumberFormatException ignore) {
    }
    try {
      return new Date(fromJulian(Double.parseDouble(s)));
    }
    catch (NumberFormatException ignore) {
    }
    return null;
  }

  private static long fromJulian(double d) {
    return (long)(d - START_JULIAN_DATE) * MS_PER_DAY;
  }

  private static synchronized @NotNull Date getUtcDate(@NotNull FormatsCache formatsCache, @NotNull FormatterCreator formatterCreator) {
    Date currentDate = new Date();
    DateFormat format = formatsCache.get(SHORT_TIMESTAMP_FORMAT_PROVIDER, formatterCreator);
    format.setTimeZone(TimeZone.getDefault());
    String formattedInLocalTz = format.format(currentDate);
    format.setTimeZone(getDefaultTimeZone());
    try {
      return format.parse(formattedInLocalTz);
    }
    catch (ParseException e) {
      LOG.warn(e);
    }

    return currentDate;
  }

  public static @NotNull Date getDateFrom(@Nullable Object o,
                                          @NotNull CoreGrid<GridRow, GridColumn> grid,
                                          @NotNull ModelIndex<GridColumn> column,
                                          @NotNull FormatsCache formatsCache,
                                          @NotNull FormatterCreator formatterCreator) {
    if (o instanceof String) {
      Date fromNumber = parseDateFromNumber((String)o);
      if (fromNumber != null) return fromNumber;
      GridColumn c = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumn(column);
      Formatter format = formatterCreator.create(getTimestampKey(c, null, formatsCache));
      try {
        Date res = ObjectUtils.tryCast(format.parse((String)o), Date.class);
        return ObjectUtils.notNull(res, getUtcDate(formatsCache, formatterCreator));
      }
      catch (ParseException e) {
        getUtcDate(formatsCache, formatterCreator);
      }
    }
    return o instanceof Double ? new Date(fromJulian((Double)o)) :
           o instanceof Number ? new Date(((Number)o).longValue()) :
           o instanceof Date ? (Date)o :
           getUtcDate(formatsCache, formatterCreator);
  }

  public static @NotNull Date getBoundedValue(@NotNull Object value, @NotNull ModelIndex<GridColumn> column, @NotNull CoreGrid<GridRow, GridColumn> grid) {
    BoundaryValueResolver resolver = GridCellEditorHelper.get(grid).getResolver(grid, column);
    return resolver.bound(value);
  }

  public static @Nullable ZoneId getZoneId(@Nullable ObjectFormatterConfig config) {
    if (config == null) return null;
    DataGridSettings settings = config.getSettings();
    if (settings == null) return null;
    return settings.getEffectiveZoneId();
  }

  public static @NotNull ZoneId getZoneIdOrDefault(@Nullable ObjectFormatterConfig config) {
    return ObjectUtils.notNull(getZoneId(config), ZoneId.systemDefault());
  }

  public static @NotNull ZoneOffset getLocalTimeOffset(@Nullable ObjectFormatterConfig config) {
    ZoneId zoneId = getZoneIdOrDefault(config);
    LocalDateTime referenceDateTime = LocalDateTime.of(1970, 1, 1, 0, 0, 1);
    return ZonedDateTime.of(referenceDateTime, zoneId).getOffset();
  }

  public static @Nullable TimeZone getTimeZoneOrDefault(@Nullable ObjectFormatterConfig config) {
    ZoneId zoneId = getZoneId(config);
    return zoneId != null ? TimeZone.getTimeZone(zoneId) : getDefaultTimeZone();
  }

  public static @NotNull ZoneOffset getZoneOffsetOrDefault(@Nullable ObjectFormatterConfig config, @NotNull Instant instant) {
    ZoneId zoneId = getZoneId(config);
    return zoneId != null ? zoneId.getRules().getOffset(instant) : getDefaultOffset();
  }

  public static @NotNull ZoneOffset getZoneOffsetByEpochOrDefault(@Nullable ObjectFormatterConfig config) {
    return getZoneOffsetOrDefault(config, Instant.now());
  }

  public static @NotNull OffsetTime adjustOffset(@NotNull OffsetTime time, @Nullable ZoneId zoneId) {
    if (zoneId != null) {
      ZoneOffset offset = zoneId.getRules().getOffset(time.toLocalTime().atDate(LocalDate.now()));
      return time.withOffsetSameInstant(offset);
    }
    return time;
  }

  public static @NotNull Temporal adjustTimeZone(@NotNull OffsetDateTime dateTime, @Nullable ZoneId zoneId) {
    return zoneId != null ? dateTime.atZoneSameInstant(zoneId) : dateTime;
  }

  public static @NotNull OffsetDateTime adjustOffset(@NotNull OffsetDateTime dateTime, @Nullable ZoneId zoneId) {
    return zoneId != null ? dateTime.atZoneSameInstant(zoneId).toOffsetDateTime() : dateTime;
  }

  public static @NotNull OffsetDateTime toOffsetDateTime(@NotNull LocalDateTime dateTime, @Nullable ZoneId zoneId) {
    return dateTime.atZone(ObjectUtils.notNull(zoneId, ZoneId.systemDefault())).toOffsetDateTime();
  }

  public static @NotNull OffsetDateTime toOffsetDateTime(@NotNull TemporalAccessor value, @Nullable ZoneId zoneId) {
    return value instanceof LocalDateTime dateTime
           ? toOffsetDateTime(dateTime, zoneId)
           : adjustOffset((OffsetDateTime)value, zoneId);
  }
}
