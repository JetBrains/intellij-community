/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.PathChooserDialog;
import com.intellij.openapi.fileChooser.impl.FileChooserUtil;
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.welcomeScreen.NewWelcomeScreen;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.projectImport.ProjectAttachProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.EnumSet;
import java.util.List;

public class OpenFileAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project eventProject = e.getProject();
    ApplicationManager.getApplication().invokeLater(() -> prepareFileChooserAndOpen(eventProject));
  }

  private void prepareFileChooserAndOpen(final Project eventProject) {
    final boolean showFiles = eventProject != null || PlatformProjectOpenProcessor.getInstanceIfItExists() != null;
    final FileChooserDescriptor descriptor = showFiles ? new ProjectOrFileChooserDescriptor() : new ProjectOnlyFileChooserDescriptor();

    VirtualFile toSelect = null;
    if (StringUtil.isNotEmpty(GeneralSettings.getInstance().getDefaultProjectDirectory())) {
      toSelect = VfsUtil.findFileByIoFile(new File(GeneralSettings.getInstance().getDefaultProjectDirectory()), true);
    }

    descriptor.putUserData(PathChooserDialog.PREFER_LAST_OVER_EXPLICIT, toSelect == null && showFiles);

    FileChooser.chooseFiles(descriptor, eventProject, toSelect != null ? toSelect : getPathToSelect(), files -> {
      for (VirtualFile file : files) {
        if (!descriptor.isFileSelectable(file)) {
          String message = IdeBundle.message("error.dir.contains.no.project", file.getPresentableUrl());
          Messages.showInfoMessage(eventProject, message, IdeBundle.message("title.cannot.open.project"));
          return;
        }
      }
      doOpenFile(eventProject, files);
    });
  }

  @Nullable
  protected VirtualFile getPathToSelect() {
    return VfsUtil.getUserHomeDir();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (NewWelcomeScreen.isNewWelcomeScreen(e)) {
      e.getPresentation().setIcon(AllIcons.Actions.Menu_open);
    }
  }

  private static void doOpenFile(@Nullable Project project, @NotNull List<VirtualFile> result) {
    for (VirtualFile file : result) {
      if (file.isDirectory()) {
        Project openedProject;
        if (ProjectAttachProcessor.canAttachToProject()) {
          EnumSet<PlatformProjectOpenProcessor.Option> options = EnumSet.noneOf(PlatformProjectOpenProcessor.Option.class);
          openedProject = PlatformProjectOpenProcessor.doOpenProject(file, project, -1, null, options);
        }
        else {
          openedProject = ProjectUtil.openOrImport(file.getPath(), project, false);
        }
        FileChooserUtil.setLastOpenedFile(openedProject, file);
        return;
      }

      // try to open as a project - unless the file is an .ipr of the current one
      if ((project == null || !file.equals(project.getProjectFile())) && OpenProjectFileChooserDescriptor.isProjectFile(file)) {
        int answer = file.getFileType() instanceof ProjectFileType
                     ? Messages.YES
                     : Messages.showYesNoCancelDialog(project,
                                                IdeBundle.message("message.open.file.is.project", file.getName()),
                                                IdeBundle.message("title.open.project"),
                                                IdeBundle.message("message.open.file.is.project.open.as.project"),
                                                IdeBundle.message("message.open.file.is.project.open.as.file"),
                                                IdeBundle.message("button.cancel"),
                                                Messages.getQuestionIcon());
        if (answer == Messages.CANCEL)  return;

        if (answer == Messages.YES) {
          Project openedProject = ProjectUtil.openOrImport(file.getPath(), project, false);
          if (openedProject != null) {
            FileChooserUtil.setLastOpenedFile(openedProject, file);
          }
          return;
        }
      }

      FileType type = FileTypeChooser.getKnownFileTypeOrAssociate(file, project);
      if (type == null) return;

      if (project != null) {
        openFile(file, project);
      }
      else {
        PlatformProjectOpenProcessor.doOpenProject(file, null, -1, null,
                                                   EnumSet.of(PlatformProjectOpenProcessor.Option.TEMP_PROJECT));
      }
    }
  }

  public static void openFile(String filePath, @NotNull Project project) {
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath);
    if (file != null && file.isValid()) {
      openFile(file, project);
    }
  }

  public static void openFile(VirtualFile file, @NotNull Project project) {
    NonProjectFileWritingAccessProvider.allowWriting(file);
    PsiNavigationSupport.getInstance().createNavigatable(project, file, -1).navigate(true);
  }

  private static class ProjectOnlyFileChooserDescriptor extends OpenProjectFileChooserDescriptor {
    public ProjectOnlyFileChooserDescriptor() {
      super(true);
      setTitle(IdeBundle.message("title.open.project"));
    }
  }

  // vanilla OpenProjectFileChooserDescriptor only accepts project files; this one is overridden to accept any files
  private static class ProjectOrFileChooserDescriptor extends OpenProjectFileChooserDescriptor {
    private final FileChooserDescriptor myStandardDescriptor =
      FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().withHideIgnored(false);

    public ProjectOrFileChooserDescriptor() {
      super(true);
      setTitle(IdeBundle.message("title.open.file.or.project"));
    }

    @Override
    public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
      return file.isDirectory() ? super.isFileVisible(file, showHiddenFiles) : myStandardDescriptor.isFileVisible(file, showHiddenFiles);
    }

    @Override
    public boolean isFileSelectable(VirtualFile file) {
      return file.isDirectory() ? super.isFileSelectable(file) : myStandardDescriptor.isFileSelectable(file);
    }

    @Override
    public boolean isChooseMultiple() {
      return true;
    }
  }
}
