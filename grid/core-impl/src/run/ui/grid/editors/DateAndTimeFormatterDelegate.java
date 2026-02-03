package com.intellij.database.run.ui.grid.editors;

import org.jetbrains.annotations.NotNull;

import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQuery;

public abstract class DateAndTimeFormatterDelegate<T, V extends TemporalAccessor> {
  private final TemporalQuery<V>[] myQueries;

  protected DateAndTimeFormatterDelegate(TemporalQuery<V> @NotNull ... queries) {
    myQueries = queries;
  }

  TemporalQuery<V> @NotNull [] getQueries() {
    return myQueries;
  }

  protected abstract V toTemporalAccessor(@NotNull Object value);
  protected abstract T createFromTemporal(@NotNull V value);
}
