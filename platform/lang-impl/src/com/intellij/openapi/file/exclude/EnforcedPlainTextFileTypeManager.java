// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.file.exclude;

import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.util.ArrayUtil;
import com.intellij.util.FileContentUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Retrieves plain text file type from open projects' configurations.
 *
 * @author Rustam Vishnyakov
 */
@Service
public final class EnforcedPlainTextFileTypeManager {
  // optimization: manual arrays to optimize iteration
  private Collection/*<VirtualFile>*/[] explicitlyMarkedSets = new Collection[0];
  private Project[] explicitlyMarkedProjects = new Project[0];

  public EnforcedPlainTextFileTypeManager() {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(@NotNull Project project) {
        addProjectPlainTextFiles(project);
      }

      @Override
      public void projectClosed(@NotNull Project project) {
        ensureProjectFileSetRemoved(project);
      }

      private void addProjectPlainTextFiles(@NotNull Project project) {
        if (!project.isDisposed()) {
          ensureProjectFileUpToDate(project);
          Disposer.register(project, () -> ensureProjectFileSetRemoved(project));
        }
      }
    });
  }

  public static EnforcedPlainTextFileTypeManager getInstance() {
    return ApplicationManager.getApplication().getService(EnforcedPlainTextFileTypeManager.class);
  }

  public boolean isMarkedAsPlainText(@NotNull VirtualFile file) {
    if (!(file instanceof VirtualFileWithId) || file.isDirectory()) {
      return false;
    }
    for (Collection explicitlyMarked : explicitlyMarkedSets) {
      if (explicitlyMarked.contains(file)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isApplicableFor(@Nullable VirtualFile file) {
    if (!(file instanceof VirtualFileWithId) || file.isDirectory()) return false;
    if (ScratchUtil.isScratch(file)) return false;
    FileType originalType = FileTypeManager.getInstance().getFileTypeByFileName(file.getNameSequence());
    return !originalType.isBinary() && originalType != FileTypes.PLAIN_TEXT && originalType != StdFileTypes.JAVA;
  }

  public void markAsPlainText(@NotNull Project project, VirtualFile @NotNull ... files) {
    setPlainTextStatus(project, true, files);
  }

  public void resetOriginalFileType(@NotNull Project project, VirtualFile @NotNull ... files) {
    setPlainTextStatus(project, false, files);
  }

  private void setPlainTextStatus(@NotNull final Project project, final boolean toAdd, final VirtualFile @NotNull ... files) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
      ProjectPlainTextFileTypeManager plainTextFileTypeManager = ProjectPlainTextFileTypeManager.getInstance(project);
      for (VirtualFile file : files) {
        if (fileIndex.isInContent(file) || fileIndex.isInLibrarySource(file) || fileIndex.isExcluded(file)) {
          boolean changed = toAdd ?
                            plainTextFileTypeManager.addFile(file) :
                            plainTextFileTypeManager.removeFile(file);
          if (changed) {
            ensureProjectFileUpToDate(project);
          }
        }
      }
      FileContentUtilCore.reparseFiles(files);
    });
  }

  private void ensureProjectFileUpToDate(@NotNull Project project) {
    int i = ArrayUtil.indexOf(explicitlyMarkedProjects, project);
    ProjectPlainTextFileTypeManager projectPlainTextFileTypeManager = ProjectPlainTextFileTypeManager.getInstance(project);
    if (i == -1) {
      explicitlyMarkedProjects = ArrayUtil.append(explicitlyMarkedProjects, project);
      explicitlyMarkedSets = ArrayUtil.append(explicitlyMarkedSets, projectPlainTextFileTypeManager.getFiles());
    }
    else {
      explicitlyMarkedSets[i] = projectPlainTextFileTypeManager.getFiles();
    }
  }
  private void ensureProjectFileSetRemoved(@NotNull Project project) {
    int i = ArrayUtil.indexOf(explicitlyMarkedProjects, project);
    if (i >= 0) {
      explicitlyMarkedProjects = ArrayUtil.remove(explicitlyMarkedProjects, i);
      explicitlyMarkedSets = ArrayUtil.remove(explicitlyMarkedSets, i);
    }
  }
}
