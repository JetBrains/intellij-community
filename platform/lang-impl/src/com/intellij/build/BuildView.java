/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.build;

import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.OutputBuildEvent;
import com.intellij.build.events.StartBuildEvent;
import com.intellij.build.process.BuildProcessHandler;
import com.intellij.execution.actions.StopAction;
import com.intellij.execution.actions.StopProcessAction;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.FakeRerunAction;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.ide.actions.PinActiveTabAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Experimental
public class BuildView extends CompositeView<ExecutionConsole> implements BuildProgressListener, ConsoleView, DataProvider {
  public static final String CONSOLE_VIEW_NAME = "consoleView";
  private final AtomicReference<StartBuildEvent> myStartBuildEventRef = new AtomicReference<>();
  private final BuildDescriptor myBuildDescriptor;
  private final Project myProject;
  private final AtomicBoolean isBuildStartEventProcessed = new AtomicBoolean();
  private final List<BuildEvent> myAfterStartEvents = ContainerUtil.createConcurrentList();
  private final ViewManager myViewManager;

  public BuildView(Project project, BuildDescriptor buildDescriptor, String selectionStateKey, ViewManager viewManager) {
    this(project, null, buildDescriptor, selectionStateKey, viewManager);
  }

  public BuildView(Project project,
                   @Nullable ExecutionConsole executionConsole,
                   BuildDescriptor buildDescriptor,
                   String selectionStateKey,
                   ViewManager viewManager) {
    super(selectionStateKey);
    myProject = project;
    myBuildDescriptor = buildDescriptor;
    myViewManager = viewManager;
    if (executionConsole != null) {
      addView(executionConsole, CONSOLE_VIEW_NAME, viewManager.isConsoleEnabledByDefault());
    }
  }

  @Override
  public void onEvent(@NotNull BuildEvent event) {
    if (event instanceof StartBuildEvent) {
      ApplicationManager.getApplication().invokeAndWait(() -> {
        onStartBuild((StartBuildEvent)event);
        for (BuildEvent buildEvent : myAfterStartEvents) {
          processEvent(buildEvent);
        }
        myAfterStartEvents.clear();
        isBuildStartEventProcessed.set(true);
      });
      return;
    }

    if (!isBuildStartEventProcessed.get()) {
      myAfterStartEvents.add(event);
    }
    else {
      processEvent(event);
    }
  }

  private void processEvent(BuildEvent event) {
    if (event instanceof OutputBuildEvent) {
      ExecutionConsole consoleView = getConsoleView();
      if (consoleView instanceof BuildProgressListener) {
        ((BuildProgressListener)consoleView).onEvent(event);
      }
    }
    else {
      String eventViewName = BuildTreeConsoleView.class.getName();
      BuildTreeConsoleView eventView = getView(eventViewName, BuildTreeConsoleView.class);
      if (eventView != null) {
        EdtExecutorService.getInstance().execute(() -> eventView.onEvent(event));
      }
    }
  }

  private void onStartBuild(StartBuildEvent startBuildEvent) {
    myStartBuildEventRef.set(startBuildEvent);
    String eventViewName = BuildTreeConsoleView.class.getName();
    BuildTreeConsoleView eventView = getView(eventViewName, BuildTreeConsoleView.class);
    if (eventView == null) {
      eventView = new BuildTreeConsoleView(myProject, myBuildDescriptor);
      addView(eventView, eventViewName, !myViewManager.isConsoleEnabledByDefault());
    }

    ExecutionConsole executionConsoleView = getConsoleView();
    if (executionConsoleView == null) {
      Supplier<RunContentDescriptor> descriptorSupplier = startBuildEvent.getContentDescriptorSupplier();
      RunContentDescriptor runContentDescriptor = descriptorSupplier != null ? descriptorSupplier.get() : null;
      executionConsoleView = runContentDescriptor != null &&
                             runContentDescriptor.getExecutionConsole() != null &&
                             runContentDescriptor.getExecutionConsole() != this ?
                             runContentDescriptor.getExecutionConsole() : new BuildTextConsoleView(myProject);
      addView(executionConsoleView, CONSOLE_VIEW_NAME, myViewManager.isConsoleEnabledByDefault());
      if (runContentDescriptor != null && Disposer.findRegisteredObject(runContentDescriptor, this) == null) {
        Disposer.register(this, runContentDescriptor);
      }
    }

    BuildProcessHandler processHandler = startBuildEvent.getProcessHandler();
    if (executionConsoleView instanceof ConsoleView) {
      for (Filter filter : startBuildEvent.getExecutionFilters()) {
        ((ConsoleView)executionConsoleView).addMessageFilter(filter);
      }

      if (processHandler != null) {
        ((ConsoleView)executionConsoleView).attachToProcess(processHandler);
        Consumer<ConsoleView> attachedConsoleConsumer = startBuildEvent.getAttachedConsoleConsumer();
        if (attachedConsoleConsumer != null) {
          attachedConsoleConsumer.consume((ConsoleView)executionConsoleView);
        }
        if (!processHandler.isStartNotified()) {
          processHandler.startNotify();
        }
      }
    }
    if (processHandler != null && !processHandler.isStartNotified()) {
      processHandler.startNotify();
    }

    eventView.onEvent(startBuildEvent);
  }

  private ExecutionConsole getConsoleView() {
    return getView(CONSOLE_VIEW_NAME, ExecutionConsole.class);
  }

  @Override
  public void print(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
    delegateToConsoleView(view -> view.print(text, contentType));
  }

