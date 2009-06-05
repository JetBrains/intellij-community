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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * @author max
 */
public class FileSystemSynchronizer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.startup.FileSystemSynchronizer");

  protected final ArrayList<CacheUpdater> myUpdaters = new ArrayList<CacheUpdater>();
  private LinkedHashSet<VirtualFile> myFilesToUpdate = new LinkedHashSet<VirtualFile>();
  protected CacheUpdateSets myIndexingSets;
  protected CacheUpdateSets myContentSets;

  private boolean myIsCancelable = false;

  public void registerCacheUpdater(@NotNull CacheUpdater cacheUpdater) {
    myUpdaters.add(cacheUpdater);
  }

  public void setCancelable(boolean isCancelable) {
    myIsCancelable = isCancelable;
  }

  public void executeFileUpdate() {
    //final long l = System.currentTimeMillis();

    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (!myIsCancelable && indicator != null) {
      indicator.startNonCancelableSection();
    }

    try {
      if (myIndexingSets == null) { // collectFilesToUpdate() was not executed before
        if (collectFilesToUpdate() == 0) return;
      }

      updateFiles();
    }
    catch (ProcessCanceledException e) {
      for (CacheUpdater updater : myUpdaters) {
        if (updater != null) {
          updater.canceled();
        }
      }
      throw e;
    }
    finally {
      if (!myIsCancelable && indicator != null) {
        indicator.finishNonCancelableSection();
      }
      /*System.out.println("FileSystemSynchronizerImpl.executeFileUpdate");
      System.out.println("modal indexing took " + (System.currentTimeMillis() - l));*/
    }
  }

  public int collectFilesToUpdate() {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.pushState();
      indicator.setText(IdeBundle.message("progress.scanning.files"));
    }

    List<List<VirtualFile>> updateSets = new ArrayList<List<VirtualFile>>();
    for (CacheUpdater updater : myUpdaters) {
      try {
        List<VirtualFile> updaterFiles = Arrays.asList(updater.queryNeededFiles());
        updateSets.add(updaterFiles);
        myFilesToUpdate.addAll(updaterFiles);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
    myContentSets = new CacheUpdateSets(updateSets);
    myIndexingSets = new CacheUpdateSets(updateSets);

    if (indicator != null) {
      indicator.popState();
    }

    if (myFilesToUpdate.isEmpty()) {
      updatingDone();
    }

    return myFilesToUpdate.size();
  }

  protected void updateFiles() {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.pushState();
      indicator.setText(IdeBundle.message("progress.parsing.files"));
    }

    final int updaterCount = myUpdaters.size();
    int totalFiles = myFilesToUpdate.size();
    final FileContentQueue contentQueue = new FileContentQueue() {
      @Override
      protected void addLast(VirtualFile file) throws InterruptedException {
        if (!myIndexingSets.contains(file)) {
          return;
        }

        super.addLast(file);
      }
    };

    contentQueue.queue(myFilesToUpdate, indicator);

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
      for (int i = 0; i < updaterCount; i++) {
        CacheUpdater updater = myUpdaters.get(i);
        if (updater != null && myIndexingSets.remove(i, file)) {
          try {
            updater.processFile(content);
          }
          catch (ProcessCanceledException e) {
            throw e;
          }
          catch (Throwable e) {
            LOG.error(e);
          }
          if (myIndexingSets.isDoneForegroundly(i)) {
            try {
              updater.updatingDone();
            }
            catch (ProcessCanceledException e) {
              throw e;
            }
            catch (Throwable e) {
              LOG.error(e);
            }
            myUpdaters.set(i, null); //not to call updatingDone second time below
          }
        }
      }
    }

    updatingDone();

    if (indicator != null) {
      indicator.popState();
    }
  }

  private void updatingDone() {
    for (int i = 0, myUpdatersSize = myUpdaters.size(); i < myUpdatersSize; i++) {
      CacheUpdater updater = myUpdaters.get(i);
      try {
        if (updater != null && myIndexingSets.isDoneForegroundly(i)) {
          updater.updatingDone();
        }
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    myUpdaters.clear();
    myFilesToUpdate.clear();
    myIndexingSets = null;
    myContentSets = null;
  }

}
