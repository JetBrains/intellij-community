// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.platform.diagnostic.telemetry.Scope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

// use only JDK classes here (avoid StringUtil and so on)
@ApiStatus.Internal
public final class ActivityImpl implements Activity {
  public Scope scope;
  public String[] attributes;

  private final String name;
  private String description;

  private String threadName;
  private long threadId;

  private final long start;
  private long end;

  // null doesn't mean root - not obligated to set parent, only as a hint
  private final ActivityImpl parent;

  private final @Nullable ActivityCategory category;

  private final @Nullable String pluginId;

  public ActivityImpl(@Nullable String name, long start, @Nullable ActivityImpl parent) {
    this(name, start, parent, null);
  }

  ActivityImpl(@Nullable String name, long start, @Nullable ActivityImpl parent, @Nullable String pluginId) {
    this(name, start, parent, pluginId, null);
  }

  ActivityImpl(@Nullable String name, long start, @Nullable ActivityImpl parent, @Nullable String pluginId, @Nullable ActivityCategory category) {
    this.name = name;
    this.start = start;
    this.parent = parent;
    this.pluginId = pluginId;
    this.category = category;

    updateThreadName();
  }

  public @NotNull String getThreadName() {
    return threadName;
  }

  // Not clear - should we always set it on end of activity or not. Method maybe called in such rare cases.
  private void updateThreadName() {
    Thread thread = Thread.currentThread();
    threadId = thread.getId();
    threadName = thread.getName();
  }

  public long getThreadId() {
    return threadId;
  }

  public @Nullable ActivityImpl getParent() {
    return parent;
  }

  public @Nullable ActivityCategory getCategory() {
    return category;
  }

  // and how can we sort correctly, when parent item equals to child (start and end); also there is another child with start equals to end?
  // so, parent added to API but as it was not enough, decided to measure time in nanoseconds instead of ms to mitigate such situations
  @Override
  public @NotNull ActivityImpl startChild(@NotNull String name) {
    return new ActivityImpl(name, System.nanoTime(), this, pluginId, category);
  }

  @Override
  public @NotNull String getName() {
    return name;
  }

  public @Nullable String getDescription() {
    return description;
  }

  public @Nullable String getPluginId() {
    return pluginId;
  }

  public long getStart() {
    return start;
  }

  public long getEnd() {
    return end;
  }

  @ApiStatus.Internal
  public void setEnd(long end) {
    assert this.end == 0 : "not started or already ended";
    this.end = end;
  }

  @Override
  public void end() {
    end = System.nanoTime();
    StartUpMeasurer.addActivity(this);
  }

  @Override
  public void setDescription(@NotNull String value) {
    description = value;
  }

  @Override
  public @NotNull ActivityImpl endAndStart(@NotNull String name) {
    end = System.nanoTime();
    StartUpMeasurer.addActivity(this);
    return new ActivityImpl(name, /* start = */end, parent, /* pluginId = */ pluginId, category);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("ActivityImpl(name=").append(name).append(", start=");
    nanoToString(start, builder);
    builder.append(", end=");
    nanoToString(end, builder);
    builder.append(", category=").append(category).append(")");
    return builder.toString();
  }

  private static void nanoToString(long start, @NotNull StringBuilder builder) {
    //noinspection NonAsciiCharacters
    builder
      .append(TimeUnit.NANOSECONDS.toMillis(start - StartUpMeasurer.getStartTime())).append("ms (")
      .append(TimeUnit.NANOSECONDS.toMicros(start - StartUpMeasurer.getStartTime())).append("μs)");
  }
}