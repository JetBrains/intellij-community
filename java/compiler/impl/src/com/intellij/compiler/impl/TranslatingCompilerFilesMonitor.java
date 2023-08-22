// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.impl;

import com.intellij.compiler.server.BuildManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProcessCanceledException;
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
import com.intellij.util.SmartList;
import com.intellij.util.containers.FileCollectionFactory;
import com.intellij.util.containers.SmartHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
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
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.TranslatingCompilerFilesMonitor");
  
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

  private static boolean isToProcess(VFileEvent event) {
    VirtualFileSystem fileSystem = event.getFileSystem();
    return fileSystem instanceof LocalFileSystem && !(fileSystem instanceof TempFileSystemMarker);
  }

  private static boolean isInContentOfOpenedProject(@NotNull final VirtualFile file) {
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
    List<VFileEvent> processLaterEvents = new SmartList<>();
    for (VFileEvent event : events) {
      if (!isToProcess(event)) {
        continue;
      }
      if (isAfterProcessEvent(event)) {
        processLaterEvents.add(event);
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
    return processLaterEvents.isEmpty() && filesDeleted.isEmpty() && filesChanged.isEmpty()? null : new ChangeApplier() {
      @Override
      public void afterVfsChange() {
        after(processLaterEvents, filesDeleted, filesChanged);
      }
    };
  }

  private static boolean isAfterProcessEvent(VFileEvent e) {
    return e instanceof VFileMoveEvent || e instanceof VFileCreateEvent || e instanceof VFileCopyEvent || isRenameEvent(e);
  }

  private static boolean isRenameEvent(@NotNull VFileEvent e) {
    if (e instanceof VFilePropertyChangeEvent propChangeEvent && VirtualFile.PROP_NAME.equals(propChangeEvent.getPropertyName())) {
      final String oldName = (String)propChangeEvent.getOldValue();
      final String newName = (String)propChangeEvent.getNewValue();
      // Old and new names may actually be the same: sometimes such events are sent by VFS
      return !Objects.equals(oldName, newName);
    }
    return false;
  }

  private static void after(@NotNull List<? extends VFileEvent> events, Set<File> filesDeleted, Set<File> filesChanged) {
    var changedFilesCollector = new Consumer<VirtualFile>() {
      private final Set<VirtualFile> dirsToTraverse = new SmartHashSet<>();
      
      @Override
      public void accept(VirtualFile file) {
        if (file != null) {
          if (file.isDirectory()) {
            // need to traverse directories on IDE side, because JPS build
            // has less knowledge to efficiently traverse the directory up-down according to the project layout
            dirsToTraverse.add(file);
          }
          else {
            collectPaths(file, filesChanged, false);
          }
        }
      }

      public boolean traverseDirs() throws ProcessCanceledException{
        Set<VirtualFile> processed = new SmartHashSet<>();
        try {
          for (VirtualFile root : dirsToTraverse) {
            if (!root.isValid()) {
              if (LOG.isDebugEnabled()) {
                LOG.debug("File invalidated while processing VFS events, clearing build state: " + root.getPath());
              }
              return false;
            }
            collectPaths(root, filesChanged, true);
            processed.add(root);
          }
        }
        finally {
          dirsToTraverse.removeAll(processed);
        }
        return true;
      }

      public boolean hasPaths() {
        return !filesChanged.isEmpty() || !dirsToTraverse.isEmpty();
      }
    };

    for (VFileEvent event : events) {
      if (event instanceof VFileMoveEvent || event instanceof VFileCreateEvent) {
        changedFilesCollector.accept(event.getFile());
      }
      else if (event instanceof VFileCopyEvent copyEvent) {
        changedFilesCollector.accept(copyEvent.findCreatedFile());
      }
      else {
        handleFileRename(event, e -> changedFilesCollector.accept(e.getFile()));
      }
    }

    // If a file name differs ony in case, on case-insensitive file systems such name still denotes the same file.
    // In this situation filesDeleted and filesChanged sets will contain paths which are different only in case.
    // Thus, the order in which BuildManager is notified, is important:
    // first deleted paths notification and only then changed paths notification
    BuildManager buildManager = BuildManager.getInstance();
    buildManager.notifyFilesDeleted(filesDeleted);
    if (changedFilesCollector.hasPaths()) {
      buildManager.notifyFilesChanged(() -> {
        // traversing dirs may take time and block UI thread, so traversing them in a non-blocking read-action in background
        if (changedFilesCollector.dirsToTraverse.isEmpty()) {
          return filesChanged;
        }
        if (ReadAction.nonBlocking(changedFilesCollector::traverseDirs).executeSynchronously()) {
          return filesChanged;
        }
        // force FS rescan on the next build, because information from event may be incomplete
        buildManager.clearState();
        return Collections.emptyList();
      });
    }
  }

  private static void handleFileRename(@NotNull VFileEvent e, Consumer<VFilePropertyChangeEvent> action) {
    if (isRenameEvent(e)) {
      VFilePropertyChangeEvent propChangeEvent = (VFilePropertyChangeEvent)e;
      if (isInContentOfOpenedProject(propChangeEvent.getFile())) {
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

  private static void collectPaths(@Nullable VirtualFile file, @NotNull Collection<? super File> outFiles, boolean recursive) throws ProcessCanceledException {
    if (file != null && !isIgnoredOrUnderIgnoredDirectory(file)) {
      if (recursive) {
        processRecursively(file, !isInContentOfOpenedProject(file), f -> outFiles.add(new File(f.getPath())));
      }
      else {
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
}
