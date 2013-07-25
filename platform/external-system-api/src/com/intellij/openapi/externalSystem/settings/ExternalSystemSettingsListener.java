/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.externalSystem.settings;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

/**
 * Defines callback for external system settings change.
 * <p/>
 * Implementations of this interface are not obliged to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 4/3/13 4:13 PM
 * @param <S>  target project setting type
 */
public interface ExternalSystemSettingsListener<S extends ExternalProjectSettings> {

  /**
   * This method is present here only because IJ platform doesn't has corresponding messaging set up for 'project rename' event.
   * 
   * @param oldName  old project name
   * @param newName  new project name
   */
  void onProjectRenamed(@NotNull String oldName, @NotNull String newName);
  
  void onProjectsLinked(@NotNull Collection<S> settings);

  void onProjectsUnlinked(@NotNull Set<String> linkedProjectPaths);
  
  void onUseAutoImportChange(boolean currentValue, @NotNull String linkedProjectPath);

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
  void onBulkChangeStart();

  /**
   * @see #onBulkChangeStart()
   */
  void onBulkChangeEnd();
}
