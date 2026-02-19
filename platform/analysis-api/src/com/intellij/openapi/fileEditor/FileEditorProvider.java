// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Should be registered via {@link #EP_FILE_EDITOR_PROVIDER}.
 * <p>
 * Synchronous providers are queried and constructed on the UI thread, so {@link #accept(Project, VirtualFile)} and
 * {@link #createEditor(Project, VirtualFile)} must remain lightweight. If editor creation needs indexing, PSI, or other
 * expensive work, implement {@link AsyncFileEditorProvider} to offload preparation to a background thread and only build
 * UI on the EDT.
 *
 * @see DumbAware
 */
public interface FileEditorProvider extends PossiblyDumbAware {
  ExtensionPointName<FileEditorProvider> EP_FILE_EDITOR_PROVIDER = new ExtensionPointName<>("com.intellij.fileEditorProvider");
  Key<FileEditorProvider> KEY = Key.create("com.intellij.fileEditorProvider");

  FileEditorProvider[] EMPTY_ARRAY = {};

  /**
   * The method is expected to run fast (typically on the UI thread).
   * Avoid PSI/index access and any potentially blocking work here; keep checks limited to quick file metadata.
   * If you need heavier validation, consider {@link AsyncFileEditorProvider}.
   *
   * @param file file to be tested for acceptance.
   * @return {@code true} if provider can create valid editor for the specified {@code file}.
   */
  boolean accept(@NotNull Project project, @NotNull VirtualFile file);

  default boolean acceptRequiresReadAction() {
    return true;
  }

  /**
   * Creates editor for the specified file.
   * <p>
   * This method is called only if the provider has accepted this file (i.e., method {@link #accept(Project, VirtualFile)} returned
   * {@code true}). For synchronous providers it is invoked on the UI thread, so it must not block. Use
   * {@link AsyncFileEditorProvider} if editor preparation is expensive.
   * The provider should return only valid editor.
   *
   * @return created editor for specified file.
   */
  @NotNull
  FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file);

  /**
   * Disposes the specified {@code editor}. It is guaranteed that this method is invoked only for editors
   * created with this provider.
   *
   * @param editor editor to be disposed.
   */
  default void disposeEditor(@NotNull FileEditor editor) {
    Disposer.dispose(editor);
  }

  /**
   * Deserializes state from the specified {@code sourceElement}.
   */
  default @NotNull FileEditorState readState(@NotNull Element sourceElement, @NotNull Project project, @NotNull VirtualFile file) {
    return FileEditorState.INSTANCE;
  }

  /**
   * Serializes state into the specified {@code targetElement}.
   */
  default void writeState(@NotNull FileEditorState state, @NotNull Project project, @NotNull Element targetElement) {
  }

  /**
   * @return editor type ID for the editors created with this FileEditorProvider. Each FileEditorProvider should have
   * a unique nonnull ID. The ID is used for saving/loading of EditorStates.
   * <p>
   * Please consider setting extension id in registration also for performance reasons.
   */
  @NotNull
  @NonNls
  String getEditorTypeId();

  /**
   * @return a policy that specifies how an editor created via this provider should be opened.
   * @see FileEditorPolicy#NONE
   * @see FileEditorPolicy#HIDE_DEFAULT_EDITOR
   * @see FileEditorPolicy#HIDE_OTHER_EDITORS
   * @see FileEditorPolicy#PLACE_BEFORE_DEFAULT_EDITOR
   * @see FileEditorPolicy#PLACE_AFTER_DEFAULT_EDITOR
   */
  @NotNull
  FileEditorPolicy getPolicy();
}
