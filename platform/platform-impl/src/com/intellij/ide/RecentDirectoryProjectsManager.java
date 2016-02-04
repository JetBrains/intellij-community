/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.platform.ProjectBaseDirectory;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
  name = "RecentDirectoryProjectsManager",
  storages = {
    @Storage(value = "recentProjectDirectories.xml", roamingType = RoamingType.DISABLED),
    @Storage(value = "other.xml", deprecated = true)
  }
)
public class RecentDirectoryProjectsManager extends RecentProjectsManagerBase {
  public RecentDirectoryProjectsManager(MessageBus messageBus) {
    super(messageBus);
  }

  @Override
  @Nullable
  protected String getProjectPath(@NotNull Project project) {
    final ProjectBaseDirectory baseDir = ProjectBaseDirectory.getInstance(project);
    final VirtualFile baseDirVFile = baseDir.getBaseDir() != null ? baseDir.getBaseDir() : project.getBaseDir();
    return baseDirVFile != null ? FileUtil.toSystemDependentName(baseDirVFile.getPath()) : null;
  }

  @Override
  protected void doOpenProject(@NotNull String projectPath, Project projectToClose, boolean forceOpenInNewFrame) {
    final VirtualFile projectDir = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(projectPath));
    if (projectDir != null) {
      PlatformProjectOpenProcessor.doOpenProject(projectDir, projectToClose, forceOpenInNewFrame, -1, null, true);
    }
  }
}
