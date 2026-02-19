// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.services;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface ServiceViewGroupingContributor<T, G> extends ServiceViewContributor<T> {
  @NotNull
  List<G> getGroups(@NotNull T service);

  @NotNull
  ServiceViewDescriptor getGroupDescriptor(@NotNull G group);
}
