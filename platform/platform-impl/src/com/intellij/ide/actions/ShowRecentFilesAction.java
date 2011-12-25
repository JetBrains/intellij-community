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

  @Override
  protected String getPeerActionId() {
    return "RecentChangedFiles";
  }

  protected String getTitle() {
    return IdeBundle.message("title.popup.recent.files");
  }
}
