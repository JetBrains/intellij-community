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
import com.intellij.util.indexing.FileBasedIndex;

import java.util.*;

/**
 * Maintains a list of files marked as plain text in a local environment (configuration). Every time a project is loaded/open, it reads
 * files marked as plain text from a project into local environment (configuration). User actions (mark/unmark as plain text) are
 * synchronized between local and project configurations.
 *
 * @author Rustam Vishnyakov
 */
@State(name = "EnforcedPlainTextFileTypeManager", storages = {@Storage( file = StoragePathMacros.APP_CONFIG + "/plainTextFiles.xml")})
public class EnforcedPlainTextFileTypeManager extends PersistentFileSetManager implements ProjectManagerListener {

  private Set<Project> myProcessedProjects = new HashSet<Project>();
  private boolean myNeedsSync = true;

  public EnforcedPlainTextFileTypeManager() {
    ProjectManager.getInstance().addProjectManagerListener(this);
  }

  public boolean isMarkedAsPlainText(VirtualFile file) {
    if (myNeedsSync) {
      myNeedsSync = !syncWithOpenProject();
    }
    return containsFile(file);
  }

  public boolean syncWithOpenProject() {
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    if (openProjects.length > 0) {
      Project firstOpenProject = openProjects[0];
      if (!myProcessedProjects.contains(firstOpenProject)) {
        return syncWithProject(firstOpenProject);
      }
      return true;
    }
    return false;
  }

  public static boolean isApplicableFor(VirtualFile file) {
    if (file.isDirectory()) return false;
    FileType originalType = FileTypeManager.getInstance().getFileTypeByFileName(file.getName());
    if (originalType.isBinary() ||
        originalType == FileTypes.PLAIN_TEXT ||
        originalType == StdFileTypes.JAVA) {
      return false;
    }
    return true;
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

  private static EnforcedPlainTextFileTypeManager ourInstance;

  public static EnforcedPlainTextFileTypeManager getInstance() {
    if (ourInstance == null) {
      ourInstance = ServiceManager.getService(EnforcedPlainTextFileTypeManager.class);
    }
    return ourInstance;
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
    if (myProcessedProjects.contains(project)) {
      myProcessedProjects.remove(project);
    }
  }

  @Override
  public void projectClosing(Project project) {
  }

  private boolean syncWithProject(Project project) {
    if (!DirectoryIndex.getInstance(project).isInitialized()) return false;
    myProcessedProjects.add(project);
    ProjectPlainTextFileTypeManager projectPlainTextFileTypeManager = ProjectPlainTextFileTypeManager.getInstance(project);
    if (projectPlainTextFileTypeManager == null) return true;
    for (VirtualFile file : projectPlainTextFileTypeManager.getFiles()) {
      addFile(file);
    }
    for (VirtualFile file : getFiles()) {
      if (projectPlainTextFileTypeManager.hasProjectContaining(file)) {
        projectPlainTextFileTypeManager.addFile(file);
      }
    }
    return true;
  }
}
