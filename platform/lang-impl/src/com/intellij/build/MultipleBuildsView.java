// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build;

import com.intellij.build.events.*;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.impl.ContentImpl;
import com.intellij.util.SingleEdtTaskScheduler;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Experimental
public final class MultipleBuildsView implements BuildProgressListener, Disposable {
  private static final Logger LOG = Logger.getInstance(MultipleBuildsView.class);
  private static final @NonNls String SPLITTER_PROPERTY = "MultipleBuildsView.Splitter.Proportion";

  private final Project myProject;
  private final BuildContentManager myBuildContentManager;
  private final AtomicBoolean isInitializeStarted;
  private final AtomicBoolean isFirstErrorShown = new AtomicBoolean();
  private final List<Runnable> myPostponedRunnables;
  private final ProgressWatcher myProgressWatcher;
  private final OnePixelSplitter threeComponentsSplitter;
  private final JBList<AbstractViewManager.BuildInfo> myBuildsList;
  private final Map<Object, AbstractViewManager.BuildInfo> myBuildsMap;
  private final Map<AbstractViewManager.BuildInfo, BuildView> myViewMap;
  private final AbstractViewManager myViewManager;
  private volatile Content myContent;
  private volatile DefaultActionGroup myToolbarActions;
  private volatile boolean myDisposed;
  private BuildView myActiveView;

  public MultipleBuildsView(Project project,
                            BuildContentManager buildContentManager,
                            AbstractViewManager viewManager) {
    myProject = project;
    myBuildContentManager = buildContentManager;
    myViewManager = viewManager;
    isInitializeStarted = new AtomicBoolean();
    myPostponedRunnables = ContainerUtil.createConcurrentList();
    threeComponentsSplitter = new OnePixelSplitter(SPLITTER_PROPERTY, 0.25f);
    if (ExperimentalUI.isNewUI()) {
      ScrollableContentBorder.setup(threeComponentsSplitter, Side.LEFT);
    }
    myBuildsList = new JBList<>();
    myBuildsList.setModel(new DefaultListModel<>());
    updateBuildsListRowHeight();
    AnsiEscapeDecoder ansiEscapeDecoder = new AnsiEscapeDecoder();
    myBuildsList.installCellRenderer(obj -> {
      JPanel panel = new JPanel(new BorderLayout());
      SimpleColoredComponent mainComponent = new SimpleColoredComponent();
      mainComponent.setIcon(obj.getIcon());
      mainComponent.append(obj.getTitle() + ": ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      mainComponent.append(obj.message, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      panel.add(mainComponent, BorderLayout.NORTH);
      if (obj.statusMessage != null) {
        SimpleColoredComponent statusComponent = new SimpleColoredComponent();
        statusComponent.setIcon(EmptyIcon.ICON_16);
        ansiEscapeDecoder.escapeText(obj.statusMessage, ProcessOutputTypes.STDOUT, (text, attributes) -> {
          statusComponent.append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES); //NON-NLS
        });
        panel.add(statusComponent, BorderLayout.SOUTH);
      }
      return panel;
    });
    myViewMap = new ConcurrentHashMap<>();
    myBuildsMap = new ConcurrentHashMap<>();
    myProgressWatcher = new ProgressWatcher();
  }

  private void updateBuildsListRowHeight() {
    myBuildsList.setFixedCellHeight(JBUI.scale(UIUtil.LIST_FIXED_CELL_HEIGHT * 2));
  }

  @Override
  public void dispose() {
    myDisposed = true;
    myProgressWatcher.stopWatching();
  }

  public Content getContent() {
    return myContent;
  }

  public Map<BuildDescriptor, BuildView> getBuildsMap() {
    return Collections.unmodifiableMap(myViewMap);
  }

  public boolean shouldConsume(@NotNull Object buildId) {
    return myBuildsMap.containsKey(buildId);
  }

