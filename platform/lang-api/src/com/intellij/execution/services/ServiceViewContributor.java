// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.services;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Experimental
public interface ServiceViewContributor<T> {
  @NotNull
  ServiceViewDescriptor getViewDescriptor();

  @NotNull
  List<T> getServices(@NotNull Project project);

  @NotNull
  ServiceViewDescriptor getServiceDescriptor(@NotNull T service);
}
