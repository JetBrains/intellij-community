// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class StartUpMeasurer {
  // use constants for better overview of existing phases (and preserve consistent naming)
  // `what + noun` is used as scheme for name to make analyzing easier (to visually group - `components loading/initialization/etc`, not to put common part of name to end of)
  public static final class Phases {
    // this phase name is not fully clear - it is time from `IdeaApplication.initApplication` to `IdeaApplication.run`
    public static final String INIT_APP = "app initialization";

    public static final String LOAD_PLUGIN_DESCRIPTORS = "plugin descriptors loading";
    public static final String INIT_PLUGINS = "plugins initialization";

    public static final String INITIALIZE_COMPONENTS_SUFFIX = "components initialization";
    public static final String REGISTER_COMPONENTS_SUFFIX = "components registration";
    public static final String COMPONENTS_REGISTERED_CALLBACK_SUFFIX = "components registered callback";
    public static final String CREATE_COMPONENTS_SUFFIX = "components creation";

    public static final String APP_INITIALIZED_CALLBACK = "app initialized callback";

    public static final String PROJECT_PRE_STARTUP = "project pre-startup";
    public static final String PROJECT_STARTUP = "project startup";

    public static final String PROJECT_DUMB_POST_STARTUP = "project dumb post-startup";
  }

  public static final String COMPONENT_INITIALIZED_INTERNAL_NAME = "_component initialized";
  public static final String PRELOAD_ACTIVITY_FINISHED = "_preload activity finished";

  private static final long startTime = System.currentTimeMillis();

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

  public static void reportComponentInitialized(@NotNull Class<?> componentClass, long startTime, long endTime) {
    new Item(COMPONENT_INITIALIZED_INTERNAL_NAME, componentClass.getName(), startTime).end(endTime);
  }

  @NotNull
  public static List<Item> getAndClear() {
    ArrayList<Item> list = new ArrayList<>();
    while (true) {
      Item item = items.poll();
      if (item == null) {
        break;
      }

      list.add(item);
    }
    return list;
  }

  public final static class Item implements MeasureToken, AutoCloseable {
    private final String name;
    private String description;

    private final long start;
    private long end;

    private Item(@Nullable String name, @Nullable String description) {
      this(name, description, System.currentTimeMillis());
    }

    private Item(@Nullable String name, @Nullable String description, long start) {
      this.name = name;
      this.description = StringUtil.nullize(description);
      this.start = start;
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
      end = System.currentTimeMillis();
      items.add(this);
    }

    void end(long end) {
      this.end = end;
      items.add(this);
    }

    @Override
    public void close() {
      end();
    }
  }

  public interface MeasureToken extends AutoCloseable {
    void end(@Nullable String description);

    default void end() {
      end(null);
    }
  }
}
