// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.file.exclude.ui;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.file.exclude.EnforcedPlainTextFileTypeManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.intellij.openapi.file.exclude.EnforcedPlainTextFileTypeManager.isApplicableFor;

/**
 * @author Rustam Vishnyakov
 */
public class MarkAsOriginalTypeAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    EnforcedPlainTextFileTypeManager typeManager = EnforcedPlainTextFileTypeManager.getInstance();
    JBIterable<VirtualFile> selectedFiles =
      JBIterable.of(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY))
        .filter(file -> isApplicableFor(file) && typeManager.isMarkedAsPlainText(file));
    typeManager.resetOriginalFileType(project, VfsUtilCore.toVirtualFileArray(selectedFiles.toList()));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    EnforcedPlainTextFileTypeManager typeManager = EnforcedPlainTextFileTypeManager.getInstance();
    JBIterable<VirtualFile> selectedFiles =
      JBIterable.of(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY))
        .filter(file -> isApplicableFor(file) && typeManager.isMarkedAsPlainText(file));
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    boolean enabled = e.getProject() != null && !selectedFiles.isEmpty();
    Set<FileType> fileTypes = selectedFiles.map(file -> fileTypeManager.getFileTypeByFileName(file.getNameSequence())).toSet();

    if (fileTypes.size() == 1) {
      FileType original = fileTypes.iterator().next();
      String originalName = StringUtil.defaultIfEmpty(original.getDescription(), original.getName());
      String text = ActionsBundle.actionText("MarkAsOriginalTypeAction").replace("Original File Type", originalName);
      e.getPresentation().setText(text);
      e.getPresentation().setIcon(original.getIcon());
    }
    e.getPresentation().setEnabledAndVisible(enabled);
  }
    
}
