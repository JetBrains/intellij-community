package com.intellij.database.extractors;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.CoreGrid;
import com.intellij.database.datagrid.FormatterCreatorProvider;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.run.ui.grid.editors.*;
import com.intellij.database.settings.DataGridSettings;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PairFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.*;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static com.intellij.database.run.ui.grid.editors.DataGridFormattersUtilCore.*;


public class FormatterCreator {
  public static final int MAX_FRACTION_DIGITS = 340; // see java.text.DecimalFormat.setMaximumFractionDigits
  public static final FormatterKey<SimpleDateFormat> SIMPLE_TIMESTAMP_FORMATTER_KEY = new FormatterKey<>("SIMPLE_TIMESTAMP_FORMATTER_KEY");
  public static final FormatterKey<SimpleDateFormat> SHORT_TIMESTAMP_FORMATTER_KEY = new FormatterKey<>("SHORT_TIMESTAMP_FORMATTER_KEY");
  public static final FormatterKey<SimpleDateFormat> TIMESTAMP_WITH_MILLI_FORMATTER_KEY = new FormatterKey<>("TIMESTAMP_WITH_MILLI_FORMATTER_KEY");
  public static final FormatterKey<SimpleDateFormat> SIMPLE_DATE_FORMATTER_KEY = new FormatterKey<>("SIMPLE_DATE_FORMATTER_KEY");
  public static final FormatterKey<DateTimeFormatter> LOCAL_DATE_WITH_MILLI_FORMATTER_KEY = new FormatterKey<>("LOCAL_DATE_WITH_MILLI");
  public static final FormatterKey<DateTimeFormatter> LOCAL_DATE_FORMATTER_KEY = new FormatterKey<>("LOCAL_DATE");
  public static final FormatterKey<DateTimeFormatter> OFFSET_DATE_TIME_FORMATTER_KEY = new FormatterKey<>("OFFSET_DATE_TIME");

  private static final Key<Function<@NotNull CoreGrid<GridRow, GridColumn>, @NotNull FormatterCreator>> FORMATTER_CREATOR_KEY = new Key<>("FORMATTER_CREATOR_KEY");
  private static final String DATE_PATTERN = "yyyy-MM-dd";
  private static final String ERA_PATTERN = "[ ][GGG]";
  private static final String INT_FORMATTER_KEY = "INT_FORMATTER_KEY";
  private static final String LONG_FORMATTER_KEY = "LONG_FORMATTER_KEY";
  private static final String BIG_INT_FORMATTER_KEY = "BIG_INT_FORMATTER_KEY";
  private static final String FLOAT_FORMATTER_KEY = "FLOAT_FORMATTER_KEY";
  private static final String DOUBLE_FORMATTER_KEY = "DOUBLE_FORMATTER_KEY";
  private static final String DECIMAL_WITH_PRIORITY_TYPE = "DECIMAL_WITH_PRIORITY_TYPE";
  private static final String TIMESTAMP_WITH_SCALE_TYPE = "TIMESTAMP_WITH_SCALE_TYPE";
  private static final String TIME_FORMATTER_KEY = "TIME_FORMATTER_KEY";
  private static final String TIMESTAMP_FORMATTER_KEY = "TIMESTAMP_FORMATTER_KEY";
  private static final String SHORT_ERA_ZONED_TIMESTAMP = "SHORT_ERA_ZONED_TIMESTAMP";
  private static final String ERA_TIMESTAMP = "ERA_TIMESTAMP";
  private static final String ZONED_TIME_FORMATTER_KEY = "ZONED_TIME_FORMATTER_KEY";
  private static final String ZONED_TIMESTAMP_FORMATTER_KEY = "ZONED_TIMESTAMP_FORMATTER_KEY";
  private static final String DATE_FORMATTER_KEY = "DATE_FORMATTER_KEY";
  private static final String DECIMAL_FORMATTER_KEY = "DECIMAL_FORMATTER_KEY";
  private static final String BIG_DECIMAL_FORMATTER_KEY = "BIG_DECIMAL_FORMATTER_KEY";

  public static @NotNull FormatterCreator get(@NotNull CoreGrid<GridRow, GridColumn> grid) {
    Function<@NotNull CoreGrid<GridRow, GridColumn>, @NotNull FormatterCreator> cache = grid.getUserData(FORMATTER_CREATOR_KEY);
    if (cache == null) {
      cache = FormatterCreatorProvider.getCache();
      grid.putUserData(FORMATTER_CREATOR_KEY, cache);
    }
    return cache.fun(grid);
  }

  public static FormatterKey<NumberFormatter> getIntKey(@Nullable ObjectFormatterConfig config) {
    return add(new FormatterKey<>(INT_FORMATTER_KEY), config);
  }

  public static FormatterKey<NumberFormatter> getLongKey(@Nullable ObjectFormatterConfig config) {
    return add(new FormatterKey<>(LONG_FORMATTER_KEY), config);
  }

