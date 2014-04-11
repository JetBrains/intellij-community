/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.indexing;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.caches.FileContent;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.CacheUpdateRunner;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CollectingContentIterator;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Eugene Zhuravlev
 * @since Jan 29, 2008
 */
public class UnindexedFilesUpdater extends DumbModeTask {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.UnindexedFilesUpdater");

  private final FileBasedIndexImpl myIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();
  private final Project myProject;
  private final boolean myOnStartup;

  public UnindexedFilesUpdater(final Project project, boolean onStartup) {
    myProject = project;
    myOnStartup = onStartup;
  }

  private void updateUnindexedFiles(ProgressIndicator indicator) {
    PushedFilePropertiesUpdater.getInstance(myProject).performPushTasks(indicator);

    indicator.setIndeterminate(true);
    indicator.setText(IdeBundle.message("progress.indexing.scanning"));

    CollectingContentIterator finder = myIndex.createContentIterator(indicator);
    long l = System.currentTimeMillis();
    myIndex.iterateIndexableFiles(finder, myProject, indicator);
    myIndex.filesUpdateEnumerationFinished();

    LOG.info("Indexable files iterated in " + (System.currentTimeMillis() - l) + " ms");
    List<VirtualFile> files = finder.getFiles();

    if (myOnStartup && !ApplicationManager.getApplication().isUnitTestMode()) {
      // full VFS refresh makes sense only after it's loaded, i.e. after scanning files to index is finished
      ((StartupManagerImpl)StartupManager.getInstance(myProject)).scheduleInitialVfsRefresh();
    }

    if (files.isEmpty()) {
      return;
    }

    long started = System.currentTimeMillis();
    LOG.info("Unindexed files update started: " + files.size() + " files to update");

    indicator.setIndeterminate(false);
    indicator.setText(IdeBundle.message("progress.indexing.updating"));

    indexFiles(indicator, files);
    LOG.info("Unindexed files update done in " + (System.currentTimeMillis() - started) + " ms");
  }

  private void indexFiles(ProgressIndicator indicator, List<VirtualFile> files) {
    CacheUpdateRunner.processFiles(indicator, true, files, myProject, new Consumer<FileContent>() {
      @Override
      public void consume(FileContent content) {
        try {
          myIndex.indexFileContent(myProject, content);
        }
        finally {
          IndexingStamp.flushCache(content.getVirtualFile());
        }
      }
    });
  }

  @Override
  public void performInDumbMode(@NotNull ProgressIndicator indicator) {
    myIndex.filesUpdateStarted(myProject);
    try {
      updateUnindexedFiles(indicator);
    }
    catch (ProcessCanceledException e) {
      LOG.info("Unindexed files update canceled");
      throw e;
    } finally {
      myIndex.filesUpdateFinished(myProject);
    }
  }
}
