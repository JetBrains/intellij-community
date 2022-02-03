// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.List;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 * @see TextEditor
 */
public interface FileEditor extends UserDataHolder, Disposable {
  /**
   * @see #isModified()
   */
  String PROP_MODIFIED = "modified";
  /**
   * @see #isValid()
   */
  String PROP_VALID = "valid";

  FileEditor[] EMPTY_ARRAY = {};

  /**
   * Returns a component which represents the editor in UI.
   */
  @NotNull JComponent getComponent();

  /**
   * Returns a component to be focused when the editor is opened.
   */
  @Nullable JComponent getPreferredFocusedComponent();

  /**
   * Returns editor's name - a string that identifies the editor among others
   * (e.g.: "GUI Designer" for graphical editing and "Text" for textual representation of a GUI form editors).
   */
  @Nls(capitalization = Nls.Capitalization.Title) @NotNull String getName();

  /**
   * Returns editor's internal state.
   */
  default @NotNull FileEditorState getState(@NotNull FileEditorStateLevel level) {
    return FileEditorState.INSTANCE;
  }

  /**
   * Applies given state to the editor.
   */
  void setState(@NotNull FileEditorState state);

  /**
   * In some cases, it's desirable to set state exactly as requested (e.g. on tab splitting), in other cases different behaviour is
   * preferred, e.g. bringing caret into view on text editor opening. This method passes additional flag to FileEditor to indicate
   * the desired way to set state.
   */
  default void setState(@NotNull FileEditorState state, boolean exactState) {
    setState(state);
  }

  /**
   * Returns {@code true} when editor's content differs from its source (e.g. a file).
   */
  boolean isModified();

  /**
   * An editor is valid if its contents still exist.
   * For example, an editor displaying the contents of some file stops being valid if the file is deleted.
   * An editor can also become invalid after being disposed of.
   */
  boolean isValid();

  /**
   * This method is invoked each time when the editor is selected.
   * This can happen in two cases: an editor is selected because the selected file has been changed,
   * or an editor for the selected file has been changed.
   */
  default void selectNotify() { }

  /**
   * This method is invoked each time when the editor is deselected.
   */
  default void deselectNotify() { }

  /**
   * Adds specified listener.
   */
  void addPropertyChangeListener(@NotNull PropertyChangeListener listener);

  /**
   * Removes specified listener.
   */
  void removePropertyChangeListener(@NotNull PropertyChangeListener listener);

  /**
   * A highlighter object to perform background analysis and highlighting activities on.
   * Return {@code null} if no background highlighting activity necessary for this file editor.
   */
  default @Nullable BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  /**
   * The method is optional. Currently, it is used only by the Find Usages subsystem.
   * Expected to return a location of user's focus - a caret or any other form of selection start.
   */
  @Nullable FileEditorLocation getCurrentLocation();

  default @Nullable StructureViewBuilder getStructureViewBuilder() {
    return null;
  }

  @ApiStatus.Internal
  Key<VirtualFile> FILE_KEY = Key.create("FILE_KEY");

  /**
   * Returns the file for which {@link FileEditorProvider#createEditor)} was called.
   * The default implementation is temporary, and shall be dropped in the future.
   */
  default @Nullable VirtualFile getFile() {
    PluginException.reportDeprecatedDefault(getClass(), "getFile", "A proper @NotNull implementation required");
    return FILE_KEY.get(this);
  }

  /**
   * Returns the files for which {@link com.intellij.ide.SaveAndSyncHandler)} should be called on frame activation.
   */
  default @NotNull List<VirtualFile> getFilesToRefresh() {
    VirtualFile file = getFile();
    return file != null ? Collections.singletonList(file) : Collections.emptyList();
  }
}
