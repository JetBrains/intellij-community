// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface FileEditorManagerListener extends EventListener{
  Topic<FileEditorManagerListener> FILE_EDITOR_MANAGER =
    new Topic<>("file editor events", FileEditorManagerListener.class, Topic.BroadcastDirection.TO_PARENT);

  /**
   * This method is called synchronously (in the same EDT event), as the creation of FileEditor(s).
   *
   * @see #fileOpened(FileEditorManager, VirtualFile)
   */
  default void fileOpenedSync(@NotNull FileEditorManager source, @NotNull VirtualFile file,
                              @NotNull Pair<FileEditor[], FileEditorProvider[]> editors) {
  }

  /**
   * This method is after focus settles down (if requested) in newly created FileEditor.
   * {@link #fileOpenedSync(FileEditorManager, VirtualFile, Pair)} is always invoked before this method (in same or previous EDT event).
   *
   * @see #fileOpenedSync(FileEditorManager, VirtualFile, Pair)
   */
  default void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
  }

  default void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
  }

  default void selectionChanged(@NotNull FileEditorManagerEvent event) {
  }

  interface Before extends EventListener {
    Topic<Before> FILE_EDITOR_MANAGER =
      new Topic<>("file editor before events", Before.class, Topic.BroadcastDirection.TO_PARENT);

    default void beforeFileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    }

    default void beforeFileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    }

    /**
     * @deprecated use {@link Before} directly
     */
    @Deprecated
    class Adapter implements Before {
      @Override
      public void beforeFileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) { }

      @Override
      public void beforeFileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) { }
    }
  }
}