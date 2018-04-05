// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.ProjectBaseDirectory;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

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
  @SystemIndependent
  protected String getProjectPath(@NotNull Project project) {
    final ProjectBaseDirectory baseDir = ProjectBaseDirectory.getInstance(project);
    final VirtualFile baseDirVFile = baseDir.getBaseDir() != null ? baseDir.getBaseDir() : project.getBaseDir();
    return baseDirVFile != null ? baseDirVFile.getPath() : null;
  }
}
