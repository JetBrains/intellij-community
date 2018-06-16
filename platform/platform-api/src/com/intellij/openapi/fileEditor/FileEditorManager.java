// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public abstract class FileEditorManager {

  public static final Key<Boolean> USE_CURRENT_WINDOW = Key.create("OpenFile.searchForOpen");

  public static FileEditorManager getInstance(@NotNull Project project) {
    return project.getComponent(FileEditorManager.class);
  }

  /**
   * @param file file to open. Parameter cannot be null. File should be valid.
   * Must be called from <a href="https://docs.oracle.com/javase/tutorial/uiswing/concurrency/dispatch.html">EDT</a>.
   * @return array of opened editors
   */
  @NotNull
  public abstract FileEditor[] openFile(@NotNull VirtualFile file, boolean focusEditor);


  /**
   * Opens a file.
   * Must be called from <a href="https://docs.oracle.com/javase/tutorial/uiswing/concurrency/dispatch.html">EDT</a>.
   *
   * @param file file to open
   * @param focusEditor {@code true} if need to focus
   * @return array of opened editors
   */
  @NotNull
  public FileEditor[] openFile(@NotNull VirtualFile file, boolean focusEditor, boolean searchForOpen) {
    throw new UnsupportedOperationException("Not implemented");
  }

  /**
   * Closes all editors opened for the file.
   * Must be called from <a href="https://docs.oracle.com/javase/tutorial/uiswing/concurrency/dispatch.html">EDT</a>.
   *
   * @param file file to be closed. Cannot be null.
   */
  public abstract void closeFile(@NotNull VirtualFile file);

  /**
   * Works as {@link #openFile(VirtualFile, boolean)} but forces opening of text editor.
   * This method ignores {@link FileEditorPolicy#HIDE_DEFAULT_EDITOR} policy.
   * Must be called from <a href="https://docs.oracle.com/javase/tutorial/uiswing/concurrency/dispatch.html">EDT</a>.
   *
   * @return opened text editor. The method returns {@code null} in case if text editor wasn't opened.
   */
  @Nullable
  public abstract Editor openTextEditor(@NotNull OpenFileDescriptor descriptor, boolean focusEditor);

  /**
   * Same as {@link #openTextEditor(OpenFileDescriptor, boolean)}
   * but potentially can be faster thanks to not checking for injected editor at the specified offset.
   * Must be called from <a href="https://docs.oracle.com/javase/tutorial/uiswing/concurrency/dispatch.html">EDT</a>.
   */
  public void navigateToTextEditor(@NotNull OpenFileDescriptor descriptor, boolean focusEditor) {
    openTextEditor(descriptor, focusEditor);
  }

  /**
   * @return currently selected text editor. The method returns {@code null} in case
   * there is no selected editor at all or selected editor is not a text one.
   * Must be called from <a href="https://docs.oracle.com/javase/tutorial/uiswing/concurrency/dispatch.html">EDT</a>.
   */
  @Nullable
  public abstract Editor getSelectedTextEditor();

  /**
   * @return {@code true} if {@code file} is opened, {@code false} otherwise
   */
  public abstract boolean isFileOpen(@NotNull VirtualFile file);

  /**
   * @return all opened files. Order of files in the array corresponds to the order of editor tabs.
   */
  @NotNull
  public abstract VirtualFile[] getOpenFiles();

  /**
   * @return files currently selected. The method returns empty array if there are no selected files.
   * If more than one file is selected (split), the file with most recent focused editor is returned first.
   */
  @NotNull
  public abstract VirtualFile[] getSelectedFiles();

  /**
   * @return editors currently selected. The method returns empty array if no editors are open.
   */
  @NotNull
  public abstract FileEditor[] getSelectedEditors();

  /**
   * @return currently selected file editor or {@code null} if there is no selected editor at all.
   */
  @Nullable
  public FileEditor getSelectedEditor() {
    VirtualFile[] files = getSelectedFiles();
    return files.length == 0 ? null : getSelectedEditor(files[0]);
  }

  /**
   * @param file cannot be null
   *
   * @return editor which is currently selected in the currently selected file.
   * The method returns {@code null} if {@code file} is not opened.
   */
  @Nullable
  public abstract FileEditor getSelectedEditor(@NotNull VirtualFile file);

  /**
   * @param file cannot be null
   *
   * @return current editors for the specified {@code file}
   */
  @NotNull
  public abstract FileEditor[] getEditors(@NotNull VirtualFile file);

  /**
   * @param file cannot be null
   *
   * @return all editors for the specified {@code file}
   */
  @NotNull
  public abstract FileEditor[] getAllEditors(@NotNull VirtualFile file);

  /**
   * @return all open editors
   */
  @NotNull
  public abstract FileEditor[] getAllEditors();

  /**
   * @deprecated use addTopComponent
   */
  @Deprecated
  public abstract void showEditorAnnotation(@NotNull FileEditor editor, @NotNull JComponent annotationComponent);
  /**
   * @deprecated use removeTopComponent
   */
  @Deprecated
  public abstract void removeEditorAnnotation(@NotNull FileEditor editor, @NotNull JComponent annotationComponent);

  /**
   * Adds the specified component above the editor and paints a separator line below it.
   * If a separator line is not needed, set the client property to {@code true}:
   * <pre>    component.putClientProperty(SEPARATOR_DISABLED, true);    </pre>
   * Otherwise, a separator line will be painted by a
   * {@link com.intellij.openapi.editor.colors.EditorColors#SEPARATOR_ABOVE_COLOR SEPARATOR_ABOVE_COLOR} or
   * {@link com.intellij.openapi.editor.colors.EditorColors#TEARLINE_COLOR TEARLINE_COLOR} if it is not set.
   * <p>
   * This method allows to add several components above the editor.
   * To change an order of components the specified component may implement the
   * {@link com.intellij.openapi.util.Weighted Weighted} interface.
   */
  public abstract void addTopComponent(@NotNull final FileEditor editor, @NotNull final JComponent component);
  public abstract void removeTopComponent(@NotNull final FileEditor editor, @NotNull final JComponent component);
  /**
   * Adds the specified component below the editor and paints a separator line above it.
   * If a separator line is not needed, set the client property to {@code true}:
   * <pre>    component.putClientProperty(SEPARATOR_DISABLED, true);    </pre>
   * Otherwise, a separator line will be painted by a
   * {@link com.intellij.openapi.editor.colors.EditorColors#SEPARATOR_BELOW_COLOR SEPARATOR_BELOW_COLOR} or
   * {@link com.intellij.openapi.editor.colors.EditorColors#TEARLINE_COLOR TEARLINE_COLOR} if it is not set.
   * <p>
   * This method allows to add several components below the editor.
   * To change an order of components the specified component may implement the
   * {@link com.intellij.openapi.util.Weighted Weighted} interface.
   */
  public abstract void addBottomComponent(@NotNull final FileEditor editor, @NotNull final JComponent component);
  public abstract void removeBottomComponent(@NotNull final FileEditor editor, @NotNull final JComponent component);

  public static final Key<Boolean> SEPARATOR_DISABLED = Key.create("FileEditorSeparatorDisabled");

  /**
   * Adds specified {@code listener}
   * @param listener listener to be added
   * @deprecated Use {@link com.intellij.util.messages.MessageBus} instead: see {@link FileEditorManagerListener#FILE_EDITOR_MANAGER}
   */
  @Deprecated
  public abstract void addFileEditorManagerListener(@NotNull FileEditorManagerListener listener);

  /**
   * @deprecated Use {@link FileEditorManagerListener#FILE_EDITOR_MANAGER} instead
   */
  @Deprecated
  public abstract void addFileEditorManagerListener(@NotNull FileEditorManagerListener listener, @NotNull Disposable parentDisposable);

  /**
   * Removes specified {@code listener}
   *
   * @param listener listener to be removed
   * @deprecated Use {@link FileEditorManagerListener#FILE_EDITOR_MANAGER} instead
   */
  @Deprecated
  public abstract void removeFileEditorManagerListener(@NotNull FileEditorManagerListener listener);

  /**
   * Must be called from <a href="https://docs.oracle.com/javase/tutorial/uiswing/concurrency/dispatch.html">EDT</a>.
   * @return opened file editors
   */
  @NotNull
  public abstract List<FileEditor> openEditor(@NotNull OpenFileDescriptor descriptor, boolean focusEditor);

  /**
   * Returns the project with which the file editor manager is associated.
   *
   * @return the project instance.
   * @since 5.0.1
   */
  @NotNull
  public abstract Project getProject();

  public abstract void registerExtraEditorDataProvider(@NotNull EditorDataProvider provider, Disposable parentDisposable);

  /**
   * Returns data associated with given editor/caret context. Data providers are registered via
   * {@link #registerExtraEditorDataProvider(EditorDataProvider, Disposable)} method.
   */
  @Nullable
  public abstract Object getData(@NotNull String dataId, @NotNull Editor editor, @NotNull Caret caret);

  /**
   * Selects a specified file editor tab for the specified editor.
   * @param file a file to switch the file editor tab for. The function does nothing if the file is not currently open in the editor.
   * @param fileEditorProviderId the ID of the file editor to open; matches the return value of
   * {@link FileEditorProvider#getEditorTypeId()}
   */
  public abstract void setSelectedEditor(@NotNull VirtualFile file, @NotNull String fileEditorProviderId);

  /**
   * {@link FileEditorManager} supports asynchronous opening of text editors, i.e. when one of 'openFile' methods returns, returned
   * editor might not be fully initialized yet. This method allows to delay (if needed) execution of given runnable until editor is
   * fully loaded.
   */
  public abstract void runWhenLoaded(@NotNull Editor editor, @NotNull Runnable runnable);
}
