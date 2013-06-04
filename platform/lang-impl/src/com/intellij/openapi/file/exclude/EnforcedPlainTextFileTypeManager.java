/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.file.exclude;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.WeakList;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Maintains a list of files marked as plain text in a local environment (configuration). Every time a project is loaded/open, it reads
 * files marked as plain text from a project into local environment (configuration). User actions (mark/unmark as plain text) are
 * synchronized between local and project configurations.
 *
 * @author Rustam Vishnyakov
 */
@State(name = "EnforcedPlainTextFileTypeManager", storages = {@Storage( file = StoragePathMacros.APP_CONFIG + "/plainTextFiles.xml")})
public class EnforcedPlainTextFileTypeManager extends PersistentFileSetManager implements ProjectManagerListener {
  private final Collection<Project> myProcessedProjects = new WeakList<Project>();
  private boolean myNeedsSync = true;

  public EnforcedPlainTextFileTypeManager() {
    ProjectManager.getInstance().addProjectManagerListener(this);
  }

  public boolean isMarkedAsPlainText(VirtualFile file) {
    if (myNeedsSync) {
      myNeedsSync = !syncWithOpenProjects();
    }
    return containsFile(file);
  }

  private boolean syncWithOpenProjects() {
    boolean success = true;
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    for (Project openProject : openProjects) {
      if (!myProcessedProjects.contains(openProject)) {
        if (!syncWithProject(openProject)) success = false;
      }
    }
    return success;
  }

  public static boolean isApplicableFor(@NotNull VirtualFile file) {
    if (file.isDirectory()) return false;
    FileType originalType = FileTypeManager.getInstance().getFileTypeByFileName(file.getName());
    return !originalType.isBinary() && originalType != FileTypes.PLAIN_TEXT && originalType != StdFileTypes.JAVA;
  }

  public void markAsPlainText(VirtualFile... files) {
    List<VirtualFile> filesToSync = new ArrayList<VirtualFile>();
    for (VirtualFile file : files) {
      if (addFile(file)) {
        filesToSync.add(file);
        FileBasedIndex.getInstance().requestReindex(file);
      }
    }
    fireRootsChanged(filesToSync, true);
  }

  public void unmarkPlainText(VirtualFile... files) {
    List<VirtualFile> filesToSync = new ArrayList<VirtualFile>();
    for (VirtualFile file : files) {
      if (removeFile(file)) {
        filesToSync.add(file);
        FileBasedIndex.getInstance().requestReindex(file);
      }
    }
    fireRootsChanged(filesToSync, false);
  }

  private static void fireRootsChanged(final Collection<VirtualFile> files, final boolean isAdded) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
          ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(EmptyRunnable.getInstance(), false, true);
          ProjectPlainTextFileTypeManager projectPlainTextFileTypeManager = ProjectPlainTextFileTypeManager.getInstance(project);
          for (VirtualFile file : files) {
            if (projectPlainTextFileTypeManager.hasProjectContaining(file)) {
              if (isAdded) {
                projectPlainTextFileTypeManager.addFile(file);
              }
              else {
                projectPlainTextFileTypeManager.removeFile(file);
              }
            }
          }
        }
      }
    });
  }

  private static class EnforcedPlainTextFileTypeManagerHolder {
    private static final EnforcedPlainTextFileTypeManager ourInstance = ServiceManager.getService(EnforcedPlainTextFileTypeManager.class);
  }

  public static EnforcedPlainTextFileTypeManager getInstance() {
    return EnforcedPlainTextFileTypeManagerHolder.ourInstance;
  }

  @Override
  public void projectOpened(Project project) {
    syncWithProject(project);
  }

  @Override
  public boolean canCloseProject(Project project) {
    return true;
  }

  @Override
  public void projectClosed(Project project) {
    myProcessedProjects.remove(project);
  }

  @Override
  public void projectClosing(Project project) {
  }

  private boolean syncWithProject(Project project) {
    if (project.isDisposed()) return false;
    ProjectPlainTextFileTypeManager projectPlainTextFileTypeManager = ProjectPlainTextFileTypeManager.getInstance(project);
    if (projectPlainTextFileTypeManager == null) return true;
    for (VirtualFile file : projectPlainTextFileTypeManager.getFiles()) {
      addFile(file);
    }
    if (!DirectoryIndex.getInstance(project).isInitialized()) return false;
    for (VirtualFile file : getFiles()) {
      if (projectPlainTextFileTypeManager.hasProjectContaining(file)) {
        projectPlainTextFileTypeManager.addFile(file);
      }
    }
    myProcessedProjects.add(project);
    return true;
  }
}
