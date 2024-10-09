// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView;

import com.intellij.execution.services.*;
import com.intellij.ide.DeleteProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.platform.execution.serviceView.ServiceModel.ServiceViewItem;
import com.intellij.pom.Navigatable;
import com.intellij.ui.AutoScrollToSourceHandler;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

abstract class ServiceView extends JPanel implements UiDataProvider, Disposable {
  private final Project myProject;
  private final ServiceViewModel myModel;
  protected final ServiceViewUi myUi;
  private final ServiceViewOptions myViewOptions = new ServiceViewOptions() {
    @Override
    public boolean isGroupByContributor() {
      return ServiceView.this.isGroupByContributor();
    }
  };
  private AutoScrollToSourceHandler myAutoScrollToSourceHandler;

  protected ServiceView(LayoutManager layout, @NotNull Project project, @NotNull ServiceViewModel model, @NotNull ServiceViewUi ui) {
    super(layout);
    myProject = project;
    myModel = model;
    myUi = ui;
  }

  @Override
  public void dispose() {
  }

  Project getProject() {
    return myProject;
  }

  ServiceViewModel getModel() {
    return myModel;
  }

  ServiceViewUi getUi() {
    return myUi;
  }

  void saveState(@NotNull ServiceViewState state) {
    myModel.saveState(state);
  }

  @NotNull
  abstract List<ServiceViewItem> getSelectedItems();

  abstract Promise<Void> select(@NotNull Object service, @NotNull Class<?> contributorClass);

  abstract Promise<Void> expand(@NotNull Object service, @NotNull Class<?> contributorClass);

  abstract Promise<Void> extract(@NotNull Object service, @NotNull Class<?> contributorClass);

  abstract void onViewSelected();

  abstract void onViewUnselected();

  boolean isGroupByContributor() {
    return myModel.isGroupByContributor();
  }

  void setGroupByContributor(boolean value) {
    myModel.setGroupByContributor(value);
  }

  abstract List<Object> getChildrenSafe(@NotNull List<Object> valueSubPath, @NotNull Class<?> contributorClass);

  void setAutoScrollToSourceHandler(@NotNull AutoScrollToSourceHandler autoScrollToSourceHandler) {
    myAutoScrollToSourceHandler = autoScrollToSourceHandler;
  }

  void onViewSelected(@NotNull ServiceViewDescriptor descriptor) {
    descriptor.onNodeSelected(ContainerUtil.map(getSelectedItems(), ServiceViewItem::getValue));
    if (myAutoScrollToSourceHandler != null) {
      myAutoScrollToSourceHandler.onMouseClicked(this);
    }
  }

  abstract void jumpToServices();

  static ServiceView createView(@NotNull Project project, @NotNull ServiceViewModel viewModel, @NotNull ServiceViewState viewState) {
    setViewModelState(viewModel, viewState);
    return viewModel instanceof ServiceViewModel.SingeServiceModel ?
           createSingleView(project, viewModel) :
           createTreeView(project, viewModel, viewState);
  }

  private static ServiceView createTreeView(@NotNull Project project, @NotNull ServiceViewModel model, @NotNull ServiceViewState state) {
    return new ServiceTreeView(project, model, new ServiceViewTreeUi(state), state);
  }

  private static ServiceView createSingleView(@NotNull Project project, @NotNull ServiceViewModel model) {
    return new ServiceSingleView(project, model, new ServiceViewSingleUi());
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    List<ServiceViewItem> selection = getSelectedItems();
    ServiceViewItem onlyItem = ContainerUtil.getOnlyItem(selection);

    sink.set(PlatformCoreDataKeys.HELP_ID,
             ServiceViewManagerImpl.getToolWindowContextHelpId());
    sink.set(PlatformCoreDataKeys.SELECTED_ITEMS,
             ContainerUtil.map2Array(selection, ServiceViewItem::getValue));
    sink.set(PlatformCoreDataKeys.SELECTED_ITEM,
             onlyItem != null ? onlyItem.getValue() : null);
    sink.set(ServiceViewActionProvider.SERVICES_SELECTED_ITEMS, selection);

    ServiceViewContributor<?> contributor = ServiceViewDragHelper.getTheOnlyRootContributor(selection);
    sink.lazy(PlatformDataKeys.DELETE_ELEMENT_PROVIDER, () -> {
      DeleteProvider deleteProvider = getDeleteProvider(contributor);
      ServiceViewDeleteProvider viewDeleteProvider = new ServiceViewDeleteProvider(this);
      if (deleteProvider == null) {
        return viewDeleteProvider;
      }
      else {
        if (deleteProvider instanceof ServiceViewContributorDeleteProvider o) {
          o.setFallbackProvider(viewDeleteProvider);
        }
        return deleteProvider;
      }
    });

    sink.set(PlatformDataKeys.COPY_PROVIDER, new ServiceViewCopyProvider(this));
    sink.set(ServiceViewActionUtils.CONTRIBUTORS_KEY,
             getModel().getRoots().stream().map(item -> item.getRootContributor()).collect(Collectors.toSet()));
    sink.set(ServiceViewActionUtils.OPTIONS_KEY, myViewOptions);

    List<Navigatable> navigatables = ContainerUtil.mapNotNull(selection, item -> item.getViewDescriptor().getNavigatable());
    sink.set(CommonDataKeys.NAVIGATABLE_ARRAY,
             navigatables.toArray(Navigatable.EMPTY_NAVIGATABLE_ARRAY));

    ServiceViewDescriptor descriptor = onlyItem == null || onlyItem.isRemoved() ? null : onlyItem.getViewDescriptor();
    if (descriptor instanceof UiDataProvider dp) {
      sink.uiDataSnapshot(dp);
    }
    else {
      DataProvider dataProvider = descriptor == null ? null : descriptor.getDataProvider();
      if (dataProvider != null) {
        RecursionManager.doPreventingRecursion(
          this, false, () -> {
            DataSink.uiDataSnapshot(sink, dataProvider);
            return null;
          });
      }
    }
  }

  private @Nullable DeleteProvider getDeleteProvider(@Nullable ServiceViewContributor<?> contributor) {
    if (contributor == null) return null;
    ServiceViewDescriptor viewDescriptor = contributor.getViewDescriptor(myProject);
    if (viewDescriptor instanceof UiDataProvider dataProvider) {
      DataContext context = CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT, sink -> {
        sink.uiDataSnapshot(dataProvider);
      });
      DeleteProvider deleteProvider = PlatformDataKeys.DELETE_ELEMENT_PROVIDER.getData(context);
      if (deleteProvider != null) return deleteProvider;
    }

    DataProvider delegate = viewDescriptor.getDataProvider();
    return delegate == null ? null : PlatformDataKeys.DELETE_ELEMENT_PROVIDER.getData(delegate);
  }

  private static void setViewModelState(@NotNull ServiceViewModel viewModel, @NotNull ServiceViewState viewState) {
    viewModel.setGroupByContributor(viewState.groupByContributor);
  }
}