  private void delegateToConsoleView(Consumer<? super ConsoleView> viewConsumer) {
    ExecutionConsole console = getConsoleView();
    if (console instanceof ConsoleView) {
      viewConsumer.consume((ConsoleView)console);
    }
  }

  @Nullable
  private <R> R getConsoleViewValue(Function<? super ConsoleView, ? extends R> viewConsumer) {
    ExecutionConsole console = getConsoleView();
    if (console instanceof ConsoleView) {
      return viewConsumer.apply((ConsoleView)console);
    }
    return null;
  }

  @Override
  public void clear() {
    delegateToConsoleView(ConsoleView::clear);
  }

  @Override
  public void scrollTo(int offset) {
    delegateToConsoleView(view -> view.scrollTo(offset));
  }

  @Override
  public void attachToProcess(ProcessHandler processHandler) {
    delegateToConsoleView(view -> view.attachToProcess(processHandler));
  }

  @Override
  public void setOutputPaused(boolean value) {
    delegateToConsoleView(view -> view.setOutputPaused(value));
  }

  @Override
  public boolean isOutputPaused() {
    Boolean result = getConsoleViewValue(ConsoleView::isOutputPaused);
    return result != null && result;
  }

  @Override
  public boolean hasDeferredOutput() {
    Boolean result = getConsoleViewValue(ConsoleView::hasDeferredOutput);
    return result != null && result;
  }

  @Override
  public void performWhenNoDeferredOutput(@NotNull Runnable runnable) {
    delegateToConsoleView(view -> view.performWhenNoDeferredOutput(runnable));
  }

  @Override
  public void setHelpId(@NotNull String helpId) {
    delegateToConsoleView(view -> view.setHelpId(helpId));
  }

  @Override
  public void addMessageFilter(@NotNull Filter filter) {
    delegateToConsoleView(view -> view.addMessageFilter(filter));
  }

  @Override
  public void printHyperlink(@NotNull String hyperlinkText, @Nullable HyperlinkInfo info) {
    delegateToConsoleView(view -> view.printHyperlink(hyperlinkText, info));
  }

  @Override
  public int getContentSize() {
    Integer result = getConsoleViewValue(ConsoleView::getContentSize);
    return result == null ? 0 : result;
  }

  @Override
  public boolean canPause() {
    Boolean result = getConsoleViewValue(ConsoleView::canPause);
    return result != null && result;
  }

  @NotNull
  @Override
  public AnAction[] createConsoleActions() {
    final DefaultActionGroup rerunActionGroup = new DefaultActionGroup();
    AnAction stopAction = null;
    StartBuildEvent startBuildEvent = myStartBuildEventRef.get();
    if (startBuildEvent != null && startBuildEvent.getProcessHandler() != null) {
      stopAction = new StopProcessAction("Stop", "Stop", startBuildEvent.getProcessHandler());
      AnAction generalStopAction = ActionManager.getInstance().getAction(IdeActions.ACTION_STOP_PROGRAM);
      if (generalStopAction != null) {
        stopAction.copyFrom(generalStopAction);
        stopAction.registerCustomShortcutSet(generalStopAction.getShortcutSet(), this);
      }
    }
    final DefaultActionGroup consoleActionGroup = new DefaultActionGroup() {
      @Override
      public void update(@NotNull AnActionEvent e) {
        super.update(e);
        String eventViewName = BuildTreeConsoleView.class.getName();
        e.getPresentation().setVisible(!BuildView.this.isViewEnabled(eventViewName));
      }
    };

    ExecutionConsole consoleView = getConsoleView();
    if (consoleView instanceof ConsoleView) {
      final AnAction[] consoleActions = ((ConsoleView)consoleView).createConsoleActions();
      for (AnAction anAction : consoleActions) {
        if (anAction instanceof StopAction) {
          if (stopAction == null) {
            stopAction = anAction;
          }
        }
        else if (!(anAction instanceof FakeRerunAction ||
                   anAction instanceof PinActiveTabAction ||
                   anAction instanceof CloseAction)) {
          consoleActionGroup.add(anAction);
        }
      }
    }
    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    if (startBuildEvent != null) {
      for (AnAction anAction : startBuildEvent.getRestartActions()) {
        rerunActionGroup.add(anAction);
      }
    }
    if (stopAction != null) {
      rerunActionGroup.add(stopAction);
    }
    actionGroup.add(rerunActionGroup);
    if (myViewManager.isBuildContentView()) {
      actionGroup.addAll(getSwitchActions());
      actionGroup.addSeparator();
    }
    return new AnAction[]{actionGroup, consoleActionGroup};
  }

  @Override
  public void allowHeavyFilters() {
    delegateToConsoleView(ConsoleView::allowHeavyFilters);
  }

  @Nullable
  @Override
  public Object getData(@NotNull String dataId) {
    if (LangDataKeys.CONSOLE_VIEW.is(dataId)) {
      return getConsoleView();
    }
    Object data = super.getData(dataId);
    if (data != null) return data;
    StartBuildEvent startBuildEvent = myStartBuildEventRef.get();
    if (startBuildEvent != null && LangDataKeys.RUN_PROFILE.is(dataId)) {
      ExecutionEnvironment environment = startBuildEvent.getExecutionEnvironment();
      return environment == null ? null : environment.getRunProfile();
    }
    if (startBuildEvent != null && LangDataKeys.EXECUTION_ENVIRONMENT.is(dataId)) {
      return startBuildEvent.getExecutionEnvironment();
    }
    return null;
  }
}