  public static FormatterKey<NumberFormatter> getBigIntKey(@Nullable ObjectFormatterConfig config) {
    return add(new FormatterKey<>(BIG_INT_FORMATTER_KEY), config);
  }

  public static FormatterKey<NumberFormatter> getFloatKey(@Nullable ObjectFormatterConfig config) {
    return add(new FormatterKey<>(FLOAT_FORMATTER_KEY), config);
  }

  public static FormatterKey<NumberFormatter> getDoubleKey(@Nullable ObjectFormatterConfig config) {
    return add(new FormatterKey<>(DOUBLE_FORMATTER_KEY), config);
  }

  public static FormatterKey<NumberFormatter> getDecimalWithPriorityTypeKey(int type, @Nullable ObjectFormatterConfig config) {
    return add(add(new FormatterKey<>(DECIMAL_WITH_PRIORITY_TYPE), type), config);
  }

  public static FormatterKey<Formatter> getTimeKey(@Nullable GridColumn column, @Nullable ObjectFormatterConfig config, @NotNull FormatsCache formatsCache) {
    return add(add(add(new FormatterKey<>(TIME_FORMATTER_KEY), column), config), formatsCache);
  }

  public static FormatterKey<Formatter> getTimestampKey(int scale, @NotNull FormatsCache formatsCache) {
    return add(add(new FormatterKey<>(TIMESTAMP_WITH_SCALE_TYPE), scale), formatsCache);
  }

  public static FormatterKey<Formatter> getTimestampKey(@Nullable GridColumn column, @Nullable ObjectFormatterConfig config, @NotNull FormatsCache formatsCache) {
    return add(add(add(new FormatterKey<>(TIMESTAMP_FORMATTER_KEY), column), config), formatsCache);
  }

  public static FormatterKey<Formatter> getShortEraZonedTimestampKey(@Nullable GridColumn column, @Nullable ObjectFormatterConfig config, @NotNull FormatsCache formatsCache) {
    return add(add(add(new FormatterKey<>(SHORT_ERA_ZONED_TIMESTAMP), column), config), formatsCache);
  }

  public static FormatterKey<Formatter> getEraTimestampKey(@Nullable GridColumn column, @Nullable ObjectFormatterConfig config, @NotNull FormatsCache formatsCache) {
    return add(add(add(new FormatterKey<>(ERA_TIMESTAMP), column), config), formatsCache);
  }

  public static FormatterKey<CompositeFormatter> getZonedTimeKey(@Nullable GridColumn column, @Nullable ObjectFormatterConfig config, @NotNull FormatsCache formatsCache) {
    return add(add(add(new FormatterKey<>(ZONED_TIME_FORMATTER_KEY), column), config), formatsCache);
  }

  public static FormatterKey<CompositeFormatter> getZonedTimestampKey(@Nullable GridColumn column, @Nullable ObjectFormatterConfig config, @NotNull FormatsCache formatsCache) {
    return add(add(add(new FormatterKey<>(ZONED_TIMESTAMP_FORMATTER_KEY), column), config), formatsCache);
  }

  public static FormatterKey<Formatter> getDateKey(
    @Nullable GridColumn column,
    @Nullable ObjectFormatterConfig config,
    @NotNull FormatsCache formatsCache
  ) {
    return add(add(add(new FormatterKey<>(DATE_FORMATTER_KEY), column), config), formatsCache);
  }

  public static FormatterKey<NumberFormatter> getDecimalKey(
    @Nullable GridColumn column,
    @Nullable ObjectFormatterConfig config
  ) {
    return add(add(new FormatterKey<>(DECIMAL_FORMATTER_KEY), column), config);
  }

