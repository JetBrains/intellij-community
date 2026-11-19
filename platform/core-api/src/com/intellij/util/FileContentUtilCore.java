// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

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
    // files must be processed under one write action to prevent firing event for invalid files.
    ApplicationManager.getApplication().runWriteAction(() -> fireReparseEvents(createReparseEvents(files)));
  }

  /**
   * Creates the reparse events for the valid files in {@code files} that are not directories.
   * To reparse the files, give the result to {@code RefreshQueue.processEvents}, which drops the events of invalid files.
   * Then increment {@link ForcefulReparseModificationTracker}.
   */
  @ApiStatus.Internal
  @RequiresReadLock
  public static @Unmodifiable @NotNull List<VFileEvent> createReparseEvents(@NotNull Collection<? extends VirtualFile> files) {
    Set<VFilePropertyChangeEvent> events = new HashSet<>();
    for (VirtualFile file : files) {
      if (file != null && !file.isDirectory() && file.isValid()) {
        events.add(new VFilePropertyChangeEvent(FORCE_RELOAD_REQUESTOR, file, VirtualFile.PROP_NAME, file.getName(), file.getName()));
      }
    }
    return Collections.unmodifiableList(new ArrayList<>(events));
  }

  /**
   * Sends {@code events} from {@link #createReparseEvents} to the VFS listeners, which reparse the files.
   */
  @RequiresWriteLock
  private static void fireReparseEvents(@NotNull List<VFileEvent> events) {
    VirtualFileManager.getInstance().fireEvents(events, EmptyRunnable.getInstance());
    ForcefulReparseModificationTracker.increment();
  }
}
