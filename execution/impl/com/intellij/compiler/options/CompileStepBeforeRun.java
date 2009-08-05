package com.intellij.compiler.options;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfileWithCompileBeforeLaunchOption;
import com.intellij.execution.remote.RemoteConfiguration;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author spleaner
 */
public class CompileStepBeforeRun implements BeforeRunTaskProvider<CompileStepBeforeRun.MakeBeforeRunTask> {
  public static final Key<MakeBeforeRunTask> ID = Key.create("Make");
  private static final Key<RunConfiguration> RUN_CONFIGURATION = Key.create("RUN_CONFIGURATION");

  @NonNls protected static final String MAKE_PROJECT_ON_RUN_KEY = "makeProjectOnRun";
  private final Project myProject;

  public CompileStepBeforeRun(@NotNull final Project project) {
    myProject = project;
  }

  public Key<MakeBeforeRunTask> getId() {
    return ID;
  }

  public String getDescription(final RunConfiguration runConfiguration, MakeBeforeRunTask task) {
    return ExecutionBundle.message("before.launch.compile.step");
  }

  public MakeBeforeRunTask createTask(RunConfiguration runConfiguration) {
    return runConfiguration instanceof RemoteConfiguration? null : new MakeBeforeRunTask();
  }

  public void configureTask(RunConfiguration runConfiguration, MakeBeforeRunTask task) {
  }

  public boolean executeTask(DataContext context, final RunConfiguration configuration, MakeBeforeRunTask task) {
    if (!(configuration instanceof RunProfileWithCompileBeforeLaunchOption)) {
      return true;
    }

    final RunProfileWithCompileBeforeLaunchOption runConfiguration = (RunProfileWithCompileBeforeLaunchOption)configuration;
    final Semaphore done = new Semaphore();
    final boolean[] result = new boolean[1];
    try {
      final CompileStatusNotification callback = new CompileStatusNotification() {
        public void finished(final boolean aborted, final int errors, final int warnings, CompileContext compileContext) {
          if (errors == 0 && !aborted) {
            result[0] = true;
          }

          done.up();
        }
      };

      ApplicationManager.getApplication().invokeAndWait(new Runnable() {
        public void run() {
          CompileScope scope;
          final CompilerManager compilerManager = CompilerManager.getInstance(myProject);
          if (Boolean.valueOf(System.getProperty(MAKE_PROJECT_ON_RUN_KEY, Boolean.FALSE.toString())).booleanValue()) {
            // user explicitly requested whole-project make
            scope = compilerManager.createProjectCompileScope(myProject);
          }
          else {
            final Module[] modules = runConfiguration.getModules();
            if (modules.length > 0) {
              scope = compilerManager.createModulesCompileScope(modules, true);
            }
            else {
              scope = compilerManager.createProjectCompileScope(myProject);
            }
          }

          done.down();
          scope.putUserData(RUN_CONFIGURATION, configuration);
          compilerManager.make(scope, callback);
        }
      }, ModalityState.NON_MODAL);
    }
    catch (Exception e) {
      return false;
    }

    done.waitFor();
    return result[0];
  }

  public boolean hasConfigurationButton() {
    return false;
  }

  @Nullable
  public static RunConfiguration getRunConfiguration(final CompileContext context) {
    return context.getCompileScope().getUserData(RUN_CONFIGURATION);
  }

  @Nullable
  public static RunConfiguration getRunConfiguration(final CompileScope compileScope) {
    return compileScope.getUserData(RUN_CONFIGURATION);
  }

  public static class MakeBeforeRunTask extends BeforeRunTask {
    private MakeBeforeRunTask() {
      setEnabled(true);
    }
  }
}