  @SuppressWarnings("unchecked")
  public <T> T create(@NotNull FormatterKey<T> key) {
    T res = (T)(key.getName().equals(INT_FORMATTER_KEY)        ? newIntFormat(getFormaterConfig(key)) :
              key.getName().equals(FLOAT_FORMATTER_KEY)        ? newFloatFormat(getFormaterConfig(key)) :
              key.getName().equals(DOUBLE_FORMATTER_KEY)       ? newDoubleFormat(getFormaterConfig(key)) :
              key.getName().equals(LONG_FORMATTER_KEY)         ? newLongFormat(getFormaterConfig(key)) :
              key.getName().equals(BIG_INT_FORMATTER_KEY)      ? newBigIntFormat(getFormaterConfig(key)) :
              key.getName().equals(DECIMAL_FORMATTER_KEY)      ? newDecimalFormat(getColumn(key), getFormaterConfig(key)) :
              key.getName().equals(BIG_DECIMAL_FORMATTER_KEY)  ? newBigDecimalFormat(getFormaterConfig(key)) :
              key.getName().equals(DECIMAL_WITH_PRIORITY_TYPE) ? getDecimalFormatWithPriorityType(getInt(key), getFormaterConfig(key)) :
              key.getName().equals(TIMESTAMP_WITH_SCALE_TYPE)  ? newTimestampFormat(null, getInt(key), resolver(null), null, getFormatsCache(key)) :
              key.getName().equals(TIMESTAMP_FORMATTER_KEY)    ? newTimestampFormat(getColumn(key), calculateScale(getColumn(key), getFormaterConfig(key)), resolver(getColumn(key)), getFormaterConfig(key), getFormatsCache(key)) :
              key.getName().equals(SHORT_ERA_ZONED_TIMESTAMP)  ? newShortEraZonedTimestampFormat(getColumn(key), getFormaterConfig(key), getFormatsCache(key)) :
              key.getName().equals(ERA_TIMESTAMP)              ? newEraTimestampFormat(getColumn(key), getFormaterConfig(key), getFormatsCache(key)) :
              key.getName().equals(TIME_FORMATTER_KEY)         ? newTimeFormat(getFormaterConfig(key), calculateScale(getColumn(key), getFormaterConfig(key))) :
              key.is(SIMPLE_TIMESTAMP_FORMATTER_KEY) ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss G Z", Locale.US) :
              key.is(SHORT_TIMESTAMP_FORMATTER_KEY)            ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss G", Locale.US) :
              key.is(TIMESTAMP_WITH_MILLI_FORMATTER_KEY)       ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss G SSS", Locale.US) :
              key.is(SIMPLE_DATE_FORMATTER_KEY) ? new SimpleDateFormat("yyyy-MM-dd G", Locale.US) :
              key.is(LOCAL_DATE_WITH_MILLI_FORMATTER_KEY)      ? DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss G SSS", Locale.US) :
              key.is(LOCAL_DATE_FORMATTER_KEY)                 ? DateTimeFormatter.ofPattern("yyyy-MM-dd G", Locale.US) :
              key.is(OFFSET_DATE_TIME_FORMATTER_KEY)           ? DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss G ZZZ", Locale.US) :
              key.getName().equals(ZONED_TIME_FORMATTER_KEY)   ? newZonedTimeFormat(getColumn(key), getFormaterConfig(key), getFormatsCache(key)) :
              key.getName().equals(ZONED_TIMESTAMP_FORMATTER_KEY) ? newZonedTimestampFormat(getColumn(key), getFormaterConfig(key), getFormatsCache(key)) :
              key.getName().equals(DATE_FORMATTER_KEY)         ? newDateFormatter(getColumn(key), getFormaterConfig(key), getFormatsCache(key)) :
              null
    );
    if (res != null) return res;
    throw new IllegalArgumentException("Unknown key: " + key.getName());
  }

  protected static @NotNull <T> FormatsCache getFormatsCache(@NotNull FormatterKey<T> key) {
    return Objects.requireNonNull(key.getUserData(FORMATTER_KEY_FORMATS_CACHE_KEY));
  }

  private static <T> int getInt(FormatterKey<T> key) {
    return Objects.requireNonNull(key.getUserData(FORMATTER_KEY_INT_KEY));
  }

  private static @Nullable <T> GridColumn getColumn(FormatterKey<T> key) {
    return key.getUserData(FORMATTER_KEY_GRID_COLUMN_KEY);
  }

  private static @Nullable <T> ObjectFormatterConfig getFormaterConfig(FormatterKey<T> key) {
    return key.getUserData(FORMATTER_KEY_CONFIG_KEY);
  }

  protected @NotNull NumberFormatter newDecimalFormat(
    @Nullable GridColumn column,
    @Nullable ObjectFormatterConfig config
  ) {
    return configureDecimalFormat(new DecimalFormat(), config);
  }

  protected static @NotNull NumberFormatter newBigDecimalFormat(@Nullable ObjectFormatterConfig config) {
    NumberFormatter formatter = configureDecimalFormat(new DecimalFormat(), config);
    formatter.setParseBigDecimal(true);
    formatter.setMaximumFractionDigits(MAX_FRACTION_DIGITS);
    return formatter;
  }

  protected @NotNull NumberFormatter newFloatFormat(@Nullable ObjectFormatterConfig config) {
    return configureDecimalFormat(new DecimalFormat() {
      @Override
      public Number parse(String text, ParsePosition pos) {
        Number n = super.parse(text, pos);
        return n != null ? n.floatValue() : null;
      }

      @Override
      public StringBuffer format(double number, StringBuffer result, FieldPosition fieldPosition) {
        // The original value was converted from Float, so we convert it back, obtain a string representation of it,
        // and parse a double value from this representation. Thus we obtain a more precise value from a human point of view.
        // For instance Float.toString(0.99f) == "0.99", whereas Double.toString(0.99f) == "0.9900000095367432".
        number = Double.valueOf(Float.toString((float)number)).doubleValue();
        return super.format(number, result, fieldPosition);
      }
    }, config);
  }

  /**
   * This formatter may return double, int, long, NaN, Infinity
   */
  private @NotNull NumberFormatter newDoubleFormat(@Nullable ObjectFormatterConfig config) {
    return configureDecimalFormat(new DecimalFormat() {
      @Override
      public Number parse(String text, ParsePosition pos) {
        Number n = super.parse(text, pos);
        return n == null ? null :
               ObjectUtils.notNull(asInt(n), n); // use int if number fits in int
      }
    }, config);
  }

