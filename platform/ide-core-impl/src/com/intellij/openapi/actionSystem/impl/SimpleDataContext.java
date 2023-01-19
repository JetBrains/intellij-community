// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.ui.IdeUiService;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.CustomizedDataContext;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class SimpleDataContext extends CustomizedDataContext {
  private final Map<String, Object> myMap;
  private final DataContext myParent;

  private SimpleDataContext(@NotNull Map<String, Object> map, @Nullable DataContext parent) {
    myMap = map;
    myParent = Objects.requireNonNullElse(parent, DataContext.EMPTY_CONTEXT);
  }

  @Override
  public @NotNull DataContext getParent() {
    return myParent;
  }

  @Override
  public @Nullable Object getRawCustomData(@NotNull String dataId) {
    return myMap.containsKey(dataId) ?
           Objects.requireNonNullElse(myMap.get(dataId), EXPLICIT_NULL) : null;
  }

  /** @deprecated use {@link SimpleDataContext#getSimpleContext(DataKey, Object, DataContext)} instead. */
  @Deprecated(forRemoval = true)
  public static @NotNull DataContext getSimpleContext(@NotNull String dataId, @NotNull Object data, DataContext parent) {
    return new SimpleDataContext(Map.of(dataId, data), parent);
  }

  public static @NotNull <T> DataContext getSimpleContext(@NotNull DataKey<? super T> dataKey, @NotNull T data, @Nullable DataContext parent) {
    return new SimpleDataContext(Map.of(dataKey.getName(), data), parent);
  }

  /**
   * @see SimpleDataContext#builder()
   * @deprecated prefer type-safe {@link SimpleDataContext#builder()} where possible.
   */
  @Deprecated(forRemoval = true)
  public static @NotNull DataContext getSimpleContext(@NotNull Map<String, Object> dataId2data, @Nullable DataContext parent) {
    return new SimpleDataContext(dataId2data, parent);
  }

  /** @deprecated use {@link SimpleDataContext#getSimpleContext(DataKey, Object)} instead. */
  @Deprecated(forRemoval = true)
  public static @NotNull DataContext getSimpleContext(@NotNull String dataId, @NotNull Object data) {
    return getSimpleContext(dataId, data, null);
  }

  public static @NotNull <T> DataContext getSimpleContext(@NotNull DataKey<? super T> dataKey, @NotNull T data) {
    return getSimpleContext(dataKey, data, null);
  }

  public static @NotNull DataContext getProjectContext(@NotNull Project project) {
    return getSimpleContext(CommonDataKeys.PROJECT.getName(), project);
  }

  public static @NotNull Builder builder() {
    return new Builder(null);
  }

  public static final class Builder {
    private DataContext myParent;
    private Map<String, Object> myMap;

    Builder(DataContext parent) {
      myParent = parent;
    }

    public Builder setParent(@Nullable DataContext parent) {
      myParent = parent;
      return this;
    }

    public @NotNull <T> Builder add(@NotNull DataKey<? super T> dataKey, @Nullable T value) {
      if (value != null) {
        if (myMap == null) myMap = new HashMap<>();
        myMap.put(dataKey.getName(), value);
      }
      return this;
    }

    public @NotNull <T> Builder addNull(@NotNull DataKey<? super T> dataKey) {
      if (myMap == null) myMap = new HashMap<>();
      myMap.put(dataKey.getName(), EXPLICIT_NULL);
      return this;
    }

    /** @deprecated Shall not be used since the introduction of {@link IdeUiService#createAsyncDataContext(DataContext)} */
    @Deprecated
    public @NotNull Builder addAll(@NotNull DataContext dataContext, DataKey<?> @NotNull ... keys) {
      if (keys.length == 0) {
        throw new IllegalArgumentException("Keys argument must not be empty");
      }
      for (DataKey<?> key : keys) {
        //noinspection unchecked
        add((DataKey<Object>)key, dataContext.getData(key));
      }
      return this;
    }

    public @NotNull DataContext build() {
      if (myMap == null && myParent == null) return EMPTY_CONTEXT;
      return new SimpleDataContext(myMap != null ? myMap : Collections.emptyMap(), myParent);
    }
  }
}
