// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.action;

import com.intellij.icons.AllIcons;
import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.actions.ImportModuleAction;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportProvider;
import com.intellij.openapi.externalSystem.statistics.ExternalSystemActionsCollector;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.UiUtils;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public final class AttachExternalProjectAction extends DumbAwareAction {
  public AttachExternalProjectAction() {
    getTemplatePresentation().setText(JavaUiBundle.messagePointer("action.attach.external.project.text", "External"));
    getTemplatePresentation().setDescription(JavaUiBundle.messagePointer("action.attach.external.project.description", "external"));
    getTemplatePresentation().setIcon(AllIcons.General.Add);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    ProjectSystemId externalSystemId = e.getData(ExternalSystemDataKeys.EXTERNAL_SYSTEM_ID);

    if (externalSystemId != null) {
      String name = externalSystemId.getReadableName();
      presentation.setText(JavaUiBundle.messagePointer("action.attach.external.project.text", name));
      presentation.setDescription(JavaUiBundle.messagePointer("action.attach.external.project.description", name));
    }

    presentation.setIcon(AllIcons.General.Add);
    presentation.setEnabledAndVisible(externalSystemId != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ProjectSystemId externalSystemId = e.getData(ExternalSystemDataKeys.EXTERNAL_SYSTEM_ID);
    if (externalSystemId == null) {
      return;
    }

    ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(externalSystemId);
    if (manager == null) {
      return;
    }

    Project project = e.getProject();
    if (project == null) {
      return;
    }
    ExternalSystemActionsCollector.trigger(project, externalSystemId, this, e);

    ProjectImportProvider projectImportProvider = ProjectImportProvider.PROJECT_IMPORT_PROVIDER
      .findFirstSafe(it -> it instanceof AbstractExternalProjectImportProvider
                           && externalSystemId.equals(((AbstractExternalProjectImportProvider)it).getExternalSystemId()));
    if (projectImportProvider == null) {
      return;
    }
    ImportModuleAction.doImport(project, () ->
      ImportModuleAction.selectFileAndCreateWizard(
        project,
        null,
        getFileChooserDescriptor(manager, project, externalSystemId),
        getSelectedFileValidator(project, externalSystemId),
        projectImportProvider
      )
    );
  }

  private static FileChooserDescriptor getFileChooserDescriptor(
    @NotNull ExternalSystemManager<?, ?, ?, ?, ?> manager,
    @NotNull Project project,
    @NotNull ProjectSystemId externalSystemId
  ) {
    ExternalSystemUnlinkedProjectAware unlinkedProjectAware = ExternalSystemUnlinkedProjectAware.getInstance(externalSystemId);
    if (unlinkedProjectAware == null) {
      return manager.getExternalProjectDescriptor();
    }
    return new FileChooserDescriptor(true, true, false, false, false, false)
      .withFileFilter(virtualFile -> unlinkedProjectAware.isBuildFile(project, virtualFile));
  }

  private static Predicate<VirtualFile> getSelectedFileValidator(@NotNull Project project, @NotNull ProjectSystemId externalSystemId) {
    ExternalSystemUnlinkedProjectAware unlinkedProjectAware = ExternalSystemUnlinkedProjectAware.getInstance(externalSystemId);
    if (unlinkedProjectAware == null) {
      return __ -> true;
    }
    Predicate<VirtualFile> isSelectedFile = virtualFile -> {
      return virtualFile.isDirectory()
             ? ContainerUtil.exists(virtualFile.getChildren(), it -> unlinkedProjectAware.isBuildFile(project, it))
             : unlinkedProjectAware.isBuildFile(project, virtualFile);
    };
    return virtualFile -> {
      if (!isSelectedFile.test(virtualFile)) {
        String name = externalSystemId.getReadableName();
        String projectPath = UiUtils.getPresentablePath(virtualFile.getPath());
        String message = virtualFile.isDirectory()
                         ? JavaUiBundle.message("action.attach.external.project.warning.message.directory", projectPath, name)
                         : JavaUiBundle.message("action.attach.external.project.warning.message.file", projectPath, name);
        String title = JavaUiBundle.message("action.attach.external.project.warning.title", name);
        Messages.showWarningDialog(project, message, title);
        return false;
      }
      return true;
    };
  }
}