  private @NotNull NumberFormatter newIntFormat(@Nullable ObjectFormatterConfig config) {
    DecimalFormat format = new DecimalFormat() {
      @Override
      public Number parse(String text, ParsePosition pos) {
        Number n = super.parse(text, pos);
        return n != null ? n.intValue() : null;
      }
    };
    NumberFormatter formatter = configureDecimalFormat(format, config);
    formatter.setParseIntegerOnly(true);
    return formatter;
  }

  /**
   * This formatter may return long, NaN, Infinity
   */
  private @NotNull NumberFormatter newLongFormat(@Nullable ObjectFormatterConfig config) {
    NumberFormatter formatter = configureDecimalFormat(new DecimalFormat() {
      @Override
      public Number parse(String text, ParsePosition pos) {
        Number n = super.parse(text, pos);
        return n != null ? n.longValue() : null;
      }
    }, config);
    formatter.setParseIntegerOnly(true);
    return formatter;
  }

  /**
   * This formatter may return BigDecimal that contains integer value, NaN, Infinity
   */
  private @NotNull NumberFormatter newBigIntFormat(@Nullable ObjectFormatterConfig config) {
    NumberFormatter format = newDecimalFormat(null, config);
    format.setParseIntegerOnly(true);
    format.setParseBigDecimal(true);
    return format;
  }

  private static NumberFormatter getDecimalFormatWithPriorityType(int priorityType, @Nullable ObjectFormatterConfig config) {
    NumberFormatter formatter = configureDecimalFormat(new DecimalFormat() {
      @Override
      public Number parse(String text, ParsePosition pos) {
        Number n = super.parse(text, pos);
        return n == null ? null :
               ObjectUtils.notNull(
                 priorityType == Types.INTEGER ? asInt(n) : null, ObjectUtils.notNull(
                   priorityType == Types.INTEGER || priorityType == Types.BIGINT ? asLong(n) : null, ObjectUtils.notNull(
                     priorityType == Types.DOUBLE ? asDouble(n) : null,
                     n)));
      }
    }, config);
    formatter.setParseBigDecimal(true);
    formatter.setMaximumFractionDigits(MAX_FRACTION_DIGITS);
    return formatter;
  }

  private static @Nullable Integer asInt(Number n) {
    try {
      return n instanceof Long && n.intValue() == (long)n ? (Integer)n.intValue() :
             n instanceof BigDecimal ? (Integer)((BigDecimal)n).intValueExact() : null;
    }
    catch (ArithmeticException ignored) {
      return null;
    }
  }

  private static @Nullable Long asLong(Number n) {
    try {
      return n instanceof Long ? (Long)n :
             n instanceof BigDecimal ? ((BigDecimal)n).longValueExact() : null;
    }
    catch (ArithmeticException ignored) {
      return null;
    }
  }

  private static Double asDouble(Number n) {
    return n instanceof Double
           ? (Double)n
           : n instanceof BigDecimal && n.equals(BigDecimal.valueOf(n.doubleValue())) ? n.doubleValue() : null;
  }

  private @NotNull Formatter newZonedTimeFormat(
    @Nullable GridColumn column,
    @Nullable ObjectFormatterConfig config,
    @NotNull FormatsCache formatsCache
  ) {
    Formatter customFormatter = newCustomDateTimeFormat(config, DataGridSettings::getEffectiveZonedTimePattern, newZonedTimeDelegate(config));
    if (customFormatter != null) return customFormatter;
    return newZonedTimeFormat(newZonedTimeDelegate(config), calculateScale(column, config), config);
  }

  protected @NotNull ZonedTimeDelegate<?> newZonedTimeDelegate(@Nullable ObjectFormatterConfig config) {
    return new ZonedTimeDelegate<>() {
      @Override
      protected TemporalAccessor toTemporalAccessor(@NotNull Object value) {
        if (!(value instanceof OffsetTime time)) throw new IllegalArgumentException("Value must be of type OffsetTime");
        return adjustOffset(time, getZoneId(config));
      }

      @Override
      protected OffsetTime createFromTemporal(@NotNull TemporalAccessor value) {
        return adjustOffset(OffsetTime.from(value), getZoneId(config));
      }
    };
  }

  private @NotNull Formatter newTimeFormat(
    @Nullable ObjectFormatterConfig config,
    int scale
  ) {
    return newTimeFormat(config, scale, new TimeDelegate(config));
  }

  public @NotNull Formatter newTimeFormat(
    @Nullable ObjectFormatterConfig config,
    int scale,
    @NotNull DateAndTimeFormatterDelegate<?, ?> delegate
  ) {
    Formatter customFormatter = newCustomDateTimeFormat(config, DataGridSettings::getEffectiveTimePattern, delegate);
    if (customFormatter != null) return customFormatter;

    Builders builders = new Builders();
    appendTime(builders);
    if (scale > 0) {
      appendFraction(builders, scale);
    }
    return new DateAndTimeFormatter<>(builders.sb.toString(), toFormatter(builders.fb), delegate);
  }

