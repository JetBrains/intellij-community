// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @see FileEditorManagerListener
 */
public abstract class FileEditorManager {
  public static final Key<Boolean> USE_CURRENT_WINDOW = Key.create("OpenFile.searchForOpen");

  public static FileEditorManager getInstance(@NotNull Project project) {
    return project.getService(FileEditorManager.class);
  }

  public abstract @Nullable FileEditorComposite getComposite(@NotNull VirtualFile file);

  /**
   * @param file file to open. The file should be valid.
   * @return array of opened editors
   */
  public abstract FileEditor @NotNull [] openFile(@NotNull VirtualFile file, boolean focusEditor);

  @ApiStatus.Experimental
  public void requestOpenFile(@NotNull VirtualFile file) {
    openFile(file, true);
  }

  public abstract @NotNull @Unmodifiable List<@NotNull FileEditor> openFile(@NotNull VirtualFile file);

  /**
   * Opens a file.
   * Must be called from <a href="https://docs.oracle.com/javase/tutorial/uiswing/concurrency/dispatch.html">EDT</a>.
   *
   * @param file        file to open
   * @param focusEditor {@code true} if need to focus
   * @return array of opened editors
   */
  public FileEditor @NotNull [] openFile(@NotNull VirtualFile file, boolean focusEditor, boolean searchForOpen) {
    throw new UnsupportedOperationException("Not implemented");
  }

  /**
   * Closes all the editors opened for a given file.
   * Must be called from <a href="https://docs.oracle.com/javase/tutorial/uiswing/concurrency/dispatch.html">EDT</a>.
   *
   * @param file file to be closed.
   */
  public abstract void closeFile(@NotNull VirtualFile file);

  /**
   * Works as {@link #openFile(VirtualFile, boolean)} but forces opening of text editor (see {@link TextEditor}).
   * If several text editors are opened, including the default one, the default text editor is focused (if requested) and returned.
   * Must be called from <a href="https://docs.oracle.com/javase/tutorial/uiswing/concurrency/dispatch.html">EDT</a>.
   *
   * @return opened text editor. The method returns {@code null} in case if text editor wasn't opened.
   */
  public abstract @Nullable Editor openTextEditor(@NotNull OpenFileDescriptor descriptor, boolean focusEditor);

  /**
   * @deprecated use {@link #openTextEditor(OpenFileDescriptor, boolean)}
   */
  @Deprecated(forRemoval = true)
  public void navigateToTextEditor(@NotNull OpenFileDescriptor descriptor, boolean focusEditor) {
    openTextEditor(descriptor, focusEditor);
  }

  /**
   * @return currently selected text editor. The method returns {@code null} in case
   * there is no selected editor at all or selected editor is not a text one.
   */
  @RequiresEdt
  public abstract @Nullable Editor getSelectedTextEditor();

  @ApiStatus.Experimental
  public @Nullable Editor getSelectedTextEditor(boolean lockFree) {
    return getSelectedTextEditor();
  }

  /**
   * @return currently selected TEXT editors including ones which were opened by guests during a collaborative development session
   * The method returns an empty array in case there are no selected editors or none of them is a text one.
   * Must be called from <a href="https://docs.oracle.com/javase/tutorial/uiswing/concurrency/dispatch.html">EDT</a>.
   */
  @ApiStatus.Experimental
  @RequiresEdt
  public Editor @NotNull [] getSelectedTextEditorWithRemotes() {
    Editor editor = getSelectedTextEditor();
    return editor != null ? new Editor[]{editor} : Editor.EMPTY_ARRAY;
  }

  /**
   * @return {@code true} if {@code file} is opened, {@code false} otherwise
   */
  public abstract boolean isFileOpen(@NotNull VirtualFile file);

  /**
   * @return {@code true} if {@code file} is opened, {@code false} otherwise.
   * Unlike {@link #isFileOpen(VirtualFile)} includes files which were opened by all guests during a collaborative development session.
   */
  @ApiStatus.Experimental
  public boolean isFileOpenWithRemotes(@NotNull VirtualFile file) {
    return isFileOpen(file);
  }

  /**
   * @return all opened files. The order of files in the array corresponds to the order of editor tabs.
   */
  public abstract VirtualFile @NotNull [] getOpenFiles();

  /**
   * @return all opened files, including ones which were opened by guests during a collaborative development session.
   * The order of files in the array corresponds to the order of host's editor tabs, order for guests isn't determined.
   * There are cases when only editors of a particular user are needed (e.g. a search scope 'open files'),
   * but at the same time editor notifications should be shown to all users.
   */
  @ApiStatus.Experimental
  public abstract @NotNull @Unmodifiable List<VirtualFile> getOpenFilesWithRemotes();

  public boolean hasOpenFiles() {
    return getOpenFiles().length > 0;
  }

  /**
   * @return files currently selected. The method returns an empty array if there are no selected files.
   * If more than one file is selected (split), the file with the most recent focused editor is returned first.
   */
  public abstract VirtualFile @NotNull [] getSelectedFiles();

  /**
   * @return editors currently selected. The method returns an empty array if no editors are open.
   */
  public abstract FileEditor @NotNull [] getSelectedEditors();

  /**
   * @return editors currently selected including ones which were opened by guests during a collaborative development session.
   * The method returns an empty array if no editors are open.
   */
  @ApiStatus.Experimental
  public @NotNull @Unmodifiable Collection<FileEditor> getSelectedEditorWithRemotes() {
    return List.of(getSelectedEditors());
  }

  /**
   * @return currently selected file editor or {@code null} if there is no selected editor at all.
   */
  public @Nullable FileEditor getSelectedEditor() {
    VirtualFile[] files = getSelectedFiles();
    return files.length == 0 ? null : getSelectedEditor(files[0]);
  }

