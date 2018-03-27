/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.util.FileContentUtilCore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

/**
 * Retrieves plain text file type from open projects' configurations.
 *
 * @author Rustam Vishnyakov
 */
public class EnforcedPlainTextFileTypeManager implements ProjectManagerListener {
  private final Map<Project, Collection<VirtualFile>> myPlainTextFileSets = ContainerUtil.createConcurrentWeakMap();
  private volatile boolean mySetsInitialized;
  private final Object LOCK = new Object();

  public EnforcedPlainTextFileTypeManager() {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(ProjectManager.TOPIC, this);
  }

  public boolean isMarkedAsPlainText(@NotNull VirtualFile file) {
    if (!(file instanceof VirtualFileWithId) || file.isDirectory()) return false;
    if (!mySetsInitialized) {
      synchronized (LOCK) {
        if (!mySetsInitialized) {
          initPlainTextFileSets();
          mySetsInitialized = true;
        }
      }
    }
    if (!myPlainTextFileSets.isEmpty()) {
      for (Collection<VirtualFile> projectSet : myPlainTextFileSets.values()) {
        if (projectSet != null && projectSet.contains(file)) return true;
      }
    }
    return false;
  }

  private void initPlainTextFileSets() {
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    for (Project openProject : openProjects) {
      addProjectPlainTextFiles(openProject);
    }
  }

  public static boolean isApplicableFor(@Nullable VirtualFile file) {
    if (!(file instanceof VirtualFileWithId) || file.isDirectory()) return false;
    if (ScratchUtil.isScratch(file)) return false;
    FileType originalType = FileTypeManager.getInstance().getFileTypeByFileName(file.getName());
    return !originalType.isBinary() && originalType != FileTypes.PLAIN_TEXT && originalType != StdFileTypes.JAVA;
  }

  public void markAsPlainText(@NotNull Project project, @NotNull VirtualFile... files) {
    setPlainTextStatus(project, true, files);
  }

  public void resetOriginalFileType(@NotNull Project project, @NotNull VirtualFile... files) {
    setPlainTextStatus(project, false, files);
  }

  private void setPlainTextStatus(@NotNull final Project project, final boolean isAdded, @NotNull final VirtualFile... files) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      ProjectPlainTextFileTypeManager projectManager = ProjectPlainTextFileTypeManager.getInstance(project);
      for (VirtualFile file : files) {
        if (projectManager.isInContent(file) || projectManager.isInLibrarySource(file)) {
          ensureProjectFileSetAdded(project, projectManager);
          if (isAdded ?
              projectManager.addFile(file) :
              projectManager.removeFile(file)) {
            FileBasedIndex.getInstance().requestReindex(file);
          }
        }
      }
      FileContentUtilCore.reparseFiles(files);
    });
  }

  private void ensureProjectFileSetAdded(@NotNull Project project,
                                         @NotNull ProjectPlainTextFileTypeManager projectPlainTextFileTypeManager) {
    if (!myPlainTextFileSets.containsKey(project)) {
      myPlainTextFileSets.put(project, projectPlainTextFileTypeManager.getFiles());
    }
  }

  public static EnforcedPlainTextFileTypeManager getInstance() {
    return ServiceManager.getService(EnforcedPlainTextFileTypeManager.class);
  }

  @Override
  public void projectOpened(Project project) {
    addProjectPlainTextFiles(project);
  }

  @Override
  public void projectClosed(Project project) {
    myPlainTextFileSets.remove(project);
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
