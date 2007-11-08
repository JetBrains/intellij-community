package com.intellij.openapi.fileEditor.ex;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class FileEditorManagerEx extends FileEditorManager {
  public static FileEditorManagerEx getInstanceEx(Project project) {
    return (FileEditorManagerEx)getInstance(project);
  }

  /**
   * @return <code>JComponent</code> which represent the place where all editors are located
   */
  public abstract JComponent getComponent();

  /**
   * @return preferred focused component inside myEditor tabbed container.
   * This method does similar things like {@link FileEditor#getPreferredFocusedComponent()}
   * but it also tracks (and remember) focus movement inside tabbed container.
   *
   * @see EditorComposite#getPreferredFocusedComponent()
   */
  public abstract JComponent getPreferredFocusedComponent();

  @NotNull
  public abstract Pair<FileEditor[], FileEditorProvider[]> getEditorsWithProviders(@NotNull VirtualFile file);

  @Nullable
  public abstract VirtualFile getFile(@NotNull FileEditor editor);

  /**
   *
   * @return current window in splitters
   */
  public abstract EditorWindow getCurrentWindow();

  public abstract void setCurrentWindow(EditorWindow window);

  /**
   * Closes editors for the file opened in particular window.
   *
   * @param file file to be closed. Cannot be null.
   */
  public abstract void closeFile(@NotNull VirtualFile file, @NotNull EditorWindow window);

  public abstract void unsplitWindow();

  public abstract void unsplitAllWindow();

  @NotNull
  public abstract EditorWindow[] getWindows();

  /**
   * @return arrays of all files (including <code>file</code> itself) that belong
   * to the same tabbed container. The method returns empty array if <code>file</code>
   * is not open. The returned files have the same order as they have in the
   * tabbed container.
   */
  @NotNull public abstract VirtualFile[] getSiblings(VirtualFile file);

  public abstract void createSplitter(int orientation);

  public abstract void changeSplitterOrientation();

  public abstract void flipTabs();
  public abstract boolean tabsMode();

  public abstract boolean isInSplitter();

  public abstract boolean hasOpenedFile ();

  public abstract VirtualFile getCurrentFile();

  public abstract Pair <FileEditor, FileEditorProvider> getSelectedEditorWithProvider(@NotNull VirtualFile file);

  public abstract void closeAllFiles();

  @NotNull
  public FileEditor[] openFile(@NotNull final VirtualFile file, final boolean focusEditor) {
    return openFileWithProviders(file, focusEditor).getFirst ();
  }

  public abstract Editor openTextEditorEnsureNoFocus(@NotNull OpenFileDescriptor descriptor);

  @NotNull public abstract Pair<FileEditor[],FileEditorProvider[]> openFileWithProviders(@NotNull VirtualFile file, boolean focusEditor);

  public abstract boolean isChanged(@NotNull EditorComposite editor);

  public abstract EditorWindow getNextWindow(@NotNull final EditorWindow window);

  public abstract EditorWindow getPrevWindow(@NotNull final EditorWindow window);

  public abstract boolean isInsideChange();
}
