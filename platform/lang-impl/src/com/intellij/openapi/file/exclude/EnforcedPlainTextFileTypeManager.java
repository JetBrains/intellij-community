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
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

/**
 * Retrieves plain text file type from open projects' configurations.
 *
 * @author Rustam Vishnyakov
 */
public class EnforcedPlainTextFileTypeManager implements ProjectManagerListener {
  private final Map<Project, Collection<VirtualFile>> myPlainTextFileSets = new ConcurrentWeakHashMap<Project, Collection<VirtualFile>>();
  private volatile boolean mySetsInitialized = false;
  private static final Object LOCK = new Object();

  public EnforcedPlainTextFileTypeManager() {
    ProjectManager.getInstance().addProjectManagerListener(this);
  }

  public boolean isMarkedAsPlainText(VirtualFile file) {
    if (!(file instanceof VirtualFileWithId) || file.isDirectory()) return false;
    if (!mySetsInitialized) {
      synchronized (LOCK) {
        if (!mySetsInitialized) {
          initPlainTextFileSets();
          mySetsInitialized = true;
        }
      }
    }
    for (Project project : myPlainTextFileSets.keySet()) {
      Collection<VirtualFile> projectSet = myPlainTextFileSets.get(project);
      if (projectSet != null && projectSet.contains(file)) return true;
    }
    return false;
  }

  private void initPlainTextFileSets() {
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    for (Project openProject : openProjects) {
      addProjectPlainTextFiles(openProject);
    }
  }

  public static boolean isApplicableFor(@NotNull VirtualFile file) {
    if (!(file instanceof VirtualFileWithId) || file.isDirectory()) return false;
    FileType originalType = FileTypeManager.getInstance().getFileTypeByFileName(file.getName());
    return !originalType.isBinary() && originalType != FileTypes.PLAIN_TEXT && originalType != StdFileTypes.JAVA;
  }

  public void markAsPlainText(@NotNull Project project, VirtualFile... files) {
    setPlainTextStatus(project, true, files);
  }

  public void resetOriginalFileType(@NotNull Project project, VirtualFile... files) {
    setPlainTextStatus(project, false, files);
  }

  private void setPlainTextStatus(@NotNull final Project project, final boolean isAdded, final VirtualFile... files) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(new Runnable() {
          @Override
          public void run() {
            ProjectPlainTextFileTypeManager projectPlainTextFileTypeManager = ProjectPlainTextFileTypeManager.getInstance(project);
            for (VirtualFile file : files) {
              if (projectPlainTextFileTypeManager.hasProjectContaining(file)) {
                ensureProjectFileSetAdded(project, projectPlainTextFileTypeManager);
                if (isAdded ?
                    projectPlainTextFileTypeManager.addFile(file) :
                    projectPlainTextFileTypeManager.removeFile(file)) {
                  FileBasedIndex.getInstance().requestReindex(file);
                }
              }
            }
          }
        }, false, true);
      }
    });
  }

  private void ensureProjectFileSetAdded(@NotNull Project project,
                                         @NotNull ProjectPlainTextFileTypeManager projectPlainTextFileTypeManager) {
    if (!myPlainTextFileSets.containsKey(project)) {
      myPlainTextFileSets.put(project, projectPlainTextFileTypeManager.getFiles());
    }
  }

  private static class EnforcedPlainTextFileTypeManagerHolder {
    private static final EnforcedPlainTextFileTypeManager ourInstance = ServiceManager.getService(EnforcedPlainTextFileTypeManager.class);
  }

  public static EnforcedPlainTextFileTypeManager getInstance() {
    return EnforcedPlainTextFileTypeManagerHolder.ourInstance;
  }

  @Override
  public void projectOpened(Project project) {
    addProjectPlainTextFiles(project);
  }

  @Override
  public boolean canCloseProject(Project project) {
    return true;
  }

  @Override
  public void projectClosed(Project project) {
    myPlainTextFileSets.remove(project);
  }

  @Override
  public void projectClosing(Project project) {
  }

  private void addProjectPlainTextFiles(@NotNull Project project) {
    if (!project.isDisposed()) {
      ProjectPlainTextFileTypeManager projectPlainTextFileTypeManager = ProjectPlainTextFileTypeManager.getInstance(project);
      if (projectPlainTextFileTypeManager != null) {
        myPlainTextFileSets.put(project, projectPlainTextFileTypeManager.getFiles());
      }
    }
  }
}
