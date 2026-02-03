// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.BulkFileListenerBackgroundable;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.util.concurrency.TransferredWriteActionService;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility functions to trigger file reparsing programmatically.
 */
public final class FileContentUtilCore {
  public static final String FORCE_RELOAD_REQUESTOR = "FileContentUtilCore.saveOrReload";

  /**
   * Forces reparsing the specified files.
   *
   * @param files the files to reparse.
   */
  public static void reparseFiles(VirtualFile @NotNull ... files) {
    reparseFiles(Arrays.asList(files));
  }

  /**
   * Forces reparsing the specified files.
   *
   * @param files the files to reparse.
   */
  public static void reparseFiles(@NotNull Collection<? extends VirtualFile> files) {
    Application application = ApplicationManager.getApplication();
    application.runWriteAction(() -> {
      // files must be processed under one write action to prevent firing event for invalid files.
      Set<VFilePropertyChangeEvent> events = new HashSet<>();
      for (VirtualFile file : files) {
        if (file != null && !file.isDirectory() && file.isValid()) {
          events.add(new VFilePropertyChangeEvent(FORCE_RELOAD_REQUESTOR, file, VirtualFile.PROP_NAME, file.getName(), file.getName()));
        }
      }

      BulkFileListener publisher = ApplicationManager.getApplication().getMessageBus().syncPublisher(VirtualFileManager.VFS_CHANGES);
      BulkFileListenerBackgroundable publisherBackgroundable = ApplicationManager.getApplication().getMessageBus().syncPublisher(VirtualFileManager.VFS_CHANGES_BG);
      List<VFileEvent> eventList = Collections.unmodifiableList(new ArrayList<>(events));
      if (EDT.isCurrentThreadEdt()) {
        publisher.before(eventList);
      } else {
        application.getService(TransferredWriteActionService.class).runOnEdtWithTransferredWriteActionAndWait(() -> {
          publisher.before(eventList);
        });
      }
      publisherBackgroundable.before(eventList);
      if (EDT.isCurrentThreadEdt()) {
        publisher.after(eventList);
      } else {
        application.getService(TransferredWriteActionService.class).runOnEdtWithTransferredWriteActionAndWait(() -> {
          publisher.after(eventList);
        });
      }
      publisherBackgroundable.after(eventList);

      ForcefulReparseModificationTracker.increment();
    });
  }
}
