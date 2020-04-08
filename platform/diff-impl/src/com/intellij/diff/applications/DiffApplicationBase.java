/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.applications;

import com.intellij.openapi.application.ApplicationStarterBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class DiffApplicationBase extends ApplicationStarterBase {
  protected static final String NULL_PATH = "/dev/null";

  protected static final Logger LOG = Logger.getInstance(DiffApplicationBase.class);

  protected DiffApplicationBase(@NotNull @NonNls String commandName, int... possibleArgumentsCount) {
    super(commandName, possibleArgumentsCount);
  }

  //
  // Impl
  //

  @NotNull
  public static List<VirtualFile> findFiles(@NotNull List<String> filePaths, @Nullable String currentDirectory) throws Exception {
    List<VirtualFile> files = new ArrayList<>();

    for (String path : filePaths) {
      if (NULL_PATH.equals(path)) {
        files.add(null);
      }
      else {
        VirtualFile virtualFile = findFile(path, currentDirectory);
        if (virtualFile == null) throw new Exception("Can't find file: " + path);
        files.add(virtualFile);
      }
    }

    refreshAndEnsureFilesValid(ContainerUtil.skipNulls(files));

    return files;
  }

  private static void refreshAndEnsureFilesValid(@NotNull List<? extends VirtualFile> files) throws Exception {
    VfsUtil.markDirtyAndRefresh(false, false, false, VfsUtilCore.toVirtualFileArray(files));

    for (VirtualFile file : files) {
      if (!file.isValid()) throw new Exception("Can't find file: " + file.getPresentableUrl());
    }
  }

  @Nullable
  public static VirtualFile findFile(@NotNull String path, @Nullable String currentDirectory) {
    File file = getFile(path, currentDirectory);
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    if (virtualFile == null) {
      LOG.warn(String.format("Can't find file: current directory - %s; path - %s", currentDirectory, path));
    }
    return virtualFile;
  }

  @NotNull
  public static File getFile(@NotNull String path, @Nullable String currentDirectory) {
    File file = new File(path);
    if (!file.isAbsolute() && currentDirectory != null) {
      file = new File(currentDirectory, path);
    }
    return file;
  }

  @NotNull
  public static List<VirtualFile> replaceNullsWithEmptyFile(@NotNull List<? extends VirtualFile> contents) {
    return ContainerUtil.map(contents, file -> file != null ? file : new LightVirtualFile(NULL_PATH, PlainTextFileType.INSTANCE, ""));
  }


  @Nullable
  protected static Project guessProject(@NotNull List<? extends VirtualFile> files) {
    Set<Project> projects = new HashSet<>();
    for (VirtualFile file : files) {
      projects.addAll(ProjectLocator.getInstance().getProjectsForFile(file));
    }

    if (projects.isEmpty()) {
      Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
      projects.addAll(ContainerUtil.filter(openProjects, project -> project.isInitialized() && !project.isDisposed()));
    }
    if (projects.isEmpty()) return null;

    Window recentFocusedWindow = WindowManagerEx.getInstanceEx().getMostRecentFocusedWindow();
    if (recentFocusedWindow instanceof IdeFrame) {
      Project recentFocusedProject = ((IdeFrame)recentFocusedWindow).getProject();
      if (recentFocusedProject != null && projects.contains(recentFocusedProject)) {
        return recentFocusedProject;
      }
    }

    return projects.iterator().next();
  }
}
