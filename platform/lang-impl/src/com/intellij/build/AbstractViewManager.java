// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build;

import com.intellij.build.events.*;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.lang.LangBundle;
import com.intellij.notification.Notification;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Toggleable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.SystemNotifications;
import com.intellij.ui.UIBundle;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.DisposableWrapperList;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.build.ExecutionNode.getEventResultIcon;

/**
 * Provides base implementation of the {@link ViewManager}
 *
 * @author Vladislav.Soroka
 */
public abstract class AbstractViewManager implements ViewManager, BuildProgressListener, BuildProgressObservable, Disposable {
  private static final Logger LOG = Logger.getInstance(AbstractViewManager.class);
  private static final Key<Boolean> PINNED_EXTRACTED_CONTENT = new Key<>("PINNED_EXTRACTED_CONTENT");

  protected final Project myProject;
  protected final BuildContentManager myBuildContentManager;
  private final SynchronizedClearableLazy<MultipleBuildsView> myBuildsViewValue;
  private final Set<MultipleBuildsView> myPinnedViews;
  private final AtomicBoolean isDisposed = new AtomicBoolean(false);
  private final DisposableWrapperList<BuildProgressListener> myListeners = new DisposableWrapperList<>();

  public AbstractViewManager(Project project) {
    myProject = project;
    myBuildContentManager = BuildContentManager.getInstance(project);
    myBuildsViewValue = new SynchronizedClearableLazy<>(() -> {
      Ref<MultipleBuildsView> ref = new Ref<>();
      ApplicationManager.getApplication().invokeAndWait(() -> {
        ref.set(new MultipleBuildsView(myProject, myBuildContentManager, this));
      });
      MultipleBuildsView buildsView = ref.get();
      Disposer.register(this, buildsView);
      return buildsView;
    });
    myPinnedViews = ConcurrentCollectionFactory.createConcurrentSet();
    @Nullable BuildViewProblemsService buildViewProblemsService = project.getService(BuildViewProblemsService.class);
    if (buildViewProblemsService != null) {
      buildViewProblemsService.listenToBuildView(this);
    }
  }

  @Override
  public boolean isConsoleEnabledByDefault() {
    return false;
  }

  @Override
  public boolean isBuildContentView() {
    return true;
  }

  @Override
  @ApiStatus.Experimental
  public void addListener(@NotNull BuildProgressListener listener, @NotNull Disposable disposable) {
    myListeners.add(listener, disposable);
  }

  protected abstract @NotNull @NlsContexts.TabTitle String getViewName();

  protected Map<BuildDescriptor, BuildView> getBuildsMap() {
    return myBuildsViewValue.getValue().getBuildsMap();
  }

  @Override
  public void onEvent(@NotNull Object buildId, @NotNull BuildEvent event) {
    if (isDisposed.get()) return;

    MultipleBuildsView buildsView;
    if (event instanceof StartBuildEvent) {
      configurePinnedContent();
      buildsView = myBuildsViewValue.getValue();
    }
    else {
      buildsView = getMultipleBuildsView(buildId);
    }
    if (buildsView != null) {
      buildsView.onEvent(buildId, event);
    }

    for (BuildProgressListener listener : myListeners) {
      try {
        listener.onEvent(buildId, event);
      } catch (Exception e) {
        LOG.warn(e);
      }
    }
  }

  private @Nullable MultipleBuildsView getMultipleBuildsView(@NotNull Object buildId) {
    if (myProject.isDisposed()) return null;
    MultipleBuildsView buildsView = myBuildsViewValue.getValue();
    if (!buildsView.shouldConsume(buildId)) {
      buildsView = ContainerUtil.find(myPinnedViews, pinnedView -> pinnedView.shouldConsume(buildId));
    }
    return buildsView;
  }

  @ApiStatus.Internal
  public @Nullable BuildView getBuildView(@NotNull Object buildId) {
    MultipleBuildsView buildsView = getMultipleBuildsView(buildId);
    if (buildsView == null) return null;

    return buildsView.getBuildView(buildId);
  }

  void configureToolbar(@NotNull DefaultActionGroup toolbarActions,
                        @NotNull MultipleBuildsView buildsView,
                        @NotNull BuildView view) {
    toolbarActions.removeAll();
    toolbarActions.addAll(view.createConsoleActions());
    toolbarActions.add(new PinBuildViewAction(buildsView));
    toolbarActions.add(BuildTreeFilters.createFilteringActionsGroup(new WeakFilterableSupplier<>(view)));
  }

  protected @Nullable Icon getContentIcon() {
    return null;
  }

