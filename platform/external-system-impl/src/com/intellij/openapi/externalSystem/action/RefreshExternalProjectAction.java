package com.intellij.openapi.externalSystem.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

/**
 * * Forces the ide to retrieve the most up-to-date info about the linked external project and updates project state if necessary
 * (e.g. imports missing libraries).
 *
 * @author Vladislav.Soroka
 * @since 9/18/13
 */
public class RefreshExternalProjectAction extends AnAction implements DumbAware, AnAction.TransparentUpdate {

  public RefreshExternalProjectAction() {
    getTemplatePresentation().setText(ExternalSystemBundle.message("action.refresh.project.text", "external"));
    getTemplatePresentation().setDescription(ExternalSystemBundle.message("action.refresh.project.description", "external"));
  }

  @Override
  public void update(AnActionEvent e) {
    ExternalActionUtil.MyInfo info = ExternalActionUtil.getProcessingInfo(e.getDataContext());
    e.getPresentation().setEnabled(info.externalProject != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    ExternalActionUtil.MyInfo info = ExternalActionUtil.getProcessingInfo(e.getDataContext());
    if (info.settings == null || info.localSettings == null || info.externalProject == null || info.ideProject == null
        || info.externalSystemId == null)
    {
      return;
    }
    ProjectSystemId externalSystemId = ExternalSystemDataKeys.EXTERNAL_SYSTEM_ID.getData(e.getDataContext());
    if (externalSystemId == null) {
      return;
    }
    
    final Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) {
      e.getPresentation().setEnabled(false);
      return;
    }

    // We save all documents because there is a possible case that there is an external system config file changed inside the ide.
    FileDocumentManager.getInstance().saveAllDocuments();

    final ProjectDataManager projectDataManager = ServiceManager.getService(ProjectDataManager.class);
    ExternalSystemUtil.refreshProject(
      project, externalSystemId, info.externalProject.getPath(),
      new ExternalProjectRefreshCallback() {
        @Override
        public void onSuccess(@Nullable final DataNode<ProjectData> externalProject) {
          if (externalProject == null) {
            return;
          }
          ExternalSystemApiUtil.executeProjectChangeAction(true, new DisposeAwareProjectChange(project) {
            @Override
            public void execute() {
              ProjectRootManagerEx.getInstanceEx(project).mergeRootsChangesDuring(new Runnable() {
                @Override
                public void run() {
                  projectDataManager.importData(externalProject.getKey(), Collections.singleton(externalProject), project, true);
                }
              });
            }
          });
        }

        @Override
        public void onFailure(@NotNull String errorMessage, @Nullable String errorDetails) {
        }
      }, false, ProgressExecutionMode.IN_BACKGROUND_ASYNC);
  }
}
