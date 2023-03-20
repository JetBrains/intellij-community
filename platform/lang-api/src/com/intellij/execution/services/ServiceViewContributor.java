// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.services;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface ServiceViewContributor<T> {
  ExtensionPointName<ServiceViewContributor<?>> CONTRIBUTOR_EP_NAME =
    ExtensionPointName.create("com.intellij.serviceViewContributor");

  @NotNull
  ServiceViewDescriptor getViewDescriptor(@NotNull Project project);

  @NotNull
  List<T> getServices(@NotNull Project project);

  @NotNull
  ServiceViewDescriptor getServiceDescriptor(@NotNull Project project, @NotNull T service);
}
