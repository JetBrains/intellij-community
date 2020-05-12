// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

@ApiStatus.Experimental
@ApiStatus.Internal
public interface DumbUtil {

  static DumbUtil getInstance(@NotNull Project project) {
    return project.getService(DumbUtil.class);
  }

  /**
   * @return all the elements of the given collection if there's no dumb mode currently ({@link com.intellij.openapi.project.DumbService#isDumb()} is false),
   * or method is called inside {@link com.intellij.util.indexing.FileBasedIndex#ignoreDumbMode(Runnable, Project, com.intellij.util.indexing.DumbModeAccessType)}.
   * Otherwise, the dumb-aware ones are returned.
   * @see com.intellij.openapi.project.DumbService#isDumbAware(Object)
   */
  @Contract(pure = true)
  @ApiStatus.Internal
  @ApiStatus.Experimental
  @NotNull <T> List<T> filterByDumbAwarenessHonoringIgnoring(@NotNull Collection<? extends T> collection);
}
