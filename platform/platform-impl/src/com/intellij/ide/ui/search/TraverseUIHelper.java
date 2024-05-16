// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.search;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public interface TraverseUIHelper {
  ExtensionPointName<TraverseUIHelper> helperExtensionPoint = new ExtensionPointName<>("com.intellij.search.traverseUiHelper");

  /**
   * Invoked before indexing SearchableConfigurables
   */
  default void beforeStart() {}

  /**
   * Invoked after indexing all SearchableConfigurables and results are saved
   */
  default void afterResultsAreSaved() {}

  /**
   * Invoked before indexing a SearchableConfigurable
   */
  default void beforeConfigurable(@NotNull SearchableConfigurable configurable, @NotNull Set<OptionDescription> options) {}

  /**
   * Invoked after indexing a SearchableConfigurable
   */
  default void afterConfigurable(@NotNull SearchableConfigurable configurable, @NotNull Set<OptionDescription> options) {}
}