  protected void onBuildStart(BuildDescriptor buildDescriptor) {
  }

  protected void onBuildFinish(BuildDescriptor buildDescriptor) {
    BuildInfo buildInfo = (BuildInfo)buildDescriptor;
    if (buildInfo.result instanceof FailureResult) {
      boolean activate = buildInfo.isActivateToolWindowWhenFailed();
      myBuildContentManager.setSelectedContent(buildInfo.content, false, false, activate, null);
      List<? extends Failure>
        failures = ((FailureResult)buildInfo.result).getFailures();
      if (failures.isEmpty()) return;
      Failure failure = failures.get(0);
      Notification notification = failure.getNotification();
      if (notification != null) {
        String title = notification.getTitle();
        String content = notification.getContent();
        SystemNotifications.getInstance().notify(UIBundle.message("tool.window.name.build"), title, content);
      }
    }
  }

  @Override
  public void dispose() {
    isDisposed.set(true);
    myPinnedViews.clear();
    myBuildsViewValue.drop();
  }

  void onBuildsViewRemove(@NotNull MultipleBuildsView buildsView) {
    if (isDisposed.get()) return;

    if (myBuildsViewValue.getValue() == buildsView) {
      myBuildsViewValue.drop();
    }
    else {
      myPinnedViews.remove(buildsView);
    }
  }

  @ApiStatus.Internal
  static final class BuildInfo extends DefaultBuildDescriptor {
    @BuildEventsNls.Message String message;
    @BuildEventsNls.Message String statusMessage;
    long endTime = -1;
    EventResult result;
    Content content;

    BuildInfo(@NotNull BuildDescriptor descriptor) {
      super(descriptor);
    }

    public Icon getIcon() {
      return getEventResultIcon(result);
    }

    public boolean isRunning() {
      return endTime == -1;
    }
  }

  private void configurePinnedContent() {
    MultipleBuildsView buildsView = myBuildsViewValue.getValue();
    Content content = buildsView.getContent();
    if (content != null && content.isPinned()) {
      String tabName = getPinnedTabName(buildsView);
      UIUtil.invokeLaterIfNeeded(() -> {
        content.setPinnable(false);
        if (content.getIcon() == null) {
          content.setIcon(EmptyIcon.ICON_8);
        }
        content.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);
        ((BuildContentManagerImpl)myBuildContentManager).updateTabDisplayName(content, tabName);
      });
      myPinnedViews.add(buildsView);
      myBuildsViewValue.drop();
      content.putUserData(PINNED_EXTRACTED_CONTENT, Boolean.TRUE);
    }
  }

  private @NlsContexts.TabTitle String getPinnedTabName(MultipleBuildsView buildsView) {
    Map<BuildDescriptor, BuildView> buildsMap = buildsView.getBuildsMap();

    BuildDescriptor buildInfo = buildsMap.keySet()
      .stream()
      .reduce((b1, b2) -> b1.getStartTime() <= b2.getStartTime() ? b1 : b2)
      .orElse(null);
    if (buildInfo != null) {
      @BuildEventsNls.Title String title = buildInfo.getTitle();
      @NlsContexts.TabTitle String viewName = getViewName().split(" ")[0];
      String tabName = viewName + ": " + StringUtil.trimStart(title, viewName);
      if (buildsMap.size() > 1) {
        return LangBundle.message("tab.title.more", tabName, buildsMap.size() - 1);
      }
      return tabName;
    }
    return getViewName();
  }

  private static final class PinBuildViewAction extends DumbAwareAction implements Toggleable {
    private final Content myContent;

    PinBuildViewAction(MultipleBuildsView buildsView) {
      myContent = buildsView.getContent();
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      boolean selected = !myContent.isPinned();
      if (selected) {
        myContent.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);
      }
      myContent.setPinned(selected);
      Toggleable.setSelected(e.getPresentation(), selected);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (!myContent.isValid()) return;
      Boolean isPinnedAndExtracted = myContent.getUserData(PINNED_EXTRACTED_CONTENT);
      if (isPinnedAndExtracted == Boolean.TRUE) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }

      ContentManager contentManager = myContent.getManager();
      boolean isActiveTab = contentManager != null && contentManager.getSelectedContent() == myContent;
      boolean selected = myContent.isPinned();

      e.getPresentation().setIcon(AllIcons.General.Pin_tab);
      Toggleable.setSelected(e.getPresentation(), selected);
      e.getPresentation().setText(selected ? IdeBundle.message("action.unpin.tab") : IdeBundle.message("action.pin.tab"));
      e.getPresentation().setEnabledAndVisible(true);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }
}
