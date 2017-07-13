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
package com.intellij.openapi.externalSystem;

import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * External system integration provides 'auto-import' feature, i.e. it listens for external system config files changes
 * and automatically runs external project refresh and sync.
 * <p/>
 * Only {@link AbstractExternalSystemSettings#getLinkedProjectsSettings() registered project's}
 * {@link ExternalProjectSettings#getExternalProjectPath() config files} are processed by default. However, there is a possible case
 * that there are other auxiliary config files/directories which modification should trigger external project refresh. This interface
 * is supposed to handle that situation, i.e. any {@link ExternalSystemManager external system implementation} which requires
 * the functionality described above should implement this interface.
 * 
 * @author Denis Zhdanov
 * @since 6/7/13 6:44 PM
 */
public interface ExternalSystemAutoImportAware {

  /**
   * This method serves to check if particular file/dir change should trigger external project refresh.
   * <p/>
   * <b>Note:</b> there is no need to handle here {@link ExternalProjectSettings#getExternalProjectPath() external project config files}
   * known to the ide as that functionality is built-in. Only auxiliary files should be processed.
   * <p/>
   * <b>Note2:</b> this method is assume to be called rather often, that's why it's very important to return from it quickly.
   * Caching and simple check algorithms are welcomed.
   * 
   * @param changedFileOrDirPath  changed file/dir path
   * @param project               current project
   * @return                      {@code null} if target change should not trigger external project refresh;
   *                              path to config file of an external project which should be refreshed
   */
  @Nullable
  String getAffectedExternalProjectPath(@NotNull String changedFileOrDirPath, @NotNull Project project);

  default List<File> getAffectedExternalProjectFiles(String projectPath, @NotNull Project project) {
    return Collections.emptyList();
  }
}
