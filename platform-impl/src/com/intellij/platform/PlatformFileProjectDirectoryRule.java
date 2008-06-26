package com.intellij.platform;

import com.intellij.ide.impl.dataRules.GetDataRule;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;

/**
 * @author yole
 */
public class PlatformFileProjectDirectoryRule implements GetDataRule {
  public Object getData(final DataProvider dataProvider) {
    Project project = (Project) dataProvider.getData(PlatformDataKeys.PROJECT.getName());
    if (project == null) return null;
    return ProjectBaseDirectory.getInstance(project).getBaseDir();
  }
}
