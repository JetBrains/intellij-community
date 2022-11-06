// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;
import java.util.List;

/**
 * Listener for {@link FileEditorManager} events. All methods are invoked in EDT.
 */
public interface FileEditorManagerListener extends EventListener {
  @Topic.ProjectLevel
  Topic<FileEditorManagerListener> FILE_EDITOR_MANAGER = new Topic<>(FileEditorManagerListener.class, Topic.BroadcastDirection.TO_PARENT);

  /**
   * This method is called synchronously (in the same EDT event), as the creation of {@link FileEditor}s.
   *
   * @see #fileOpened(FileEditorManager, VirtualFile)
   * @deprecated use {@link FileOpenedSyncListener#fileOpenedSync(FileEditorManager, VirtualFile, List)}
   */
  @SuppressWarnings("unused")
  @Deprecated
  default void fileOpenedSync(@NotNull FileEditorManager source, @NotNull VirtualFile file,
                              @NotNull Pair<FileEditor[], FileEditorProvider[]> editors) {
  }

  /**
   * @deprecated use {@link FileOpenedSyncListener#fileOpenedSync(FileEditorManager, VirtualFile, List)}
   */
  @Deprecated
  default void fileOpenedSync(@NotNull FileEditorManager source, @NotNull VirtualFile file,
                              @NotNull List<FileEditorWithProvider> editorsWithProviders) {
    fileOpenedSync(source, file, new Pair<>(
      ContainerUtil.map2Array(editorsWithProviders, FileEditor.class, it -> it.getFileEditor()),
      ContainerUtil.map2Array(editorsWithProviders, FileEditorProvider.class, it1 -> it1.getProvider())));
  }

  /**
   * This method is called after the focus settles down (if requested) in a newly created {@link FileEditor}.
   * Be aware that this isn't always true in the case of asynchronously loaded editors, which, in general,
   * may happen with any text editor. In that case, the focus request is postponed until after the editor is fully loaded,
   * which means that it may gain the focus way after this method is called.
   * When necessary, use {@link FileEditorManager#runWhenLoaded(Editor, Runnable)}) to ensure the desired ordering.
   * <p>
   * {@link #fileOpenedSync(FileEditorManager, VirtualFile, List)} is always invoked before this method,
   * either in the same or the previous EDT event.
   *
   * @see #fileOpenedSync(FileEditorManager, VirtualFile, List)
   */
  default void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
  }

  default void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
  }

  default void selectionChanged(@NotNull FileEditorManagerEvent event) {
  }

  interface Before extends EventListener {
    /**
     * file editor before events
     */
    Topic<Before> FILE_EDITOR_MANAGER = new Topic<>(Before.class, Topic.BroadcastDirection.TO_PARENT);

    default void beforeFileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    }

    default void beforeFileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    }
  }
}
