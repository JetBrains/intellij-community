// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.services;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/// Implementation of this interface represents a (parent) node in the Service View Tree that could have multiple child nodes.
/// Top root [ServiceViewContributor]-s should be registered as a `serviceViewContributor` extension-point.
/// Nested [ServiceViewContributor]-s are returned by the [#getServices(Project)] of the parent.
///
/// @param <T> domain model type of children, if for instance, the Service View represents a list of connections to a server
///                       then [T] is a single connection. If [T] implements [ServiceViewContributor] then it would have a nested tree inside
/// @see ServiceViewDescriptor
/// @see ServiceViewManager
public interface ServiceViewContributor<T> {
  ExtensionPointName<ServiceViewContributor<?>> CONTRIBUTOR_EP_NAME =
    ExtensionPointName.create("com.intellij.serviceViewContributor");

  static <V extends ServiceViewContributor<?>> @Nullable V findRootContributor(@NotNull Class<V> contributorClass) {
    return CONTRIBUTOR_EP_NAME.findExtension(contributorClass);
  }

  /// @return a [ServiceViewDescriptor] for this (parent) node
  @NotNull
  ServiceViewDescriptor getViewDescriptor(@NotNull Project project);

  /// @return list of child domain model entities, contained in this [ServiceViewContributor]
  @NotNull
  List<T> getServices(@NotNull Project project);

  /// @return a [ServiceViewDescriptor] for the child node [T]
  @NotNull
  ServiceViewDescriptor getServiceDescriptor(@NotNull Project project, @NotNull T service);
}
