package com.intellij.execution.impl;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.RunContentManagerImpl;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author dyoma
 */
public class ExecutionManagerImpl extends ExecutionManager implements ProjectComponent {
  private final Project myProject;

  private RunContentManagerImpl myContentManager;
  @NonNls
  protected static final String MAKE_PROJECT_ON_RUN_KEY = "makeProjectOnRun";

  /**
   * reflection
   */
  ExecutionManagerImpl(final Project project) {
    myProject = project;
  }

  public void projectOpened() {
    ((RunContentManagerImpl)getContentManager()).init();
  }

  public void projectClosed() {
    myContentManager.dispose();
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public RunContentManager getContentManager() {
    if (myContentManager == null) {
      myContentManager = new RunContentManagerImpl(myProject);
    }
    return myContentManager;
  }

  public ProcessHandler[] getRunningProcesses() {
    final List<ProcessHandler> handlers = new ArrayList<ProcessHandler>();
    RunContentDescriptor[] descriptors = ((RunContentManagerImpl)getContentManager()).getAllDescriptors();
    for (RunContentDescriptor descriptor : descriptors) {
      if (descriptor != null) {
        final ProcessHandler processHandler = descriptor.getProcessHandler();
        if (processHandler != null) {
          handlers.add(processHandler);
        }
      }
    }
    return handlers.toArray(new ProcessHandler[handlers.size()]);
  }

  public void compileAndRun(final Runnable startRunnable,
                            final RunProfile configuration,
                            final RunProfileState state) {
    final Runnable antAwareRunnable = new Runnable() {
      public void run() {
        if (configuration instanceof RunConfiguration) {
          final RunConfiguration runConfiguration = (RunConfiguration)configuration;
          final RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(myProject);

          final Map<BeforeRunTaskProvider<BeforeRunTask>, BeforeRunTask> activeProviders = new LinkedHashMap<BeforeRunTaskProvider<BeforeRunTask>, BeforeRunTask>();
          for (final BeforeRunTaskProvider<BeforeRunTask> provider : Extensions.getExtensions(BeforeRunTaskProvider.EXTENSION_POINT_NAME, myProject)) {
            final BeforeRunTask task = runManager.getBeforeRunTask(runConfiguration, provider.getId());
            if (task != null && task.isEnabled()) {
              activeProviders.put(provider, task);
            }
          }

          if (!activeProviders.isEmpty()) {
            ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
              public void run() {
                final DataContext dataContext = SimpleDataContext.getProjectContext(myProject);
                for (BeforeRunTaskProvider<BeforeRunTask> provider : activeProviders.keySet()) {
                  if(!provider.executeTask(dataContext, runConfiguration, activeProviders.get(provider))) {
                    return;
                  }
                }
                DumbService.getInstance(myProject).smartInvokeLater(startRunnable);
              }
            });
          }
          else {
            startRunnable.run();
          }
        }
        else {
          startRunnable.run();
        }
      }
    };

    antAwareRunnable.run();
    //ApplicationManager.getApplication().invokeLater(antAwareRunnable);
  }

  @NotNull
  public String getComponentName() {
    return "ExecutionManager";
  }
}
