// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution.configurations;

import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

/**
 * Base interface for run configurations that support the "Make before launch" task.
 *
 * @author spleaner
 */
public interface RunProfileWithCompileBeforeLaunchOption extends RunProfile {
  /**
   * @return modules to compile before run. Empty list to build project
   */
  default Module @NotNull [] getModules() {
    return Module.EMPTY_ARRAY;
  }

  /**
   * Modifies behavior for the case when {@link #getModules()} returns empty list.
   * By default whole project will be built in this case.
   * @return true if whole project is to be built when no particular module is selected,
   *         false effectively skips project compilation
   */
  default boolean isBuildProjectOnEmptyModuleList() {
    return true;
  }


  /**
   * @return true if "Build" Before Launch task should be added automatically on run configuration creation
   */
  default boolean isBuildBeforeLaunchAddedByDefault() {
    return true;
  }

  default boolean isExcludeCompileBeforeLaunchOption() {
    return false;
  }
}
