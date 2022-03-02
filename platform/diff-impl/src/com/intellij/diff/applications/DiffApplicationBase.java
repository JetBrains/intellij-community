// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.applications;

import com.intellij.openapi.application.ApplicationStarterBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class DiffApplicationBase extends ApplicationStarterBase {
  @NlsSafe protected static final String NULL_PATH = "/dev/null";

  protected static final Logger LOG = Logger.getInstance(DiffApplicationBase.class);

  protected DiffApplicationBase(int... possibleArgumentsCount) {
    super(possibleArgumentsCount);
  }

  //
  // Impl
  //

  @NotNull
  public static List<VirtualFile> findFilesOrThrow(@NotNull List<String> filePaths, @Nullable String currentDirectory) throws Exception {
    List<VirtualFile> files = new ArrayList<>();

    for (String path : filePaths) {
      if (NULL_PATH.equals(path)) {
        files.add(null);
      }
      else {
        VirtualFile virtualFile = findFile(path, currentDirectory);
        if (virtualFile == null) throw new Exception(DiffBundle.message("cannot.find.file.error", path));
        files.add(virtualFile);
      }
    }

    refreshAndEnsureFilesValid(files);

    return files;
  }

  public static void refreshAndEnsureFilesValid(@NotNull List<? extends VirtualFile> files) throws Exception {
    VfsUtil.markDirtyAndRefresh(false, false, false, VfsUtilCore.toVirtualFileArray(files));

    for (VirtualFile file : files) {
      if (file != null && !file.isValid()) throw new Exception(DiffBundle.message("cannot.find.file.error", file.getPresentableUrl()));
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

  @Nullable
  public static VirtualFile findOrCreateFile(@NotNull String path, @Nullable String currentDirectory) throws IOException {
    File file = getFile(path, currentDirectory);
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    if (virtualFile == null) {
      boolean wasCreated = file.createNewFile();
      if (wasCreated) {
        virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
      }
    }
    if (virtualFile == null) {
      LOG.warn(String.format("Can't create file: current directory - %s; path - %s", currentDirectory, path));
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
