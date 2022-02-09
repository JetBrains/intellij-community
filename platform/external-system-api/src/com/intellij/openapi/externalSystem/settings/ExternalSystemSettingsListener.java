// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.settings;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

/**
 * Defines callback for external system settings change.
 * <p/>
 * Implementations of this interface are not obliged to be thread-safe.
 * 
 * @param <S>  target project setting type
 */
public interface ExternalSystemSettingsListener<S extends ExternalProjectSettings> {
  /**
   * This method is present here only because IJ platform doesn't have corresponding messaging set up for 'project rename' event.
   *
   * @param oldName  old project name
   * @param newName  new project name
   */
  default void onProjectRenamed(@NotNull String oldName, @NotNull String newName) { }

  /**
   * Happens after loading of project settings
   *
   * @see AbstractExternalSystemSettings#loadState
   * @see com.intellij.openapi.components.PersistentStateComponent#loadState
   */
  default void onProjectsLoaded(@NotNull Collection<S> settings) { }

  default void onProjectsLinked(@NotNull Collection<S> settings) { }

  default void onProjectsUnlinked(@NotNull Set<String> linkedProjectPaths) {
  }

  /**
   * External system settings changes might affect project structure, e.g. switching from one external system version to another
   * one can trigger new binaries usage (different external system versions might use different file system directories
   * for holding dependencies).
   * <p/>
   * So, we might want to refresh project structure on external system setting change. However, there is a possible case that more
   * than one significant setting is changed during single editing session (e.g. a user opens external system settings, changes linked
   * project path and 'use auto-import' and then presses 'Ok'.). We don't want to trigger two refreshes then. That's why
   * it's possible to indicate that settings are changed in bulk now.
   * <p/>
   * {@link #onBulkChangeEnd()} is expected to be called at the 'finally' section which starts just after the call to
   * current method.
   */
  default void onBulkChangeStart() { }

  /**
   * @see #onBulkChangeStart()
   */
  default void onBulkChangeEnd() { }
}
