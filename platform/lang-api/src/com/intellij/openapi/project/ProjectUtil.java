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

/*
 * @author max
 */
package com.intellij.openapi.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFilePathWrapper;
import org.jetbrains.annotations.Nullable;

public class ProjectUtil {
  private ProjectUtil() {
  }

  public static String calcRelativeToProjectPath(final VirtualFile file, final Project project) {
    if (file instanceof VirtualFilePathWrapper) {
      return ((VirtualFilePathWrapper)file).getPresentablePath();
    }    
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
