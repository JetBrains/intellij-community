// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.services;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class ServiceViewActionUtils {
  public static final DataKey<Set<ServiceViewContributor>> CONTRIBUTORS_KEY = DataKey.create("serviceViewContributors");
  public static final DataKey<ServiceViewOptions> OPTIONS_KEY = DataKey.create("ServiceViewTreeOptions");
  public static final DataKey<Boolean> IS_FROM_TREE_KEY = DataKey.create("IsFromTreeSource");

  private ServiceViewActionUtils() {
  }

  public static @Nullable <T> T getTarget(@NotNull AnActionEvent e, @NotNull Class<T> clazz) {
    Object[] items = e.getData(PlatformCoreDataKeys.SELECTED_ITEMS);
    return items != null && items.length == 1 ? ObjectUtils.tryCast(items[0], clazz) : null;
  }

  public static @NotNull <T> List<T> getTargets(@NotNull AnActionEvent e, @NotNull Class<T> clazz) {
    Object[] items = e.getData(PlatformCoreDataKeys.SELECTED_ITEMS);
    if (items == null) return Collections.emptyList();

    List<T> result = new ArrayList<>();
    for (Object item : items) {
      if (!clazz.isInstance(item)) {
        return Collections.emptyList();
      }
      result.add(clazz.cast(item));
    }
    return result;
  }
}
