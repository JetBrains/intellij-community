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
package com.intellij.compiler.impl;

import com.intellij.compiler.server.BuildManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.util.Consumer;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 * @since Jun 3, 2008
 *
 * A source file is scheduled for recompilation if
 * 1. its timestamp has changed
 * 2. one of its corresponding output files was deleted
 * 3. output root of containing module has changed
 *
 * An output file is scheduled for deletion if:
 * 1. corresponding source file has been scheduled for recompilation (see above)
 * 2. corresponding source file has been deleted
 */
public class TranslatingCompilerFilesMonitor {

  public TranslatingCompilerFilesMonitor(VirtualFileManager vfsManager, Application application) {
    vfsManager.addVirtualFileListener(new MyVfsListener(), application);
  }

  public static TranslatingCompilerFilesMonitor getInstance() {
    return ApplicationManager.getApplication().getComponent(TranslatingCompilerFilesMonitor.class);
  }

  private interface FileProcessor {
    void execute(VirtualFile file);
  }

  private static void processRecursively(final VirtualFile fromFile, final boolean dbOnly, final FileProcessor processor) {
    if (!(fromFile.getFileSystem() instanceof LocalFileSystem)) {
      return;
    }

    VfsUtilCore.visitChildrenRecursively(fromFile, new VirtualFileVisitor() {
      @NotNull @Override
      public Result visitFileEx(@NotNull VirtualFile file) {
        if (isIgnoredByBuild(file)) {
          return SKIP_CHILDREN;
        }

        if (!file.isDirectory()) {
          processor.execute(file);
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
  
  private static class MyVfsListener implements VirtualFileListener {
    @Override
    public void propertyChanged(@NotNull final VirtualFilePropertyEvent event) {
      if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
        final VirtualFile eventFile = event.getFile();
        if (isInContentOfOpenedProject(eventFile)) {
          final VirtualFile parent = event.getParent();
          if (parent != null) {
            final String oldName = (String)event.getOldValue();
            final String root = parent.getPath() + "/" + oldName;
            final Set<File> toMark = new THashSet<>(FileUtil.FILE_HASHING_STRATEGY);
            if (eventFile.isDirectory()) {
              VfsUtilCore.visitChildrenRecursively(eventFile, new VirtualFileVisitor() {
                private StringBuilder filePath = new StringBuilder(root);

                @Override
                public boolean visitFile(@NotNull VirtualFile child) {
                  if (child.isDirectory()) {
                    if (!Comparing.equal(child, eventFile)) {
                      filePath.append("/").append(child.getName());
                    }
                  }
                  else {
                    String childPath = filePath.toString();
                    if (!Comparing.equal(child, eventFile)) {
                      childPath += "/" + child.getName();
                    }
                    toMark.add(new File(childPath));
                  }
                  return true;
                }

                @Override
                public void afterChildrenVisited(@NotNull VirtualFile file) {
                  if (file.isDirectory() && !Comparing.equal(file, eventFile)) {
                    filePath.delete(filePath.length() - file.getName().length() - 1, filePath.length());
                  }
                }
              });
            }
            else {
              toMark.add(new File(root));
            }
            notifyFilesDeleted(toMark);
          }
          collectPathsAndNotify(eventFile, TranslatingCompilerFilesMonitor::notifyFilesChanged);
        }
      }
    }

    @Override
    public void contentsChanged(@NotNull final VirtualFileEvent event) {
      collectPathsAndNotify(event.getFile(), TranslatingCompilerFilesMonitor::notifyFilesChanged);
    }

    @Override
    public void fileCreated(@NotNull final VirtualFileEvent event) {
      collectPathsAndNotify(event.getFile(), TranslatingCompilerFilesMonitor::notifyFilesChanged);
    }

    @Override
    public void fileCopied(@NotNull final VirtualFileCopyEvent event) {
      collectPathsAndNotify(event.getFile(), TranslatingCompilerFilesMonitor::notifyFilesChanged);
    }

    @Override
    public void fileMoved(@NotNull VirtualFileMoveEvent event) {
      collectPathsAndNotify(event.getFile(), TranslatingCompilerFilesMonitor::notifyFilesChanged);
    }

    @Override
    public void beforeFileDeletion(@NotNull final VirtualFileEvent event) {
      collectPathsAndNotify(event.getFile(), TranslatingCompilerFilesMonitor::notifyFilesDeleted);
    }

    @Override
    public void beforeFileMovement(@NotNull final VirtualFileMoveEvent event) {
      collectPathsAndNotify(event.getFile(), TranslatingCompilerFilesMonitor::notifyFilesDeleted);
    }
  }

  private static void collectPathsAndNotify(final VirtualFile file, final Consumer<Collection<File>> notification) {
    if (!isIgnoredOrUnderIgnoredDirectory(file)) {
      final Set<File> pathsToMark = new THashSet<>(FileUtil.FILE_HASHING_STRATEGY);
      processRecursively(file, !isInContentOfOpenedProject(file), f -> pathsToMark.add(new File(f.getPath())));
      notification.consume(pathsToMark);
    }
  }

  private static boolean isIgnoredOrUnderIgnoredDirectory(final VirtualFile file) {
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

  private static boolean isIgnoredByBuild(VirtualFile file) {
    return
        FileTypeManager.getInstance().isFileIgnored(file) ||
        ProjectUtil.isProjectOrWorkspaceFile(file)        ||
        FileUtil.isAncestor(PathManager.getConfigPath(), file.getPath(), false); // is config file
  }

  private static void notifyFilesChanged(Collection<File> paths) {
    if (!paths.isEmpty()) {
      BuildManager.getInstance().notifyFilesChanged(paths);
    }
  }

  private static void notifyFilesDeleted(Collection<File> paths) {
    if (!paths.isEmpty()) {
      BuildManager.getInstance().notifyFilesDeleted(paths);
    }
  }

}
