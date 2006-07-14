package com.intellij.execution.impl;

import com.intellij.ant.AntConfiguration;
import com.intellij.ant.impl.MapDataContext;
import com.intellij.compiler.impl.ModuleCompileScope;
import com.intellij.compiler.impl.ProjectCompileScope;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.TextConsoleBuidlerFactory;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.JavaProgramRunner;
import com.intellij.execution.runners.RunStrategy;
import com.intellij.execution.runners.RunnerInfo;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.RunContentManagerImpl;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author dyoma
 */
public class ExecutionManagerImpl extends ExecutionManager implements ProjectComponent {
  public static final Key<RunProfileState> RUN_PROFILE_STATE_KEY = new Key<RunProfileState>("RUN_PROFILE_STATE_KEY");
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
        final AntConfiguration antConfiguration = AntConfiguration.getInstance(myProject);
        if (configuration instanceof RunConfiguration &&
            antConfiguration != null && antConfiguration.hasTasksToExecuteBeforeRun((RunConfiguration)configuration)) {
          final Thread thread = new Thread(new Runnable() {
            public void run() {
              final DataContext dataContext = MapDataContext.singleData(DataConstants.PROJECT, myProject);
              final boolean result = antConfiguration.executeTaskBeforeRun(dataContext, (RunConfiguration)configuration);
              if (result) {
                ApplicationManager.getApplication().invokeLater(startRunnable);
              }
            }
          });
          thread.start();
        }
        else {
          startRunnable.run();
        }
      }
    };
    Module[] modulesToCompile = state.getModulesToCompile();
    if (modulesToCompile == null) modulesToCompile = Module.EMPTY_ARRAY;
    if (getConfig().isCompileBeforeRunning(configuration) && modulesToCompile.length > 0) {
      final CompileStatusNotification callback = new CompileStatusNotification() {
        public void finished(final boolean aborted, final int errors, final int warnings, CompileContext compileContext) {
          if (errors == 0 && !aborted) {
            ApplicationManager.getApplication().invokeLater(antAwareRunnable);
          }
        }
      };
      CompileScope scope;
      if (Boolean.valueOf(System.getProperty(MAKE_PROJECT_ON_RUN_KEY, Boolean.FALSE.toString())).booleanValue()) {
        // user explicitly requested whole-project make
        scope = new ProjectCompileScope(myProject);
      }
      else {
        scope = new ModuleCompileScope(myProject, modulesToCompile, true);
      }
      scope.putUserData(RUN_PROFILE_STATE_KEY, state);
      CompilerManager.getInstance(myProject).make(scope, callback);
    }
    else {
      antAwareRunnable.run();
    }
  }

  public void execute(JavaParameters cmdLine,
                      String contentName,
                      final DataContext dataContext) throws ExecutionException {
    execute(cmdLine, contentName, dataContext, null);
  }

  public void execute(JavaParameters cmdLine, String contentName, DataContext dataContext, Filter[] filters) throws ExecutionException {
    JavaProgramRunner defaultRunner = ExecutionRegistry.getInstance().getDefaultRunner();
    RunStrategy.getInstance().execute(new DefaultRunProfile(cmdLine, contentName, filters), dataContext, defaultRunner, null, null);
  }

  private final class DefaultRunProfile implements RunProfile {
    private JavaParameters myParameters;
    private String myContentName;
    private Filter[] myFilters;

    public DefaultRunProfile(final JavaParameters parameters, String contentName, Filter[] filters) {
      myParameters = parameters;
      myContentName = contentName;
      myFilters = filters;
    }

    public RunProfileState getState(DataContext context,
                                    RunnerInfo runnerInfo,
                                    RunnerSettings runnerSettings,
                                    ConfigurationPerRunnerSettings configurationSettings) {
      final JavaCommandLineState state = new JavaCommandLineState(runnerSettings, configurationSettings) {
        protected JavaParameters createJavaParameters() {
          return myParameters;
        }
      };
      final TextConsoleBuilder builder = TextConsoleBuidlerFactory.getInstance().createBuilder(myProject);
      if (myFilters != null) {
        for (final Filter myFilter : myFilters) {
          builder.addFilter(myFilter);
        }
      }
      state.setConsoleBuilder(builder);
      return state;
    }

    public String getName() {
      return myContentName;
    }

    public void checkConfiguration() {}

    public Module[] getModules() {
      return new Module[0];
    }
  }

  private RunManagerConfig getConfig() {
    return RunManagerEx.getInstanceEx(myProject).getConfig();
  }

  @NotNull
  public String getComponentName() {
    return "ExecutionManager";
  }
}
