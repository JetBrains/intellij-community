package com.intellij.execution;

import com.intellij.execution.impl.RunDialog;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author spleaner
 */
public class ExecutorRegistryImpl extends ExecutorRegistry {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.ExecutorRegistryImpl");

  @NonNls public static final String RUNNERS_GROUP = "RunnerActions";

  private List<Executor> myExecutors = new ArrayList<Executor>();
  private ActionManager myActionManager;
  private Map<String, Executor> myId2Executor = new HashMap<String, Executor>();
  private Map<String, AnAction> myId2Action = new HashMap<String, AnAction>();

  public ExecutorRegistryImpl(ActionManager actionManager) {
    myActionManager = actionManager;
  }

  private void initExecutor(@NotNull final Executor executor) {
    if (myId2Executor.get(executor.getId()) != null) {
      LOG.error("Executor with id: \"" + executor.getId() + "\" was already registered!");
    }

    myExecutors.add(executor);
    myId2Executor.put(executor.getId(), executor);

    final ExecutorAction action = new ExecutorAction(executor);
    myId2Action.put(executor.getId(), action);
    myActionManager.registerAction(executor.getId(), action);

    final DefaultActionGroup group = (DefaultActionGroup)myActionManager.getAction(RUNNERS_GROUP);
    group.add(action);
  }

  private void deinitExecutor(@NotNull final Executor executor) {
    myExecutors.remove(executor);
    myId2Executor.remove(executor.getId());

    final DefaultActionGroup group = (DefaultActionGroup)myActionManager.getAction(RUNNERS_GROUP);
    if (group != null) {
      final AnAction action = myId2Action.get(executor.getId());
      if (action != null) {
        group.remove(action);
        myActionManager.unregisterAction(executor.getId());
        myId2Action.remove(executor.getId());
      }
    }
  }

  @NotNull
  public Executor[] getRegisteredExecutors() {
    return myExecutors.toArray(new Executor[myExecutors.size()]);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "ExecutorRegistyImpl";
  }

  public void initComponent() {
    final Executor[] executors = Extensions.getExtensions(Executor.EXECUTOR_EXTENSION_NAME);
    for (Executor executor : executors) {
      initExecutor(executor);
    }
  }

  public void disposeComponent() {
    if (myExecutors.size() > 0) {
      for (Executor executor : myExecutors) {
        deinitExecutor(executor);
      }

      myExecutors = null;
    }

    myActionManager = null;
  }

  private static class ExecutorAction extends AnAction {
    private static final Logger LOG = Logger.getInstance("#com.intellij.execution.ExecutorRegistryImpl.ExecutorAction");

    private Executor myExecutor;

    private ExecutorAction(@NotNull final Executor executor) {
      super(executor.getStartActionText(), executor.getActionName(), executor.getIcon());

      myExecutor = executor;
    }

    public void update(final AnActionEvent e) {
      super.update(e);

      final Presentation presentation = e.getPresentation();
      boolean enabled = false;
      final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());

      if (project == null || !project.isInitialized() || project.isDisposed()) {
        presentation.setEnabled(false);
        return;
      }

      final RunnerAndConfigurationSettingsImpl selectedConfiguration = getConfiguration(project);
      if (selectedConfiguration != null && RunManagerImpl.canRunConfiguration(selectedConfiguration.getConfiguration())) {
        final ProgramRunner runner =
          RunnerRegistry.getInstance().getRunner(myExecutor.getId(), selectedConfiguration.getConfiguration());
        enabled = runner != null;

        if (enabled) {
          presentation.setDescription(runner.getInfo().getDescription());
        }
      }

      String text = getTemplatePresentation().getTextWithMnemonic();
      text = RunManagerEx.getInstanceEx(project).getConfig().isShowSettingsBeforeRun() ? text + "..." : text;

      presentation.setEnabled(enabled);
      presentation.setText(text);
    }

    @Nullable
    private static RunnerAndConfigurationSettingsImpl getConfiguration(@NotNull final Project project) {
      return RunManagerEx.getInstanceEx(project).getSelectedConfiguration();
    }

    public void actionPerformed(final AnActionEvent e) {
      final DataContext dataContext = e.getDataContext();
      final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
      if (project == null || project.isDisposed()) {
        return;
      }

      executeConfiguration(project, dataContext);
    }

    private void executeConfiguration(final Project project, DataContext dataContext) {
      final RunnerAndConfigurationSettingsImpl configuration = getConfiguration(project);
      if (configuration == null) {
        return;
      }

      final ProgramRunner runner = getRunner(myExecutor.getId(), configuration);
      LOG.assertTrue(runner != null, "Runner MUST not be null!");

      final RunManagerEx runManager = RunManagerEx.getInstanceEx(project);
      final Component component = DataKeys.CONTEXT_COMPONENT.getData(dataContext);
      LOG.assertTrue(component != null, "component MUST not be null!");
      if (runManager.getConfig().isShowSettingsBeforeRun()) {
        final RunDialog dialog = new RunDialog(project, runner.getInfo());
        dialog.show();
        if (!dialog.isOK()) return;
        dataContext = recreateDataContext(project, component);
      }

      try {
        runner.execute(configuration.getConfiguration(), dataContext, configuration.getRunnerSettings(runner),
                       configuration.getConfigurationSettings(runner));
      }
      catch (RunCanceledByUserException e) {
      }
      catch (ExecutionException e1) {
        Messages.showErrorDialog(project, ExecutionBundle.message("error.running.configuration.with.error.error.message",
                                                                  configuration.getName(), e1.getMessage()),
                                          ExecutionBundle.message("error.run.title"));
      }
    }

    @Nullable
    private static ProgramRunner getRunner(final String executorId, final RunnerAndConfigurationSettingsImpl selectedConfiguration) {
      return RunnerRegistry.getInstance().getRunner(executorId, selectedConfiguration.getConfiguration());
    }

    private static DataContext recreateDataContext(final Project project, final Component component) {
      if (component != null && component.isDisplayable()) return DataManager.getInstance().getDataContext(component);
      return SimpleDataContext.getProjectContext(project);
    }
  }
}
