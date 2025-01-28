package com.intellij.database.run.ui.grid.editors;

import com.intellij.database.datagrid.CoreGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.extractors.FormatterCreator;
import com.intellij.database.extractors.ObjectFormatterConfig;
import com.intellij.database.settings.DataGridSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.openapi.util.Pair;
import kotlin.Triple;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import static com.intellij.database.extractors.FormatterCreator.*;

/**
 * SimpleDateFormat is not thread-safe therefore FormatsCache is not thread-safe too
 */
public class FormatsCache {
  // TODO: Get rid of SimpleDateFormat
  // Until then all users should synchronize on the same lock (currently DataGridFormattersUtilCore.class)
  public static final FormatProvider<String, SimpleDateFormat> SIMPLE_TIMESTAMP_FORMAT_PROVIDER = new BaseFormatProvider<>("SIMPLE_TIMESTAMP", SIMPLE_TIMESTAMP_FORMATTER_KEY);
  public static final FormatProvider<String, SimpleDateFormat> SHORT_TIMESTAMP_FORMAT_PROVIDER = new BaseFormatProvider<>("SHORT_TIMESTAMP", SHORT_TIMESTAMP_FORMATTER_KEY);
  public static final FormatProvider<String, SimpleDateFormat> TIMESTAMP_WITH_MILLI_FORMAT_PROVIDER = new BaseFormatProvider<>("TIMESTAMP_WITH_MILLI", TIMESTAMP_WITH_MILLI_FORMATTER_KEY);
  public static final FormatProvider<String, SimpleDateFormat> SIMPLE_DATE_FORMAT_PROVIDER = new BaseFormatProvider<>("SIMPLE_DATE", SIMPLE_DATE_FORMATTER_KEY);

  public static final FormatProvider<String, DateTimeFormatter> LOCAL_DATE_WITH_MILLI_FORMAT_PROVIDER = new BaseFormatProvider<>("LOCAL_DATE_WITH_MILLI", LOCAL_DATE_WITH_MILLI_FORMATTER_KEY);
  public static final FormatProvider<String, DateTimeFormatter> LOCAL_DATE_FORMAT_PROVIDER = new BaseFormatProvider<>("LOCAL_DATE", LOCAL_DATE_FORMATTER_KEY);
  public static final FormatProvider<String, DateTimeFormatter> OFFSET_DATE_TIME_FORMAT_PROVIDER = new BaseFormatProvider<>("OFFSET_DATE_TIME", OFFSET_DATE_TIME_FORMATTER_KEY);

  private static final Logger LOG = Logger.getInstance(FormatsCache.class);
  private static final NotNullLazyKey<FormatsCache, CoreGrid<?, ?>> FORMATS_CACHE_KEY = NotNullLazyKey.createLazyKey("FORMATS_CACHE_KEY", FormatsCache::createCache);

  private final Map<Object, Object> myCache = new ConcurrentHashMap<>();

  private static @NotNull FormatsCache createCache(CoreGrid<?, ?> grid) {
    FormatsCache cache = new FormatsCache();
    if (!Disposer.isDisposed(grid)) {
      ApplicationManager.getApplication().getMessageBus().connect(grid).subscribe(DataGridSettings.TOPIC, () -> {
        cache.myCache.clear();
      });
    }
    return cache;
  }

  public static @NotNull FormatsCache get(@NotNull CoreGrid<?, ?> grid) {
    return FORMATS_CACHE_KEY.getValue(grid);
  }

  public @NotNull <K, T> T get(@NotNull FormatProvider<K, T> provider, @NotNull FormatterCreator formatterCreator) {
    return get(provider, formatterCreator, true);
  }

  public @NotNull <K, T> T get(@NotNull FormatProvider<K, T> provider, @NotNull FormatterCreator formatterCreator, boolean cache) {
    K key = provider.getCacheKey();
    if (cache) {
      Object res = myCache.get(key);
      if (res != null) { //noinspection unchecked
        return (T)res;
      }
    }
    T r = provider.createFormatter(this, formatterCreator);
    if (cache) myCache.put(key, r);
    int cacheSize = myCache.size();
    if (cacheSize > 0 &&
        (cacheSize % 10_000 == 0 && cacheSize < 100_000 ||
         cacheSize % 100_000 == 0)) {
      LOG.error("Formats cash size seems to be too big: " + cacheSize);
    }
    return r;
  }

  public static FormatProvider<Pair<String, ObjectFormatterConfig>, NumberFormatter> getIntFormatProvider(
    @Nullable ObjectFormatterConfig config
  ) {
    return new BaseFormatProvider<>(
      new Pair<>("INT", config),
      (formatsCache, formatterCreator) -> formatterCreator.create(getIntKey(config))
    );
  }

  public static FormatProvider<Pair<String, ObjectFormatterConfig>, NumberFormatter> getLongFormatProvider(
    @Nullable ObjectFormatterConfig config
  ) {
    return new BaseFormatProvider<>(
      new Pair<>("LONG", config),
      (formatsCache, formatterCreator) -> formatterCreator.create(getLongKey(config))
    );
  }

  public static FormatProvider<Pair<String, ObjectFormatterConfig>, NumberFormatter> getBigIntFormatProvider(
    @Nullable ObjectFormatterConfig config
  ) {
    return new BaseFormatProvider<>(
      new Pair<>("BIG_INT", config),
      (formatsCache, formatterCreator) -> formatterCreator.create(getBigIntKey(config))
    );
  }

