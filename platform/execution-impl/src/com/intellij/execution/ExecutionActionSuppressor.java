// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;

/**
 * Can disable all actions, that can execute Run configurations.
 * <p></p>
 * If there are several instances of ExecutionActionSuppressor,
 * then all such actions will be disabled if at least one of the instances
 * returns <code>true</code> by {@link ExecutionActionSuppressor#isSuppressed(Project)}
 */
@ApiStatus.Experimental
@ApiStatus.Internal
public interface ExecutionActionSuppressor {
  ExtensionPointName<ExecutionActionSuppressor> EP_NAME = ExtensionPointName.create("com.intellij.executionActionSuppressor");

  /**
   * @param project current project
   * @return <code>true</code> if it is necessary to disable all actions,
   *         that can execute Run configurations,
   *         <code>false</code> otherwise
   */
  boolean isSuppressed(Project project);
}
