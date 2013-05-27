package com.intellij.openapi.externalSystem.action;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.serialization.ExternalTaskPojo;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemExecuteTaskTask;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 * @since 22.05.13 13:04
 */
public abstract class AbstractExternalTaskAction extends AnAction implements DumbAware {

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(getSelectedTask(e.getDataContext()) != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) {
      return;
    }

    ProjectSystemId externalSystemId = ExternalSystemDataKeys.EXTERNAL_SYSTEM_ID.getData(e.getDataContext());
    if (externalSystemId == null) {
      return;
    }

    ExternalTaskPojo taskPojo = getSelectedTask(e.getDataContext(), externalSystemId);
    if (taskPojo == null) {
      return;
    }

    ExternalSystemManager<?,?,?,?,?> manager = ExternalSystemApiUtil.getManager(externalSystemId);
    if (manager == null) {
      return;
    }

    final ExternalSystemExecuteTaskTask task
      = new ExternalSystemExecuteTaskTask(externalSystemId, project, ContainerUtilRt.newArrayList(taskPojo), getVmOptions());
    final boolean interestedInTaskOutput = prepareForOutput();
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        if (interestedInTaskOutput) {
          task.execute(new ExternalSystemTaskNotificationListenerAdapter() {
            @Override
            public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
              onOutput(text, stdOut ? ConsoleViewContentType.NORMAL_OUTPUT : ConsoleViewContentType.ERROR_OUTPUT);
            }
          });
        }
        else {
          task.execute();
        }
      }
    });
  }

  @Nullable
  protected abstract String getVmOptions();

  // TODO den add doc
  protected abstract boolean prepareForOutput();

  protected abstract void onOutput(@NotNull String text, @NotNull ConsoleViewContentType type);

  @Nullable
  private static ExternalTaskPojo getSelectedTask(@NotNull DataContext dataContext) {
    return getSelectedTask(dataContext, ExternalSystemDataKeys.EXTERNAL_SYSTEM_ID.getData(dataContext));
  }

  private static ExternalTaskPojo getSelectedTask(@NotNull DataContext dataContext, @Nullable ProjectSystemId externalSystemId) {
    if (externalSystemId == null) {
      return null;
    }
    return  ExternalSystemUtil.getToolWindowElement(ExternalTaskPojo.class,
                                                    dataContext,
                                                    ExternalSystemDataKeys.SELECTED_TASK,
                                                    externalSystemId);
  }
}
