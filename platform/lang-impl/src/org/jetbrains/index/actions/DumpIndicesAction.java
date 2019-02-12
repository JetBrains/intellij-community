// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.index.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.index.IndexGeneratorKt;
import org.jetbrains.index.OnDiskContentHashVerifier;
import org.jetbrains.index.ProjectContentPrebuiltIndexer;

import java.io.File;

public class DumpIndicesAction extends AnAction {
  public DumpIndicesAction() {
    super("Dump indices");
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    VirtualFile dir = FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFolderDescriptor(), project, null);
    if (dir == null) return;

    ProgressManager.getInstance().run(new Task.Modal(project, "Dumping Indices...", false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        ProjectContentPrebuiltIndexer indexer = new ProjectContentPrebuiltIndexer(project, indicator);
        indexer.buildIndex(IndexGeneratorKt.getAllIdeIndexGenerators(), dir.getPath(), new OnDiskContentHashVerifier(new File(dir.getPath(), "tmp").getAbsolutePath()));
      }
    });
  }
}
