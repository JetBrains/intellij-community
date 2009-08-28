package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 14
 * @author 2003
 */
public class ContentEntryEditorListenerAdapter implements ContentEntryEditor.ContentEntryEditorListener{
  public void editingStarted(ContentEntryEditor editor) {
  }

  public void beforeEntryDeleted(ContentEntryEditor editor) {
  }

  public void sourceFolderAdded(ContentEntryEditor editor, SourceFolder folder) {
  }

  public void sourceFolderRemoved(ContentEntryEditor editor, VirtualFile file, boolean isTestSource) {
  }

  public void folderExcluded(ContentEntryEditor editor, VirtualFile file) {
  }

  public void folderIncluded(ContentEntryEditor editor, VirtualFile file) {
  }

  public void navigationRequested(ContentEntryEditor editor, VirtualFile file) {
  }

  public void packagePrefixSet(ContentEntryEditor editor, SourceFolder folder) {
  }
}