  protected @NotNull Formatter newTimestampFormat(
    @Nullable GridColumn column,
    int scale,
    @NotNull BoundaryValueResolver resolver,
    @Nullable ObjectFormatterConfig config,
    @NotNull FormatsCache formatsCache
  ) {
    Formatter customFormatter = newCustomTimestampFormat(config, formatsCache);
    if (customFormatter != null) return customFormatter;
    return newIntrinsicTimestampFormat(column, scale, resolver, config, formatsCache);
  }

  protected @NotNull Formatter newIntrinsicTimestampFormat(
    @Nullable GridColumn column,
    int scale,
    @NotNull BoundaryValueResolver resolver,
    @Nullable ObjectFormatterConfig config,
    @NotNull FormatsCache formatsCache
  ) {
    return checkInfinity(newTimestampFormat(config, scale, formatsCache), resolver);
  }

  private @NotNull DateAndTimeFormatter<Timestamp, OffsetDateTime> newTimestampFormat(
    @Nullable ObjectFormatterConfig config,
    int scale,
    @NotNull FormatsCache formatsCache
  ) {
    Builders builders = new Builders();
    appendDate(builders);
    builders.sb.append(' ');
    builders.fb.appendPattern("[ ]");
    appendTime(builders);
    appendFraction(builders, scale);
    return new DateAndTimeFormatter<>(builders.sb.toString(), toFormatter(builders.fb), new TimestampDelegate(formatsCache, this, config));
  }

  protected @Nullable Formatter newCustomTimestampFormat(
    @Nullable ObjectFormatterConfig config,
    @NotNull FormatsCache formatsCache
  ) {
    return newCustomDateTimeFormat(config, DataGridSettings::getEffectiveDateTimePattern, new TimestampDelegate(formatsCache, this, config));
  }

  protected @Nullable Formatter newCustomDateTimeFormat(
    @Nullable ObjectFormatterConfig config,
    @NotNull Function<? super DataGridSettings, String> patternGetter,
    @NotNull DateAndTimeFormatterDelegate<?, ?> delegate
  ) {
    if (config == null || config.getMode() != ObjectFormatterMode.DISPLAY) return null;
    DataGridSettings settings = config.getSettings();
    String pattern = settings != null ? patternGetter.fun(settings) : null;
    if (pattern == null) return null;
    return new DateAndTimeFormatter<>(
      pattern,
      toFormatter(new DateTimeFormatterBuilder().appendPattern(pattern)),
      delegate
    );
  }

  private @NotNull CompositeFormatter newZonedTimestampFormat(
    @Nullable GridColumn column,
    @Nullable ObjectFormatterConfig config,
    @NotNull FormatsCache formatsCache
  ) {
    return newZonedTimestampFormat(resolver(column), calculateScale(column, config), formatsCache, config);
  }

  public @NotNull CompositeFormatter newZonedTimestampFormat(
    @NotNull BoundaryValueResolver resolver,
    int scale,
    @NotNull FormatsCache formatsCache,
    @Nullable ObjectFormatterConfig config
  ) {
    return newZonedTimestampFormat(new ZonedTimestampDelegate<OffsetDateTime>() {
      @Override
      protected OffsetDateTime toTemporalAccessor(@NotNull Object value) {
        if (!(value instanceof OffsetDateTime)) throw new IllegalArgumentException("Value must be type of OffsetDateTime");
        return (OffsetDateTime)value;
      }

      @Override
      protected OffsetDateTime createFromTemporal(@NotNull OffsetDateTime value) {
        return value;
      }
    }, resolver, scale, config);
  }

  public @NotNull Formatter newIsoFormatter(@NotNull DateAndTimeFormatterDelegate<?, ?> delegate) {
    return new DateAndTimeFormatter<>("ISO-8601", DateTimeFormatter.ISO_DATE_TIME, delegate);
  }

  public @NotNull <T, V extends TemporalAccessor> CompositeFormatter newZonedTimestampFormat(
    @NotNull DateAndTimeFormatterDelegate<T, V> delegate,
    @NotNull BoundaryValueResolver resolver,
    int scale,
    @Nullable ObjectFormatterConfig config
  ) {
    Builders builders = createBuildersForTimestamp(scale);
    List<DateTimeFormatter> formatters = appendTimeZone(builders, config);
    String pattern = builders.sb.toString();
    List<Formatter> mapped = ContainerUtil.map(formatters, f -> eraFormatter(pattern, f, delegate, resolver, false));
    return new CompositeFormatter(DataGridBundle.message("expected.timestamp.with.time.zone"), mapped);
  }

