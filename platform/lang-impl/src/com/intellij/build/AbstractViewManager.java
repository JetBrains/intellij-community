// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build;

import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.StartBuildEvent;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.DisposableWrapperList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provides base implementation of the {@link ViewManager}
 *
 * @author Vladislav.Soroka
 */
public abstract class AbstractViewManager implements ViewManager, BuildProgressListener, BuildProgressObservable, Disposable {
  private static final Logger LOG = Logger.getInstance(AbstractViewManager.class);

  protected final Project myProject;
  protected final BuildContentManager myBuildContentManager;
  private final SynchronizedClearableLazy<AbstractMultipleBuildsView> myBuildsViewValue;
  private final Set<AbstractMultipleBuildsView> myPinnedViews;
  private final AtomicBoolean isDisposed = new AtomicBoolean(false);
  private final DisposableWrapperList<BuildProgressListener> myListeners = new DisposableWrapperList<>();

  public AbstractViewManager(Project project) {
    myProject = project;
    myBuildContentManager = BuildContentManager.getInstance(project);
    myBuildsViewValue = new SynchronizedClearableLazy<>(() -> {
      Ref<AbstractMultipleBuildsView> ref = new Ref<>();
      ApplicationManager.getApplication().invokeAndWait(() -> {
        ref.set(new MultipleBuildsView(myProject, myBuildContentManager, this));
      });
      AbstractMultipleBuildsView buildsView = ref.get();
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

    AbstractMultipleBuildsView buildsView;
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

  private @Nullable AbstractMultipleBuildsView getMultipleBuildsView(@NotNull Object buildId) {
    if (myProject.isDisposed()) return null;
    AbstractMultipleBuildsView buildsView = myBuildsViewValue.getValue();
    if (!buildsView.shouldConsume(buildId)) {
      buildsView = ContainerUtil.find(myPinnedViews, pinnedView -> pinnedView.shouldConsume(buildId));
    }
    return buildsView;
  }

  @ApiStatus.Internal
  public @Nullable BuildView getBuildView(@NotNull Object buildId) {
    AbstractMultipleBuildsView buildsView = getMultipleBuildsView(buildId);
    if (buildsView == null) return null;

    return buildsView.getBuildView(buildId);
  }

  protected @Nullable Icon getContentIcon() {
    return null;
  }

  protected void onBuildStart(BuildDescriptor buildDescriptor) {
  }

  protected void onBuildFinish(BuildDescriptor buildDescriptor) {
  }

  @Override
  public void dispose() {
    isDisposed.set(true);
    myPinnedViews.clear();
    myBuildsViewValue.drop();
  }

  void onBuildsViewRemove(@NotNull AbstractMultipleBuildsView buildsView) {
    if (isDisposed.get()) return;

    if (myBuildsViewValue.getValue() == buildsView) {
      myBuildsViewValue.drop();
    }
    else {
      myPinnedViews.remove(buildsView);
    }
  }

  private void configurePinnedContent() {
    AbstractMultipleBuildsView buildsView = myBuildsViewValue.getValue();
    if (buildsView.isPinned()) {
      buildsView.lockContent();
      myPinnedViews.add(buildsView);
      myBuildsViewValue.drop();
    }
  }
}
