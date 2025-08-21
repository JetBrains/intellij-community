// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import org.jetbrains.annotations.NotNull;

import java.util.*;

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
    ApplicationManager.getApplication().runWriteAction(() -> {
      // files must be processed under one write action to prevent firing event for invalid files.
      Set<VFilePropertyChangeEvent> events = new HashSet<>();
      for (VirtualFile file : files) {
        if (file != null && !file.isDirectory() && file.isValid()) {
          events.add(new VFilePropertyChangeEvent(FORCE_RELOAD_REQUESTOR, file, VirtualFile.PROP_NAME, file.getName(), file.getName()));
        }
      }

      BulkFileListener publisher = ApplicationManager.getApplication().getMessageBus().syncPublisher(VirtualFileManager.VFS_CHANGES);
      BulkFileListener publisherBackgroundable = ApplicationManager.getApplication().getMessageBus().syncPublisher(VirtualFileManager.VFS_CHANGES_BG);
      List<VFileEvent> eventList = Collections.unmodifiableList(new ArrayList<>(events));
      publisher.before(eventList);
      publisherBackgroundable.before(eventList);
      publisher.after(eventList);
      publisherBackgroundable.after(eventList);

      ForcefulReparseModificationTracker.increment();
    });
  }
}
