package com.intellij.openapi.externalSystem.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData;
import com.intellij.openapi.externalSystem.model.project.ExternalConfigPathAware;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.externalSystem.view.ExternalSystemNode;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Forces the ide to retrieve the most up-to-date info about the linked external project and updates project state if necessary
 * (e.g. imports missing libraries).
 *
 * @author Vladislav.Soroka
 * @since 9/18/13
 */
public class RefreshExternalProjectAction extends ExternalSystemNodeAction<AbstractExternalEntityData> {

  public RefreshExternalProjectAction() {
    super(AbstractExternalEntityData.class);
    getTemplatePresentation().setText(ExternalSystemBundle.message("action.refresh.project.text", "external"));
    getTemplatePresentation().setDescription(ExternalSystemBundle.message("action.refresh.project.description", "external"));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    if(this.getClass() != RefreshExternalProjectAction.class) return;

    ProjectSystemId systemId = getSystemId(e);
    final String systemIdName = systemId != null ? systemId.getReadableName() : "external";
    Presentation presentation = e.getPresentation();
    presentation.setText(ExternalSystemBundle.message("action.refresh.project.text", systemIdName));
    presentation.setDescription(ExternalSystemBundle.message("action.refresh.project.description", systemIdName));
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

    final ExternalProjectSettings linkedProjectSettings = ExternalSystemApiUtil.getSettings(
      project, projectSystemId).getLinkedProjectSettings(externalConfigPathAware.getLinkedExternalProjectPath());
    final String externalProjectPath = linkedProjectSettings == null
                                       ? externalConfigPathAware.getLinkedExternalProjectPath()
                                       : linkedProjectSettings.getExternalProjectPath();

    ExternalSystemUtil.refreshProject(project, projectSystemId, externalProjectPath, false, ProgressExecutionMode.IN_BACKGROUND_ASYNC);
  }
}
