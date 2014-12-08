package com.intellij.openapi.externalSystem.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData;
import com.intellij.openapi.externalSystem.model.project.ExternalConfigPathAware;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.externalSystem.view.ExternalSystemNode;
import com.intellij.openapi.externalSystem.view.ProjectNode;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * * Forces the ide to retrieve the most up-to-date info about the linked external project and updates project state if necessary
 * (e.g. imports missing libraries).
 *
 * @author Vladislav.Soroka
 * @since 9/18/13
 */
public class RefreshExternalProjectAction extends ExternalSystemNodeAction<AbstractExternalEntityData> implements DumbAware {

  public RefreshExternalProjectAction() {
    super(AbstractExternalEntityData.class);
    getTemplatePresentation().setText(ExternalSystemBundle.message("action.refresh.project.text", "external"));
    getTemplatePresentation().setDescription(ExternalSystemBundle.message("action.refresh.project.description", "external"));
  }

  @Override
  protected boolean isEnabled(AnActionEvent e) {
    if (!super.isEnabled(e)) return false;
    final List<ExternalSystemNode> selectedNodes = ExternalSystemDataKeys.SELECTED_NODES.getData(e.getDataContext());
    if (selectedNodes == null || selectedNodes.size() != 1) return false;
    final Object externalData = selectedNodes.get(0).getData();
    return (externalData instanceof ProjectData || externalData instanceof ModuleData);
  }

  @Override
  public void perform(@NotNull final Project project,
                      @NotNull ProjectSystemId projectSystemId,
                      @NotNull AbstractExternalEntityData externalEntityData,
                      @NotNull AnActionEvent e) {

    final List<ExternalSystemNode> selectedNodes = ExternalSystemDataKeys.SELECTED_NODES.getData(e.getDataContext());
    final ExternalSystemNode<?> externalSystemNode = ContainerUtil.getFirstItem(selectedNodes);
    assert externalSystemNode != null;

    final ExternalConfigPathAware externalConfigPathAware =
      externalSystemNode.getData() instanceof ExternalConfigPathAware ? (ExternalConfigPathAware)externalSystemNode.getData() : null;
    assert externalConfigPathAware != null;

    // We save all documents because there is a possible case that there is an external system config file changed inside the ide.
    FileDocumentManager.getInstance().saveAllDocuments();

    final ProjectDataManager projectDataManager = ServiceManager.getService(ProjectDataManager.class);
    ExternalSystemUtil.refreshProject(
      project, projectSystemId, externalConfigPathAware.getLinkedExternalProjectPath(),
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
