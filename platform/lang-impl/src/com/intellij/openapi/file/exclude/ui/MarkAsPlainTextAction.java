/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.file.exclude.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.file.exclude.EnforcedPlainTextFileTypeFactory;
import com.intellij.openapi.file.exclude.EnforcedPlainTextFileTypeManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Rustam Vishnyakov
 */
public class MarkAsPlainTextAction extends DumbAwareAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final VirtualFile[] selectedFiles = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (selectedFiles == null || selectedFiles.length == 0) return;
    EnforcedPlainTextFileTypeManager typeManager = EnforcedPlainTextFileTypeManager.getInstance();
    assert typeManager != null;
    Collection<VirtualFile> filesToMark = new ArrayList<>();
    for (VirtualFile file : selectedFiles) {
      if (file != null &&
          !file.isDirectory() &&
          EnforcedPlainTextFileTypeManager.isApplicableFor(file) &&
          !typeManager.isMarkedAsPlainText(file)) {
        filesToMark.add(file);
      }
    }
    if (project != null) {
      typeManager.markAsPlainText(project, filesToMark.toArray(new VirtualFile[filesToMark.size()]));
    }
  }

  @Override
  public void update(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final VirtualFile[] selectedFiles = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
    final Presentation presentation = e.getPresentation();
    final EnforcedPlainTextFileTypeManager typeManager = EnforcedPlainTextFileTypeManager.getInstance();
    presentation.setVisible(false);
    if (typeManager == null || selectedFiles == null || selectedFiles.length == 0) {
      return;
    }
    for (VirtualFile file : selectedFiles) {
      if (!EnforcedPlainTextFileTypeManager.isApplicableFor(file) || typeManager.isMarkedAsPlainText(file)) {
        return;
      }
    }
    presentation.setVisible(true);
    presentation.setIcon(EnforcedPlainTextFileTypeFactory.ENFORCED_PLAIN_TEXT_ICON);
  }
    
}
