// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.task;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * An {@link ProjectTask} represents a single atomic piece of work for IDE workflow, such as 'Make Project' or run configurations.
 *
 * @author Vladislav.Soroka
 */
public interface ProjectTask {
  @NotNull
  @Nls
  String getPresentableName();
}
