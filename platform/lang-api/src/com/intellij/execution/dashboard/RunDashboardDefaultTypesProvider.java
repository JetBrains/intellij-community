// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.dashboard;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Register this extension to provide run configuration types available in Run Dashboard/Services by default.
 */
@ApiStatus.Experimental
public interface RunDashboardDefaultTypesProvider {
  @NotNull
  Collection<String> getDefaultTypeIds(@NotNull Project project);
}