  @Override
  public void onEvent(@NotNull Object buildId, @NotNull BuildEvent event) {
    List<Runnable> runOnEdt = new SmartList<>();
    AbstractViewManager.BuildInfo buildInfo;
    if (event instanceof StartBuildEvent startBuildEvent) {
      if (isInitializeStarted.get()) {
        clearOldBuilds(runOnEdt, startBuildEvent);
      }
      buildInfo = new AbstractViewManager.BuildInfo(startBuildEvent.getBuildDescriptor());
      myBuildsMap.put(buildId, buildInfo);
    }
    else {
      buildInfo = myBuildsMap.get(buildId);
    }
    if (buildInfo == null) {
      LOG.warn("Build can not be found for buildId: '" + buildId + "'");
      return;
    }

    runOnEdt.add(() -> {
      if (event instanceof StartBuildEvent) {
        buildInfo.message = event.getMessage();

        DefaultListModel<AbstractViewManager.BuildInfo> listModel =
          (DefaultListModel<AbstractViewManager.BuildInfo>)myBuildsList.getModel();
        listModel.addElement(buildInfo);

        RunContentDescriptor contentDescriptor;
        Supplier<? extends RunContentDescriptor> contentDescriptorSupplier = buildInfo.getContentDescriptorSupplier();
        contentDescriptor = contentDescriptorSupplier != null ? contentDescriptorSupplier.get() : null;
        final Runnable activationCallback;
        if (contentDescriptor != null) {
          buildInfo.setActivateToolWindowWhenAdded(contentDescriptor.isActivateToolWindowWhenAdded());
          if (contentDescriptor instanceof BuildContentDescriptor) {
            buildInfo.setNavigateToError(((BuildContentDescriptor)contentDescriptor).isNavigateToError());
            buildInfo.setActivateToolWindowWhenFailed(((BuildContentDescriptor)contentDescriptor).isActivateToolWindowWhenFailed());
          }
          buildInfo.setAutoFocusContent(contentDescriptor.isAutoFocusContent());
          activationCallback = contentDescriptor.getActivationCallback();
        }
        else {
          activationCallback = null;
        }

        BuildView view = myViewMap.computeIfAbsent(buildInfo, info -> {
          String selectionStateKey = "build.toolwindow." + myViewManager.getViewName() + ".selection.state";
          BuildView buildView = new BuildView(myProject, buildInfo, selectionStateKey, myViewManager);
          Disposer.register(this, buildView);
          if (contentDescriptor != null) {
            Disposer.register(buildView, contentDescriptor);
          }
          return buildView;
        });
        view.onEvent(buildId, event);

        myBuildContentManager.setSelectedContent(myContent,
                                                 buildInfo.isAutoFocusContent(),
                                                 buildInfo.isAutoFocusContent(),
                                                 buildInfo.isActivateToolWindowWhenAdded(),
                                                 activationCallback);
        buildInfo.content = myContent;

        if (myActiveView == null) {
          setActiveView(view);
        }
        if (myBuildsList.getModel().getSize() > 1) {
          JBScrollPane scrollPane = new JBScrollPane();
          scrollPane.setBorder(JBUI.Borders.empty());
          scrollPane.setViewportView(myBuildsList);
          threeComponentsSplitter.setFirstComponent(scrollPane);
          myBuildsList.setVisible(true);
          myBuildsList.setSelectedIndex(0);

          for (BuildView consoleView : myViewMap.values()) {
            BuildTreeConsoleView buildConsoleView = consoleView.getView(BuildTreeConsoleView.class.getName(), BuildTreeConsoleView.class);
            if (buildConsoleView != null) {
              buildConsoleView.hideRootNode();
            }
          }
        }
        else {
          threeComponentsSplitter.setFirstComponent(null);
        }
        myViewManager.onBuildStart(buildInfo);
        myProgressWatcher.addBuild(buildInfo);
        ((BuildContentManagerImpl)myBuildContentManager).startBuildNotified(buildInfo, buildInfo.content, buildInfo.getProcessHandler());
      }
      else {
        if (!isFirstErrorShown.get() &&
            (event instanceof FinishEvent && ((FinishEvent)event).getResult() instanceof FailureResult) ||
            (event instanceof MessageEvent && ((MessageEvent)event).getResult().getKind() == MessageEvent.Kind.ERROR)) {
          if (isFirstErrorShown.compareAndSet(false, true)) {
            ListModel<AbstractViewManager.BuildInfo> listModel = myBuildsList.getModel();
            IntStream.range(0, listModel.getSize())
              .filter(i -> buildInfo == listModel.getElementAt(i))
              .findFirst()
              .ifPresent(myBuildsList::setSelectedIndex);
          }
        }
        BuildView view = myViewMap.get(buildInfo);
        if (view != null) {
          view.onEvent(buildId, event);
        }
        if (event instanceof FinishBuildEvent) {
          buildInfo.endTime = event.getEventTime();
          buildInfo.message = event.getMessage();
          buildInfo.result = ((FinishBuildEvent)event).getResult();
          myProgressWatcher.stopBuild(buildInfo);
          ((BuildContentManagerImpl)myBuildContentManager).finishBuildNotified(buildInfo, buildInfo.content);
          myViewManager.onBuildFinish(buildInfo);
        }
        else {
          buildInfo.statusMessage = event.getMessage();
        }

      }
    });

    if (myContent == null) {
      myPostponedRunnables.addAll(runOnEdt);
      if (isInitializeStarted.compareAndSet(false, true)) {
        EdtExecutorService.getInstance().execute(() -> {
          if (myDisposed) return;
          myBuildsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
          myBuildsList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
              AbstractViewManager.BuildInfo selectedBuild = myBuildsList.getSelectedValue();
              if (selectedBuild == null) return;
              setActiveView(myViewMap.get(selectedBuild));
            }
          });

          final JComponent consoleComponent = new MultipleBuildsPanel();
          consoleComponent.add(threeComponentsSplitter, BorderLayout.CENTER);
          myToolbarActions = new DefaultActionGroup();
          ActionToolbar tb = ActionManager.getInstance().createActionToolbar("BuildView", myToolbarActions, false);
          tb.setTargetComponent(consoleComponent);
          if (!ExperimentalUI.isNewUI()) {
            tb.getComponent().setBorder(
              JBUI.Borders.merge(tb.getComponent().getBorder(), JBUI.Borders.customLine(OnePixelDivider.BACKGROUND, 0, 0, 0, 1), true)
            );
          }
          consoleComponent.add(tb.getComponent(), BorderLayout.WEST);

          myContent = new ContentImpl(consoleComponent, myViewManager.getViewName(), true) {
            @Override
            public void dispose() {
              super.dispose();
              Disposer.dispose(MultipleBuildsView.this);
              myViewManager.onBuildsViewRemove(MultipleBuildsView.this);
            }
          };

          Icon contentIcon = myViewManager.getContentIcon();
          if (contentIcon != null) {
            myContent.setIcon(contentIcon);
            myContent.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);
          }
          myBuildContentManager.addContent(myContent);

          List<Runnable> postponedRunnables = new ArrayList<>(myPostponedRunnables);
          myPostponedRunnables.clear();
          for (Runnable postponedRunnable : postponedRunnables) {
            postponedRunnable.run();
          }
        });
      }
    }
    else {
      EdtExecutorService.getInstance().execute(() -> {
        if (myDisposed) return;
        for (Runnable runnable : runOnEdt) {
          runnable.run();
        }
      });
    }
  }

  private void setActiveView(@Nullable BuildView view) {
    if (myActiveView == view) {
      return;
    }
    if (myActiveView != null) {
      myActiveView.getComponent().setVisible(false);
    }
    myActiveView = view;
    if (view == null) {
      threeComponentsSplitter.setSecondComponent(null);
      myContent.setPreferredFocusableComponent(null);
    } else {
      JComponent viewComponent = view.getComponent();
      threeComponentsSplitter.setSecondComponent(viewComponent);
      myContent.setPreferredFocusedComponent(view::getPreferredFocusableComponent);
      myViewManager.configureToolbar(myToolbarActions, this, view);
      viewComponent.setVisible(true);
      viewComponent.repaint();
    }
  }

  private void clearOldBuilds(List<Runnable> runOnEdt, StartBuildEvent startBuildEvent) {
    long currentTime = System.currentTimeMillis();
    DefaultListModel<AbstractViewManager.BuildInfo> listModel = (DefaultListModel<AbstractViewManager.BuildInfo>)myBuildsList.getModel();
    boolean clearAll = !listModel.isEmpty();
    List<AbstractViewManager.BuildInfo> sameBuildsToClear = new SmartList<>();
    for (int i = 0; i < listModel.getSize(); i++) {
      AbstractViewManager.BuildInfo build = listModel.getElementAt(i);
      boolean sameBuildKind = build.getWorkingDir().equals(startBuildEvent.getBuildDescriptor().getWorkingDir());
      boolean differentBuildsFromSameBuildGroup = !build.getId().equals(startBuildEvent.getBuildDescriptor().getId()) &&
                                                  build.getGroupId() != null &&
                                                  build.getGroupId().equals(startBuildEvent.getBuildDescriptor().getGroupId());

      if (!build.isRunning() && sameBuildKind && !differentBuildsFromSameBuildGroup) {
        sameBuildsToClear.add(build);
      }
      boolean buildFinishedRecently = currentTime - build.endTime < TimeUnit.SECONDS.toMillis(1);
      if (build.isRunning() || !sameBuildKind && buildFinishedRecently || differentBuildsFromSameBuildGroup) {
        clearAll = false;
      }
    }
    if (clearAll) {
      myBuildsMap.clear();
      SmartList<BuildView> viewsToDispose = new SmartList<>(myViewMap.values());
      runOnEdt.add(() -> viewsToDispose.forEach(Disposer::dispose));

      myViewMap.clear();
      listModel.clear();
      runOnEdt.add(() -> {
        myBuildsList.setVisible(false);
        threeComponentsSplitter.setFirstComponent(null);
        setActiveView(null);
      });
      myToolbarActions.removeAll();
      isFirstErrorShown.set(false);
    }
    else {
      sameBuildsToClear.forEach(info -> {
        BuildView buildView = myViewMap.remove(info);
        if (buildView != null) {
          runOnEdt.add(() -> Disposer.dispose(buildView));
        }
        listModel.removeElement(info);
      });
    }
  }

  @ApiStatus.Internal
  public BuildView getBuildView(Object buildId) {
    AbstractViewManager.BuildInfo buildInfo = myBuildsMap.get(buildId);
    if (buildInfo == null) return null;
    return myViewMap.get(buildInfo);
  }

  private final class MultipleBuildsPanel extends JPanel implements OccurenceNavigator {
    MultipleBuildsPanel() {super(new BorderLayout());}

    @Override
    public boolean hasNextOccurence() {
      return getOccurenceNavigator(true) != null;
    }

    private @Nullable Pair<Integer, Supplier<OccurenceInfo>> getOccurenceNavigator(boolean next) {
      if (myBuildsList.getItemsCount() == 0) return null;
      int index = Math.max(myBuildsList.getSelectedIndex(), 0);

      Function<Integer, Pair<Integer, Supplier<OccurenceInfo>>> function = i -> {
        AbstractViewManager.BuildInfo buildInfo = myBuildsList.getModel().getElementAt(i);
        BuildView buildView = myViewMap.get(buildInfo);
        if (buildView == null) return null;
        if (i != index) {
          BuildTreeConsoleView eventView = buildView.getEventView();
          if (eventView == null) return null;
          eventView.getTree().clearSelection();
        }
        if (next) {
          if (buildView.hasNextOccurence()) return Pair.create(i, buildView::goNextOccurence);
        }
        else {
          if (buildView.hasPreviousOccurence()) {
            return Pair.create(i, buildView::goPreviousOccurence);
          }
          else if (i != index && buildView.hasNextOccurence()) {
            return Pair.create(i, buildView::goNextOccurence);
          }
        }
        return null;
      };
      if (next) {
        for (int i = index; i < myBuildsList.getItemsCount(); i++) {
          Pair<Integer, Supplier<OccurenceInfo>> buildViewPair = function.apply(i);
          if (buildViewPair != null) return buildViewPair;
        }
      }
      else {
        for (int i = index; i >= 0; i--) {
          Pair<Integer, Supplier<OccurenceInfo>> buildViewPair = function.apply(i);
          if (buildViewPair != null) return buildViewPair;
        }
      }
      return null;
    }

    @Override
    public boolean hasPreviousOccurence() {
      return getOccurenceNavigator(false) != null;
    }

    @Override
    public OccurenceInfo goNextOccurence() {
      Pair<Integer, Supplier<OccurenceInfo>> navigator = getOccurenceNavigator(true);
      if (navigator != null) {
        myBuildsList.setSelectedIndex(navigator.first);
        return navigator.second.get();
      }
      return null;
    }

    @Override
    public OccurenceInfo goPreviousOccurence() {
      Pair<Integer, Supplier<OccurenceInfo>> navigator = getOccurenceNavigator(false);
      if (navigator != null) {
        myBuildsList.setSelectedIndex(navigator.first);
        return navigator.second.get();
      }
      return null;
    }

    @Override
    public @NotNull String getNextOccurenceActionName() {
      return IdeBundle.message("action.next.problem");
    }

    @Override
    public @NotNull String getPreviousOccurenceActionName() {
      return IdeBundle.message("action.previous.problem");
    }

    @Override
    public void updateUI() {
      super.updateUI();
      updateBuildsListRowHeight();
    }
  }

  private final class ProgressWatcher implements Runnable {

    private final SingleEdtTaskScheduler refreshAlarm = SingleEdtTaskScheduler.createSingleEdtTaskScheduler();
    private final Set<AbstractViewManager.BuildInfo> builds = ConcurrentCollectionFactory.createConcurrentSet();

    private volatile boolean isStopped = false;

    @Override
    public void run() {
      refreshAlarm.cancel();
      JComponent firstComponent = threeComponentsSplitter.getFirstComponent();
      if (firstComponent != null) {
        firstComponent.revalidate();
        firstComponent.repaint();
      }
      if (!builds.isEmpty()) {
        refreshAlarm.request(300, this);
      }
    }

    void addBuild(AbstractViewManager.BuildInfo buildInfo) {
      if (isStopped) {
        LOG.warn("Attempt to add new build " + buildInfo + ";title=" + buildInfo.getTitle() + " to stopped watcher instance");
        return;
      }
      builds.add(buildInfo);
      if (builds.size() > 1) {
        refreshAlarm.cancelAndRequest(300, this);
      }
    }

    void stopBuild(AbstractViewManager.BuildInfo buildInfo) {
      builds.remove(buildInfo);
    }

    public void stopWatching() {
      isStopped = true;
      refreshAlarm.cancel();
    }
  }
}
