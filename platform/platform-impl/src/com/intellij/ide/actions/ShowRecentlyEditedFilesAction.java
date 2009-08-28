/*
 * @author max
 */
package com.intellij.ide.actions;

import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public class ShowRecentlyEditedFilesAction extends BaseShowRecentFilesAction {
  protected VirtualFile[] filesToShow(Project project) {
    return IdeDocumentHistory.getInstance(project).getChangedFiles();
  }

  protected String getTitle() {
    return "Recently Edited Files";
  }
}
