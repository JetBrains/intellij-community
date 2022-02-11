// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.ide.impl.dataRules.GetDataRule;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class SimpleDataContext implements DataContext {
  private final Map<String, Object> myDataId2Data;
  private final DataContext myParent;

  private SimpleDataContext(@NotNull Map<String, Object> dataId2data, @Nullable DataContext parent) {
    myDataId2Data = dataId2data;
    myParent = parent;
  }

  @Override
  public Object getData(@NotNull String dataId) {
    Object result = getDataFromSelfOrParent(dataId);

    if (result == null) {
      GetDataRule rule = ((DataManagerImpl)DataManager.getInstance()).getDataRule(dataId);
      if (rule != null) {
        return rule.getData(this::getDataFromSelfOrParent);
      }
    }

    return result;
  }

  private Object getDataFromSelfOrParent(@NotNull String dataId) {
    return myDataId2Data.containsKey(dataId) ? myDataId2Data.get(dataId) :
           myParent == null ? null : myParent.getData(dataId);
  }

  /** @deprecated use {@link SimpleDataContext#getSimpleContext(DataKey, Object, DataContext)} instead. */
  @Deprecated(forRemoval = true)
  @NotNull
  public static DataContext getSimpleContext(@NotNull String dataId, @NotNull Object data, DataContext parent) {
    return new SimpleDataContext(Map.of(dataId, data), parent);
  }

  @NotNull
  public static <T> DataContext getSimpleContext(@NotNull DataKey<? super T> dataKey, @NotNull T data, @Nullable DataContext parent) {
    return new SimpleDataContext(Map.of(dataKey.getName(), data), parent);
  }

  /**
   * @see SimpleDataContext#builder()
   * @deprecated prefer type-safe {@link SimpleDataContext#builder()} where possible.
   */
  @Deprecated(forRemoval = true)
  @NotNull
  public static DataContext getSimpleContext(@NotNull Map<String, Object> dataId2data, @Nullable DataContext parent) {
    return new SimpleDataContext(dataId2data, parent);
  }

  /** @deprecated use {@link SimpleDataContext#getSimpleContext(DataKey, Object)} instead. */
  @Deprecated(forRemoval = true)
  @NotNull
  public static DataContext getSimpleContext(@NotNull String dataId, @NotNull Object data) {
    return getSimpleContext(dataId, data, null);
  }

  @NotNull
  public static <T> DataContext getSimpleContext(@NotNull DataKey<? super T> dataKey, @NotNull T data) {
    return getSimpleContext(dataKey, data, null);
  }

  @NotNull
  public static DataContext getProjectContext(@NotNull Project project) {
    return getSimpleContext(CommonDataKeys.PROJECT.getName(), project);
  }

  @NotNull
  public static Builder builder() {
    return new Builder(null);
  }

  public final static class Builder {
    private DataContext myParent;
    private Map<String, Object> myMap;

    Builder(DataContext parent) {
      myParent = parent;
    }

    public Builder setParent(@Nullable DataContext parent) {
      myParent = parent;
      return this;
    }

    @NotNull
    public <T> Builder add(@NotNull DataKey<? super T> dataKey, @Nullable T value) {
      if (value != null) {
        if (myMap == null) myMap = new HashMap<>();
        myMap.put(dataKey.getName(), value);
      }
      return this;
    }

    @NotNull
    public Builder addAll(@NotNull DataContext dataContext, DataKey<?> @NotNull ... keys) {
      for (DataKey<?> key : keys) {
        //noinspection unchecked
        add((DataKey<Object>)key, dataContext.getData(key));
      }
      return this;
    }

    @NotNull
    public DataContext build() {
      if (myMap == null && myParent == null) return EMPTY_CONTEXT;
      return new SimpleDataContext(myMap != null ? myMap : Collections.emptyMap(), myParent);
    }
  }
}
