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

/*
 * @author max
 */
package com.intellij.util.indexing;

import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.*;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class FileBasedIndexProjectHandler implements IndexableFileSet, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.FileBasedIndexProjectHandler");

  private final FileBasedIndex myIndex;
  private FileBasedIndexScanRunnableCollector myCollector;

  public FileBasedIndexProjectHandler(@NotNull Project project, FileBasedIndex index, FileBasedIndexScanRunnableCollector collector) {
    myIndex = index;
    myCollector = collector;

    if (ApplicationManager.getApplication().isInternal()) {
      project.getMessageBus().connect(this).subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
        @Override
        public void enteredDumbMode() { }

        @Override
        public void exitDumbMode() {
          LOG.info("Has changed files: " + (createChangedFilesIndexingTask(project) != null) + "; project=" + project);
        }
      });
    }

    StartupManager startupManager = StartupManager.getInstance(project);
    if (startupManager != null) {
      startupManager.registerPreStartupActivity(() -> {
        PushedFilePropertiesUpdater.getInstance(project).initializeProperties();

        // schedule dumb mode start after the read action we're currently in
        TransactionGuard.submitTransaction(project, () -> {
          if (FileBasedIndex.getInstance() instanceof FileBasedIndexImpl) {
            DumbService.getInstance(project).queueTask(new UnindexedFilesUpdater(project));
          }
        });

        myIndex.registerIndexableSet(this, project);
        project.getMessageBus().connect(this).subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
          private boolean removed;

          @Override
          public void projectClosing(Project eventProject) {
            if (eventProject == project && !removed) {
              removed = true;
              myIndex.removeIndexableSet(FileBasedIndexProjectHandler.this);
            }
          }
        });
      });
    }
  }

  @Override
  public boolean isInSet(@NotNull final VirtualFile file) {
    return myCollector.shouldCollect(file);
  }

  @Override
  public void iterateIndexableFilesIn(@NotNull final VirtualFile file, @NotNull final ContentIterator iterator) {
    VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {

        if (!isInSet(file)) return false;
        iterator.processFile(file);

        return true;
      }
    });
  }

  @Override
  public void dispose() {
    // done mostly for tests. In real life this is no-op, because the set was removed on project closing
    myIndex.removeIndexableSet(this);
  }

  @Nullable
  public static DumbModeTask createChangedFilesIndexingTask(final Project project) {
    final FileBasedIndex i = FileBasedIndex.getInstance();
    if (!(i instanceof FileBasedIndexImpl)) {
      return null;
    }

    final FileBasedIndexImpl index = (FileBasedIndexImpl)i;
    if (index.getChangedFileCount() < 20) {
      return null;
    }

    return new DumbModeTask(project.getComponent(FileBasedIndexProjectHandler.class)) {
      @Override
      public void performInDumbMode(@NotNull ProgressIndicator indicator) {
        long start = System.currentTimeMillis();
        Collection<VirtualFile> files = index.getFilesToUpdate(project);
        long calcDuration = System.currentTimeMillis() - start;

        indicator.setIndeterminate(false);
        indicator.setText(IdeBundle.message("progress.indexing.updating"));
        
        LOG.info("Reindexing refreshed files: " + files.size() + " to update, calculated in " + calcDuration + "ms");
        if (!files.isEmpty()) {
          PerformanceWatcher.Snapshot snapshot = PerformanceWatcher.takeSnapshot();
          reindexRefreshedFiles(indicator, files, project, index);
          snapshot.logResponsivenessSinceCreation("Reindexing refreshed files");
        }
      }

      @Override
      public String toString() {
        return getClass().getName() + "[" + index.dumpSomeChangedFiles() + "]";
      }
    };
  }

  private static void reindexRefreshedFiles(ProgressIndicator indicator,
                                            Collection<VirtualFile> files,
                                            final Project project,
                                            final FileBasedIndexImpl index) {
    CacheUpdateRunner.processFiles(indicator, true, files, project, content -> index.processRefreshedFile(project, content));
  }
}
