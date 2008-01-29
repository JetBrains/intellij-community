package com.intellij.execution.impl;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.StepsBeforeRunProvider;
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
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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
    RunContentDescriptor[] descriptors = myContentManager.getAllDescriptors();
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
          final Map<String, Boolean> beforeRun = runManager.getStepsBeforeLaunch(runConfiguration);

          final Collection<StepsBeforeRunProvider> activeProviders = new ArrayList<StepsBeforeRunProvider>();
          for (final StepsBeforeRunProvider provider : Extensions.getExtensions(StepsBeforeRunProvider.EXTENSION_POINT_NAME, myProject)) {
            final Boolean enabled = beforeRun.get(provider.getStepName());
            if (enabled != null && enabled.booleanValue() && provider.hasTask(runConfiguration)) {
              activeProviders.add(provider);
            }
          }

          if (!activeProviders.isEmpty()) {
            ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
              public void run() {
                final DataContext dataContext = SimpleDataContext.getProjectContext(myProject);
                for (StepsBeforeRunProvider provider : activeProviders) {
                  if(!provider.executeTask(dataContext, runConfiguration)) {
                    return;
                  }
                }
                ApplicationManager.getApplication().invokeLater(startRunnable);
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
