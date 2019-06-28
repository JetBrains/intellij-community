// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.ProjectBaseDirectory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

// todo the only difference - usage of ProjectBaseDirectory Is it really make sense?
@State(name = "RecentDirectoryProjectsManager", storages = @Storage(value = "recentProjectDirectories.xml", roamingType = RoamingType.DISABLED))
public class RecentDirectoryProjectsManager extends RecentProjectsManagerBase {
  @Override
  @Nullable
  @SystemIndependent
  protected String getProjectPath(@NotNull Project project) {
    final ProjectBaseDirectory baseDir = ProjectBaseDirectory.getInstance(project);
    if (baseDir.getBaseDir() == null) {
      return project.getBasePath();
    }
    else {
      VirtualFile baseDirVFile = baseDir.getBaseDir();
      return baseDirVFile != null ? baseDirVFile.getPath() : null;
    }
  }
}