  public static FormatProvider<Pair<String, ObjectFormatterConfig>, NumberFormatter> getFloatFormatProvider(
    @Nullable ObjectFormatterConfig config
  ) {
    return new BaseFormatProvider<>(
      new Pair<>("FLOAT", config),
      (formatsCache, formatterCreator) -> formatterCreator.create(getFloatKey(config))
    );
  }

  public static FormatProvider<Pair<String, ObjectFormatterConfig>, NumberFormatter> getDoubleFormatProvider(
    @Nullable ObjectFormatterConfig config
  ) {
    return new BaseFormatProvider<>(
      new Pair<>("DOUBLE", config),
      (formatsCache, formatterCreator) -> formatterCreator.create(getDoubleKey(config))
    );
  }

  public static FormatProvider<Triple<String, GridColumn, ObjectFormatterConfig>, Formatter> getZonedTimeFormatProvider(
    @Nullable GridColumn column,
    @Nullable ObjectFormatterConfig config
  ) {
    return new BaseFormatProvider<>(new Triple<>("ZONED_TIME", column, config), (formatsCache, formatterCreator) -> formatterCreator.create(getZonedTimeKey(column, config, formatsCache)));
  }

  public static FormatProvider<Triple<String, GridColumn, ObjectFormatterConfig>, CompositeFormatter> getZonedTimestampFormatProvider(
    @Nullable GridColumn column,
    @Nullable ObjectFormatterConfig config
  ) {
    return new BaseFormatProvider<>(new Triple<>("ZONED_TIMESTAMP", column, config), (formatsCache, formatterCreator) -> formatterCreator.create(getZonedTimestampKey(column, config, formatsCache)));
  }

  public static FormatProvider<Triple<String, GridColumn, ObjectFormatterConfig>, Formatter> getShortEraZonedTimestampFormatProvider(
    @Nullable GridColumn column,
    @Nullable ObjectFormatterConfig config
  ) {
    return new BaseFormatProvider<>(new Triple<>("SHORT_ERA_ZONED_TIMESTAMP", column, config), (formatsCache, formatterCreator) -> formatterCreator.create(getShortEraZonedTimestampKey(column, config, formatsCache)));
  }

  public static FormatProvider<Triple<String, Integer, ObjectFormatterConfig>, NumberFormatter> getBigDecimalWithPriorityTypeFormatProvider(
    int type,
    @Nullable ObjectFormatterConfig config
  ) {
    return new BaseFormatProvider<>(new Triple<>("DECIMAL_WITH_PRIORITY_TYPE", type, config), (formatsCache, formatterCreator) -> formatterCreator.create(getDecimalWithPriorityTypeKey(type, config)));
  }

  public static FormatProvider<Triple<String, GridColumn, ObjectFormatterConfig>, Formatter> getDateFormatProvider(
    @Nullable GridColumn column,
    @Nullable ObjectFormatterConfig config
  ) {
    return new BaseFormatProvider<>(new Triple<>("DATE", column, config), (formatsCache, formatterCreator) -> formatterCreator.create(getDateKey(column, config, formatsCache)));
  }

  public static FormatProvider<Triple<String, GridColumn, ObjectFormatterConfig>, NumberFormatter> getDecimalFormatProvider(
    @Nullable GridColumn column,
    @Nullable ObjectFormatterConfig config
  ) {
    return new BaseFormatProvider<>(new Triple<>("DECIMAL", column, config), (formatsCache, formatterCreator) -> formatterCreator.create(getDecimalKey(column, config)));
  }

  public static FormatProvider<Triple<String, GridColumn, ObjectFormatterConfig>, Formatter> getTimeFormatProvider(
    @Nullable GridColumn column,
    @Nullable ObjectFormatterConfig config
  ) {
    return new BaseFormatProvider<>(new Triple<>("TIME", column, config), (formatsCache, formatterCreator) -> formatterCreator.create(getTimeKey(column, config, formatsCache)));
  }

  public static FormatProvider<Triple<String, GridColumn, ObjectFormatterConfig>, Formatter> getTimestampFormatProvider(
    @Nullable GridColumn column,
    @Nullable ObjectFormatterConfig config
  ) {
    return new BaseFormatProvider<>(new Triple<>("TIMESTAMP", column, config), (formatsCache, formatterCreator) -> formatterCreator.create(getTimestampKey(column, config, formatsCache)));
  }

  public interface FormatProvider<K, T> {
    K getCacheKey();
    T createFormatter(@NotNull FormatsCache formatsCache, @NotNull FormatterCreator formatterCreator);
  }

  public static class BaseFormatProvider<K, T> implements FormatProvider<K, T> {
    private final K myKey;
    private final BiFunction<FormatsCache, FormatterCreator, T> myCompute;

    BaseFormatProvider(@NotNull K key, @NotNull FormatterKey<T> formatterKey) {
      myKey = key;
      myCompute = (formatsCache, formatterCreator) -> formatterCreator.create(formatterKey);
    }

    public BaseFormatProvider(@NotNull K key, @NotNull BiFunction<FormatsCache, FormatterCreator, T> compute) {
      myKey = key;
      myCompute = compute;
    }

    @Override
    public K getCacheKey() {
      return myKey;
    }

    @Override
    public T createFormatter(@NotNull FormatsCache formatsCache, @NotNull FormatterCreator formatterCreator) {
      return myCompute.apply(formatsCache, formatterCreator);
    }
  }
}
