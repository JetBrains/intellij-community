/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public abstract class FileEditorManager {
  public static FileEditorManager getInstance(Project project) {
    return project.getComponent(FileEditorManager.class);
  }

  /**
   * @param file file to open. Parameter cannot be null. File should be valid.
   *
   * @return array of opened editors
   */
  public abstract FileEditor[] openFile(VirtualFile file, boolean focusEditor);

  /**
   * Closes all editors opened for the file.
   * 
   * @param file file to be closed. Cannot be null. 
   */
  public abstract void closeFile(VirtualFile file);

  /**
   * Works as {@link #openFile(VirtualFile, boolean)} but forces opening of text editor. 
   * This method ignores {@link FileEditorPolicy#HIDE_DEFAULT_EDITOR} policy.
   *
   * @return opened text editor. The method returns <code>null</code> in case if text editor wasn't opened.
   */
  public abstract Editor openTextEditor(OpenFileDescriptor descriptor, boolean focusEditor);

  /**
   * @return currently selected text editor. The method returns <code>null</code> in case
   * there is no selected editor at all or selected editor is not a text one.
   */
  public abstract Editor getSelectedTextEditor();
  
  /**
   * @return <code>true</code> if <code>file</code> is opened, <code>false</code> otherwise
   */
  public abstract boolean isFileOpen(VirtualFile file);

  /**
   * @return all opened files. Order of files in the array corresponds to the order of editor tabs.
   */
  public abstract VirtualFile[] getOpenFiles();
  
  /**
   * @return files currently selected. The method returns empty array if there are no selected files. 
   * If more than one file is selected (split), the file with most recent focused editor is returned first.  
   */
  public abstract VirtualFile[] getSelectedFiles();

  /**
   * @return editors currently selected. The method returns empty array if no editors are open.
   */
  public abstract FileEditor[] getSelectedEditors();

  /**
   * @param file cannot be null
   * 
   * @return editor which is currently selected in the currently selected file.
   * The method returns <code>null</code> if <code>file</code> is not opened.
   */
  public abstract FileEditor getSelectedEditor(VirtualFile file);
  
  /**
   * @param file cannot be null
   *
   * @return all editors for the specified <code>file</code>
   */
  public abstract FileEditor[] getEditors(VirtualFile file);

  /**
   * @return all open editors
   */
  public abstract FileEditor[] getAllEditors();

  /**
   * Adds specified <code>listener</code>
   *
   * @param listener listener to be added; cannot be null
   */
  public abstract void addFileEditorManagerListener(FileEditorManagerListener listener);

  /**
   * Removes specified <code>listener</code>
   *
   * @param listener listener to be removed; cannot be null
   */
  public abstract void removeFileEditorManagerListener(FileEditorManagerListener listener);
}