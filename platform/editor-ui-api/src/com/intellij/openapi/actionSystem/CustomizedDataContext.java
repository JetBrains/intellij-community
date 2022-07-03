// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.ide.DataManager;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CustomizedDataContext implements DataContext {

  /**
   * Tells {@link DataContext} implementations to return {@code null} and query other providers no further.
   */
  public static final Object EXPLICIT_NULL = ObjectUtils.sentinel("explicit.null");

  @ApiStatus.OverrideOnly
  public abstract @NotNull DataContext getParent();

  @ApiStatus.OverrideOnly
  public abstract @Nullable Object getRawCustomData(@NotNull String dataId);

  @ApiStatus.Internal
  public final @Nullable Object getCustomData(@NotNull String dataId) {
    return getRawCustomData(dataId);
  }

  @Override
  @ApiStatus.NonExtendable
  public @Nullable Object getData(@NotNull String dataId) {
    Object data = DataManager.getInstance().getCustomizedData(dataId, getParent(), this::getCustomData);
    return data == EXPLICIT_NULL ? null : data;
  }

  public static @NotNull CustomizedDataContext create(@NotNull DataContext parent, @NotNull DataProvider provider) {
    return new CustomizedDataContext() {
      @Override
      public @NotNull DataContext getParent() {
        return parent;
      }

      @Override
      public @Nullable Object getRawCustomData(@NotNull String dataId) {
        return provider.getData(dataId);
      }
    };
  }
}
