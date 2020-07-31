// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.menuBar;

import com.intellij.ide.RecentProjectListActionProvider;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.ide.lightEdit.LightEditFeatureUsagesUtil;
import com.intellij.ide.lightEdit.LightEditUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import static com.intellij.ide.lightEdit.LightEditFeatureUsagesUtil.OpenPlace.RecentFiles;

class RecentFileActionGroup extends ActionGroup implements DumbAware, AlwaysVisibleActionGroup {
  RecentFileActionGroup() {
    super(ApplicationBundle.messagePointer("light.edit.action.recentFile.text"), true);
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    Project project = e != null ? e.getProject() : null;
    if (!LightEdit.owns(project)) {
      return AnAction.EMPTY_ARRAY;
    }
    List<VirtualFile> recentFiles = getRecentFiles(project);
    final List<AnAction> actions = new ArrayList<>();
    actions.addAll(ContainerUtil.map(recentFiles, file -> new OpenFileAction(file)));
    List<AnAction> recentProjectsActions = RecentProjectListActionProvider.getInstance().getActions(false);
    if (!recentProjectsActions.isEmpty()) {
      if (!actions.isEmpty()) {
        actions.add(Separator.create());
      }
      actions.addAll(recentProjectsActions);
    }
    return actions.toArray(AnAction.EMPTY_ARRAY);
  }

  @NotNull
  private static List<VirtualFile> getRecentFiles(@NotNull Project project) {
    List<VirtualFile> historyFiles = EditorHistoryManager.getInstance(project).getFileList();
    LinkedHashSet<VirtualFile> result = new LinkedHashSet<>(historyFiles);
    result.removeAll(Arrays.asList(FileEditorManager.getInstance(project).getOpenFiles()));
    return new ArrayList<>(result);
  }

  private static class OpenFileAction extends DumbAwareAction implements LightEditCompatible {
    private final VirtualFile myFile;

    private OpenFileAction(@NotNull VirtualFile file) {
      myFile = file;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setText(myFile.getName());
      presentation.setIcon(IconUtil.getIcon(myFile, Iconable.ICON_FLAG_READ_STATUS, e.getProject()));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      if (project != null) {
        LightEditUtil.markUnknownFileTypeAsPlainTextIfNeeded(project, myFile);
        LightEditFeatureUsagesUtil.logFileOpen(RecentFiles);
        new OpenFileDescriptor(project, myFile).navigate(true);
      }
    }
  }
}
