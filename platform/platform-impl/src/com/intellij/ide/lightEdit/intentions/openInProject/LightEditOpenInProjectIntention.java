// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit.intentions.openInProject;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.ide.actions.OpenFileAction;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.ide.lightEdit.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.ide.lightEdit.LightEditFeatureUsagesUtil.ProjectStatus.Open;

public final class LightEditOpenInProjectIntention implements IntentionAction, LightEditCompatible, DumbAware {
  @IntentionName
  @Override
  public @NotNull String getText() {
    return ApplicationBundle.message("light.edit.open.in.project.intention");
  }

  @IntentionFamilyName
  @Override
  public @NotNull String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             Editor editor,
                             PsiFile file) {
    return LightEdit.owns(project);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    performOn(project, file.getVirtualFile());
  }

  public static void performOn(@NotNull Project project, @NotNull VirtualFile currentFile) throws IncorrectOperationException {
    LightEditorInfo editorInfo = ((LightEditorManagerImpl)LightEditService.getInstance().getEditorManager()).findOpen(currentFile);
    if (editorInfo == null) {
      return;
    }

    Project openProject = findOpenProject(currentFile);
    if (openProject != null) {
      LightEditFeatureUsagesUtil.logOpenFileInProject(project, Open);
    }
    else {
      VirtualFile projectRoot = ProjectRootSearchUtil.findProjectRoot(project, currentFile);
      if (projectRoot != null) {
        openProject = PlatformProjectOpenProcessor.Companion.doOpenProject(projectRoot.toNioPath(), OpenProjectTask.build());
      }
    }
    if (openProject != null) {
      ((LightEditServiceImpl)LightEditService.getInstance()).closeEditor(editorInfo);
      OpenFileAction.openFile(currentFile, openProject);
    }
  }

  @Nullable
  private static Project findOpenProject(@NotNull VirtualFile file) {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      if (ProjectRootManager.getInstance(project).getFileIndex().isInContent(file)) {
        return project;
      }
    }
    return null;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
