// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.impl;

import com.intellij.compiler.server.BuildManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.temp.TempFileSystemMarker;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.util.containers.FileCollectionFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A source file is scheduled for recompilation if
 * 1. its timestamp has changed
 * 2. one of its corresponding output files was deleted
 * 3. output root of containing module has changed
 *
 * An output file is scheduled for deletion if:
 * 1. corresponding source file has been scheduled for recompilation (see above)
 * 2. corresponding source file has been deleted
 */
public final class TranslatingCompilerFilesMonitor implements AsyncFileListener {
  public static TranslatingCompilerFilesMonitor getInstance() {
    return ApplicationManager.getApplication().getComponent(TranslatingCompilerFilesMonitor.class);
  }

  private static void processRecursively(@NotNull VirtualFile fromFile, final boolean dbOnly, @NotNull Consumer<VirtualFile> processor) {
    VfsUtilCore.visitChildrenRecursively(fromFile, new VirtualFileVisitor<Void>() {
      @NotNull @Override
      public Result visitFileEx(@NotNull VirtualFile file) {
        ProgressManager.checkCanceled();
        if (isIgnoredByBuild(file)) {
          return SKIP_CHILDREN;
        }

        if (!file.isDirectory()) {
          processor.accept(file);
        }
        return CONTINUE;
      }

      @Nullable
      @Override
      public Iterable<VirtualFile> getChildrenIterable(@NotNull VirtualFile file) {
        if (dbOnly) {
          return file.isDirectory()? ((NewVirtualFile)file).iterInDbChildren() : null;
        }
        if (file.equals(fromFile) || !file.isDirectory()) {
          return null; // skipping additional checks for the initial file and non-directory files
        }
        // optimization: for all files that are not under content of currently opened projects iterate over DB children
        return isInContentOfOpenedProject(file)? null : ((NewVirtualFile)file).iterInDbChildren();
      }
    });
  }

  private static boolean isToProcess(@NotNull VirtualFileSystem fileSystem) {
    return fileSystem instanceof LocalFileSystem && !(fileSystem instanceof TempFileSystemMarker);
  }

