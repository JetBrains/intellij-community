/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.ide.IdeBundle;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.components.AbstractProjectComponent;
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

public class FileBasedIndexProjectHandler extends AbstractProjectComponent implements IndexableFileSet {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.FileBasedIndexProjectHandler");

  private final FileBasedIndex myIndex;
  private FileBasedIndexScanRunnableCollector myCollector;

  public FileBasedIndexProjectHandler(FileBasedIndex index,
                                      Project project,
                                      FileBasedIndexScanRunnableCollector collector,
                                      ProjectManager projectManager) {
    super(project);
    myIndex = index;
    myCollector = collector;

    if (ApplicationManager.getApplication().isInternal()) {
      project.getMessageBus().connect().subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
        @Override
        public void enteredDumbMode() { }

        @Override
        public void exitDumbMode() {
          LOG.info("Has changed files: " + (createChangedFilesIndexingTask(project) != null) + "; project=" + project);
        }
      });
    }

    final StartupManagerEx startupManager = (StartupManagerEx)StartupManager.getInstance(project);
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
        projectManager.addProjectManagerListener(project, new ProjectManagerAdapter() {
          private boolean removed;
          @Override
          public void projectClosing(Project project1) {
            if (!removed) {
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
  public void disposeComponent() {
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
        final Collection<VirtualFile> files = index.getFilesToUpdate(project);
        indicator.setIndeterminate(false);
        indicator.setText(IdeBundle.message("progress.indexing.updating"));
        reindexRefreshedFiles(indicator, files, project, index);
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
