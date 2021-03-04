// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.file.exclude;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

/**
 * @author Rustam Vishnyakov
 */
class MarkAsPlainTextAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    EnforcedPlainTextFileTypeManager typeManager = EnforcedPlainTextFileTypeManager.getInstance();
    JBIterable<VirtualFile> selectedFiles =
      JBIterable.of(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY))
              .filter(file -> EnforcedPlainTextFileTypeManager.isApplicableFor(file, false) && !typeManager.isMarkedAsPlainText(file));
    typeManager.markAsPlainText(project, VfsUtilCore.toVirtualFileArray(selectedFiles.toList()));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    EnforcedPlainTextFileTypeManager typeManager = EnforcedPlainTextFileTypeManager.getInstance();
    JBIterable<VirtualFile> selectedFiles =
      JBIterable.of(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY))
        .filter(file -> EnforcedPlainTextFileTypeManager.isApplicableFor(file, false) && !typeManager.isMarkedAsPlainText(file));
    boolean enabled = e.getProject() != null && !selectedFiles.isEmpty();
    e.getPresentation().setEnabledAndVisible(enabled);
    e.getPresentation().setIcon(EnforcedPlainTextFileType.INSTANCE.getIcon());
  }
    
}
