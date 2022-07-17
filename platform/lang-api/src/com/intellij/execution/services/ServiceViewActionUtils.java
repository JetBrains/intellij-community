// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.services;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ServiceViewActionUtils {
  public static final DataKey<Set<ServiceViewContributor>> CONTRIBUTORS_KEY = DataKey.create("serviceViewContributors");
  public static final DataKey<ServiceViewOptions> OPTIONS_KEY = DataKey.create("ServiceViewTreeOptions");

  private ServiceViewActionUtils() {
  }

  @Nullable
  public static <T> T getTarget(@NotNull AnActionEvent e, @NotNull Class<T> clazz) {
    Object[] items = e.getData(PlatformCoreDataKeys.SELECTED_ITEMS);
    return items != null && items.length == 1 ? ObjectUtils.tryCast(items[0], clazz) : null;
  }

  @NotNull
  public static <T> JBIterable<T> getTargets(@NotNull AnActionEvent e, @NotNull Class<T> clazz) {
    Object[] items = e.getData(PlatformCoreDataKeys.SELECTED_ITEMS);
    if (items == null) return JBIterable.empty();

    List<T> result = new ArrayList<>();
    for (Object item : items) {
      if (!clazz.isInstance(item)) {
        return JBIterable.empty();
      }
      result.add(clazz.cast(item));
    }
    return JBIterable.from(result);
  }
}
