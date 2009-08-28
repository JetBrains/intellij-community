/*
 * @author max
 */
package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public class ShowRecentFilesAction extends BaseShowRecentFilesAction  {

  @Override
  public void actionPerformed(AnActionEvent e) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.recent.files");
    super.actionPerformed(e);
  }

  @Override
  protected VirtualFile[] filesToShow(Project project) {
    return EditorHistoryManager.getInstance(project).getFiles();
  }

  protected String getTitle() {
    return IdeBundle.message("title.popup.recent.files");
  }
}