  /**
   * @return editor which is currently selected for a given file.
   * The method returns {@code null} if {@code file} is not opened.
   */
  public abstract @Nullable FileEditor getSelectedEditor(@NotNull VirtualFile file);

  /**
   * @return current editors for the specified {@code file}
   */
  public abstract FileEditor @NotNull [] getEditors(@NotNull VirtualFile file);

  public @NotNull @Unmodifiable List<FileEditor> getEditorList(@NotNull VirtualFile file) {
    return Arrays.asList(getEditors(file));
  }

  /**
   * @return all editors for the specified {@code file}
   */
  public abstract FileEditor @NotNull [] getAllEditors(@NotNull VirtualFile file);

  public abstract @NotNull @Unmodifiable List<@NotNull FileEditor> getAllEditorList(@NotNull VirtualFile file);

  /**
   * @return all open editors
   */
  public abstract FileEditor @NotNull [] getAllEditors();

  /**
   * Adds the specified component above the editor and paints a separator line below it.
   * If a separator line is not needed, set the client property to {@code true}:
   * <pre>    component.putClientProperty(SEPARATOR_DISABLED, true);    </pre>
   * Otherwise, a separator line will be painted by a
   * {@link com.intellij.openapi.editor.colors.EditorColors#TEARLINE_COLOR TEARLINE_COLOR} if it is set.
   * <p>
   * This method allows adding several components above the editor.
   * To change the order of components, the specified component may implement the
   * {@link com.intellij.openapi.util.Weighted Weighted} interface.
   */
  @RequiresEdt
  public abstract void addTopComponent(final @NotNull FileEditor editor, final @NotNull JComponent component);

  @RequiresEdt
  public abstract void removeTopComponent(final @NotNull FileEditor editor, final @NotNull JComponent component);

  /**
   * Adds the specified component below the editor and paints a separator line above it.
   * If a separator line is not needed, set the client property to {@code true}:
   * <pre>    component.putClientProperty(SEPARATOR_DISABLED, true);    </pre>
   * Otherwise, a separator line will be painted by a
   * {@link com.intellij.openapi.editor.colors.EditorColors#TEARLINE_COLOR TEARLINE_COLOR} if it is set.
   * <p>
   * This method allows adding several components below the editor.
   * To change the order of components, the specified component may implement the
   * {@link com.intellij.openapi.util.Weighted Weighted} interface.
   */
  @RequiresEdt
  public abstract void addBottomComponent(final @NotNull FileEditor editor, final @NotNull JComponent component);

  @RequiresEdt
  public abstract void removeBottomComponent(final @NotNull FileEditor editor, final @NotNull JComponent component);

  public static final Key<Boolean> SEPARATOR_DISABLED = Key.create("FileEditorSeparatorDisabled");

  public static final Key<Border> SEPARATOR_BORDER = Key.create("FileEditorSeparatorBorder");

  public static final Key<Color> SEPARATOR_COLOR = Key.create("FileEditorSeparatorColor");

  /**
   * Adds specified {@code listener}.
   *
   * @param listener listener to be added
   * @deprecated Use {@link com.intellij.util.messages.MessageBus} instead: see {@link FileEditorManagerListener#FILE_EDITOR_MANAGER}
   */
  @Deprecated
  public void addFileEditorManagerListener(@NotNull FileEditorManagerListener listener) {
  }

  /**
   * Removes specified {@code listener}.
   *
   * @param listener listener to be removed
   * @deprecated Use {@link FileEditorManagerListener#FILE_EDITOR_MANAGER} instead
   */
  @Deprecated
  public void removeFileEditorManagerListener(@NotNull FileEditorManagerListener listener) {
  }

  @RequiresEdt
  public final @NotNull List<FileEditor> openEditor(@NotNull OpenFileDescriptor descriptor, boolean focusEditor) {
    return openFileEditor(descriptor, focusEditor);
  }

  @RequiresEdt
  public abstract @NotNull @Unmodifiable List<FileEditor> openFileEditor(@NotNull FileEditorNavigatable descriptor, boolean focusEditor);

  /**
   * @return the project which the file editor manager is associated with.
   */
  public abstract @NotNull Project getProject();

  /**
   * Selects a specified file editor tab for the specified editor.
   *
   * @param file                 a file to switch the file editor tab for. The function does nothing if the file is not currently open in the editor.
   * @param fileEditorProviderId the ID of the file editor to open; matches the return value of {@link FileEditorProvider#getEditorTypeId()}
   */
  public abstract void setSelectedEditor(@NotNull VirtualFile file, @NotNull String fileEditorProviderId);

  /**
   * {@link FileEditorManager} supports asynchronous opening of text editors, i.e. when one of {@code openFile*} methods returns, returned
   * editor might not be fully initialized yet. This method allows delaying (if needed) execution of a given runnable until the editor is
   * fully loaded.
   */
  public abstract void runWhenLoaded(@NotNull Editor editor, @NotNull Runnable runnable);

  /**
   * Refreshes the text, colors, and icon of the editor tabs representing the specified file.
   *
   * @param file refreshed file
   */
  public void updateFilePresentation(@NotNull VirtualFile file) { }

  /**
   * Updates the file color of the editor tabs representing the specified file.
   *
   * @param file refreshed file
   */
  public void updateFileColor(@NotNull VirtualFile file) { }

  /**
   * Returns currently focused editor (if any). It is either {@code null} or the value returned by {@link #getSelectedEditor()}.
   */
  public @Nullable FileEditor getFocusedEditor() {
    FileEditor editor = getSelectedEditor();
    return editor != null && UIUtil.isFocusAncestor(editor.getComponent()) ? editor : null;
  }
}