  private static boolean isInContentOfOpenedProject(@NotNull final VirtualFile file) {
    // probably need a read action to ensure that the project was not disposed during the iteration over the project list
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      if (!project.isInitialized() || !BuildManager.getInstance().isProjectWatched(project)) {
        continue;
      }
      if (ProjectRootManager.getInstance(project).getFileIndex().isInContent(file)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public ChangeApplier prepareChange(@NotNull List<? extends @NotNull VFileEvent> events) {
    if (events.isEmpty()) {
      return null;
    }
    Set<File> filesChanged = FileCollectionFactory.createCanonicalFileSet();
    Set<File> filesDeleted = FileCollectionFactory.createCanonicalFileSet();
    for (VFileEvent event : events) {
      if (!isToProcess(event.getFileSystem())) {
        continue;
      }
      if (event instanceof VFileDeleteEvent || event instanceof VFileMoveEvent) {
        collectPaths(event.getFile(), filesDeleted, true);
      }
      else if (event instanceof VFileContentChangeEvent) {
        collectPaths(event.getFile(), filesChanged, true);
      }
      else {
        handleFileRename(event, e -> collectDeletedPathsOnFileRename(e, filesDeleted));
      }
    }
    return new ChangeApplier() {
      @Override
      public void afterVfsChange() {
        after(events, filesDeleted, filesChanged);
      }
    };
  }

  private static void after(@NotNull List<? extends VFileEvent> events, Set<File> filesDeleted, Set<File> filesChanged) {
    for (VFileEvent event : events) {
      if (!isToProcess(event.getFileSystem())) {
        continue;
      }
      if (event instanceof VFileMoveEvent || event instanceof VFileCreateEvent) {
        collectPaths(event.getFile(), filesChanged, false);
      }
      else if (event instanceof VFileCopyEvent copyEvent) {
        collectPaths(copyEvent.findCreatedFile(), filesChanged, false);
      }
      else {
        handleFileRename(event, e -> collectPaths(e.getFile(), filesChanged, false));
      }
    }

    // If a file name differs ony in case, on case-insensitive file systems such name still denotes the same file.
    // In this situation filesDeleted and filesChanged sets will contain paths which are different only in case.
    // Thus, the order in which BuildManager is notified, is important:
    // first deleted paths notification and only then changed paths notification
    notifyFilesDeleted(filesDeleted);
    notifyFilesChanged(filesChanged);
  }

  private static void handleFileRename(@NotNull VFileEvent e, Consumer<VFilePropertyChangeEvent> action) {
    if (e instanceof VFilePropertyChangeEvent propChangeEvent && VirtualFile.PROP_NAME.equals(propChangeEvent.getPropertyName())) {
      final String oldName = (String)propChangeEvent.getOldValue();
      final String newName = (String)propChangeEvent.getNewValue();
      // Old and new names may actually be the same: sometimes such events are sent by VFS
      if (!Objects.equals(oldName, newName) && isInContentOfOpenedProject(propChangeEvent.getFile())) {
        action.accept(propChangeEvent);
      }
    }
  }

  private static void collectDeletedPathsOnFileRename(@NotNull VFilePropertyChangeEvent event, @NotNull Collection<? super File> filesDeleted) {
    final VirtualFile eventFile = event.getFile();
    final VirtualFile parent = eventFile.getParent();
    if (parent != null) {
      final String oldName = (String)event.getOldValue();
      final String root = parent.getPath() + "/" + oldName;
      if (eventFile.isDirectory()) {
        VfsUtilCore.visitChildrenRecursively(eventFile, new VirtualFileVisitor<Void>() {
          private final StringBuilder filePath = new StringBuilder(root);

          @Override
          public boolean visitFile(@NotNull VirtualFile child) {
            ProgressManager.checkCanceled();
            if (child.isDirectory()) {
              if (!child.equals(eventFile)) {
                filePath.append("/").append(child.getName());
              }
            }
            else {
              String childPath = filePath.toString();
              if (!child.equals(eventFile)) {
                childPath += "/" + child.getName();
              }
              filesDeleted.add(new File(childPath));
            }
            return true;
          }

          @Override
          public void afterChildrenVisited(@NotNull VirtualFile file) {
            if (file.isDirectory() && !file.equals(eventFile)) {
              filePath.delete(filePath.length() - file.getName().length() - 1, filePath.length());
            }
          }
        });
      }
      else {
        filesDeleted.add(new File(root));
      }
    }
  }

  private static void collectPaths(@Nullable VirtualFile file, @NotNull Collection<? super File> outFiles, boolean recursive) {
    if (file != null && !isIgnoredOrUnderIgnoredDirectory(file)) {
      if (recursive) {
        processRecursively(file, !isInContentOfOpenedProject(file), f -> outFiles.add(new File(f.getPath())));
      }
      else {
        ProgressManager.checkCanceled();
        outFiles.add(new File(file.getPath()));
      }
    }
  }

  private static boolean isIgnoredOrUnderIgnoredDirectory(@NotNull VirtualFile file) {
    if (isIgnoredByBuild(file)) {
      return true;
    }
    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    VirtualFile current = file.getParent();
    while (current != null) {
      if (fileTypeManager.isFileIgnored(current)) {
        return true;
      }
      current = current.getParent();
    }
    return false;
  }

  private static boolean isIgnoredByBuild(@NotNull VirtualFile file) {
    return
        FileTypeManager.getInstance().isFileIgnored(file) ||
        ProjectUtil.isProjectOrWorkspaceFile(file)        ||
        FileUtil.isAncestor(PathManager.getConfigPath(), file.getPath(), false); // is config file
  }

  private static void notifyFilesChanged(@NotNull Collection<? extends File> paths) {
    if (!paths.isEmpty()) {
      BuildManager.getInstance().notifyFilesChanged(paths);
    }
  }

  private static void notifyFilesDeleted(@NotNull Collection<? extends File> paths) {
    if (!paths.isEmpty()) {
      BuildManager.getInstance().notifyFilesDeleted(paths);
    }
  }
}
