package com.intellij.execution.impl;

import com.intellij.debugger.impl.GenericDebuggerRunner;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionRegistry;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.actions.RunContextAction;
import com.intellij.execution.actions.RunnerAction;
import com.intellij.execution.configurations.ConfigurationInfoProvider;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.runners.JavaProgramRunner;
import com.intellij.execution.runners.RunStrategy;
import com.intellij.execution.runners.RunnerInfo;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;


public class ExecutionRegistryImpl extends ExecutionRegistry {
  private final EventDispatcher<Listener> myListeners = EventDispatcher.create(Listener.class);
  private final List<JavaProgramRunner> myRunnersOrder = new ArrayList<JavaProgramRunner>();
  private JavaProgramRunner myDefaultRunner;
  private JavaProgramRunner myDebuggerRunner;
  @NonNls public static final String RUN_CONTEXT_GROUP = "RunContextGroup";
  @NonNls public static final String RUNNERS_GROUP = "RunnerActions";
  private ActionManager myActionManager;

  public ExecutionRegistryImpl(ActionManager actionManager) {
    myActionManager = actionManager;

    // default runners
    addListener(new BaseRunnerActionListener(RUNNERS_GROUP) {
      protected String getActionId(final JavaProgramRunner runner) {
        return runner.getInfo().getRunActionId();
      }

      protected AnAction createActionFor(final JavaProgramRunner runner) {
        return new RunnerAction(runner);
      }
    });
    addListener(new BaseRunnerActionListener(RUN_CONTEXT_GROUP) {
      protected String getActionId(final JavaProgramRunner runner) {
        return runner.getInfo().getRunContextActionId();
      }

      protected AnAction createActionFor(final JavaProgramRunner runner) {
        return new RunContextAction(runner);
      }
    });

    myDefaultRunner  = new DefaultJavaProgramRunner();
    myDebuggerRunner = new GenericDebuggerRunner();

    registerRunner(myDefaultRunner);
    registerRunner(myDebuggerRunner);
  }

  @NotNull
  public String getComponentName() {
    return "ExecutionRegistryImpl";
  }

  public void initComponent() { }

  public synchronized void disposeComponent() {
    while (myRunnersOrder.size() > 0) {
      final JavaProgramRunner runner = myRunnersOrder.get(myRunnersOrder.size() - 1);
      unregisterRunner(runner);
    }
    myListeners.getMulticaster().onRegisteryDisposed(this);
  }

  public void addListener(final Listener listener) {
    myListeners.addListener(listener);
  }

  public void removeListener(final Listener listener) {
    myListeners.removeListener(listener);
  }

  public synchronized void registerRunner(final JavaProgramRunner runner) {
    if (myRunnersOrder.contains(runner)) return;
    myRunnersOrder.add(runner);
    myListeners.getMulticaster().onRunnerRegistered(runner);
  }

  public synchronized void unregisterRunner(final JavaProgramRunner runner) {
    if (!myRunnersOrder.remove(runner)) return;
    if (runner == myDefaultRunner) myDefaultRunner = null;
    myListeners.getMulticaster().onRunnerUnregistered(runner);
  }

  public JavaProgramRunner getDefaultRunner() {
    return myDefaultRunner;
  }

  public JavaProgramRunner getDebuggerRunner() {
    return myDebuggerRunner;
  }

  public synchronized JavaProgramRunner[] getRegisteredRunners() {
    return myRunnersOrder.toArray(new JavaProgramRunner[myRunnersOrder.size()]);
  }

  public interface Listener extends EventListener {
    void onRunnerRegistered(JavaProgramRunner runner);

    void onRunnerUnregistered(JavaProgramRunner runner);

    void onRegisteryDisposed(ExecutionRegistryImpl registry);
  }

  private abstract class BaseRunnerActionListener implements Listener {
    private final Set<JavaProgramRunner> myRunnersWithActions = new HashSet<JavaProgramRunner>();
    private final DefaultActionGroup myActionGroup;

    protected BaseRunnerActionListener(final String groupId) {
      myActionGroup = (DefaultActionGroup)myActionManager.getAction(groupId);
    }

    public void onRegisteryDisposed(final ExecutionRegistryImpl registry) {
      registry.removeListener(this);
    }

    public void onRunnerRegistered(final JavaProgramRunner runner) {
      final String actionId = getActionId(runner);
      final ActionManager actionManager = myActionManager;
      AnAction action = actionManager.getAction(actionId);
      if (action == null) {
        action = createActionFor(runner);
        myRunnersWithActions.add(runner);
        actionManager.registerAction(actionId, action);
      }
      myActionGroup.add(action);
    }

    public void onRunnerUnregistered(final JavaProgramRunner runner) {
      final ActionManager actionManager = myActionManager;
      final String actionId = getActionId(runner);
      myActionGroup.remove(actionManager.getAction(actionId));
      if (!myRunnersWithActions.contains(runner)) return;
      actionManager.unregisterAction(actionId);
      myRunnersWithActions.remove(runner);
    }

    protected abstract String getActionId(JavaProgramRunner runner);

    protected abstract AnAction createActionFor(JavaProgramRunner runner);
  }

  public JavaProgramRunner findRunnerById(String id) {
    JavaProgramRunner[] registeredRunners = getRegisteredRunners();
    for (JavaProgramRunner registeredRunner : registeredRunners) {
      if (Comparing.equal(id, registeredRunner.getInfo().getId())) {
        return registeredRunner;
      }
    }
    return null;
  }

  private static class DefaultJavaProgramRunner implements JavaProgramRunner {
    public JDOMExternalizable createConfigurationData(ConfigurationInfoProvider settingsProvider) {
      return null;
    }

    public SettingsEditor<JDOMExternalizable> getSettingsEditor(RunConfiguration configuration) {
      return null;
    }

    public void patch(JavaParameters javaParameters, RunnerSettings settings) throws ExecutionException {
    }

    public AnAction[] createActions(ExecutionResult executionResult) {
      return AnAction.EMPTY_ARRAY;
    }

    public RunnerInfo getInfo() {
      return RunStrategy.getInstance().getInfo();
    }
  }
}
