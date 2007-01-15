package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author Eugene Zhuravlev
 *         Date: Feb 10, 2004
 */
public class ProjectFileDirectoryRule implements GetDataRule {
  public Object getData(DataProvider dataProvider) {
    VirtualFile dir = (VirtualFile)dataProvider.getData(DataConstantsEx.PROJECT_FILE_DIRECTORY);
    if (dir == null) {
      final Project project = (Project)dataProvider.getData(DataConstants.PROJECT);
      if (project != null) {
        dir = project.getBaseDir();
      }
    }
    return dir;
  }
}