  protected @NotNull Formatter newEraTimestampFormat(
    @Nullable GridColumn column,
    @Nullable ObjectFormatterConfig config,
    @NotNull FormatsCache formatsCache
  ) {
    Builders builders = createBuildersForTimestamp(calculateScale(column, config));
    return checkInfinity(eraFormatter(
      builders.sb.toString(),
      toFormatter(builders.fb),
      new TimestampDelegate(formatsCache, this, config),
      resolver(column),
      omitEmptyTime(column)
    ), resolver(column));
  }

  protected boolean omitEmptyTime(@Nullable GridColumn column) {
    return false;
  }

  protected @NotNull BoundaryValueResolver resolver(@Nullable GridColumn column) {
    return BoundaryValueResolver.ALWAYS_NULL;
  }

  private @NotNull Formatter newShortEraZonedTimestampFormat(
    @Nullable GridColumn column,
    @Nullable ObjectFormatterConfig config,
    @NotNull FormatsCache formatsCache
  ) {
    Formatter customFormatter = newCustomDateTimeFormat(
      config,
      DataGridSettings::getEffectiveZonedDateTimePattern,
      newShortZonedTimestampDelegate(config)
    );
    if (customFormatter != null) return customFormatter;

    return checkInfinity(newShortEraZonedTimestampFormat(calculateScale(column, config), config, formatsCache), resolver(column));
  }

  protected int calculateScale(@Nullable GridColumn column, @Nullable ObjectFormatterConfig config) {
    return 0;
  }

  private @NotNull Formatter newShortEraZonedTimestampFormat(int scale, @Nullable ObjectFormatterConfig config, @NotNull FormatsCache formatsCache) {
    return newShortZonedTimestampFormat(scale, (formatter, pattern) -> eraFormatter(
      pattern,
      formatter,
      newShortZonedTimestampDelegate(config),
      BoundaryValueResolver.ALWAYS_NULL,
      false
    ), config);
  }

  protected @NotNull ShortZonedTimestampDelegate<?> newShortZonedTimestampDelegate(@Nullable ObjectFormatterConfig config) {
    return new ShortZonedTimestampDelegate<>() {
      @Override
      protected Temporal toTemporalAccessor(@NotNull Object value) {
        if (!(value instanceof OffsetDateTime dateTime)) throw new IllegalArgumentException("Value must be of type OffsetDateTime");
        return adjustTimeZone(dateTime, getZoneId(config));
      }

      @Override
      protected Temporal createFromTemporal(@NotNull TemporalAccessor value) {
        return toOffsetDateTime(value, getZoneId(config));
      }
    };
  }

  protected @NotNull Formatter newEraDateFormatter(
    @Nullable ObjectFormatterConfig config,
    @NotNull FormatsCache formatsCache,
    @NotNull BoundaryValueResolver resolver
  ) {
    return newEraDateFormatter(config, new DateDelegate(formatsCache, this), resolver);
  }

  public @Nullable Formatter newGeoWrapperFormatter(GridColumn column, ObjectFormatter formatter ) {
    return null; // will be overridden in DatabaseFormatterCreator
  }

  public @NotNull Formatter newEraDateFormatter(
    @Nullable ObjectFormatterConfig config,
    @NotNull DateAndTimeFormatterDelegate<?, ?> delegate,
    @NotNull BoundaryValueResolver resolver
  ) {
    Formatter formatter = newCustomDateTimeFormat(config, DataGridSettings::getEffectiveDatePattern, delegate);
    if (formatter != null) return formatter;

    Builders builders = new Builders();
    appendDate(builders);
    String pattern = builders.sb.toString();
    DateAndTimeFormatter<?, ?> regular = new DateAndTimeFormatter<>(pattern, toFormatter(builders.fb), delegate, resolver, null);
    builders.fb.appendPattern(ERA_PATTERN);
    DateAndTimeFormatter<?, ?> era = new DateAndTimeFormatter<>(pattern, toFormatter(builders.fb), delegate, resolver, null);
    return new EraDateAndTimeFormatter(regular, era);
  }

  protected @NotNull Formatter newDateFormatter(@Nullable GridColumn column, @Nullable ObjectFormatterConfig config, @NotNull FormatsCache formatsCache) {
    DateDelegate delegate = new DateDelegate(formatsCache, this);

    Formatter formatter = newCustomDateTimeFormat(config, DataGridSettings::getEffectiveDatePattern, delegate);
    if (formatter != null) return formatter;

    Builders builders = new Builders();
    appendDate(builders);
    formatter = new DateAndTimeFormatter<>(builders.sb.toString(), toFormatter(builders.fb), delegate);
    return checkInfinity(formatter, resolver(column));
  }

  protected @NotNull Formatter newDateFormatWithTime(
    @Nullable GridColumn column,
    @Nullable ObjectFormatterConfig config,
    @NotNull FormatsCache formatsCache
  ) {
    Builders builders = new Builders();
    appendDate(builders);
    builders.sb.append('T');
    builders.fb.appendLiteral('T');
    appendTime(builders);
    appendFraction(builders, 3);
    builders.sb.append('Z');
    builders.fb.appendLiteral('Z');
    DateAndTimeFormatter<Date, LocalDateTime> formatter =
      new DateAndTimeFormatter<>(builders.sb.toString(), toFormatter(builders.fb), new DateToLocalDateTimeDelegate(formatsCache, this,
                                                                                                                   config));
    return checkInfinity(formatter, resolver(column));
  }

