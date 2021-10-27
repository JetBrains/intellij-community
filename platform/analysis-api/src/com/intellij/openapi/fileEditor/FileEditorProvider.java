// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Should be registered via {@link #EP_FILE_EDITOR_PROVIDER}.
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 * @see DumbAware
 */
public interface FileEditorProvider {
  ExtensionPointName<FileEditorProvider> EP_FILE_EDITOR_PROVIDER = new ExtensionPointName<>("com.intellij.fileEditorProvider");
  Key<FileEditorProvider> KEY = Key.create("com.intellij.fileEditorProvider");

  FileEditorProvider[] EMPTY_ARRAY = {};
  /**
   * Method is expected to run fast.
   *
   * @param file file to be tested for acceptance.
   * @return {@code true} if provider can create valid editor for the specified {@code file}.
   */
  boolean accept(@NotNull Project project, @NotNull VirtualFile file);

  /**
   * Creates editor for the specified file.
   * <p>
   * This method is called only if the provider has accepted this file (i.e. method {@link #accept(Project, VirtualFile)} returned
   * {@code true}).
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
   * Deserialize state from the specified {@code sourceElement}.
   */
  @NotNull
  default FileEditorState readState(@NotNull Element sourceElement, @NotNull Project project, @NotNull VirtualFile file) {
    return FileEditorState.INSTANCE;
  }

  /**
   * Serializes state into the specified {@code targetElement}.
   */
  default void writeState(@NotNull FileEditorState state, @NotNull Project project, @NotNull Element targetElement) {
  }

  /**
   * @return id of type of the editors created with this FileEditorProvider. Each FileEditorProvider should have
   * unique nonnull id. The id is used for saving/loading of EditorStates.
   */
  @NotNull
  @NonNls
  String getEditorTypeId();

  /**
   * @return policy that specifies how editor created via this provider should be opened.
   * @see FileEditorPolicy#NONE
   * @see FileEditorPolicy#HIDE_DEFAULT_EDITOR
   * @see FileEditorPolicy#PLACE_BEFORE_DEFAULT_EDITOR
   */
  @NotNull
  FileEditorPolicy getPolicy();
}