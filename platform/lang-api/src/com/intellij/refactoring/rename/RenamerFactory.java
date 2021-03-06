// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

@ApiStatus.Experimental
@ApiStatus.OverrideOnly
public interface RenamerFactory {

  @ApiStatus.Internal
  ExtensionPointName<RenamerFactory> EP_NAME = new ExtensionPointName<>("com.intellij.renamerFactory");

  /**
   * Obtains needed data from {@code dataContext} and returns collections of available choices.
   * Returned instance must not hold a reference to the data context.
   * <p>
   * The user is presented with the popup if there are multiple choices available.
   * This can happen if there are several targets to rename.
   */
  @NotNull Collection<? extends @NotNull Renamer> createRenamers(@NotNull DataContext dataContext);
}
