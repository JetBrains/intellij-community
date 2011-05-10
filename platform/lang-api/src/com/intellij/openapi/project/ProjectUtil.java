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

import com.intellij.ide.highlighter.InternalFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFilePathWrapper;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class ProjectUtil {
  @NonNls public static final String DIRECTORY_BASED_PROJECT_DIR = ".idea";

  private ProjectUtil() {
  }

  @Nullable
  public static String getProjectLocationString(@NotNull final Project project) {
    String projectPath = project.getLocation();
    return getLocationRelativeToUserHome(projectPath);
  }
  
  @Nullable
  public static String getLocationRelativeToUserHome(final String path) {
    if (path == null) return null;
    
    String _path = path;
    
    if ((SystemInfo.isLinux || SystemInfo.isMac)) {
      final File projectDir = new File(path);
      final File userHomeDir = new File(SystemProperties.getUserHome());
      try {
        if (FileUtil.isAncestor(userHomeDir, projectDir, true)) {
          _path = "~/" + FileUtil.getRelativePath(userHomeDir, projectDir);
        }
      }
      catch (IOException e) {
        // nothing
      }
    }

    return _path;
  }

  public static String calcRelativeToProjectPath(final VirtualFile file, final Project project, final boolean includeFilePath) {
    if (file instanceof VirtualFilePathWrapper) {
      return includeFilePath ? ((VirtualFilePathWrapper)file).getPresentablePath() : file.getName();
    }    
    String url = includeFilePath ? file.getPresentableUrl() : file.getName();
    if (project == null) {
      return url;
    }
    else {
      final VirtualFile baseDir = project.getBaseDir();
      if (baseDir != null && includeFilePath) {
        //noinspection ConstantConditions
        final String projectHomeUrl = baseDir.getPresentableUrl();
        if (url.startsWith(projectHomeUrl)) {
          url = "..." + url.substring(projectHomeUrl.length());
        }
      }
      final Module module = ModuleUtil.findModuleForFile(file, project);
      
      if (SystemInfo.isMac && file.getFileSystem() instanceof JarFileSystem) {
        final VirtualFile fileForJar = ((JarFileSystem)file.getFileSystem()).getVirtualFileForJar(file);
        if (fileForJar != null) {
          final OrderEntry libraryEntry = LibraryUtil.findLibraryEntry(file, project);
          if (libraryEntry != null) {
            if (libraryEntry instanceof JdkOrderEntry) {
              url = new StringBuilder(url).append(" - [").append(((JdkOrderEntry)libraryEntry).getJdkName()).append("]").toString();
            } else {
              url = new StringBuilder(url).append(" - [").append(libraryEntry.getPresentableName()).append("]").toString();
            }
          } else {
            url = new StringBuilder(url).append(" - [").append(fileForJar.getName()).append("]").toString();
          }
        }
      }
      
      if (module == null) return url;
      return SystemInfo.isMac ? new StringBuffer().append(url).append(" - [").append(module.getName()).append("]").toString() :
        new StringBuffer().append("[").append(module.getName()).append("] - ").append(url).toString();
    }
  }  
  
  public static String calcRelativeToProjectPath(final VirtualFile file, final Project project) {
    return calcRelativeToProjectPath(file, project, true);
  }

  @Nullable
  public static Project guessProjectForFile(VirtualFile file) {
    return ProjectLocator.getInstance().guessProjectForFile(file);
  }

  public static boolean isProjectOrWorkspaceFile(final VirtualFile file) {
    return isProjectOrWorkspaceFile(file, file.getFileType());
  }

  public static boolean isProjectOrWorkspaceFile(final VirtualFile file,
                                                 final FileType fileType) {
    if (fileType instanceof InternalFileType) return true;
    return file.getPath().contains("/"+ DIRECTORY_BASED_PROJECT_DIR +"/");
  }
}
