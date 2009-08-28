/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.ide.startup;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * @author max
 */
public class FileSystemSynchronizer {
  private volatile ArrayList<CacheUpdater> myUpdaters = new ArrayList<CacheUpdater>();
  private volatile boolean myIsCancelable = false;

  public void registerCacheUpdater(@NotNull CacheUpdater cacheUpdater) {
    final ArrayList<CacheUpdater> updaters = myUpdaters;
    if (updaters == null) {
      throw new AssertionError("Cannot add cache updater during synchronization session, all updaters should be added before it");
    }
    updaters.add(cacheUpdater);
  }

  public void setCancelable(boolean isCancelable) {
    myIsCancelable = isCancelable;
  }

  public void executeFileUpdate() {
    //final long l = System.currentTimeMillis();

    final SyncSession syncSession = collectFilesToUpdate();
    if (syncSession.getFilesToUpdate().size() != 0) {
      executeFileUpdate(syncSession);
    }
    /*System.out.println("FileSystemSynchronizerImpl.executeFileUpdate");
System.out.println("modal indexing took " + (System.currentTimeMillis() - l));*/

  }

  public void executeFileUpdate(SyncSession syncSession) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (!myIsCancelable && indicator != null) {
      indicator.startNonCancelableSection();
    }

    try {
      updateFiles(syncSession);
    }
    catch (ProcessCanceledException e) {
      syncSession.canceled();
      throw e;
    }
    finally {
      if (!myIsCancelable && indicator != null) {
        indicator.finishNonCancelableSection();
      }
    }
  }

  public SyncSession collectFilesToUpdate() {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.pushState();
      indicator.setText(IdeBundle.message("progress.scanning.files"));
      if (!myIsCancelable) {
        indicator.startNonCancelableSection();
      }
    }

    try {
      final SyncSession syncSession = new SyncSession(myUpdaters);
      myUpdaters = null;

      if (indicator != null) {
        indicator.popState();
      }

      if (syncSession.getFilesToUpdate().size() == 0) {
        syncSession.updatingDone();
      }
      return syncSession;
    }
    finally {
      if (indicator != null && !myIsCancelable) {
        indicator.finishNonCancelableSection();
      }
    }

  }

  protected void updateFiles(final SyncSession syncSession) {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.pushState();
      indicator.setText(IdeBundle.message("progress.parsing.files"));
    }

    int totalFiles = syncSession.getFilesToUpdate().size();
    final FileContentQueue contentQueue = new FileContentQueue() {
      @Override
      protected void addLast(VirtualFile file) throws InterruptedException {
        if (!syncSession.shouldIndex(file)) {
          return;
        }

        super.addLast(file);
      }
    };

    contentQueue.queue(syncSession.getFilesToUpdate(), indicator);

    int count = 0;
    while (true) {
      final FileContent content = contentQueue.take();
      if (content == null) break;
      final VirtualFile file = content.getVirtualFile();
      if (file == null) break;

      if (indicator != null) {
        indicator.checkCanceled();
        indicator.setFraction((double)++count / totalFiles);
        indicator.setText2(file.getPresentableUrl());
      }

      syncSession.processFile(content);
    }

    syncSession.updatingDone();

    if (indicator != null) {
      indicator.popState();
    }
  }


}
