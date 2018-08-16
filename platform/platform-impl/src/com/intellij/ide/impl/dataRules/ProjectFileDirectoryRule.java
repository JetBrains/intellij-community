// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author Eugene Zhuravlev
 */
public class ProjectFileDirectoryRule implements GetDataRule {
  @Override
  public Object getData(DataProvider dataProvider) {
    VirtualFile dir = PlatformDataKeys.PROJECT_FILE_DIRECTORY.getData(dataProvider);
    if (dir == null) {
      final Project project = CommonDataKeys.PROJECT.getData(dataProvider);
      if (project != null) {
        dir = project.getBaseDir();
      }
    }
    return dir;
  }
}
