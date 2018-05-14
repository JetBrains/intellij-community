// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.groups;

import com.intellij.ide.actions.runAnything.activity.RunAnythingActivityProvider;
import com.intellij.ide.actions.runAnything.items.RunAnythingItem;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class RunAnythingHelpGroup<P extends RunAnythingActivityProvider>
  extends RunAnythingGroupBase {
  public static final ExtensionPointName<RunAnythingGroup> EP_NAME = ExtensionPointName.create("com.intellij.runAnything.helpGroup");

  @NotNull
  public abstract Collection<P> getProviders();

  @NotNull
  @Override
  public Collection<RunAnythingItem> getGroupItems(@NotNull DataContext dataContext) {
    return getProviders()
      .stream()
      .map(provider -> provider.getHelpItem(dataContext))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }
}