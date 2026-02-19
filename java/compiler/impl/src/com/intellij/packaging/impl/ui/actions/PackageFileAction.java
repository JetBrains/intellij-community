// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.ui.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.impl.artifacts.ArtifactBySourceFileFinder;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class PackageFileAction extends AnAction {
  public PackageFileAction() {
    super(JavaCompilerBundle.messagePointer("action.name.package.file"), JavaCompilerBundle.messagePointer("action.description.package.file"));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean visible = false;
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project != null) {
      final List<VirtualFile> files = getFilesToPackage(e, project);
      if (!files.isEmpty()) {
        visible = true;
        e.getPresentation().setText(files.size() == 1 ? JavaCompilerBundle.message("action.name.package.file") : JavaCompilerBundle
          .message("action.name.package.files"));
      }
    }

    e.getPresentation().setEnabledAndVisible(visible);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private static @NotNull List<VirtualFile> getFilesToPackage(@NotNull AnActionEvent e, @NotNull Project project) {
    final VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (files == null) return Collections.emptyList();

    List<VirtualFile> result = new ArrayList<>();
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final CompilerManager compilerManager = CompilerManager.getInstance(project);
    for (VirtualFile file : files) {
      if (file == null || file.isDirectory() ||
          fileIndex.isUnderSourceRootOfType(file, JavaModuleSourceRootTypes.SOURCES) && compilerManager.isCompilableFileType(file.getFileType())) {
        return Collections.emptyList();
      }
      final Collection<? extends Artifact> artifacts = ArtifactBySourceFileFinder.getInstance(project).findArtifacts(file);
      for (Artifact artifact : artifacts) {
        if (!StringUtil.isEmpty(artifact.getOutputPath())) {
          result.add(file);
          break;
        }
      }
    }
    return result;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final Project project = event.getData(CommonDataKeys.PROJECT);
    if (project == null) return;

    FileDocumentManager.getInstance().saveAllDocuments();
    final List<VirtualFile> files = getFilesToPackage(event, project);
    Artifact[] allArtifacts = ArtifactManager.getInstance(project).getArtifacts();
    PackageFileWorker.startPackagingFiles(project, files, allArtifacts, () -> setStatusText(project, files));
  }

  private static void setStatusText(Project project, List<VirtualFile> files) {
    if (!files.isEmpty()) {
      StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
      if (statusBar != null) {
        StringBuilder fileNames = new StringBuilder();
        for (VirtualFile file : files) {
          if (!fileNames.isEmpty()) fileNames.append(", ");
          fileNames.append("'").append(file.getName()).append("'");
        }
        String time = DateFormatUtil.formatTimeWithSeconds(Clock.getTime());
        String statusText = JavaCompilerBundle.message("status.text.file.has.been.packaged", files.size(), fileNames, time);
        statusBar.setInfo(statusText);
      }
    }
  }
}
