/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.dvcs;

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;

/**
 * @author Kirill Likhodedov
 */
public class DvcsUtil {

  public static void installStatusBarWidget(@NotNull Project project, @NotNull StatusBarWidget widget) {
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
    if (statusBar != null) {
      statusBar.addWidget(widget, "after " + (SystemInfo.isMac ? "Encoding" : "InsertOverwrite"), project);
    }
  }

  public static void removeStatusBarWidget(@NotNull Project project, @NotNull StatusBarWidget widget) {
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
    if (statusBar != null) {
      statusBar.removeWidget(widget.ID());
    }
  }

  @NotNull
  public static String getShortRepositoryName(@NotNull Project project, @NotNull VirtualFile root) {
    VirtualFile projectDir = project.getBaseDir();

    String repositoryPath = root.getPresentableUrl();
    if (projectDir != null) {
      String relativePath = VfsUtilCore.getRelativePath(root, projectDir, File.separatorChar);
      if (relativePath != null) {
        repositoryPath = relativePath;
      }
    }

    return repositoryPath.isEmpty() ? root.getName() : repositoryPath;
  }

  @NotNull
  public static String getShortRepositoryName(@NotNull Repository repository) {
    return getShortRepositoryName(repository.getProject(), repository.getRoot());
  }

  @NotNull
  public static String getShortNames(@NotNull Collection<? extends Repository> repositories) {
    return StringUtil.join(repositories, new Function<Repository, String>() {
      @Override
      public String fun(Repository repository) {
        return getShortRepositoryName(repository);
      }
    }, ", ");
  }

  @NotNull
  public static String joinRootsPaths(@NotNull Collection<VirtualFile> roots) {
    return StringUtil.join(roots, new Function<VirtualFile, String>() {
      @Override
      public String fun(VirtualFile virtualFile) {
        return virtualFile.getPresentableUrl();
      }
    }, ", ");
  }

  public static boolean anyRepositoryIsFresh(Collection<? extends Repository> repositories) {
    for (Repository repository : repositories) {
      if (repository.isFresh()) {
        return true;
      }
    }
    return false;
  }
}
