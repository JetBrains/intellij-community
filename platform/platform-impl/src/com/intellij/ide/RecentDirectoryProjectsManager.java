// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.platform.ProjectBaseDirectory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import java.nio.file.Path;

// todo the only difference - usage of ProjectBaseDirectory Does it really make sense?
@State(name = "RecentDirectoryProjectsManager", storages = @Storage(value = "recentProjectDirectories.xml", roamingType = RoamingType.DISABLED))
public class RecentDirectoryProjectsManager extends RecentProjectsManagerBase {
  @Override
  @Nullable
  @SystemIndependent
  protected String getProjectPath(@NotNull Project project) {
    ProjectBaseDirectory baseDir = ProjectBaseDirectory.getInstance(project);
    Path baseDirFile = baseDir.getBase();
    return baseDirFile == null ? project.getBasePath() : baseDirFile.toString();
  }
}
