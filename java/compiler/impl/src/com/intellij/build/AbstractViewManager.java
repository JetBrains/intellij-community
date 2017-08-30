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
import com.intellij.execution.console.DuplexConsoleView;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.impl.ContentImpl;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
  private final Map<BuildInfo, DuplexConsoleView<BuildConsoleView, BuildConsoleView>> myViewMap;
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
            for (DuplexConsoleView<BuildConsoleView, BuildConsoleView> view : myViewMap.values()) {
              view.clear();
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

        DuplexConsoleView<BuildConsoleView, BuildConsoleView> view = myViewMap.computeIfAbsent(buildInfo, info -> {
          BuildTextConsoleView textConsoleView = new BuildTextConsoleView(myProject, false, "CONSOLE");
          final DuplexConsoleView<BuildConsoleView, BuildConsoleView> duplexConsoleView =
            new DuplexConsoleView<>(textConsoleView, new BuildTreeConsoleView(myProject));
          duplexConsoleView.setDisableSwitchConsoleActionOnProcessEnd(false);
          if(isConsoleEnabledByDefault()) {
            duplexConsoleView.enableConsole(true);
          }
          ProcessHandler processHandler = ((StartBuildEvent)event).getProcessHandler();
          if (processHandler != null) {
            textConsoleView.attachToProcess(processHandler);
            Consumer<BuildConsoleView> attachedConsoleConsumer = ((StartBuildEvent)event).getAttachedConsoleConsumer();
            if (attachedConsoleConsumer != null) {
              attachedConsoleConsumer.consume(textConsoleView);
            }
          }
          Disposer.register(myThreeComponentsSplitter, duplexConsoleView);
          if (isTabbedView()) {
            final JComponent consoleComponent = new JPanel(new BorderLayout());
            consoleComponent.add(duplexConsoleView, BorderLayout.CENTER);
            DefaultActionGroup toolbarActions = new DefaultActionGroup();
            consoleComponent.add(ActionManager.getInstance().createActionToolbar(
              "", toolbarActions, false).getComponent(), BorderLayout.WEST);
            toolbarActions.addAll(duplexConsoleView.createConsoleActions());
            myBuildContentManager.addTabbedContent(
              consoleComponent, getViewName(), buildInfo.title + ", " + DateFormatUtil.formatDateTime(System.currentTimeMillis()) + " ",
              true, AllIcons.CodeStyle.Gear, duplexConsoleView);
          }
          return duplexConsoleView;
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
          scrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));
          scrollPane.setViewportView(myBuildsList);
          myThreeComponentsSplitter.setFirstComponent(scrollPane);
          myBuildsList.setVisible(true);
          myBuildsList.setSelectedIndex(0);
          myThreeComponentsSplitter.repaint();

          for (DuplexConsoleView<BuildConsoleView, BuildConsoleView> consoleView : myViewMap.values()) {
            BuildConsoleView buildConsoleView = consoleView.getSecondaryConsoleView();
            if (buildConsoleView instanceof BuildTreeConsoleView) {
              ((BuildTreeConsoleView)buildConsoleView).hideRootNode();
            }
          }
        }
        else {
          myThreeComponentsSplitter.setFirstComponent(null);
        }
        myProgressWatcher.addBuild(buildInfo);
        view.getPrimaryConsoleView().print("\r", ConsoleViewContentType.SYSTEM_OUTPUT);
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
      DuplexConsoleView<BuildConsoleView, BuildConsoleView> view = myViewMap.get(buildInfo);
      if (event instanceof OutputBuildEvent) {
        view.getPrimaryConsoleView().onEvent(event);
      }
      else {
        view.getSecondaryConsoleView().onEvent(event);
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

              DuplexConsoleView<BuildConsoleView, BuildConsoleView> view = myViewMap.get(selectedBuild);
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
}
