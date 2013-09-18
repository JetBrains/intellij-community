package com.intellij.openapi.externalSystem.action;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ExternalProjectPojo;
import com.intellij.openapi.externalSystem.service.settings.ExternalSystemConfigLocator;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 * @since 7/16/13 2:19 PM
 */
public class OpenExternalConfigAction extends AnAction implements DumbAware {

  public OpenExternalConfigAction() {
    getTemplatePresentation().setText(ExternalSystemBundle.message("action.open.config.text", "external"));
    getTemplatePresentation().setDescription(ExternalSystemBundle.message("action.open.config.description", "external"));
  }

  @Override
  public void update(AnActionEvent e) {
    ProjectSystemId externalSystemId = ExternalSystemDataKeys.EXTERNAL_SYSTEM_ID.getData(e.getDataContext());
    if (externalSystemId == null) {
      e.getPresentation().setEnabled(false);
      return;
    }

    e.getPresentation().setText(ExternalSystemBundle.message("action.open.config.text", externalSystemId.getReadableName()));
    e.getPresentation().setDescription(ExternalSystemBundle.message("action.open.config.description", externalSystemId.getReadableName()));
    e.getPresentation().setIcon(ExternalSystemUiUtil.getUiAware(externalSystemId).getProjectIcon());

    VirtualFile config = getExternalConfig(e.getDataContext());
    e.getPresentation().setEnabled(config != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) {
      return;
    }
    
    VirtualFile configFile = getExternalConfig(e.getDataContext());
    if (configFile == null) {
      return;
    }
    
    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, configFile);
    FileEditorManager.getInstance(project).openTextEditor(descriptor, true); 
  }

  @Nullable
  private static VirtualFile getExternalConfig(@NotNull DataContext context) {
    ProjectSystemId externalSystemId = ExternalSystemDataKeys.EXTERNAL_SYSTEM_ID.getData(context);
    if (externalSystemId == null) {
      return null;
    }

    ExternalProjectPojo projectPojo = ExternalSystemDataKeys.SELECTED_PROJECT.getData(context);
    if (projectPojo == null) {
      return null;
    }

    String path = projectPojo.getPath();
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    VirtualFile externalSystemConfigPath = fileSystem.refreshAndFindFileByPath(path);
    if (externalSystemConfigPath == null) {
      return null;
    }

    VirtualFile toOpen = externalSystemConfigPath;
    for (ExternalSystemConfigLocator locator : ExternalSystemConfigLocator.EP_NAME.getExtensions()) {
      if (externalSystemId.equals(locator.getTargetExternalSystemId())) {
        toOpen = locator.adjust(toOpen);
        if (toOpen == null) {
          return null;
        }
      }
    }
    return toOpen.isDirectory() ? null : toOpen;
  }
}
