/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public abstract class FileEditorManager {
  public static FileEditorManager getInstance(Project project) {
    return project.getComponent(FileEditorManager.class);
  }

  /**
   * @param file file to open. Parameter cannot be null. File should be valid.
   *
   * @return array of opened editors
   */
  @NotNull public abstract FileEditor[] openFile(@NotNull VirtualFile file, boolean focusEditor);

  /**
   * Closes all editors opened for the file.
   * 
   * @param file file to be closed. Cannot be null. 
   */
  public abstract void closeFile(@NotNull VirtualFile file);

  /**
   * Works as {@link #openFile(VirtualFile, boolean)} but forces opening of text editor. 
   * This method ignores {@link FileEditorPolicy#HIDE_DEFAULT_EDITOR} policy.
   *
   * @return opened text editor. The method returns <code>null</code> in case if text editor wasn't opened.
   */
  @Nullable
  public abstract Editor openTextEditor(OpenFileDescriptor descriptor, boolean focusEditor);

  /**
   * @return currently selected text editor. The method returns <code>null</code> in case
   * there is no selected editor at all or selected editor is not a text one.
   */
  @Nullable
  public abstract Editor getSelectedTextEditor();

  /**
   * @return <code>true</code> if <code>file</code> is opened, <code>false</code> otherwise
   */
  public abstract boolean isFileOpen(@NotNull VirtualFile file);

  /**
   * @return all opened files. Order of files in the array corresponds to the order of editor tabs.
   */
  @NotNull public abstract VirtualFile[] getOpenFiles();

  /**
   * @return files currently selected. The method returns empty array if there are no selected files. 
   * If more than one file is selected (split), the file with most recent focused editor is returned first.  
   */
  @NotNull public abstract VirtualFile[] getSelectedFiles();

  /**
   * @return editors currently selected. The method returns empty array if no editors are open.
   */
  @NotNull public abstract FileEditor[] getSelectedEditors();

  /**
   * @param file cannot be null
   * 
   * @return editor which is currently selected in the currently selected file.
   * The method returns <code>null</code> if <code>file</code> is not opened.
   */
  public abstract FileEditor getSelectedEditor(@NotNull VirtualFile file);

  /**
   * @param file cannot be null
   *
   * @return current editors for the specified <code>file</code>
   */
  @NotNull public abstract FileEditor[] getEditors(@NotNull VirtualFile file);

  /**
   * @param file cannot be null
   *
   * @return all editors for the specified <code>file</code>
   */
  @NotNull public abstract FileEditor[] getAllEditors(@NotNull VirtualFile file);

  /**
   * @return all open editors
   */
  @NotNull public abstract FileEditor[] getAllEditors();

  /**
   * @deprecated use addTopComponent
   */
  public abstract void showEditorAnnotation(@NotNull FileEditor editor, @NotNull JComponent annotationComoponent);
  /**
   * @deprecated use removeTopComponent
   */
  public abstract void removeEditorAnnotation(@NotNull FileEditor editor, @NotNull JComponent annotationComoponent);

  public abstract void addTopComponent(@NotNull final FileEditor editor, @NotNull final JComponent component);
  public abstract void removeTopComponent(@NotNull final FileEditor editor, @NotNull final JComponent component);
  public abstract void addBottomComponent(@NotNull final FileEditor editor, @NotNull final JComponent component);
  public abstract void removeBottomComponent(@NotNull final FileEditor editor, @NotNull final JComponent component);


  /**
   * Adds specified <code>listener</code>
   * @param listener listener to be added
   * @deprecated Use MessageBus instead
   */
  public abstract void addFileEditorManagerListener(@NotNull FileEditorManagerListener listener);

  /**
   * @param listener
   * @param parentDisposable
   * @deprecated Use {@link FileEditorManagerListener#FILE_EDITOR_MANAGER} instead
   */
  public abstract void addFileEditorManagerListener(@NotNull FileEditorManagerListener listener, Disposable parentDisposable);

  /**
   * Removes specified <code>listener</code>
   *
   * @param listener listener to be removed
   * @deprecated Use {@link FileEditorManagerListener#FILE_EDITOR_MANAGER} instead
   */
  public abstract void removeFileEditorManagerListener(@NotNull FileEditorManagerListener listener);

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

}
