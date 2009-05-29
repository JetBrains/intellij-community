/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.startup.impl;

import com.intellij.ide.startup.BackgroundableCacheUpdater;
import com.intellij.ide.startup.CacheUpdateSets;
import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

/**
 * @author peter
 */
public class FileSystemSynchronizerImpl extends FileSystemSynchronizer {

  @Override
  protected void updateFiles() {
    final CacheUpdateSets indexingSets = myIndexingSets;
    final CacheUpdateSets contentSets = myContentSets;
    final CacheUpdater[] updaters = myUpdaters.toArray(myUpdaters.toArray(new CacheUpdater[myUpdaters.size()]));
    boolean showBackgroundButton = false;
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      for (int i = 0, myUpdatersSize = updaters.length; i < myUpdatersSize; i++) {
        CacheUpdater updater = updaters[i];
        if (updater instanceof BackgroundableCacheUpdater) {
          if (((BackgroundableCacheUpdater)updater).initiallyBackgrounded()) {
            List<VirtualFile> remaining = indexingSets.backgrounded(i);
            if (remaining != null && !remaining.isEmpty()) {
              ((BackgroundableCacheUpdater)updater).backgrounded(remaining);
            }
            contentSets.backgrounded(i);
          }
          else {
            showBackgroundButton = true;
          }
        }
      }
    }

    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator instanceof ProgressWindow && showBackgroundButton) {
      final ProgressWindow progressWindow = (ProgressWindow)indicator;
      progressWindow.setBackgroundHandler(new Runnable() {
        public void run() {
          handleBackgroundAction(progressWindow, indexingSets, contentSets, updaters);
        }
      });
    }

    try {
      super.updateFiles();
    }
    finally {
      if (indicator instanceof ProgressWindow) {
        ((ProgressWindow)indicator).setBackgroundHandler(null);
      }
    }
  }

  private static void handleBackgroundAction(final ProgressWindow progressWindow, final CacheUpdateSets indexingSets,
                                      final CacheUpdateSets contentSets, CacheUpdater[] updaters) {
    boolean allBackgrounded = true;
    for (int i = 0; i < updaters.length; i++) {
      CacheUpdater updater = updaters[i];
      if (updater instanceof BackgroundableCacheUpdater) {
        List<VirtualFile> remaining = indexingSets.getRemainingFiles(i);
        if (remaining == null || remaining.isEmpty()) {
          continue;
        }

        final boolean backgrounded = ((BackgroundableCacheUpdater)updater).canBeSentToBackground(remaining);
        if (backgrounded) {
          remaining = indexingSets.backgrounded(i); //during canBeSentToBackground, more files were already indexed
          if (remaining != null && !remaining.isEmpty()) {
            ((BackgroundableCacheUpdater)updater).backgrounded(remaining);
          }
          contentSets.backgrounded(i); //not to load the content of files needed only for the backgrounded CacheUpdater
        } else {
          allBackgrounded = false;
        }
      }
    }
    if (allBackgrounded) {
      progressWindow.setBackgroundHandler(null);
    }
  }

}
