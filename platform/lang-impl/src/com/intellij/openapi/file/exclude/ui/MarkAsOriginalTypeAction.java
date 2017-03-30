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

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.file.exclude.EnforcedPlainTextFileTypeManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.JBIterable;

import java.util.Set;

import static com.intellij.openapi.file.exclude.EnforcedPlainTextFileTypeManager.isApplicableFor;

/**
 * @author Rustam Vishnyakov
 */
public class MarkAsOriginalTypeAction extends DumbAwareAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    EnforcedPlainTextFileTypeManager typeManager = EnforcedPlainTextFileTypeManager.getInstance();
    if (project == null || typeManager == null) return;
    JBIterable<VirtualFile> selectedFiles =
      JBIterable.of(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY))
        .filter(file -> isApplicableFor(file) && typeManager.isMarkedAsPlainText(file));
    typeManager.resetOriginalFileType(project, VfsUtilCore.toVirtualFileArray(selectedFiles.toList()));
  }

  @Override
  public void update(AnActionEvent e) {
    EnforcedPlainTextFileTypeManager typeManager = EnforcedPlainTextFileTypeManager.getInstance();
    JBIterable<VirtualFile> selectedFiles =
      typeManager == null ? JBIterable.empty() :
      JBIterable.of(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY))
        .filter(file -> isApplicableFor(file) && typeManager.isMarkedAsPlainText(file));
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    boolean enabled = e.getProject() != null && !selectedFiles.isEmpty();
    Set<FileType> fileTypes = selectedFiles.map(file -> fileTypeManager.getFileTypeByFileName(file.getName())).toSet();

    if (fileTypes.size() == 1) {
      FileType original = fileTypes.iterator().next();
      String originalName = original.getName();
      String text = ActionsBundle.actionText("MarkAsOriginalTypeAction").replace("Original File Type", originalName);
      e.getPresentation().setText(text);
      e.getPresentation().setIcon(original.getIcon());
    }
    e.getPresentation().setEnabledAndVisible(enabled);
  }
    
}
