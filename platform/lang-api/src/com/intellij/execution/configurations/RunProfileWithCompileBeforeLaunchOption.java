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
  @NotNull
  default Module[] getModules() {
    return Module.EMPTY_ARRAY;
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
