/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.startup.impl;

import com.intellij.ide.startup.*;
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
  protected void updateFiles(final SyncSession syncSession) {
    boolean showBackgroundButton = false;
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      final CacheUpdater[] updaters = syncSession.getUpdaters();
      for (int i = 0; i < updaters.length; i++) {
        CacheUpdater updater = updaters[i];
        if (updater instanceof BackgroundableCacheUpdater) {
          if (((BackgroundableCacheUpdater)updater).initiallyBackgrounded()) {
            List<VirtualFile> remaining = syncSession.backgrounded(i);
            if (remaining != null && !remaining.isEmpty()) {
              ((BackgroundableCacheUpdater)updater).backgrounded(remaining);
            }
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
          handleBackgroundAction(progressWindow, syncSession);
        }
      });
    }

    try {
      super.updateFiles(syncSession);
    }
    finally {
      if (indicator instanceof ProgressWindow) {
        ((ProgressWindow)indicator).setBackgroundHandler(null);
      }
    }
  }

  private static void handleBackgroundAction(final ProgressWindow progressWindow, final SyncSession syncSession) {
    boolean allBackgrounded = true;
    final CacheUpdater[] updaters = syncSession.getUpdaters();
    for (int i = 0; i < updaters.length; i++) {
      CacheUpdater updater = updaters[i];
      if (updater instanceof BackgroundableCacheUpdater) {
        List<VirtualFile> remaining = syncSession.getRemainingFiles(i);
        if (remaining == null || remaining.isEmpty()) {
          continue;
        }

        final boolean backgrounded = ((BackgroundableCacheUpdater)updater).canBeSentToBackground(remaining);
        if (backgrounded) {
          remaining = syncSession.backgrounded(i); //during canBeSentToBackground, more files were already indexed
          if (remaining != null && !remaining.isEmpty()) {
            ((BackgroundableCacheUpdater)updater).backgrounded(remaining);
          }
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
