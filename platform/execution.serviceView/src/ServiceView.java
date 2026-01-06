// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView;

import com.intellij.execution.services.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.platform.execution.serviceView.ServiceModel.ServiceViewItem;
import com.intellij.ui.AutoScrollToSourceHandler;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
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

  abstract @NotNull @Unmodifiable List<ServiceViewItem> getSelectedItems();

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

  abstract @Unmodifiable List<Object> getChildrenSafe(@NotNull List<Object> valueSubPath, @NotNull Class<?> contributorClass);

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

    sink.set(ServiceViewActionProvider.SERVICE_VIEW, this);
    sink.set(PlatformCoreDataKeys.HELP_ID,
             ServiceViewManagerImpl.getToolWindowContextHelpId());
    sink.set(PlatformCoreDataKeys.SELECTED_ITEM,
             onlyItem != null ? onlyItem.getValue() : null);
    sink.set(ServiceViewActionProvider.SERVICES_SELECTED_ITEMS, selection);

    //ServiceViewContributor<?> contributor = ServiceViewDragHelper.getTheOnlyRootContributor(selection);
    //ServiceViewDescriptor contributorDescriptor = contributor != null ? contributor.getViewDescriptor(myProject) : null;
    //if (contributorDescriptor instanceof UiDataProvider uiDataProvider) {
    //  sink.uiDataSnapshot(uiDataProvider);
    //}
    //else {
    //  DataSink.uiDataSnapshot(sink, contributorDescriptor != null ? contributorDescriptor.getDataProvider() : null);
    //}
    sink.set(ServiceViewActionUtils.CONTRIBUTORS_KEY,
             getModel().getRoots().stream().map(item -> item.getRootContributor()).collect(Collectors.toSet()));
    sink.set(ServiceViewActionUtils.OPTIONS_KEY, myViewOptions);

    List<ServiceViewDescriptorId> selectedDescriptorIds = ContainerUtil.map(selection,
                                                                            item -> ServiceViewDescriptorIdKt.toId(item, myProject));
    sink.set(ServiceViewActionProvider.SERVICES_SELECTED_DESCRIPTOR_IDS, selectedDescriptorIds);

    //ServiceViewDescriptor descriptor = onlyItem == null || onlyItem.isRemoved() ? null : onlyItem.getViewDescriptor();
    //if (descriptor instanceof UiDataProvider uiDataProvider) {
    //  sink.uiDataSnapshot(uiDataProvider);
    //}
    //else {
    //  DataSink.uiDataSnapshot(sink, descriptor != null ? descriptor.getDataProvider() : null);
    //}
  }

  private static void setViewModelState(@NotNull ServiceViewModel viewModel, @NotNull ServiceViewState viewState) {
    viewModel.setGroupByContributor(viewState.groupByContributor);
  }
}