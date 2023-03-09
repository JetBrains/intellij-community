// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.menuBar;

import com.intellij.ide.RecentProjectListActionProvider;
import com.intellij.ide.actions.RecentProjectsGroup;
import com.intellij.ide.lightEdit.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.UniqueVFilePathBuilder;
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

/**
 * @see RecentProjectsGroup
 */
class LightEditRecentFileActionGroup extends ActionGroup implements DumbAware, AlwaysVisibleActionGroup {
  LightEditRecentFileActionGroup() {
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
    Arrays.asList(FileEditorManager.getInstance(project).getOpenFiles()).forEach(result::remove);
    return ContainerUtil.reverse(new ArrayList<>(result));
  }

  private static final class OpenFileAction extends DumbAwareAction implements LightEditCompatible {
    private final VirtualFile myFile;

    private OpenFileAction(@NotNull VirtualFile file) {
      myFile = file;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      Project project = e.getProject();
      if (project == null) {
        presentation.setEnabled(false);
        return;
      }
      String path = UniqueVFilePathBuilder.getInstance().getUniqueVirtualFilePath(project, myFile);
      presentation.setText(path);
      presentation.setIcon(IconUtil.getIcon(myFile, Iconable.ICON_FLAG_READ_STATUS, project));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      if (project != null) {
        LightEditUtil.markUnknownFileTypeAsPlainTextIfNeeded(project, myFile);
        LightEditFeatureUsagesUtil.logFileOpen(project, RecentFiles);
        LightEditService.getInstance().openFile(myFile);
      }
    }
  }
}
