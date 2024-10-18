// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.unscramble;

import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Implement to add additional tab after invoking "Analyze Stacktrace and Thread Dumps" action
 */
@ApiStatus.Experimental
public interface StacktraceTabContentProvider {
  /**
   * @param text text of thread dump or stacktrace
   */
  @Nullable RunContentDescriptor createRunTabDescriptor(Project project, String text);
}
