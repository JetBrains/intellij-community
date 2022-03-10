// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.lineMarker;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerSettings;
import com.intellij.codeInsight.daemon.MergeableLineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

class RunnableStatusListener implements DaemonCodeAnalyzer.DaemonListener {

  @Override
  public void daemonFinished(@NotNull Collection<? extends FileEditor> fileEditors) {
    if (!LineMarkerSettings.getSettings().isEnabled(new RunLineMarkerProvider())) return;

    for (FileEditor fileEditor : fileEditors) {
      if (fileEditor instanceof TextEditor && fileEditor.isValid()) {
        Editor editor = ((TextEditor)fileEditor).getEditor();
        Project project = editor.getProject();
        VirtualFile file = fileEditor.getFile();
        if (file != null && project != null && file.isValid()) {
          boolean hasRunMarkers = hasRunMarkers(editor, project);
          FileViewProvider vp = PsiManager.getInstance(project).findViewProvider(file);
          if (hasRunMarkers || (vp != null && weMayTrustRunGutterContributors(vp))) {
            RunLineMarkerProvider.markRunnable(file, hasRunMarkers);
          }
        }
      }
    }
  }

  private static boolean hasRunMarkers(Editor editor, Project project) {
    for (LineMarkerInfo<?> marker : DaemonCodeAnalyzerImpl.getLineMarkers(editor.getDocument(), project)) {
      if (marker instanceof RunLineMarkerProvider.RunLineMarkerInfo) {
        return true;
      }
      if (ContainerUtil.findInstance(MergeableLineMarkerInfo.getMergedMarkers(marker), RunLineMarkerProvider.RunLineMarkerInfo.class) != null) {
        return true;
      }
    }

    return false;
  }

  private static boolean weMayTrustRunGutterContributors(FileViewProvider vp) {
    for (PsiFile file : vp.getAllFiles()) {
      for (RunLineMarkerContributor contributor : RunLineMarkerContributor.EXTENSION.allForLanguage(file.getLanguage())) {
        if (!contributor.producesAllPossibleConfigurations(file)) {
          return false;
        }
      }
    }
    return true;
  }
}
interface I { static void main(String[] args) { }}