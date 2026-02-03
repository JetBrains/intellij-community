// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
@ApiStatus.Internal
public interface DumbUtil {

  static DumbUtil getInstance(@NotNull Project project) {
    return project.getService(DumbUtil.class);
  }

  /**
   * @return true iff one may use file based indices, i.e. project is not in dumb mode, or
   * {@link com.intellij.util.indexing.FileBasedIndex#ignoreDumbMode} was used
   */
  boolean mayUseIndices();
}
