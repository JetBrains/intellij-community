package com.intellij.openapi.externalSystem.service.internal;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.internal.InternalExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskState;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.service.ExternalSystemFacadeManager;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.service.remote.RemoteExternalSystemProjectResolver;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe.
 *
 * @author Denis Zhdanov
 * @since 1/24/12 7:21 AM
 */
public class ExternalSystemResolveProjectTask extends AbstractExternalSystemTask {

  private final AtomicReference<DataNode<ProjectData>> myExternalProject = new AtomicReference<>();

  @NotNull private final String myProjectPath;
  private final boolean myIsPreviewMode;

  public ExternalSystemResolveProjectTask(@NotNull ProjectSystemId externalSystemId,
                                          @NotNull Project project,
                                          @NotNull String projectPath,
                                          boolean isPreviewMode) {
    super(externalSystemId, ExternalSystemTaskType.RESOLVE_PROJECT, project, projectPath);
    myProjectPath = projectPath;
    myIsPreviewMode = isPreviewMode;
  }

  @SuppressWarnings("unchecked")
  protected void doExecute() throws Exception {
    final ExternalSystemFacadeManager manager = ServiceManager.getService(ExternalSystemFacadeManager.class);
    Project ideProject = getIdeProject();
    RemoteExternalSystemProjectResolver resolver = manager.getFacade(ideProject, myProjectPath, getExternalSystemId()).getResolver();
    ExternalSystemExecutionSettings settings = ExternalSystemApiUtil.getExecutionSettings(ideProject, myProjectPath, getExternalSystemId());

    DataNode<ProjectData> project = resolver.resolveProjectInfo(getId(), myProjectPath, myIsPreviewMode, settings);

    if (project == null) {
      return;
    }
    myExternalProject.set(project);
  }

  protected boolean doCancel() throws Exception {
    final ExternalSystemFacadeManager manager = ServiceManager.getService(ExternalSystemFacadeManager.class);
    Project ideProject = getIdeProject();
    RemoteExternalSystemProjectResolver resolver = manager.getFacade(ideProject, myProjectPath, getExternalSystemId()).getResolver();

    return resolver.cancelTask(getId());
  }

  @Nullable
  public DataNode<ProjectData> getExternalProject() {
    return myExternalProject.get();
  }

  @Override
  @NotNull
  protected String wrapProgressText(@NotNull String text) {
    return ExternalSystemBundle.message("progress.update.text", getExternalSystemId().getReadableName(), text);
  }

  @Override
  protected void setState(@NotNull ExternalSystemTaskState state) {
    super.setState(state);
    if (state.isStopped()) {
      InternalExternalProjectInfo projectInfo =
        new InternalExternalProjectInfo(getExternalSystemId(), getExternalProjectPath(), getExternalProject());
      final long currentTimeMillis = System.currentTimeMillis();
      projectInfo.setLastImportTimestamp(currentTimeMillis);
      projectInfo.setLastSuccessfulImportTimestamp(state == ExternalSystemTaskState.FAILED ? -1 : currentTimeMillis);
      ProjectDataManager.getInstance().updateExternalProjectData(getIdeProject(), projectInfo);
    }
  }
}
