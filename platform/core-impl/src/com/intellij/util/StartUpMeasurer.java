// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class StartUpMeasurer {
  public static final long MEASURE_THRESHOLD = TimeUnit.MILLISECONDS.toNanos(10);

  // Use constants for better overview of existing phases (and preserve consistent naming).
  // `what + noun` is used as scheme for name to make analyzing easier (to visually group - `components loading/initialization/etc`,
  // not to put common part of name to end of).
  // It is not serves only display purposes - it is IDs. Visualizer and another tools to analyze data uses phase IDs,
  // so, any changes must be discussed across all involved and reflected in changelog (see `format-changelog.md`).
  public static final class Phases {
    // this phase name is not fully clear - it is time from `PluginManager.start` to `IdeaApplication.initApplication`
    public static final String PREPARE_TO_INIT_APP = "app initialization preparation";
    // this phase name is not fully clear - it is time from `IdeaApplication.initApplication` to `IdeaApplication.run`
    public static final String INIT_APP = "app initialization";

    public static final String LOAD_PLUGIN_DESCRIPTORS = "plugin descriptors loading";
    public static final String INIT_PLUGINS = "plugins initialization";

    public static final String INITIALIZE_COMPONENTS_SUFFIX = "components initialization";
    // actually, now it is also registers services, not only components,but it doesn't worth to rename
    public static final String REGISTER_COMPONENTS_SUFFIX = "components registration";
    public static final String COMPONENTS_REGISTERED_CALLBACK_SUFFIX = "components registered callback";
    public static final String CREATE_COMPONENTS_SUFFIX = "components creation";

    public static final String APP_INITIALIZED_CALLBACK = "app initialized callback";

    public static final String PROJECT_PRE_STARTUP = "project pre-startup";
    public static final String PROJECT_STARTUP = "project startup";

    public static final String PROJECT_DUMB_POST_STARTUP = "project dumb post-startup";

    public static final String LOAD_MODULES = "module loading";
  }

  // non-sequential and repeated items
  public static final class Activities {
    public static final String APP_COMPONENT = "_appComponent";
    public static final String PROJECT_COMPONENT = "_projectComponent";

    public static final String APP_SERVICE = "_appService";
    public static final String PROJECT_SERVICE = "_projectService";

    public static final String PRELOAD_ACTIVITY = "_preloadActivity";

    public static final String APP_OPTIONS_TOP_HIT_PROVIDER = "_appOptionsTopHitProvider";
    public static final String PROJECT_OPTIONS_TOP_HIT_PROVIDER = "_projectOptionsTopHitProvider";
  }

  private static final long startTime = System.nanoTime();

  private static final ConcurrentLinkedQueue<Item> items = new ConcurrentLinkedQueue<>();

  public static long getStartTime() {
    return startTime;
  }

  @NotNull
  public static MeasureToken start(@NotNull String name, @Nullable String description) {
    return new Item(name, description);
  }

  @NotNull
  public static MeasureToken start(@NotNull String name) {
    return new Item(name, null);
  }

  public static void processAndClear(@NotNull Consumer<Item> consumer) {
    while (true) {
      Item item = items.poll();
      if (item == null) {
        break;
      }

      consumer.accept(item);
    }
  }

  public final static class Item implements MeasureToken, AutoCloseable {
    private final String name;
    private String description;

    private final long start;
    private long end;

    // null doesn't mean root - not obligated to set parent, only as hint
    private final Item parent;

    private Item(@Nullable String name, @Nullable String description) {
      this(name, description, System.nanoTime(), null);
    }

    private Item(@Nullable String name, @Nullable String description, long start, @Nullable Item parent) {
      this.name = name;
      this.description = StringUtil.nullize(description);
      this.start = start;
      this.parent = parent;
    }

    @Nullable
    public Item getParent() {
      return parent;
    }

    // and how do we can sort correctly, when parent item equals to child (start and end) and also there is another child with start equals to end?
    // so, parent added to API but as it was not enough, decided to measure time in nanoseconds instead of ms to mitigate such situations
    @Override
    @NotNull
    public Item startChild(@NotNull String name) {
      return new Item(name, null, System.nanoTime(), this);
    }

    @NotNull
    public String getName() {
      return name;
    }

    @Nullable
    public String getDescription() {
      return description;
    }

    public long getStart() {
      return start;
    }

    public long getEnd() {
      return end;
    }

    @Override
    public void end(@Nullable String description) {
      if (description != null) {
        this.description = description;
      }
      end = System.nanoTime();
      items.add(this);
    }

    @Override
    public void endWithThreshold(@NotNull Class<?> clazz) {
      this.description = clazz.getName();
      end = System.nanoTime();
      if ((end - start) > MEASURE_THRESHOLD) {
        items.add(this);
      }
    }

    @Override
    public void close() {
      end();
    }

    @Override
    public String toString() {
      return name;
    }
  }

  public interface MeasureToken extends AutoCloseable {
    void end(@Nullable String description);

    void endWithThreshold(@NotNull Class<?> clazz);

    @NotNull
    Item startChild(@NotNull String name);

    default void end() {
      end(null);
    }
  }
}