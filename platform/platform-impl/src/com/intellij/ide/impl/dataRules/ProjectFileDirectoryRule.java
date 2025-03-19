// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
@ApiStatus.Internal
public final class ProjectFileDirectoryRule implements GetDataRule {
  @Override
  public Object getData(@NotNull DataProvider dataProvider) {
    VirtualFile dir = PlatformCoreDataKeys.PROJECT_FILE_DIRECTORY.getData(dataProvider);
    if (dir == null) {
      final Project project = CommonDataKeys.PROJECT.getData(dataProvider);
      if (project != null) {
        dir = project.getBaseDir();
      }
    }
    return dir;
  }
}