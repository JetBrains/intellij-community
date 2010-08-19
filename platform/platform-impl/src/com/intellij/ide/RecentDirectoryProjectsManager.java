/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.platform.ProjectBaseDirectory;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
@State(
  name = "RecentDirectoryProjectsManager",
  roamingType = RoamingType.DISABLED,
  storages = {
    @Storage(
      id ="other",
      file = "$APP_CONFIG$/other.xml"
    )}
)
public class RecentDirectoryProjectsManager extends RecentProjectsManagerBase {
  public RecentDirectoryProjectsManager(ProjectManager projectManager, MessageBus messageBus) {
    super(projectManager, messageBus);
  }

  @Nullable
  protected String getProjectPath(final Project project) {
    final ProjectBaseDirectory baseDir = ProjectBaseDirectory.getInstance(project);
    if (baseDir.getBaseDir() != null) {
      return FileUtil.toSystemDependentName(baseDir.getBaseDir().getPath());
    }
    return null;
  }

  protected void doOpenProject(final String projectPath, final Project projectToClose, final boolean forceOpenInNewFrame) {
    final VirtualFile projectDir = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(projectPath));
    if (projectDir != null) {
      PlatformProjectOpenProcessor.getInstance().doOpenProject(projectDir, projectToClose, forceOpenInNewFrame);
    }
  }
}
