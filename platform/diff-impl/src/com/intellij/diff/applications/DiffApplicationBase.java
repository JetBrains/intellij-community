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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationStarterEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
public abstract class DiffApplicationBase extends ApplicationStarterEx {
  protected static final String NULL_PATH = "/dev/null";

  protected static final Logger LOG = Logger.getInstance(DiffApplicationBase.class);

  protected abstract boolean checkArguments(@NotNull String[] args);

  @NotNull
  protected abstract String getUsageMessage();

  protected abstract void processCommand(@NotNull String[] args, @Nullable String currentDirectory)
    throws Exception;

  //
  // Impl
  //

  @Override
  public boolean isHeadless() {
    return false;
  }

  @Override
  public void processExternalCommandLine(@NotNull String[] args, @Nullable String currentDirectory) {
    if (!checkArguments(args)) {
      Messages.showMessageDialog(getUsageMessage(), StringUtil.toTitleCase(getCommandName()), Messages.getInformationIcon());
      return;
    }
    try {
      processCommand(args, currentDirectory);
    }
    catch (Exception e) {
      Messages.showMessageDialog(String.format("Error showing %s: %s", getCommandName(), e.getMessage()),
                                 StringUtil.toTitleCase(getCommandName()),
                                 Messages.getErrorIcon());
    }
    finally {
      saveAll();
    }
  }

  private static void saveAll() {
    FileDocumentManager.getInstance().saveAllDocuments();
    ApplicationManager.getApplication().saveSettings();
  }

  @Override
  public void premain(String[] args) {
    if (!checkArguments(args)) {
      System.out.println(getUsageMessage());
      System.exit(1);
    }
  }

  @Override
  public void main(String[] args) {
    try {
      processCommand(args, null);
    }
    catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
    catch (Throwable t) {
      t.printStackTrace();
      System.exit(2);
    }
    finally {
      saveAll();
    }

    System.exit(0);
  }

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

  private static void refreshAndEnsureFilesValid(@NotNull List<VirtualFile> files) throws Exception {
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
  public List<VirtualFile> replaceNullsWithEmptyFile(@NotNull List<VirtualFile> contents) {
    return ContainerUtil.map(contents, file -> {
      return ObjectUtils.notNull(file, () -> new LightVirtualFile(NULL_PATH, PlainTextFileType.INSTANCE, ""));
    });
  }


  @Override
  public boolean canProcessExternalCommandLine() {
    return true;
  }

  @Nullable
  protected static Project guessProject(@NotNull List<VirtualFile> files) {
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
