// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.openapi.project.Project;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Run task once in the history for project or application.
 *
 * @author Konstantin Bulenkov
 */
public final class RunOnceUtil {
  /**
   * Perform the task if it was not performed before for the given project.
   *
   * @param id unique id for the task
   * @return {@code true} if task was performed, {@code false} if task had already been performed before.
   */
  public static boolean runOnceForProject(@NotNull Project project, @NotNull @NonNls String id, @NotNull Runnable task) {
    return _runOnce(PropertiesComponent.getInstance(project), id, false, task);
  }

  /**
   * Perform the task if it was not performed before for the given project.
   *
   * @param id unique id for the task
   * @param isIDEAware if {@code true}, the task will be performed for each IDE
   * @return {@code true} if task was performed, {@code false} if task had already been performed before.
   */
  public static boolean runOnceForProject(@NotNull Project project, @NotNull @NonNls String id, boolean isIDEAware, @NotNull Runnable task) {
    return _runOnce(PropertiesComponent.getInstance(project), id, isIDEAware, task);
  }

  /**
   * Perform the task if it was not performed before for this application.
   *
   * @param id unique id for the task
   * @return {@code true} if task was performed, {@code false} if task had already been performed before.
   */
  public static boolean runOnceForApp(@NotNull @NonNls String id, @NotNull Runnable task) {
    return _runOnce(PropertiesComponent.getInstance(), id, false, task);
  }

  private static boolean _runOnce(@NotNull PropertiesComponent storage,
                                  @NotNull @NonNls String id,
                                  boolean ideAware,
                                  @NotNull Runnable activity) {
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (storage) {
      String key = createKey(id, ideAware);
      if (storage.isTrueValue(key)) {
        return false;
      }
      storage.setValue(key, true);
    }
    activity.run();
    return true;
  }

  private static @NonNls String createKey(@NotNull String id, boolean ideAware) {
    String key = "RunOnceActivity.";
    if (ideAware) {
      key += PlatformUtils.getPlatformPrefix() + ".";
    }
    return  key + id;
  }
}
