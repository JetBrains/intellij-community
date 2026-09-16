// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.search;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * A hook into the build of the searchable options index.
 *
 * <p>The {@code traverseUI} application starter opens each {@link SearchableConfigurable}, collects the labels of its
 * Swing components, and writes the result to the searchable options index. A helper extends this process. Use a helper
 * to prepare the environment that a configurable needs, or to add an option that the Swing traversal cannot see.
 *
 * <p>A helper runs only in the headless {@code traverseUI} starter. It never runs in a normal IDE session.
 *
 * <p>An exception from any method of a helper stops the build of the index. The step reports no partial result, so a
 * plugin that cannot run here belongs in {@code ProductModulesLayout.pluginModulesWithoutSearchableOptions}.
 *
 * <p>Register an implementation on the {@code com.intellij.search.traverseUiHelper} extension point. The extension
 * point is not dynamic.
 *
 * @see SearchableOptionEntry
 */
@ApiStatus.Internal
public interface TraverseUIHelper {
  ExtensionPointName<TraverseUIHelper> helperExtensionPoint = new ExtensionPointName<>("com.intellij.search.traverseUiHelper");

  /**
   * Prepares the environment before the starter indexes the first configurable.
   *
   * <p>The starter calls this method on the EDT, in a write-intent read action.
   */
  default void beforeStart() {}

  /**
   * Releases the resources after the starter saved the index files.
   *
   * <p>The starter calls this method on a background thread, after it wrote every index file. Use it to shut down a
   * process or a protocol that {@link #beforeStart()} started.
   */
  default void afterResultsAreSaved() {}

  /**
   * Adds options before the starter traverses the UI of the configurable.
   *
   * <p>The set is empty at this point. The starter calls this method on the EDT, in a write-intent read action.
   *
   * @param configurable the configurable that the starter indexes next
   * @param options      the mutable set of the options of this configurable. Add an entry to index it.
   */
  default void beforeConfigurable(@NotNull SearchableConfigurable configurable, @NotNull Set<SearchableOptionEntry> options) {}

  /**
   * Adds options after the starter traversed the UI of the configurable.
   *
   * <p>The set holds the labels that the Swing traversal found. The starter calls this method on the EDT, in a
   * write-intent read action.
   *
   * @param configurable the configurable that the starter indexed
   * @param options      the mutable set of the options of this configurable. Add an entry to index it.
   */
  default void afterConfigurable(@NotNull SearchableConfigurable configurable, @NotNull Set<SearchableOptionEntry> options) {}
}