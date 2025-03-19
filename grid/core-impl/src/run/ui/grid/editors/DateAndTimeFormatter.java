package com.intellij.database.run.ui.grid.editors;

import com.intellij.database.DataGridBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.ParsePosition;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQuery;

public class DateAndTimeFormatter<T, V extends TemporalAccessor> extends FormatterImpl {
  private final DateTimeFormatter myFormatter;
  private final String myPattern;
  private final DateAndTimeFormatterDelegate<T, V> myDelegate;
  private final BoundaryValueResolver myBoundaryValuesResolver;
  private final DateTimeFormatter myDateFormatterForValueWithoutTime;

  public DateAndTimeFormatter(@NotNull String pattern,
                              @NotNull DateTimeFormatter formatter,
                              @NotNull DateAndTimeFormatterDelegate<T, V> delegate) {
    this(pattern, formatter, delegate, BoundaryValueResolver.ALWAYS_NULL, null);
  }

  public DateAndTimeFormatter(@NotNull String pattern,
                              @NotNull DateTimeFormatter formatter,
                              @NotNull DateAndTimeFormatterDelegate<T, V> delegate,
                              @NotNull BoundaryValueResolver resolver,
                              @Nullable DateTimeFormatter dateFormatterForValueWithoutTime) {
    myPattern = pattern;
    myFormatter = formatter;
    myDelegate = delegate;
    myBoundaryValuesResolver = resolver;
    myDateFormatterForValueWithoutTime = dateFormatterForValueWithoutTime;
  }

  @Override
  protected @Nls @NotNull String getErrorMessage() {
    return DataGridBundle.message("unexpected.data.format");
  }

  private V query(TemporalAccessor parsed, TemporalQuery<V>[] queries) {
    if (parsed == null) return null;
    for (TemporalQuery<V> query : queries) {
      try {
        return parsed.query(query);
      }
      catch (RuntimeException ignore) {
      }
    }
    return null;
  }

  private @Nullable TemporalAccessor parseValue(String value, @NotNull ParsePosition position) {
    try {
      TemporalAccessor parsed = myFormatter.parse(value, position);
      if (position.getIndex() != value.length() || position.getErrorIndex() != -1) {
        return null;
      }
      return parsed;
    }
    catch (DateTimeParseException e) {
      position.setErrorIndex(e.getErrorIndex());
      return null;
    }
  }

  @Override
  public Object parse(@NotNull String value, @NotNull ParsePosition position) {
    Object boundary = myBoundaryValuesResolver.createFromInfinityString(value);
    if (boundary != null) return boundary;
    TemporalAccessor parsed = parseValue(value, position);
    if (parsed == null) return null;
    V temporal = query(parsed, myDelegate.getQueries());
    if (temporal == null) {
      position.setErrorIndex(0);
      return null;
    }
    T result = myDelegate.createFromTemporal(temporal);
    String boundaryString = myBoundaryValuesResolver.resolve(result);
    return boundaryString != null ? myBoundaryValuesResolver.createFromInfinityString(boundaryString) : result;
  }

  @Override
  public String format(@NotNull Object value) {
    String boundary = myBoundaryValuesResolver.resolve(value);
    if (boundary != null) return boundary;
    V temporalAccessor = myDelegate.toTemporalAccessor(value);
    if (myDateFormatterForValueWithoutTime != null && DataGridFormattersUtilCore.isEmptyTime(temporalAccessor)) {
      return myDateFormatterForValueWithoutTime.format(temporalAccessor);
    }
    return myFormatter.format(temporalAccessor);
  }

  public TemporalAccessor getTemporalAccessor(@NotNull Object value) {
    return myDelegate.toTemporalAccessor(value);
  }

  @Override
  public String toString() {
    return myPattern;
  }
}
