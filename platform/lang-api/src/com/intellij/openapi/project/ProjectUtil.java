/*
 * @author max
 */
package com.intellij.openapi.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public class ProjectUtil {
  private ProjectUtil() {
  }

  public static String calcRelativeToProjectPath(final VirtualFile file, final Project project) {
    String url = file.getPresentableUrl();
    if (project == null) {
      return url;
    }
    else {
      final VirtualFile baseDir = project.getBaseDir();
      if (baseDir != null) {
        //noinspection ConstantConditions
        final String projectHomeUrl = baseDir.getPresentableUrl();
        if (url.startsWith(projectHomeUrl)) {
          url = "..." + url.substring(projectHomeUrl.length());
        }
      }
      final Module module = ModuleUtil.findModuleForFile(file, project);
      if (module == null) return url;
      return new StringBuffer().append("[").append(module.getName()).append("] - ").append(url).toString();
    }
  }

  @Nullable
  public static Project guessProjectForFile(VirtualFile file) {
    return ProjectLocator.getInstance().guessProjectForFile(file);
  }
}