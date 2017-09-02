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

import com.intellij.build.events.*;
import com.intellij.execution.actions.StopAction;
import com.intellij.execution.actions.StopProcessAction;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.FakeRerunAction;
import com.intellij.execution.ui.*;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.execution.ui.layout.impl.RunnerLayoutUiImpl;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentContainer;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.impl.ContentImpl;
import com.intellij.ui.content.tabs.PinToolwindowTabAction;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * @author Vladislav.Soroka
 */
public abstract class AbstractViewManager implements BuildProgressListener, Disposable {
  private final Project myProject;
  private final BuildContentManager myBuildContentManager;
  private final AtomicBoolean isInitializeStarted;
  private final List<Runnable> myPostponedRunnables;
  private final ProgressWatcher myProgressWatcher;
  private final ThreeComponentsSplitter myThreeComponentsSplitter;
  @Nullable
  private final JBList<BuildInfo> myBuildsList;
  private final Map<Object, BuildInfo> myBuildsMap;
  private final Map<BuildInfo, BuildView> myViewMap;
  private volatile Content myContent;
  private volatile DefaultActionGroup myToolbarActions;

  public AbstractViewManager(Project project, BuildContentManager buildContentManager) {
    myProject = project;
    myBuildContentManager = buildContentManager;
    isInitializeStarted = new AtomicBoolean();
    myPostponedRunnables = ContainerUtil.createConcurrentList();
    myThreeComponentsSplitter = new ThreeComponentsSplitter();
    Disposer.register(this, myThreeComponentsSplitter);
    if (!isTabbedView()) {
      myBuildsList = new JBList<>();
      myBuildsList.setFixedCellHeight(UIUtil.LIST_FIXED_CELL_HEIGHT * 2);
      myBuildsList.installCellRenderer(obj -> {
        BuildInfo buildInfo = (BuildInfo)obj;
        JPanel panel = new JPanel(new BorderLayout());
        SimpleColoredComponent mainComponent = new SimpleColoredComponent();
        mainComponent.setIcon(buildInfo.getIcon());
        mainComponent.append(buildInfo.title + ": ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        mainComponent.append(buildInfo.message, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        panel.add(mainComponent, BorderLayout.NORTH);
        if (buildInfo.statusMessage != null) {
          SimpleColoredComponent statusComponent = new SimpleColoredComponent();
          statusComponent.setIcon(EmptyIcon.ICON_16);
          statusComponent.append(buildInfo.statusMessage, SimpleTextAttributes.GRAY_ATTRIBUTES);
          panel.add(statusComponent, BorderLayout.SOUTH);
        }
        return panel;
      });
    }
    else {
      myBuildsList = null;
    }
    myViewMap = ContainerUtil.newConcurrentMap();
    myBuildsMap = ContainerUtil.newConcurrentMap();
    myProgressWatcher = new ProgressWatcher();
  }

  protected abstract String getViewName();

  protected boolean isTabbedView() {
    return false;
  }

  @Override
  public void onEvent(BuildEvent event) {
    List<Runnable> runnables = new SmartList<>();
    runnables.add(() -> {
      if (event instanceof StartBuildEvent) {
        if (!isTabbedView() && myBuildsList != null) {
          long currentTime = System.currentTimeMillis();
          DefaultListModel<BuildInfo> listModel = (DefaultListModel<BuildInfo>)myBuildsList.getModel();
          boolean shouldBeCleared = !listModel.isEmpty();
          for (int i = 0; i < listModel.getSize(); i++) {
            BuildInfo info = listModel.getElementAt(i);
            if (info.endTime == -1 || currentTime - info.endTime < TimeUnit.SECONDS.toMillis(1)) {
              shouldBeCleared = false;
              break;
            }
          }
          if (shouldBeCleared) {
            for (BuildView view : myViewMap.values()) {
              Disposer.dispose(view);
            }
            listModel.clear();
            myBuildsMap.clear();
            myViewMap.clear();
            myBuildsList.setVisible(false);
            myThreeComponentsSplitter.setFirstComponent(null);
            myThreeComponentsSplitter.setLastComponent(null);
            myToolbarActions.removeAll();
          }
        }
      }
      final BuildInfo buildInfo =
        myBuildsMap.computeIfAbsent(ObjectUtils.chooseNotNull(event.getParentId(), event.getId()), o -> new BuildInfo());
      if (event.getParentId() != null) {
        myBuildsMap.put(event.getId(), buildInfo);
      }
    });

    runnables.add(() -> {
      final BuildInfo buildInfo = myBuildsMap.get(event.getId());
      assert buildInfo != null;
      if (event instanceof StartBuildEvent) {
        buildInfo.title = ((StartBuildEvent)event).getBuildTitle();
        buildInfo.id = event.getId();
        buildInfo.message = event.getMessage();

        if (!isTabbedView() && myBuildsList != null) {
          DefaultListModel<BuildInfo> listModel = (DefaultListModel<BuildInfo>)myBuildsList.getModel();
          listModel.addElement(buildInfo);
        }

        ProcessHandler processHandler = ((StartBuildEvent)event).getProcessHandler();
        BuildView view = myViewMap.computeIfAbsent(buildInfo, info -> {
          ExecutionConsole executionConsole = null;
          ComponentContainer componentContainer = null;
          Supplier<RunContentDescriptor> contentDescriptorSupplier = ((StartBuildEvent)event).getContentDescriptorSupplier();
          if (contentDescriptorSupplier != null) {
            RunContentDescriptor contentDescriptor = contentDescriptorSupplier.get();
            if (contentDescriptor != null) {
              executionConsole = contentDescriptor.getExecutionConsole();
              List<AnAction> leftToolbarActions = ContainerUtil.newArrayList();
              RunnerLayoutUi layoutUi = contentDescriptor.getRunnerLayoutUi();
              if (layoutUi instanceof RunnerLayoutUiImpl) {
                RunnerLayoutUiImpl layoutUiImpl = (RunnerLayoutUiImpl)layoutUi;
                layoutUiImpl.setLeftToolbarVisible(false);
                layoutUiImpl.setContentToolbarBefore(false);
                leftToolbarActions.addAll(layoutUiImpl.getActions());
              }
              JComponent component = contentDescriptor.getComponent();
              AnAction[] leftToolbarActionsArray = leftToolbarActions.toArray(new AnAction[leftToolbarActions.size()]);
              componentContainer = new BuildConsoleView() {
                @Override
                public void onEvent(BuildEvent event) {
                }

                @Override
                public AnAction[] createConsoleActions() {
                  return leftToolbarActionsArray;
                }

                @Override
                public JComponent getComponent() {
                  return component;
                }

                @Override
                public JComponent getPreferredFocusableComponent() {
                  return component;
                }

                @Override
                public void dispose() {
                }
              };
            }
          }
          if (componentContainer == null) {
            componentContainer = executionConsole = new BuildTextConsoleView(myProject);
          }
          final BuildView buildView = new BuildView(myProject, componentContainer, ((StartBuildEvent)event));
          buildView.enableView(!isConsoleEnabledByDefault());
          if (processHandler != null) {
            if (executionConsole instanceof ConsoleView) {
              ((ConsoleView)executionConsole).attachToProcess(processHandler);
              Consumer<ConsoleView> attachedConsoleConsumer = ((StartBuildEvent)event).getAttachedConsoleConsumer();
              if (attachedConsoleConsumer != null) {
                attachedConsoleConsumer.consume((ConsoleView)executionConsole);
              }
            }
            if (!processHandler.isStartNotified()) {
              processHandler.startNotify();
            }
          }
          Disposer.register(myThreeComponentsSplitter, buildView);
          if (isTabbedView()) {
            final JComponent consoleComponent = new JPanel(new BorderLayout());
            consoleComponent.add(buildView, BorderLayout.CENTER);
            DefaultActionGroup toolbarActions = new DefaultActionGroup();
            consoleComponent.add(ActionManager.getInstance().createActionToolbar(
              "", toolbarActions, false).getComponent(), BorderLayout.WEST);
            toolbarActions.addAll(buildView.createConsoleActions());
            myBuildContentManager.addTabbedContent(
              consoleComponent, getViewName(), buildInfo.title + ", " + DateFormatUtil.formatDateTime(System.currentTimeMillis()) + " ",
              true, AllIcons.CodeStyle.Gear, buildView);
          }
          return buildView;
        });

        if (!isTabbedView() && myThreeComponentsSplitter.getLastComponent() == null) {
          myThreeComponentsSplitter.setLastComponent(view);
          myToolbarActions.removeAll();
          myToolbarActions.addAll(view.createConsoleActions());
        }
        if (!isTabbedView() &&
            myBuildsList != null &&
            myBuildsList.getModel().getSize() > 1 &&
            myThreeComponentsSplitter.getFirstComponent() == null) {
          JBScrollPane scrollPane = new JBScrollPane();
          scrollPane.setBorder(JBUI.Borders.empty());
          scrollPane.setViewportView(myBuildsList);
          myThreeComponentsSplitter.setFirstComponent(scrollPane);
          myBuildsList.setVisible(true);
          myBuildsList.setSelectedIndex(0);
          myThreeComponentsSplitter.repaint();

          for (BuildView consoleView : myViewMap.values()) {
            BuildConsoleView buildConsoleView = consoleView.getPrimaryView();
            if (buildConsoleView instanceof BuildTreeConsoleView) {
              ((BuildTreeConsoleView)buildConsoleView).hideRootNode();
            }
          }
        }
        else {
          myThreeComponentsSplitter.setFirstComponent(null);
        }
        myProgressWatcher.addBuild(buildInfo);
        //view.getPrimaryView().print("\r", ConsoleViewContentType.SYSTEM_OUTPUT);
      }
      else {
        if (event instanceof FinishBuildEvent) {
          buildInfo.endTime = event.getEventTime();
          buildInfo.message = event.getMessage();
          buildInfo.result = ((FinishBuildEvent)event).getResult();
          myProgressWatcher.stopBuild(buildInfo);
        }
        else {
          buildInfo.statusMessage = event.getMessage();
        }
      }
    });

    runnables.add(() -> {
      final BuildInfo buildInfo = myBuildsMap.get(event.getId());
      BuildView view = myViewMap.get(buildInfo);
      if (event instanceof OutputBuildEvent) {
        ComponentContainer consoleView = view.getSecondaryView();
        if (consoleView instanceof BuildConsoleView) {
          ((BuildConsoleView)consoleView).onEvent(event);
        }
        else if ((consoleView instanceof ConsoleView)) {
          ((ConsoleView)consoleView).print(event.getMessage(), ConsoleViewContentType.NORMAL_OUTPUT);
        }
      }
      else {
        view.getPrimaryView().onEvent(event);
      }
    });

    if (!isTabbedView() && myContent == null && myBuildsList != null) {
      myPostponedRunnables.addAll(runnables);
      if (isInitializeStarted.compareAndSet(false, true)) {
        UIUtil.invokeLaterIfNeeded(() -> {
          myBuildsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
          DefaultListModel<BuildInfo> listModel = new DefaultListModel<>();
          myBuildsList.setModel(listModel);
          myBuildsList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
              BuildInfo selectedBuild = myBuildsList.getSelectedValue();
              if (selectedBuild == null) return;

              BuildView view = myViewMap.get(selectedBuild);
              JComponent lastComponent = myThreeComponentsSplitter.getLastComponent();
              if (view != null && lastComponent != view.getComponent()) {
                myThreeComponentsSplitter.setLastComponent(view.getComponent());
                view.getComponent().setVisible(true);
                if (lastComponent != null) {
                  lastComponent.setVisible(false);
                }
                myToolbarActions.removeAll();
                myToolbarActions.addAll(view.createConsoleActions());
                view.getComponent().repaint();
              }

              int firstSize = myThreeComponentsSplitter.getFirstSize();
              int lastSize = myThreeComponentsSplitter.getLastSize();
              if (firstSize == 0 && lastSize == 0) {
                EdtInvocationManager.getInstance().invokeLater(() -> {
                  int width = Math.round(myThreeComponentsSplitter.getWidth() / 4f);
                  myThreeComponentsSplitter.setFirstSize(width);
                });
              }
            }
          });

          final JComponent consoleComponent = new JPanel(new BorderLayout());
          consoleComponent.add(myThreeComponentsSplitter, BorderLayout.CENTER);
          myToolbarActions = new DefaultActionGroup();
          consoleComponent.add(ActionManager.getInstance().createActionToolbar(
            "", myToolbarActions, false).getComponent(), BorderLayout.WEST);

          myContent = new ContentImpl(consoleComponent, getViewName(), true);
          myContent.setCloseable(false);
          myBuildContentManager.addContent(myContent);
          myBuildContentManager.setSelectedContent(myContent);

          List<Runnable> postponedRunnables = new ArrayList<>(myPostponedRunnables);
          myPostponedRunnables.clear();
          for (Runnable postponedRunnable : postponedRunnables) {
            postponedRunnable.run();
          }
        });
      }
    }
    else {
      UIUtil.invokeLaterIfNeeded(() -> {
        for (Runnable runnable : runnables) {
          runnable.run();
        }
      });
    }
  }

  protected boolean isConsoleEnabledByDefault() {
    return false;
  }

  @Override
  public void dispose() {
  }

  private class ProgressWatcher implements Runnable {

    private final Alarm myRefreshAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
    private final Set<BuildInfo> myBuilds = ContainerUtil.newConcurrentSet();

    @Override
    public void run() {
      myRefreshAlarm.cancelAllRequests();
      JComponent firstComponent = myThreeComponentsSplitter.getFirstComponent();
      if (firstComponent != null) {
        firstComponent.revalidate();
        firstComponent.repaint();
      }
      if (!myBuilds.isEmpty()) {
        myRefreshAlarm.addRequest(this, 300);
      }
    }

    void addBuild(BuildInfo buildInfo) {
      myBuilds.add(buildInfo);
      if (myBuilds.size() > 1) {
        myRefreshAlarm.cancelAllRequests();
        myRefreshAlarm.addRequest(this, 300);
      }
    }

    void stopBuild(BuildInfo buildInfo) {
      myBuilds.remove(buildInfo);
    }
  }

  private static class BuildInfo {
    Object id;
    String title;
    String message;
    String statusMessage;
    long endTime = -1;
    EventResult result;

    public Icon getIcon() {
      return getIcon(result);
    }

    private static Icon getIcon(EventResult result) {
      if (result == null) {
        return ExecutionNodeProgressAnimator.getCurrentFrame();
      }
      if (result instanceof FailureResult) {
        return AllIcons.Process.State.RedExcl;
      }
      if (result instanceof SkippedResult) {
        return AllIcons.Process.State.YellowStr;
      }
      return AllIcons.Process.State.GreenOK;
    }
  }

  private static class BuildView extends CompositeView<BuildConsoleView, ComponentContainer> implements DataProvider {
    private final ComponentContainer myComponentContainer;
    private final StartBuildEvent myEvent;

    public BuildView(Project project, ComponentContainer componentContainer, StartBuildEvent event) {
      super(new BuildTreeConsoleView(project), componentContainer);
      myComponentContainer = componentContainer;
      myEvent = event;
    }

    @NotNull
    @Override
    public AnAction[] createConsoleActions() {
      final DefaultActionGroup rerunActionGroup = new DefaultActionGroup();
      AnAction stopAction = null;
      if (myEvent.getProcessHandler() != null) {
        stopAction = new StopProcessAction("Stop", "Stop", myEvent.getProcessHandler());
      }
      final DefaultActionGroup consoleActionGroup = new DefaultActionGroup() {
        @Override
        public void update(AnActionEvent e) {
          super.update(e);
          e.getPresentation().setVisible(!BuildView.this.isPrimaryConsoleEnabled());
        }
      };
      if (myComponentContainer instanceof BuildConsoleView) {
        final AnAction[] consoleActions = ((BuildConsoleView)myComponentContainer).createConsoleActions();
        for (AnAction anAction : consoleActions) {
          if (anAction instanceof StopAction) {
            if (stopAction == null) {
              stopAction = anAction;
            }
          }
          else if (!(anAction instanceof FakeRerunAction ||
                     anAction instanceof PinToolwindowTabAction ||
                     anAction instanceof CloseAction)) {
            consoleActionGroup.add(anAction);
          }
        }
      }
      final DefaultActionGroup actionGroup = new DefaultActionGroup();
      for (AnAction anAction : myEvent.getRestartActions()) {
        rerunActionGroup.add(anAction);
      }
      if (stopAction != null) {
        rerunActionGroup.add(stopAction);
      }
      actionGroup.add(rerunActionGroup);
      actionGroup.addSeparator();
      AnAction[] actions = super.createConsoleActions();
      actionGroup.addAll(actions);
      if (actions.length > 0) {
        actionGroup.addSeparator();
      }
      return new AnAction[]{actionGroup, consoleActionGroup};
    }

    @Nullable
    @Override
    public Object getData(String dataId) {
      Object data = super.getData(dataId);
      if (data != null) return data;
      if (LangDataKeys.RUN_PROFILE.is(dataId)) {
        ExecutionEnvironment environment = myEvent.getExecutionEnvironment();
        return environment == null ? null : environment.getRunProfile();
      }
      if (LangDataKeys.EXECUTION_ENVIRONMENT.is(dataId)) {
        return myEvent.getExecutionEnvironment();
      }
      return null;
    }
  }
}
