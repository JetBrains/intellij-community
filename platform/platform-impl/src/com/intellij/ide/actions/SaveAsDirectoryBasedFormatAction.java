// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectModelSynchronizer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@ApiStatus.Internal
public final class SaveAsDirectoryBasedFormatAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    if (!isConvertableProject(project) || Messages.showOkCancelDialog(project,
                                                                      IdeBundle.message(
                                                                        "message.project.will.be.saved.and.reopened.in.new.directory.based.format"),
                                                                      IdeBundle
                                                                        .message("dialog.title.save.project.to.directory.based.format"),
                                                                   Messages.getWarningIcon()) != Messages.OK) {
      return;
    }

    IProjectStore store = ProjectKt.getStateStore(project);
    Path baseDir = store.getProjectFilePath().getParent();
    Path ideaDir = baseDir.resolve(Project.DIRECTORY_STORE_FOLDER);
    try {
      convertToDirectoryBasedFormat(project, store, baseDir, ideaDir);
      // closeAndDispose will also force save project
      ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
      projectManager.closeAndDispose(project);
      projectManager.openProject(ideaDir.getParent(), OpenProjectTask.build());
    }
    catch (IOException e) {
      Messages.showErrorDialog(project, String.format(IdeBundle.message("dialog.message.unable.to.create.idea.directory", e.getMessage()), ideaDir),
                               IdeBundle.message("dialog.title.error.saving.project"));
    }
  }

  private static void convertToDirectoryBasedFormat(Project project, IProjectStore store, Path baseDir, Path ideaDir) throws IOException {
    if (Files.isDirectory(ideaDir)) {
      LocalFileSystem.getInstance().refreshAndFindFileByNioFile(ideaDir);
    }
    else {
      createDir(ideaDir);
    }

    store.clearStorages();
    store.setPath(baseDir);
    WriteAction.run(() -> JpsProjectModelSynchronizer.Companion.getInstance(project).convertToDirectoryBasedFormat());
  }

  @TestOnly
  public static void convertToDirectoryBasedFormat(@NotNull Project project) throws IOException {
    IProjectStore store = ProjectKt.getStateStore(project);
    Path baseDir = store.getProjectFilePath().getParent();
    Path ideaDir = baseDir.resolve(Project.DIRECTORY_STORE_FOLDER);
    convertToDirectoryBasedFormat(project, store, baseDir, ideaDir);
  }

  @SuppressWarnings("UnusedReturnValue")
  private static VirtualFile createDir(@NotNull Path ideaDir) throws IOException {
    return ApplicationManager.getApplication().runWriteAction((ThrowableComputable<VirtualFile, IOException>)() -> {
      return VfsUtil.createDirectoryIfMissing(ideaDir.toString());
    });
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    e.getPresentation().setVisible(isConvertableProject(project));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private static boolean isConvertableProject(@Nullable Project project) {
    return project != null && !project.isDefault() && !ProjectKt.isDirectoryBased(project);
  }
}
