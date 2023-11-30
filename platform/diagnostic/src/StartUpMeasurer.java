// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class StartUpMeasurer {
  public static final long MEASURE_THRESHOLD = TimeUnit.MILLISECONDS.toNanos(10);

  // `What + noun` is used as a naming scheme to make analyzing easier
  // (to visually group `components loading/initialization/etc.`, a common part should be at the start).
  // The names are not only for display purposes, they are also IDs - Visualizer and other tools use phase IDs,
  // so any changes must be discussed among all involved people and reflected in the changelog (see `format-changelog.md`).
  public static final class Activities {
    // actually, the phase also registers services (in addition to components), but it isn't worth to rename
    public static final String REGISTER_COMPONENTS_SUFFIX = "component registration";
    public static final String CREATE_COMPONENTS_SUFFIX = "component creation";

    public static final String PROJECT_DUMB_POST_START_UP_ACTIVITIES = "project post-startup dumb-aware activities";
    public static final String EDITOR_RESTORING_TILL_PAINT = "editor restoring till paint";
  }

  @SuppressWarnings("StaticNonFinalField")
  public static boolean measuringPluginStartupCosts = true;

  public static void stopPluginCostMeasurement() {
    measuringPluginStartupCosts = false;
  }

  private static long startTime = System.nanoTime();
  private static long startTimeUnixNanoDiff = (System.currentTimeMillis() * 1_000_000) - startTime;

  @ApiStatus.Internal
  public static long getStartTime() {
    return startTime;
  }

  @ApiStatus.Internal
  public static long getStartTimeUnixNanoDiff() {
    return startTimeUnixNanoDiff;
  }

  private static final ConcurrentLinkedQueue<ActivityImpl> items = new ConcurrentLinkedQueue<>();

  private static boolean isEnabled = true;

  public static boolean isEnabled() {
    return isEnabled;
  }

  @TestOnly
  public static void disable() {
    isEnabled = false;
  }

  @ApiStatus.Internal
  public static final Map<String, Object2LongOpenHashMap<String>> pluginCostMap = new ConcurrentHashMap<>();

  public static long getCurrentTime() {
    return System.nanoTime();
  }

  public static long getCurrentTimeIfEnabled() {
    return isEnabled ? System.nanoTime() : -1;
  }

  /**
   * The instant events correspond to something that happens but has no duration associated with it.
   * See <a href="https://docs.google.com/document/d/1CvAClvFfyA5R-PhYUmn5OOQtYMH4h6I0nSsKchNAySU/preview#heading=h.lenwiilchoxp">this document</a> for details.
   * Scope is not supported, reported as global.
   */
  public static void addInstantEvent(@NonNls @NotNull String name) {
    if (!isEnabled) {
      return;
    }

    ActivityImpl activity = new ActivityImpl(name, System.nanoTime(), null, null);
    activity.setEnd(-1);
    items.add(activity);
  }

  public static @NotNull Activity startActivity(@NonNls @NotNull String name, @NotNull ActivityCategory category) {
    return new ActivityImpl(name, System.nanoTime(), /* parent = */ null, /* pluginId = */ null, category);
  }

  public static @NotNull Activity startActivity(@NonNls @NotNull String name) {
    return new ActivityImpl(name, System.nanoTime(), /* parent = */ null, /* pluginId = */ null, ActivityCategory.DEFAULT);
  }

  public static @NotNull Activity startActivity(@NonNls @NotNull String name, @Nullable Activity parent) {
    return new ActivityImpl(name, System.nanoTime(), /* parent = */ (ActivityImpl)parent, /* pluginId = */ null, ActivityCategory.DEFAULT);
  }

  /**
   * The default threshold is applied.
   */
  public static long addCompletedActivity(long start,
                                          @NotNull Class<?> clazz,
                                          @NotNull ActivityCategory category,
                                          @Nullable String pluginId) {
    return addCompletedActivity(start, clazz, category, pluginId, -1);
  }

  public static void addCompletedActivity(long start, @NonNls @NotNull String name, @Nullable Activity parent) {
    if (!isEnabled) {
      return;
    }

    long end = System.nanoTime();
    long duration = end - start;
    if (duration <= MEASURE_THRESHOLD) {
      return;
    }

    ActivityImpl item = new ActivityImpl(name, start, /* parent = */ (ActivityImpl)parent, null, ActivityCategory.DEFAULT);
    item.setEnd(end);
    items.add(item);
  }

  public static long addCompletedActivity(long start,
                                          @NotNull Class<?> clazz,
                                          @NotNull ActivityCategory category,
                                          @Nullable String pluginId,
                                          long threshold) {
    if (!isEnabled) {
      return -1;
    }

    long end = System.nanoTime();
    long duration = end - start;
    if (duration <= threshold) {
      return duration;
    }

    addCompletedActivity(start, end, clazz.getName(), category, pluginId);
    return duration;
  }

  /**
   * The default threshold is applied.
   */
  public static long addCompletedActivity(long start, @NonNls @NotNull String name, @NotNull ActivityCategory category, String pluginId) {
    long end = System.nanoTime();
    long duration = end - start;
    if (duration <= MEASURE_THRESHOLD) {
      return duration;
    }

    addCompletedActivity(start, end, name, category, pluginId);
    return duration;
  }

  public static void addCompletedActivity(long start,
                                          long end,
                                          @NonNls @NotNull String name,
                                          @NotNull ActivityCategory category,
                                          String pluginId) {
    if (!isEnabled) {
      return;
    }

    ActivityImpl item = new ActivityImpl(name, start, /* parent = */ null, pluginId, category);
    item.setEnd(end);
    items.add(item);
  }

  @ApiStatus.Internal
  public static void processAndClear(boolean isContinueToCollect, @NotNull Consumer<? super ActivityImpl> consumer) {
    isEnabled = isContinueToCollect;

    while (true) {
      ActivityImpl item = items.poll();
      if (item == null) {
        break;
      }

      consumer.accept(item);
    }
  }

  public static void addActivity(@NotNull ActivityImpl activity) {
    if (isEnabled) {
      items.add(activity);
    }
  }

  @ApiStatus.Internal
  public static void addTimings(List<Object> timings, String parentName, long startTimeUnixNano) {
    if (!items.isEmpty()) {
      throw new IllegalStateException("addTimings must be not called if some events were already added using API");
    }

    if (timings.isEmpty()) {
      return;
    }

    ActivityImpl parent = new ActivityImpl(parentName, (long)timings.get(1), null, null);
    parent.setEnd(System.nanoTime());

    for (int i = 0; i < timings.size(); i += 2) {
      long start = (long)timings.get(i + 1);
      if (start < startTime) {
        startTime = start;
        startTimeUnixNanoDiff = startTimeUnixNano - start;
      }

      ActivityImpl activity = new ActivityImpl((String)timings.get(i), start, parent, null);
      activity.setEnd(i == timings.size() - 2 ? parent.getEnd() : (long)timings.get(i + 3));
      items.add(activity);
    }
    items.add(parent);
  }

  @ApiStatus.Internal
  public static void addPluginCost(@NonNls @NotNull String pluginId, @NonNls @NotNull String phase, long time) {
    if (isMeasuringPluginStartupCosts()) {
      doAddPluginCost(pluginId, phase, time, pluginCostMap);
    }
  }

  public static boolean isMeasuringPluginStartupCosts() {
    return measuringPluginStartupCosts;
  }

  @ApiStatus.Internal
  public static void doAddPluginCost(@NonNls @NotNull String pluginId,
                                     @NonNls @NotNull String phase,
                                     long time,
                                     @NotNull Map<String, Object2LongOpenHashMap<String>> pluginCostMap) {
    Object2LongMap<String> costPerPhaseMap = pluginCostMap.computeIfAbsent(pluginId, __ -> new Object2LongOpenHashMap<>());
    synchronized (costPerPhaseMap) {
      costPerPhaseMap.mergeLong(phase, time, Math::addExact);
    }
  }
}
