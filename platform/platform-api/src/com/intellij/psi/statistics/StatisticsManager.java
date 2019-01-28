// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.statistics;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.KeyedExtensionCollector;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class StatisticsManager {
  /**
   * The number of last entries stored for each context in {@link #getUseCount(StatisticsInfo)}.
   * If more entries are used over time, use count will be 0 for the most ancient entries.
   */
  public static final int OBLIVION_THRESHOLD = 7;

  /**
   * The number of last entries stored for each context in {@link #getLastUseRecency(StatisticsInfo)}.
   * If more entries are used over time, recency will be {@code Integer.MAX_INT} for the most ancient entries.
   */
  public static final int RECENCY_OBLIVION_THRESHOLD = 10000;

  private static final KeyedExtensionCollector<Statistician, Key> COLLECTOR = new KeyedExtensionCollector<Statistician, Key>("com.intellij.statistician") {
    @Override
    @NotNull
    protected String keyToString(@NotNull final Key key) {
      return key.toString();
    }
  };

  @Nullable
  public static <T, Loc> StatisticsInfo serialize(Key<? extends Statistician<T, Loc>> key, T element, Loc location) {
    for (@SuppressWarnings("unchecked") Statistician<T, Loc> statistician : COLLECTOR.forKey(key)) {
      StatisticsInfo info = statistician.serialize(element, location);
      if (info != null) {
        return info;
      }
    }
    return null;
  }

  public static StatisticsManager getInstance() {
    return ServiceManager.getService(StatisticsManager.class);
  }

  /**
   * @return how many times {@code info.getValue()} was used among all recently registered entries with the same {@code info.getContext()}.
   * @see #incUseCount(StatisticsInfo)
   * @see #OBLIVION_THRESHOLD
   */
  public abstract int getUseCount(@NotNull StatisticsInfo info);

  /**
   * @return the position of {@code info.getValue()} in all recently registered entries with the same {@code info.getContext()}. 0 if it it's the most recent entry, {@code Integer.MAX_INT} if it was never used, or was used too long ago (more than {@link #RECENCY_OBLIVION_THRESHOLD} other entries with the same context have been registered with {@link #incUseCount(StatisticsInfo)} since.
   */
  public abstract int getLastUseRecency(@NotNull StatisticsInfo info);

  /**
   * Register a usage of an <context, value> entry represented by info parameter. This will affect subsequent {@link #getUseCount(StatisticsInfo)} and {@link #getLastUseRecency(StatisticsInfo)} results.
   */
  public abstract void incUseCount(@NotNull StatisticsInfo info);

  public <T, Loc> int getUseCount(final Key<? extends Statistician<T, Loc>> key, final T element, final Loc location) {
    final StatisticsInfo info = serialize(key, element, location);
    return info == null ? 0 : getUseCount(info);
  }

  public <T, Loc> void incUseCount(final Key<? extends Statistician<T, Loc>> key,
                                   final T element,
                                   final Loc location) {
    final StatisticsInfo info = serialize(key, element, location);
    if (info != null) {
      incUseCount(info);
    }
  }

  /**
   * @return infos by this context ordered by usage time: recent first
   */
  public abstract StatisticsInfo[] getAllValues(@NonNls String context);
}