  public @NotNull <T, V extends TemporalAccessor> CompositeFormatter newZonedTimeFormat(
    @NotNull DateAndTimeFormatterDelegate<T, V> delegate,
    int scale,
    @Nullable ObjectFormatterConfig config
  ) {
    Builders builders = new Builders();
    appendTime(builders);
    appendFraction(builders, scale);
    List<DateTimeFormatter> formatters = appendTimeZone(builders, config);
    String pattern = builders.sb.toString();
    List<Formatter> mapped = ContainerUtil.map(formatters, f -> new DateAndTimeFormatter<>(pattern, f, delegate));
    return new CompositeFormatter(DataGridBundle.message("unexpected.data.format"), mapped);
  }

  protected static @NotNull Formatter newShortZonedTimestampFormat(
    int scale,
    @NotNull PairFunction<DateTimeFormatter, String, Formatter> mapper,
    @Nullable ObjectFormatterConfig config
  ) {
    Builders builders = createBuildersForTimestamp(scale);
    List<DateTimeFormatter> formatters = appendTimeZone(builders, config);
    String pattern = builders.sb.toString();
    return new CompositeFormatter(DataGridBundle.message("unexpected.data.format"),
                                  ContainerUtil.map(formatters, formatter -> mapper.fun(formatter, pattern))
    );
  }

  private static void appendTime(@NotNull Builders builders) {
    builders.sb.append("HH:mm:ss");
    appendTime(builders.fb);
  }

  public static DateTimeFormatterBuilder appendTime(@NotNull DateTimeFormatterBuilder builder) {
    return builder.parseLenient()
      .optionalStart().appendValue(ChronoField.HOUR_OF_DAY, 2).optionalEnd()
      .optionalStart().appendLiteral(":").optionalEnd()
      .optionalStart().appendValue(ChronoField.MINUTE_OF_HOUR, 2).optionalEnd()
      .optionalStart().appendLiteral(":").optionalEnd()
      .optionalStart().appendValue(ChronoField.SECOND_OF_MINUTE, 2).optionalEnd()
      .parseStrict();
  }

  private static @NotNull List<DateTimeFormatter> appendTimeZone(
    @NotNull Builders builders,
    @Nullable ObjectFormatterConfig config
  ) {
    String hourPattern = config != null && config.getMode() == ObjectFormatterMode.NORMALIZE ? "H" : "HH";
    return withZoneOffsets(
      builders,
      builder -> builder.appendOffset("+" + hourPattern + ":MM:ss", "+00:00"),
      builder -> builder.appendOffset("+" + hourPattern + ":mm", "+00:00"),
      builder -> builder.appendOffset("+HHmm", "+0000"),
      builder -> builder.appendZoneOrOffsetId()
    );
  }

  private static @NotNull List<DateTimeFormatter> withZoneOffsets(@NotNull Builders builders,
                                                                  Consumer<DateTimeFormatterBuilder> @NotNull ... consumers) {
    return ContainerUtil.append(
      ContainerUtil.map(consumers, c -> {
        DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder()
          .append(toFormatter(builders.fb))
          .appendPattern("[ ]");
        c.consume(builder);
        return toFormatter(builder);
      }),
      toFormatter(builders.fb));
  }

  private static void appendDate(@NotNull Builders builders) {
    builders.sb.append(DATE_PATTERN);
    appendDate(builders.fb);
  }

  public static DateTimeFormatterBuilder appendDate(@NotNull DateTimeFormatterBuilder builder) {
    return builder.parseLenient()
      .appendValue(ChronoField.YEAR_OF_ERA, 4, 7, SignStyle.NEVER)
      .appendLiteral('-')
      .appendValue(ChronoField.MONTH_OF_YEAR, 2)
      .appendLiteral('-')
      .appendValue(ChronoField.DAY_OF_MONTH, 2)
      .parseStrict();
  }

  private static void appendFraction(@NotNull Builders builders, int scale) {
    builders.sb.append(scale > 0 ? "." : "");
    for (int i = 0; i < scale; i++) {
      builders.sb.append('f');
    }
    if (scale > 0) builders.fb.parseLenient().appendFraction(ChronoField.NANO_OF_SECOND, scale, scale, true).parseStrict();
  }

  protected @NotNull <T, V extends TemporalAccessor> EraDateAndTimeFormatter eraFormatter(@NotNull String regular,
                                                                                          @NotNull DateTimeFormatter regularFormatter,
                                                                                          @NotNull DateAndTimeFormatterDelegate<T, V> delegate,
                                                                                          @NotNull BoundaryValueResolver resolver,
                                                                                          boolean omitEmptyTime) {
    DateTimeFormatter era = toFormatter(new DateTimeFormatterBuilder().append(regularFormatter).appendPattern(ERA_PATTERN));
    Builders dateBuilders = omitEmptyTime ? new Builders() : null;
    if (dateBuilders != null) appendDate(dateBuilders);
    return new EraDateAndTimeFormatter(
      new DateAndTimeFormatter<>(regular, regularFormatter, delegate, resolver, dateBuilders == null ? null : toFormatter(dateBuilders.fb)),
      new DateAndTimeFormatter<>(regular, era, delegate, resolver, dateBuilders == null ? null : toFormatter(dateBuilders.fb))
    );
  }

  public static @NotNull DateTimeFormatter toFormatter(@NotNull DateTimeFormatterBuilder builder) {
    return builder.toFormatter(Locale.US);
  }

  private static @NotNull Builders createBuildersForTimestamp(int scale) {
    Builders builders = new Builders();
    appendDate(builders);
    builders.sb.append(' ');
    builders.fb.appendPattern("[ ]");
    appendTime(builders);
    appendFraction(builders, scale);
    return builders;
  }

  private static @NotNull NumberFormatter configureDecimalFormat(
    @NotNull DecimalFormat format,
    @Nullable ObjectFormatterConfig config
  ) {
    DecimalFormatSymbols symbols = format.getDecimalFormatSymbols();
    DataGridSettings settings = config != null ? config.getSettings() : null;
    String pattern = settings != null ? settings.getEffectiveNumberPattern() : null;
    if (settings != null) {
      format.setGroupingUsed(settings.isNumberGroupingEnabled());
      symbols.setGroupingSeparator(settings.getNumberGroupingSeparator());
      symbols.setDecimalSeparator(settings.getDecimalSeparator());
      symbols.setNaN(settings.getNan());
      symbols.setInfinity(settings.getInfinity());
    }
    else {
      format.setGroupingUsed(false); // no thousand separator
      symbols.setDecimalSeparator('.'); // no ',' instead of '.'
      symbols.setNaN("NaN");
      symbols.setInfinity("Infinity");
    }
    symbols.setMinusSign('-');
    format.setDecimalFormatSymbols(symbols);
    if (pattern != null) {
      format.applyLocalizedPattern(pattern);
    }
    return new NumberFormatter(format);
  }

  private static final class Builders {
    private final StringBuilder sb;
    private final DateTimeFormatterBuilder fb;

    private Builders() {
      sb = new StringBuilder();
      fb = new DateTimeFormatterBuilder();
    }
  }

  private static final Key<FormatsCache> FORMATTER_KEY_FORMATS_CACHE_KEY = new Key<>("FORMATTER_KEY_FORMATS_CACHE_KEY");
  private static final Key<Integer> FORMATTER_KEY_INT_KEY = new Key<>("FORMATTER_KEY_INT_KEY");
  private static final Key<GridColumn> FORMATTER_KEY_GRID_COLUMN_KEY = new Key<>("FORMATTER_KEY_GRID_COLUMN_KEY");
  private static final Key<ObjectFormatterConfig> FORMATTER_KEY_CONFIG_KEY = new Key<>("FORMATTER_KEY_CONFIG_KEY");

  public static class FormatterKey<T> extends UserDataHolderBase {
    private final String myName;

    public FormatterKey(@NotNull String name) {
      myName = name;
    }

    public @NotNull String getName() {
      return myName;
    }

    public boolean is(FormatterKey<?> key) {
      return myName.equals(key.myName);
    }
  }

  private static <T> FormatterKey<T> add(@NotNull FormatterKey<T> key, @Nullable GridColumn column) {
    key.putUserData(FORMATTER_KEY_GRID_COLUMN_KEY, column);
    return key;
  }

  private static <T> FormatterKey<T> add(@NotNull FormatterKey<T> key, @Nullable ObjectFormatterConfig config) {
    key.putUserData(FORMATTER_KEY_CONFIG_KEY, config);
    return key;
  }

  private static <T> FormatterKey<T> add(@NotNull FormatterKey<T> key, int value) {
    key.putUserData(FORMATTER_KEY_INT_KEY, value);
    return key;
  }

  private static <T> FormatterKey<T> add(@NotNull FormatterKey<T> key, @NotNull FormatsCache formatsCache) {
    key.putUserData(FORMATTER_KEY_FORMATS_CACHE_KEY, formatsCache);
    return key;
  }

  private static Formatter checkInfinity(@NotNull Formatter format, @NotNull BoundaryValueResolver resolver) {
    return new Formatter() {
      @Override
      public Object parse(@NotNull String value) throws ParseException {
        return format.parse(value);
      }

      @Override
      public Object parse(@NotNull String value, ParsePosition position) {
        return format.parse(value, position);
      }

      @Override
      public String format(Object value) {
        String infinityString = resolver.getInfinityString(value);
        return infinityString != null ? infinityString : format.format(value);
      }

      @Override
      public String toString() {
        return format.toString();
      }
    };
  }
}
