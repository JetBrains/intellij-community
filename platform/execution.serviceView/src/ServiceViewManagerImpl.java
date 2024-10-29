// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.services.*;
import com.intellij.execution.services.ServiceEventListener.ServiceEvent;
import com.intellij.icons.AllIcons;
import com.intellij.ide.lightEdit.LightEditUtil;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.idea.AppMode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.AppUIExecutor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowContentUiType;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.platform.execution.serviceView.ServiceModel.ServiceViewItem;
import com.intellij.platform.execution.serviceView.ServiceModelFilter.ServiceViewFilter;
import com.intellij.platform.execution.serviceView.ServiceViewDragHelper.ServiceViewDragBean;
import com.intellij.platform.execution.serviceView.ServiceViewModel.*;
import com.intellij.toolWindow.InternalDecoratorImpl;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.AutoScrollToSourceHandler;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.content.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.SmartHashSet;
import kotlin.Unit;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.intellij.execution.services.ServiceViewContributor.CONTRIBUTOR_EP_NAME;

@State(name = "ServiceViewManager", storages = @Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE),
  getStateRequiresEdt = true, defaultStateAsResource = true)
public final class ServiceViewManagerImpl implements ServiceViewManager, PersistentStateComponent<ServiceViewManagerImpl.State> {
  private static final @NonNls String HELP_ID = "services.tool.window";

  private final Project myProject;
  private State myState = new State();

  private final ServiceModel myModel;
  private final ServiceModelFilter myModelFilter;
  private final Map<String, Collection<ServiceViewContributor<?>>> myGroups = new ConcurrentHashMap<>();
  private final Set<ServiceViewContributor<?>> myNotInitializedContributors = ConcurrentHashMap.newKeySet();
  private final List<ServiceViewContentHolder> myContentHolders = new SmartList<>();
  private boolean myActivationActionsRegistered;
  private AutoScrollToSourceHandler myAutoScrollToSourceHandler;

  private final Set<String> myActiveToolWindowIds = new SmartHashSet<>();
  private boolean myRegisteringToolWindowAvailable;

  public ServiceViewManagerImpl(@NotNull Project project) {
    myProject = project;
    LightEditUtil.forbidServiceInLightEditMode(project, getClass());
    myModel = new ServiceModel(myProject);
    Disposer.register(myProject, myModel);
    myModelFilter = new ServiceModelFilter();
    myProject.getMessageBus().connect(myModel).subscribe(ServiceEventListener.TOPIC, e -> {
      myModel.handle(e).onSuccess(o -> eventHandled(e));
    });
    CONTRIBUTOR_EP_NAME.addExtensionPointListener(new ServiceViewExtensionPointListener(), myProject);
  }

  private void eventHandled(@NotNull ServiceEvent e) {
    String toolWindowId = getToolWindowId(e.contributorClass);
    if (toolWindowId == null) {
      return;
    }

    ServiceViewItem eventRoot = ContainerUtil.find(myModel.getRoots(), root -> e.contributorClass.isInstance(root.getRootContributor()));
    ServiceViewContributor<?> notInitializedContributor = findNotInitializedContributor(e.contributorClass, eventRoot);
    boolean initialized = notInitializedContributor == null;
    if (!initialized &&
        (e.type == ServiceEventListener.EventType.RESET || e.type == ServiceEventListener.EventType.UNLOAD_SYNC_RESET)) {
      myNotInitializedContributors.remove(notInitializedContributor);
    }
    if (eventRoot != null) {
      boolean show = !(eventRoot.getViewDescriptor() instanceof ServiceViewNonActivatingDescriptor) && initialized;
      updateToolWindow(toolWindowId, true, show);
    }
    else {
      Set<? extends ServiceViewContributor<?>> activeContributors = getActiveContributors();
      Collection<ServiceViewContributor<?>> toolWindowContributors = myGroups.get(toolWindowId);
      updateToolWindow(toolWindowId, ContainerUtil.intersects(activeContributors, toolWindowContributors), false);
    }
  }

  private @Nullable ServiceViewContributor<?> findNotInitializedContributor(Class<?> contributorClass, ServiceViewItem eventRoot) {
    if (eventRoot != null) {
      return myNotInitializedContributors.contains(eventRoot.getRootContributor()) ? eventRoot.getRootContributor() : null;
    }
    for (ServiceViewContributor<?> contributor : myNotInitializedContributors) {
      if (contributorClass.isInstance(contributor)) {
        return contributor;
      }
    }
    return null;
  }

  private Set<? extends ServiceViewContributor<?>> getActiveContributors() {
    return ContainerUtil.map2Set(myModel.getRoots(), ServiceViewItem::getRootContributor);
  }

  private @Nullable ServiceViewContentHolder getContentHolder(@NotNull Class<?> contributorClass) {
    for (ServiceViewContentHolder holder : myContentHolders) {
      for (ServiceViewContributor<?> rootContributor : holder.rootContributors) {
        if (contributorClass.isInstance(rootContributor)) {
          return holder;
        }
      }
    }
    return null;
  }

  private void registerToolWindows(Collection<String> toolWindowIds) {
    Set<? extends ServiceViewContributor<?>> activeContributors = getActiveContributors();
    for (Map.Entry<String, Collection<ServiceViewContributor<?>>> entry : myGroups.entrySet()) {
      if (!toolWindowIds.contains(entry.getKey())) continue;

      Collection<ServiceViewContributor<?>> contributors = entry.getValue();
      ServiceViewContributor<?> contributor = ContainerUtil.getFirstItem(contributors, null);
      if (contributor == null) continue;

      ServiceViewToolWindowDescriptor descriptor = ToolWindowId.SERVICES.equals(entry.getKey()) ?
                                                   getServicesToolWindowDescriptor() :
                                                   getContributorToolWindowDescriptor(contributor);
      registerToolWindow(descriptor, !Collections.disjoint(activeContributors, contributors));
    }
  }

  private void registerToolWindow(@NotNull ServiceViewToolWindowDescriptor descriptor, boolean active) {
    if (myProject.isDefault()) {
      return;
    }

    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    toolWindowManager.invokeLater(() -> {
      String toolWindowId = descriptor.getToolWindowId();
      if (toolWindowManager.getToolWindow(toolWindowId) != null) return;

      if (!myActivationActionsRegistered && ToolWindowId.SERVICES.equals(toolWindowId)) {
        myActivationActionsRegistered = true;
        Collection<ServiceViewContributor<?>> contributors = myGroups.get(ToolWindowId.SERVICES);
        if (contributors != null) {
          registerActivateByContributorActions(myProject, contributors);
        }
      }

      myRegisteringToolWindowAvailable = active;
      try {
        ToolWindow toolWindow = toolWindowManager.registerToolWindow(toolWindowId, builder -> {
          builder.contentFactory = new ServiceViewToolWindowFactory();
          builder.icon = descriptor.getToolWindowIcon();
          builder.hideOnEmptyContent = false;
          builder.stripeTitle = () -> {
            return descriptor.getStripeTitle();
          };
          return Unit.INSTANCE;
        });
        if (active) {
          myActiveToolWindowIds.add(toolWindowId);
        }
        restoreBrokenToolWindowIfNeeded(toolWindow);
      }
      finally {
        myRegisteringToolWindowAvailable = false;
      }
    });
  }

  /*
   * Temporary fix for restoring Services Tool Window (IDEA-288804)
   */
  @Deprecated(forRemoval = true)
  private static void restoreBrokenToolWindowIfNeeded(@NotNull ToolWindow toolWindow) {
    if (!toolWindow.isShowStripeButton() && toolWindow.isVisible()) {
      toolWindow.hide();
      toolWindow.show();
    }
  }

  private void updateToolWindow(@NotNull String toolWindowId, boolean active, boolean show) {
    if (myProject.isDisposed() || myProject.isDefault()) {
      return;
    }

    ApplicationManager.getApplication().invokeLater(() -> {
      ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(toolWindowId);
      if (toolWindow == null) {
        return;
      }

      if (active) {
        boolean doShow = show && !myActiveToolWindowIds.contains(toolWindowId);
        myActiveToolWindowIds.add(toolWindowId);
        if (doShow) {
          toolWindow.show();
        }
      }
      else if (myActiveToolWindowIds.remove(toolWindowId)) {
        // Hide tool window only if model roots became empty and there were some services shown before update.
        toolWindow.hide();
      }
    }, ModalityState.nonModal(), myProject.getDisposed());
  }

  boolean shouldBeAvailable() {
    return myRegisteringToolWindowAvailable;
  }

  void createToolWindowContent(@NotNull ToolWindow toolWindow) {
    String toolWindowId = toolWindow.getId();
    Collection<ServiceViewContributor<?>> contributors = myGroups.get(toolWindowId);
    if (contributors == null) return;

    if (myAutoScrollToSourceHandler == null) {
      myAutoScrollToSourceHandler = ServiceViewSourceScrollHelper.createAutoScrollToSourceHandler(myProject);
    }
    ToolWindowEx toolWindowEx = (ToolWindowEx)toolWindow;
    ServiceViewSourceScrollHelper.installAutoScrollSupport(myProject, toolWindowEx, myAutoScrollToSourceHandler);

    Pair<ServiceViewState, List<ServiceViewState>> states = getServiceViewStates(toolWindowId);
    AllServicesModel mainModel = new AllServicesModel(myModel, myModelFilter, contributors);
    ServiceView mainView = ServiceView.createView(myProject, mainModel, prepareViewState(states.first));
    mainView.setAutoScrollToSourceHandler(myAutoScrollToSourceHandler);

    ContentManager contentManager = toolWindow.getContentManager();
    Wrapper toolWindowHeaderSideComponent = setToolWindowHeaderSideComponent(toolWindowEx, contentManager);
    ServiceViewContentHolder holder = new ServiceViewContentHolder(
      mainView, contentManager, contributors, toolWindowId, toolWindowHeaderSideComponent);
    myContentHolders.add(holder);
    contentManager.addContentManagerListener(new ServiceViewContentMangerListener(myModelFilter, myAutoScrollToSourceHandler, holder));
    myProject.getMessageBus().connect(contentManager).subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
      @Override
      public void stateChanged(@NotNull ToolWindowManager toolWindowManager,
                               @NotNull ToolWindow toolWindow,
                               @NotNull ToolWindowManagerEventType changeType) {
        if (!toolWindowId.equals(toolWindow.getId())) return;

        if (changeType == ToolWindowManagerEventType.SetSideToolAndAnchor) {
          boolean verticalSplit = !toolWindow.getAnchor().isHorizontal();
          for (Content content : holder.contentManager.getContents()) {
            ServiceView serviceView = getServiceView(content);
            if (serviceView != null) {
              serviceView.getUi().setSplitOrientation(verticalSplit);
            }
          }
          return;
        }
        if (changeType == ToolWindowManagerEventType.SetContentUiType) {
          updateNavBar(holder);
        }
      }
    });
    addMainContent(toolWindow.getContentManager(), mainView);
    loadViews(contentManager, mainView, contributors, states.second);
    ServiceViewDragHelper.installDnDSupport(myProject, toolWindowEx.getDecorator(), contentManager);
  }

  private static Wrapper setToolWindowHeaderSideComponent(ToolWindowEx toolWindowEx, ContentManager contentManager) {
    if (!Registry.is("ide.services.tool.window.header.nav.bar", true) ||
        AppMode.isRemoteDevHost()) {
      return null;
    }
    InternalDecoratorImpl decorator = ObjectUtils.tryCast(toolWindowEx.getDecorator(), InternalDecoratorImpl.class);
    if (decorator == null) return null;

    Wrapper wrapper = new Wrapper();
    decorator.getHeader().setSideComponent(UiDataProvider.wrapComponent(wrapper, sink -> {
      Content content = contentManager.getSelectedContent();
      ServiceView serviceView = content == null ? null : getServiceView(content);
      DataSink.uiDataSnapshot(sink, serviceView);
    }));
    return wrapper;
  }

  private static void updateNavBar(ServiceViewContentHolder holder) {
    Wrapper wrapper = holder.toolWindowHeaderSideComponent;
    if (wrapper == null) return;

    ToolWindow toolWindow = ToolWindowManager.getInstance(holder.mainView.getProject()).getToolWindow(holder.toolWindowId);
    if (toolWindow == null) return;

    Content content = holder.contentManager.getSelectedContent();
    ServiceView serviceView = content == null ? null : getServiceView(content);
    if (serviceView == null) {
      wrapper.setContent(null);
    }
    else {
      ToolWindowContentUiType type = toolWindow.getContentUiType();
      boolean isSideComponent = type == ToolWindowContentUiType.COMBO || holder.contentManager.getContentCount() == 1;
      if (isSideComponent) {
        JComponent navBar = serviceView.getUi().updateNavBar(isSideComponent);
        wrapper.setContent(navBar);
      }
      else {
        wrapper.setContent(null);
        serviceView.getUi().updateNavBar(isSideComponent);
      }
    }
  }

  private static void addMainContent(ContentManager contentManager, ServiceView mainView) {
    Content mainContent = ContentFactory.getInstance().createContent(mainView, null, false);
    mainContent.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);
    mainContent.setHelpId(getToolWindowContextHelpId());
    mainContent.setCloseable(false);

    Disposer.register(mainContent, mainView);
    Disposer.register(mainContent, mainView.getModel());

    contentManager.addContent(mainContent);
    mainView.getModel().addModelListener(() -> {
      boolean isEmpty = mainView.getModel().getRoots().isEmpty();
      AppUIExecutor.onUiThread().expireWith(contentManager).submit(() -> {
        if (isEmpty) {
          if (contentManager.getIndexOfContent(mainContent) < 0) {
            if (contentManager.getContentCount() == 0) {
              contentManager.addContent(mainContent, 0);
            }
          }
          else if (contentManager.getContentCount() > 1) {
            contentManager.removeContent(mainContent, false);
          }
        }
        else {
          if (contentManager.getIndexOfContent(mainContent) < 0) {
            contentManager.addContent(mainContent, 0);
          }
        }
      });
    });
  }

  private void loadViews(ContentManager contentManager,
                         ServiceView mainView,
                         Collection<? extends ServiceViewContributor<?>> contributors,
                         List<ServiceViewState> viewStates) {
    myModel.getInvoker().invokeLater(() -> {
      Map<String, ServiceViewContributor<?>> contributorsMap = FactoryMap.create(className -> {
        for (ServiceViewContributor<?> contributor : contributors) {
          if (className.equals(contributor.getClass().getName())) {
            return contributor;
          }
        }
        return null;
      });
      List<ServiceViewFilter> filters = new ArrayList<>();

      List<Pair<ServiceViewModel, ServiceViewState>> loadedModels = new ArrayList<>();
      ServiceViewModel toSelect = null;

      for (ServiceViewState viewState : viewStates) {
        ServiceViewFilter parentFilter = mainView.getModel().getFilter();
        if (viewState.parentView >= 0 && viewState.parentView < filters.size()) {
          parentFilter = filters.get(viewState.parentView);
        }
        ServiceViewFilter filter = parentFilter;
        ServiceViewModel viewModel = ServiceViewModel.loadModel(viewState, myModel, myModelFilter, parentFilter, contributorsMap);
        if (viewModel != null) {
          loadedModels.add(Pair.create(viewModel, viewState));
          if (viewState.isSelected) {
            toSelect = viewModel;
          }
          filter = viewModel.getFilter();
        }
        filters.add(filter);
      }

      if (!loadedModels.isEmpty()) {
        ServiceViewModel modelToSelect = toSelect;
        AppUIExecutor.onUiThread().expireWith(contentManager).submit(() -> {
          for (Pair<ServiceViewModel, ServiceViewState> pair : loadedModels) {
            extract(contentManager, pair.first, pair.second, false);
          }
          selectContentByModel(contentManager, modelToSelect);
        });
      }
    });
  }

  @Override
  public @NotNull Promise<Void> select(@NotNull Object service, @NotNull Class<?> contributorClass, boolean activate, boolean focus) {
    return trackingSelect(service, contributorClass, activate, focus).then(result -> null);
  }

  public @NotNull Promise<Boolean> trackingSelect(@NotNull Object service, @NotNull Class<?> contributorClass,
                                                  boolean activate, boolean focus) {
    if (!myState.selectActiveService) {
      if (activate) {
        String toolWindowId = getToolWindowId(contributorClass);
        if (toolWindowId == null) {
          return Promises.rejectedPromise("Contributor group not found");
        }
        ToolWindowManager.getInstance(myProject).invokeLater(() -> {
          ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(toolWindowId);
          if (toolWindow != null) {
            toolWindow.activate(null, focus, focus);
          }
        });
      }
      return expand(service, contributorClass).then(o -> false);
    }
    return doSelect(service, contributorClass, activate, focus).then(o -> true);
  }

  private @NotNull Promise<Void> doSelect(@NotNull Object service, @NotNull Class<?> contributorClass, boolean activate, boolean focus) {
    AsyncPromise<Void> result = new AsyncPromise<>();
    // Ensure model is updated, then iterate over service views on EDT to find view with service and select it.
    myModel.getInvoker().invoke(() -> AppUIUtil.invokeLaterIfProjectAlive(myProject, () -> {
      String toolWindowId = getToolWindowId(contributorClass);
      if (toolWindowId == null) {
        result.setError("Contributor group not found");
        return;
      }
      Runnable runnable = () -> promiseFindView(contributorClass, result,
                                                serviceView -> serviceView.select(service, contributorClass),
                                                content -> selectContent(content, focus, myProject));
      ToolWindow toolWindow = activate ? ToolWindowManager.getInstance(myProject).getToolWindow(toolWindowId) : null;
      if (toolWindow != null) {
        toolWindow.activate(runnable, focus, focus);
      }
      else {
        runnable.run();
      }
    }));
    return result;
  }

  private void promiseFindView(Class<?> contributorClass, AsyncPromise<Void> result,
                               Function<? super ServiceView, ? extends Promise<?>> action, Consumer<? super Content> onSuccess) {
    ServiceViewContentHolder holder = getContentHolder(contributorClass);
    if (holder == null) {
      result.setError("Content manager not initialized");
      return;
    }
    List<Content> contents = new SmartList<>(holder.contentManager.getContents());
    if (contents.isEmpty()) {
      result.setError("Content not initialized");
      return;
    }
    Collections.reverse(contents);

    promiseFindView(contents.iterator(), result, action, onSuccess);
  }

  private static void promiseFindView(Iterator<? extends Content> iterator, AsyncPromise<Void> result,
                                      Function<? super ServiceView, ? extends Promise<?>> action, Consumer<? super Content> onSuccess) {
    Content content = iterator.next();
    ServiceView serviceView = getServiceView(content);
    if (serviceView == null || content.getManager() == null) {
      if (iterator.hasNext()) {
        promiseFindView(iterator, result, action, onSuccess);
      }
      else {
        result.setError("Not services content");
      }
      return;
    }
    action.apply(serviceView)
      .onSuccess(v -> {
        if (onSuccess != null) {
          onSuccess.accept(content);
        }
        result.setResult(null);
      })
      .onError(e -> {
        if (iterator.hasNext()) {
          AppUIExecutor.onUiThread().expireWith(serviceView.getProject()).submit(() -> {
            promiseFindView(iterator, result, action, onSuccess);
          });
        }
        else {
          result.setError(e);
        }
      });
  }

  private static void selectContent(Content content, boolean focus, Project project) {
    AppUIExecutor.onUiThread().expireWith(content).submit(() -> {
      ContentManager contentManager = content.getManager();
      if (contentManager == null) return;

      if (contentManager.getSelectedContent() != content && contentManager.getIndexOfContent(content) >= 0) {
        contentManager.setSelectedContent(content, focus);
      }
    });
  }

  @Override
  public @NotNull Promise<Void> expand(@NotNull Object service, @NotNull Class<?> contributorClass) {
    AsyncPromise<Void> result = new AsyncPromise<>();
    // Ensure model is updated, then iterate over service views on EDT to find view with service and select it.
    myModel.getInvoker().invoke(() -> AppUIUtil.invokeLaterIfProjectAlive(myProject, () ->
      promiseFindView(contributorClass, result,
                      serviceView -> serviceView.expand(service, contributorClass),
                      null)));
    return result;
  }

  @Override
  public @NotNull Promise<Void> extract(@NotNull Object service, @NotNull Class<?> contributorClass) {
    AsyncPromise<Void> result = new AsyncPromise<>();
    myModel.getInvoker().invoke(() -> AppUIUtil.invokeLaterIfProjectAlive(myProject, () ->
      promiseFindView(contributorClass, result,
                      serviceView -> serviceView.extract(service, contributorClass),
                      null)));
    return result;
  }

  @NotNull
  Promise<Void> select(@NotNull VirtualFile virtualFile) {
    List<ServiceViewItem> selectedItems = new SmartList<>();
    for (ServiceViewContentHolder contentHolder : myContentHolders) {
      Content content = contentHolder.contentManager.getSelectedContent();
      if (content == null) continue;

      ServiceView serviceView = getServiceView(content);
      if (serviceView == null) continue;

      List<ServiceViewItem> items = serviceView.getSelectedItems();
      ContainerUtil.addIfNotNull(selectedItems, ContainerUtil.getOnlyItem(items));
    }

    AsyncPromise<Void> result = new AsyncPromise<>();
    myModel.getInvoker().invoke(() -> {
      Condition<? super ServiceViewItem> fileCondition = item -> {
        ServiceViewDescriptor descriptor = item.getViewDescriptor();
        return descriptor instanceof ServiceViewLocatableDescriptor &&
               virtualFile.equals(((ServiceViewLocatableDescriptor)descriptor).getVirtualFile());
      };

      // Multiple services may target to one virtual file.
      // Do nothing if service, targeting to the given virtual file, is selected,
      // otherwise it may lead to jumping selection,
      // if editor have just been selected due to some service selection.
      if (ContainerUtil.find(selectedItems, fileCondition) != null) {
        result.setResult(null);
        return;
      }

      ServiceViewItem fileItem = myModel.findItem(
        item -> !(item instanceof ServiceModel.ServiceNode) ||
                item.getViewDescriptor() instanceof ServiceViewLocatableDescriptor,
        fileCondition
      );
      if (fileItem != null) {
        Promise<Void> promise = doSelect(fileItem.getValue(), fileItem.getRootContributor().getClass(), false, false);
        promise.processed(result);
      }
    });
    return result;
  }

  void extract(@NotNull ServiceViewDragBean dragBean) {
    List<ServiceViewItem> items = dragBean.getItems();
    if (items.isEmpty()) return;

    ServiceView serviceView = dragBean.getServiceView();
    ServiceViewContentHolder holder = getContentHolder(serviceView);
    if (holder == null) return;

    ServiceViewFilter parentFilter = serviceView.getModel().getFilter();
    ServiceViewModel viewModel = ServiceViewModel.createModel(items, dragBean.getContributor(), myModel, myModelFilter, parentFilter);
    ServiceViewState state = new ServiceViewState();
    serviceView.saveState(state);
    extract(holder.contentManager, viewModel, state, true);
  }

  private void extract(ContentManager contentManager, ServiceViewModel viewModel, ServiceViewState viewState, boolean select) {
    ServiceView serviceView = ServiceView.createView(myProject, viewModel, prepareViewState(viewState));
    ItemPresentation presentation = getContentPresentation(myProject, viewModel, viewState);
    if (presentation == null) return;

    Content content = addServiceContent(contentManager, serviceView, presentation, select);
    if (viewModel instanceof GroupModel) {
      extractGroup((GroupModel)viewModel, content);
    }
    else if (viewModel instanceof SingeServiceModel) {
      extractService((SingeServiceModel)viewModel, content);
    }
    else if (viewModel instanceof ServiceListModel) {
      extractList((ServiceListModel)viewModel, content);
    }
  }

  private static void extractGroup(GroupModel viewModel, Content content) {
    viewModel.addModelListener(() -> updateContentTab(viewModel.getGroup(), content));
    updateContentTab(viewModel.getGroup(), content);
  }

  private void extractService(SingeServiceModel viewModel, Content content) {
    ContentManager contentManager = content.getManager();
    viewModel.addModelListener(() -> {
      ServiceViewItem item = viewModel.getService();
      if (item != null && !viewModel.getChildren(item).isEmpty() && contentManager != null) {
        AppUIExecutor.onUiThread().expireWith(contentManager).submit(() -> {
          ServiceViewItem viewItem = viewModel.getService();
          if (viewItem == null) return;

          int index = contentManager.getIndexOfContent(content);
          if (index < 0) return;

          contentManager.removeContent(content, true);
          ServiceListModel listModel = new ServiceListModel(myModel, myModelFilter, new SmartList<>(viewItem),
                                                            viewModel.getFilter().getParent());
          ServiceView listView = ServiceView.createView(myProject, listModel, prepareViewState(new ServiceViewState()));
          Content listContent =
            addServiceContent(contentManager, listView, viewItem.getViewDescriptor().getContentPresentation(), true, index);
          extractList(listModel, listContent);
        });
      }
      else {
        updateContentTab(item, content);
      }
    });
    updateContentTab(viewModel.getService(), content);
  }

  private static void extractList(ServiceListModel viewModel, Content content) {
    viewModel.addModelListener(() -> updateContentTab(ContainerUtil.getOnlyItem(viewModel.getRoots()), content));
    updateContentTab(ContainerUtil.getOnlyItem(viewModel.getRoots()), content);
  }

  private static ItemPresentation getContentPresentation(Project project, ServiceViewModel viewModel, ServiceViewState viewState) {
    if (viewModel instanceof ContributorModel) {
      return ((ContributorModel)viewModel).getContributor().getViewDescriptor(project).getContentPresentation();
    }
    else if (viewModel instanceof GroupModel) {
      return ((GroupModel)viewModel).getGroup().getViewDescriptor().getContentPresentation();
    }
    else if (viewModel instanceof SingeServiceModel) {
      return ((SingeServiceModel)viewModel).getService().getViewDescriptor().getContentPresentation();
    }
    else if (viewModel instanceof ServiceListModel) {
      List<ServiceViewItem> items = ((ServiceListModel)viewModel).getItems();
      if (items.size() == 1) {
        return items.get(0).getViewDescriptor().getContentPresentation();
      }
      String name = viewState.id;
      if (StringUtil.isEmpty(name)) {
        name = Messages.showInputDialog(project,
                                        ExecutionBundle.message("service.view.group.label"),
                                        ExecutionBundle.message("service.view.group.title"),
                                        null, null, null);
        if (StringUtil.isEmpty(name)) return null;
      }
      return new PresentationData(name, null, AllIcons.Nodes.Folder, null);
    }
    return null;
  }

  private static Content addServiceContent(ContentManager contentManager, ServiceView serviceView, ItemPresentation presentation,
                                           boolean select) {
    return addServiceContent(contentManager, serviceView, presentation, select, -1);
  }

  private static Content addServiceContent(ContentManager contentManager, ServiceView serviceView, ItemPresentation presentation,
                                           boolean select, int index) {
    Content content =
      ContentFactory.getInstance().createContent(serviceView, ServiceViewDragHelper.getDisplayName(presentation), false);
    content.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);
    content.setHelpId(getToolWindowContextHelpId());
    content.setCloseable(true);
    content.setIcon(presentation.getIcon(false));

    Disposer.register(content, serviceView);
    Disposer.register(content, serviceView.getModel());

    contentManager.addContent(content, index);
    if (select) {
      contentManager.setSelectedContent(content);
    }
    return content;
  }

  private static void updateContentTab(ServiceViewItem item, Content content) {
    if (item != null) {
      WeakReference<ServiceViewItem> itemRef = new WeakReference<>(item);
      AppUIExecutor.onUiThread().expireWith(content).submit(() -> {
        ServiceViewItem viewItem = itemRef.get();
        if (viewItem == null) return;

        ItemPresentation itemPresentation = viewItem.getViewDescriptor().getContentPresentation();
        content.setDisplayName(ServiceViewDragHelper.getDisplayName(itemPresentation));
        content.setIcon(itemPresentation.getIcon(false));
        content.setTabColor(viewItem.getColor());
      });
    }
  }

  private void addToGroup(ServiceViewContributor<?> contributor) {
    String id = contributor.getViewDescriptor(myProject).getId();
    ServiceViewToolWindowDescriptor descriptor = getContributorToolWindowDescriptor(contributor);
    String toolWindowId = ToolWindowId.SERVICES;
    if ((descriptor.isExcludedByDefault() && !myState.included.contains(id)) ||
        !descriptor.isExcludedByDefault() && myState.excluded.contains(id)) {
      toolWindowId = descriptor.getToolWindowId();
    }
    Collection<ServiceViewContributor<?>> contributors =
      myGroups.computeIfAbsent(toolWindowId, __ -> ConcurrentCollectionFactory.createConcurrentSet());
    contributors.add(contributor);
  }

  private @NotNull Pair<ServiceViewState, List<ServiceViewState>> getServiceViewStates(@NotNull String groupId) {
    List<ServiceViewState> states =
      ContainerUtil.filter(myState.viewStates, state -> groupId.equals(state.groupId) && !StringUtil.isEmpty(state.viewType));
    ServiceViewState mainState =
      ContainerUtil.find(myState.viewStates, state -> groupId.equals(state.groupId) && StringUtil.isEmpty(state.viewType));
    if (mainState == null) {
      mainState = new ServiceViewState();
    }
    return Pair.create(mainState, states);
  }

  @Override
  public @NotNull State getState() {
    List<String> services = ContainerUtil.mapNotNull(myGroups.getOrDefault(ToolWindowId.SERVICES, Collections.emptyList()),
                                                     contributor -> contributor.getViewDescriptor(myProject).getId());
    List<String> includedByDefault = new ArrayList<>();
    List<String> excludedByDefault = new ArrayList<>();
    for (ServiceViewContributor<?> contributor : CONTRIBUTOR_EP_NAME.getExtensionList()) {
      String id = contributor.getViewDescriptor(myProject).getId();
      if (id == null) continue;
      if (getContributorToolWindowDescriptor(contributor).isExcludedByDefault()) {
        excludedByDefault.add(id);
      }
      else {
        includedByDefault.add(id);
      }
    }
    myState.included.clear();
    myState.included.addAll(excludedByDefault);
    myState.included.retainAll(services);
    myState.excluded.clear();
    myState.excluded.addAll(includedByDefault);
    myState.excluded.removeAll(services);

    ContainerUtil.retainAll(myState.viewStates, state -> myGroups.containsKey(state.groupId));
    for (ServiceViewContentHolder holder : myContentHolders) {
      ContainerUtil.retainAll(myState.viewStates, state -> !holder.toolWindowId.equals(state.groupId));

      ServiceViewFilter mainFilter = holder.mainView.getModel().getFilter();
      ServiceViewState mainState = new ServiceViewState();
      myState.viewStates.add(mainState);
      holder.mainView.saveState(mainState);
      mainState.groupId = holder.toolWindowId;
      mainState.treeStateElement = new Element("root");
      mainState.treeState.writeExternal(mainState.treeStateElement);
      mainState.clearTreeState();

      List<ServiceView> processedViews = new SmartList<>();
      for (Content content : holder.contentManager.getContents()) {
        ServiceView serviceView = getServiceView(content);
        if (serviceView == null || isMainView(serviceView)) continue;

        ServiceViewState viewState = new ServiceViewState();
        processedViews.add(serviceView);
        myState.viewStates.add(viewState);
        serviceView.saveState(viewState);
        viewState.groupId = holder.toolWindowId;
        viewState.isSelected = holder.contentManager.isSelected(content);
        ServiceViewModel viewModel = serviceView.getModel();
        if (viewModel instanceof ServiceListModel) {
          viewState.id = content.getDisplayName();
        }
        ServiceViewFilter parentFilter = viewModel.getFilter().getParent();
        if (parentFilter != null && !parentFilter.equals(mainFilter)) {
          for (int i = 0; i < processedViews.size(); i++) {
            ServiceView parentView = processedViews.get(i);
            if (parentView.getModel().getFilter().equals(parentFilter)) {
              viewState.parentView = i;
              break;
            }
          }
        }

        viewState.treeStateElement = new Element("root");
        viewState.treeState.writeExternal(viewState.treeStateElement);
        viewState.clearTreeState();
      }
    }

    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    clearViewStateIfNeeded(state);
    myState = state;
    for (ServiceViewState viewState : myState.viewStates) {
      viewState.treeState = TreeState.createFrom(viewState.treeStateElement);
    }
    loadGroups();
  }

  @Override
  public void noStateLoaded() {
    if (!myProject.isDefault()) {
      ServiceViewManagerImpl defaultManager =
        (ServiceViewManagerImpl)ServiceViewManager.getInstance(ProjectManager.getInstance().getDefaultProject());
      myState.excluded.addAll(defaultManager.myState.excluded);
      myState.included.addAll(defaultManager.myState.included);
    }
    loadGroups();
  }

  private void loadGroups() {
    for (ServiceViewContributor<?> contributor : CONTRIBUTOR_EP_NAME.getExtensionList()) {
      addToGroup(contributor);
      myNotInitializedContributors.add(contributor);
    }

    registerToolWindows(myGroups.keySet());

    Disposable disposable = Disposer.newDisposable();
    Disposer.register(myProject, disposable);
    myProject.getMessageBus().connect(disposable).subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
      @Override
      public void toolWindowShown(@NotNull ToolWindow toolWindow) {
        Collection<ServiceViewContributor<?>> contributors = myGroups.get(toolWindow.getId());
        if (contributors != null) {
          for (ServiceViewContributor<?> contributor : contributors) {
            if (myNotInitializedContributors.remove(contributor)) {
              ServiceEvent e = ServiceEvent.createResetEvent(contributor.getClass());
              myModel.handle(e);
            }
          }
        }
        if (myNotInitializedContributors.isEmpty()) {
          Disposer.dispose(disposable);
        }
      }
    });
  }

  private static void clearViewStateIfNeeded(@NotNull State state) {
    // TODO [konstantin.aleev] temporary check state for invalid values cause by 2399fc301031caea7fa90916a87114b1a98c0177
    if (state.viewStates == null) {
      state.viewStates = new SmartList<>();
      return;
    }
    for (Object o : state.viewStates) {
      if (!(o instanceof ServiceViewState)) {
        state.viewStates = new SmartList<>();
        return;
      }
    }
  }

  static final class State {
    public List<ServiceViewState> viewStates = new ArrayList<>();

    public boolean showServicesTree = true;
    public boolean selectActiveService = true;
    public final Set<String> included = new HashSet<>();
    public final Set<String> excluded = new HashSet<>();
  }

  static String getToolWindowContextHelpId() {
    return HELP_ID;
  }

  private ServiceViewState prepareViewState(ServiceViewState state) {
    state.showServicesTree = myState.showServicesTree;
    return state;
  }

  boolean isShowServicesTree() {
    return myState.showServicesTree;
  }

  void setShowServicesTree(boolean value) {
    myState.showServicesTree = value;
    for (ServiceViewContentHolder holder : myContentHolders) {
      for (ServiceView serviceView : holder.getServiceViews()) {
        serviceView.getUi().setMasterComponentVisible(value);
      }
    }
  }

  boolean isSelectActiveService() {
    return myState.selectActiveService;
  }

  void setSelectActiveService(boolean value) {
    myState.selectActiveService = value;
  }

  boolean isSplitByTypeEnabled(@NotNull ServiceView selectedView) {
    if (!isMainView(selectedView) ||
        selectedView.getModel().getVisibleRoots().isEmpty()) {
      return false;
    }

    ServiceViewContentHolder holder = getContentHolder(selectedView);
    if (holder == null) return false;

    for (Content content : holder.contentManager.getContents()) {
      ServiceView serviceView = getServiceView(content);
      if (serviceView != null && serviceView != selectedView && !(serviceView.getModel() instanceof ContributorModel)) return false;
    }
    return true;
  }

  void splitByType(@NotNull ServiceView selectedView) {
    ServiceViewContentHolder holder = getContentHolder(selectedView);
    if (holder == null) return;

    myModel.getInvoker().invokeLater(() -> {
      List<ServiceViewContributor<?>> contributors = ContainerUtil.map(myModel.getRoots(), ServiceViewItem::getRootContributor);
      AppUIUtil.invokeOnEdt(() -> {
        for (ServiceViewContributor<?> contributor : contributors) {
          splitByType(holder.contentManager, contributor);
        }
      });
    });
  }

  private ServiceViewContentHolder getContentHolder(ServiceView serviceView) {
    for (ServiceViewContentHolder holder : myContentHolders) {
      if (holder.getServiceViews().contains(serviceView)) {
        return holder;
      }
    }
    return null;
  }

  private void splitByType(ContentManager contentManager, ServiceViewContributor<?> contributor) {
    for (Content content : contentManager.getContents()) {
      ServiceView serviceView = getServiceView(content);
      if (serviceView != null) {
        ServiceViewModel viewModel = serviceView.getModel();
        if (viewModel instanceof ContributorModel && contributor.equals(((ContributorModel)viewModel).getContributor())) {
          return;
        }
      }
    }

    ContributorModel contributorModel = new ContributorModel(myModel, myModelFilter, contributor, null);
    extract(contentManager, contributorModel, prepareViewState(new ServiceViewState()), true);
  }

  public @NotNull List<Object> getChildrenSafe(@NotNull AnActionEvent e,
                                               @NotNull List<Object> valueSubPath,
                                               @NotNull Class<?> contributorClass) {
    ServiceView serviceView = ServiceViewActionProvider.getSelectedView(e);
    return serviceView != null ? serviceView.getChildrenSafe(valueSubPath, contributorClass) : Collections.emptyList();
  }

  @Override
  public @Nullable String getToolWindowId(@NotNull Class<?> contributorClass) {
    for (Map.Entry<String, Collection<ServiceViewContributor<?>>> entry : myGroups.entrySet()) {
      if (ContainerUtil.exists(entry.getValue(), contributorClass::isInstance)) {
        return entry.getKey();
      }
    }
    return null;
  }

  private static boolean isMainView(@NotNull ServiceView serviceView) {
    return serviceView.getModel() instanceof AllServicesModel;
  }

  private static @Nullable Content getMainContent(@NotNull ContentManager contentManager) {
    for (Content content : contentManager.getContents()) {
      ServiceView serviceView = getServiceView(content);
      if (serviceView != null && isMainView(serviceView)) {
        return content;
      }
    }
    return null;
  }

  private static @Nullable ServiceView getServiceView(Content content) {
    Object component = content.getComponent();
    return component instanceof ServiceView ? (ServiceView)component : null;
  }

  private static void selectContentByModel(@NotNull ContentManager contentManager, @Nullable ServiceViewModel modelToSelect) {
    if (modelToSelect != null) {
      for (Content content : contentManager.getContents()) {
        ServiceView serviceView = getServiceView(content);
        if (serviceView != null && serviceView.getModel() == modelToSelect) {
          contentManager.setSelectedContent(content);
          break;
        }
      }
    }
    else {
      Content content = getMainContent(contentManager);
      if (content != null) {
        contentManager.setSelectedContent(content);
      }
    }
  }

  private static void selectContentByContributor(@NotNull ContentManager contentManager, @NotNull ServiceViewContributor<?> contributor) {
    Content mainContent = null;
    for (Content content : contentManager.getContents()) {
      ServiceView serviceView = getServiceView(content);
      if (serviceView != null) {
        if (serviceView.getModel() instanceof ContributorModel &&
            contributor.equals(((ContributorModel)serviceView.getModel()).getContributor())) {
          contentManager.setSelectedContent(content, true);
          return;
        }
        if (isMainView(serviceView)) {
          mainContent = content;
        }
      }
    }
    if (mainContent != null) {
      contentManager.setSelectedContent(mainContent, true);
    }
  }

  private static final class ServiceViewContentMangerListener implements ContentManagerListener {
    private final ServiceModelFilter myModelFilter;
    private final AutoScrollToSourceHandler myAutoScrollToSourceHandler;
    private final ServiceViewContentHolder myContentHolder;
    private final ContentManager myContentManager;

    ServiceViewContentMangerListener(@NotNull ServiceModelFilter modelFilter,
                                     @NotNull AutoScrollToSourceHandler toSourceHandler,
                                     @NotNull ServiceViewContentHolder contentHolder) {
      myModelFilter = modelFilter;
      myAutoScrollToSourceHandler = toSourceHandler;
      myContentHolder = contentHolder;
      myContentManager = contentHolder.contentManager;
    }

    @Override
    public void contentAdded(@NotNull ContentManagerEvent event) {
      Content content = event.getContent();
      ServiceView serviceView = getServiceView(content);
      if (serviceView != null && !isMainView(serviceView)) {
        serviceView.setAutoScrollToSourceHandler(myAutoScrollToSourceHandler);
        myModelFilter.addFilter(serviceView.getModel().getFilter());
        myContentHolder.processAllModels(ServiceViewModel::filtersChanged);

        serviceView.getModel().addModelListener(() -> {
          if (serviceView.getModel().getRoots().isEmpty()) {
            AppUIExecutor.onUiThread().expireWith(myContentManager).submit(() -> myContentManager.removeContent(content, true));
          }
        });
      }

      if (myContentManager.getContentCount() > 1) {
        Content mainContent = getMainContent(myContentManager);
        if (mainContent != null) {
          mainContent.setDisplayName(ExecutionBundle.message("service.view.all.services"));
        }
      }
      if (serviceView != null) {
        // Skip adding dragging bean as a content.
        updateNavBar(myContentHolder);

        ToolWindow toolWindow = ToolWindowManager.getInstance(serviceView.getProject()).getToolWindow(myContentHolder.toolWindowId);
        if (toolWindow != null) {
          serviceView.getUi().setSplitOrientation(!toolWindow.getAnchor().isHorizontal());
        }
      }
    }

    @Override
    public void contentRemoved(@NotNull ContentManagerEvent event) {
      ServiceView serviceView = getServiceView(event.getContent());
      if (serviceView != null && !isMainView(serviceView)) {
        myModelFilter.removeFilter(serviceView.getModel().getFilter());
        myContentHolder.processAllModels(ServiceViewModel::filtersChanged);
      }
      if (myContentManager.getContentCount() == 1) {
        Content mainContent = getMainContent(myContentManager);
        if (mainContent != null) {
          mainContent.setDisplayName(null);
        }
      }
      if (serviceView != null) {
        // Skip adding dragging bean as a content.
        updateNavBar(myContentHolder);
      }
    }

    @Override
    public void selectionChanged(@NotNull ContentManagerEvent event) {
      ServiceView serviceView = getServiceView(event.getContent());
      if (serviceView == null) return;

      if (event.getOperation() == ContentManagerEvent.ContentOperation.add) {
        serviceView.onViewSelected();
        updateNavBar(myContentHolder);
      }
      else {
        serviceView.onViewUnselected();
      }
    }
  }

  private static void registerActivateByContributorActions(Project project, Collection<? extends ServiceViewContributor<?>> contributors) {
    for (ServiceViewContributor<?> contributor : contributors) {
      ActionManager actionManager = ActionManager.getInstance();
      String actionId = getActivateContributorActionId(contributor);
      if (actionId == null) continue;

      AnAction action = actionManager.getAction(actionId);
      if (action == null) {
        action = new ActivateToolWindowByContributorAction(contributor, contributor.getViewDescriptor(project).getPresentation());
        actionManager.registerAction(actionId, action);
      }
    }
  }

  private static String getActivateContributorActionId(ServiceViewContributor<?> contributor) {
    String name = contributor.getClass().getSimpleName();
    return name.isEmpty() ? null : "ServiceView.Activate" + name;
  }

  private static ServiceViewToolWindowDescriptor getServicesToolWindowDescriptor() {
    return new ServiceViewToolWindowDescriptor() {
      @Override
      public @NotNull String getToolWindowId() {
        return ToolWindowId.SERVICES;
      }

      @Override
      public @NotNull Icon getToolWindowIcon() {
        return AllIcons.Toolwindows.ToolWindowServices;
      }

      @Override
      public @NotNull String getStripeTitle() {
        return UIBundle.message("tool.window.name.services");
      }
    };
  }

  private ServiceViewToolWindowDescriptor getContributorToolWindowDescriptor(ServiceViewContributor<?> rootContributor) {
    ServiceViewDescriptor descriptor = rootContributor.getViewDescriptor(myProject);
    if (descriptor instanceof ServiceViewToolWindowDescriptor) {
      return (ServiceViewToolWindowDescriptor)descriptor;
    }
    @NlsSafe String toolWindowId = descriptor.getId();
    return new ServiceViewToolWindowDescriptor() {
      @Override
      public @NotNull String getToolWindowId() {
        return toolWindowId;
      }

      @Override
      public @NotNull Icon getToolWindowIcon() {
        return AllIcons.Toolwindows.ToolWindowServices;
      }

      @Override
      public @NotNull String getStripeTitle() {
        return toolWindowId;
      }
    };
  }

  void setExcludedContributors(@NotNull Collection<? extends ServiceViewContributor<?>> excluded) {
    List<ServiceViewContributor<?>> toExclude = new ArrayList<>();
    List<ServiceViewContributor<?>> toInclude = new ArrayList<>();
    Collection<ServiceViewContributor<?>> services = null;
    for (Map.Entry<String, Collection<ServiceViewContributor<?>>> entry : myGroups.entrySet()) {
      if (ToolWindowId.SERVICES.equals(entry.getKey())) {
        toExclude.addAll(ContainerUtil.filter(entry.getValue(), contributor -> excluded.contains(contributor)));
        services = entry.getValue();
      }
      else {
        toInclude.addAll(ContainerUtil.filter(entry.getValue(), contributor -> !excluded.contains(contributor)));
      }
    }

    Set<String> toolWindowIds = new HashSet<>();
    toolWindowIds.addAll(excludeServices(toExclude, services));
    toolWindowIds.addAll(includeServices(toInclude, services));
    registerToolWindows(toolWindowIds);

    // Notify model listeners to update tool windows' content.
    myModel.getInvoker().invokeLater(() -> {
      for (ServiceViewContributor<?> contributor : CONTRIBUTOR_EP_NAME.getExtensionList()) {
        ServiceEvent e = ServiceEvent.createResetEvent(contributor.getClass());
        myModel.notifyListeners(e);
      }
    });

    if (toExclude.isEmpty() && !toInclude.isEmpty()) {
      toolWindowIds.add(ToolWindowId.SERVICES);
    }
    activateToolWindows(toolWindowIds);
  }

  private Set<String> excludeServices(@NotNull List<ServiceViewContributor<?>> toExclude,
                                      @Nullable Collection<ServiceViewContributor<?>> services) {
    if (toExclude.isEmpty()) return Collections.emptySet();

    Set<String> toolWindowIds = new HashSet<>();
    if (services != null) {
      services.removeAll(toExclude);
      if (services.isEmpty()) {
        unregisterToolWindow(ToolWindowId.SERVICES);
      }
    }
    for (ServiceViewContributor<?> contributor : toExclude) {
      unregisterActivateByContributorActions(contributor);

      ServiceViewToolWindowDescriptor descriptor = getContributorToolWindowDescriptor(contributor);
      String toolWindowId = descriptor.getToolWindowId();
      Collection<ServiceViewContributor<?>> contributors =
        myGroups.computeIfAbsent(toolWindowId, __ -> ConcurrentCollectionFactory.createConcurrentSet());
      if (contributors.isEmpty()) {
        toolWindowIds.add(toolWindowId);
      }
      contributors.add(contributor);
    }
    return toolWindowIds;
  }

  private Set<String> includeServices(@NotNull List<ServiceViewContributor<?>> toInclude,
                                      @Nullable Collection<ServiceViewContributor<?>> services) {
    if (toInclude.isEmpty()) return Collections.emptySet();

    Set<String> toolWindowIds = new HashSet<>();
    for (ServiceViewContributor<?> contributor : toInclude) {
      for (Map.Entry<String, Collection<ServiceViewContributor<?>>> entry : myGroups.entrySet()) {
        if (!ToolWindowId.SERVICES.equals(entry.getKey()) && entry.getValue().remove(contributor)) {
          if (entry.getValue().isEmpty()) {
            unregisterToolWindow(entry.getKey());
          }
          break;
        }
      }
    }

    if (services == null) {
      Collection<ServiceViewContributor<?>> servicesContributors = ConcurrentCollectionFactory.createConcurrentSet();
      servicesContributors.addAll(toInclude);
      myGroups.put(ToolWindowId.SERVICES, servicesContributors);
      toolWindowIds.add(ToolWindowId.SERVICES);
    }
    else {
      services.addAll(toInclude);
      registerActivateByContributorActions(myProject, toInclude);
    }
    return toolWindowIds;
  }

  private void activateToolWindows(Set<String> toolWindowIds) {
    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    toolWindowManager.invokeLater(() -> {
      for (String toolWindowId : toolWindowIds) {
        if (myActiveToolWindowIds.contains(toolWindowId)) {
          ToolWindow toolWindow = toolWindowManager.getToolWindow(toolWindowId);
          if (toolWindow != null) {
            toolWindow.activate(null);
          }
        }
      }
    });
  }

  void includeToolWindow(@NotNull String toolWindowId) {
    Set<ServiceViewContributor<?>> excluded = new HashSet<>();
    Set<ServiceViewContributor<?>> toInclude = new HashSet<>();
    for (Map.Entry<String, Collection<ServiceViewContributor<?>>> entry : myGroups.entrySet()) {
      if (toolWindowId.equals(entry.getKey())) {
        toInclude.addAll(entry.getValue());
      }
      else if (!ToolWindowId.SERVICES.equals(entry.getKey())) {
        excluded.addAll(entry.getValue());
      }
    }

    setExcludedContributors(excluded);
    Set<? extends ServiceViewContributor<?>> activeContributors = getActiveContributors();
    if (!Collections.disjoint(activeContributors, toInclude)) {
      ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
      toolWindowManager.invokeLater(() -> {
        ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.SERVICES);
        if (toolWindow != null) {
          myActiveToolWindowIds.add(ToolWindowId.SERVICES);
          toolWindow.show();
        }
      });
    }
  }

  private void unregisterToolWindow(String toolWindowId) {
    myActiveToolWindowIds.remove(toolWindowId);
    myGroups.remove(toolWindowId);
    for (ServiceViewContentHolder holder : myContentHolders) {
      if (holder.toolWindowId.equals(toolWindowId)) {
        myContentHolders.remove(holder);
        break;
      }
    }
    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    toolWindowManager.invokeLater(() -> {
      if (myProject.isDisposed() || myProject.isDefault()) return;

      ToolWindow toolWindow = toolWindowManager.getToolWindow(toolWindowId);
      if (toolWindow != null) {
        toolWindow.remove();
      }
    });
  }

  private static void unregisterActivateByContributorActions(ServiceViewContributor<?> extension) {
    String actionId = getActivateContributorActionId(extension);
    if (actionId != null) {
      ActionManager actionManager = ActionManager.getInstance();
      AnAction action = actionManager.getAction(actionId);
      if (action != null) {
        actionManager.unregisterAction(actionId);
      }
    }
  }

  private final class ServiceViewExtensionPointListener implements ExtensionPointListener<ServiceViewContributor<?>> {
    @Override
    public void extensionAdded(@NotNull ServiceViewContributor<?> extension, @NotNull PluginDescriptor pluginDescriptor) {
      addToGroup(extension);
      String toolWindowId = getToolWindowId(extension.getClass());
      boolean register = myGroups.get(toolWindowId).size() == 1;
      ServiceEvent e = ServiceEvent.createResetEvent(extension.getClass());
      myModel.handle(e).onSuccess(o -> {
        if (register) {
          ServiceViewItem eventRoot = ContainerUtil.find(myModel.getRoots(), root -> {
            return extension.getClass().isInstance(root.getRootContributor());
          });
          assert toolWindowId != null;
          registerToolWindow(getContributorToolWindowDescriptor(extension), eventRoot != null);
        }
        else {
          eventHandled(e);
        }
        if (ToolWindowId.SERVICES.equals(toolWindowId)) {
          AppUIExecutor.onUiThread().expireWith(myProject)
            .submit(() -> registerActivateByContributorActions(myProject, new SmartList<>(extension)));
        }
      });
    }

    @Override
    public void extensionRemoved(@NotNull ServiceViewContributor<?> extension, @NotNull PluginDescriptor pluginDescriptor) {
      myNotInitializedContributors.remove(extension);
      ServiceEvent e = ServiceEvent.createUnloadSyncResetEvent(extension.getClass());
      myModel.handle(e).onProcessed(o -> {
        eventHandled(e);

        for (Map.Entry<String, Collection<ServiceViewContributor<?>>> entry : myGroups.entrySet()) {
          if (entry.getValue().remove(extension)) {
            if (entry.getValue().isEmpty()) {
              unregisterToolWindow(entry.getKey());
            }
            break;
          }
        }

        unregisterActivateByContributorActions(extension);
      });
    }
  }

  private static final class ActivateToolWindowByContributorAction extends DumbAwareAction {
    private final ServiceViewContributor<?> myContributor;

    ActivateToolWindowByContributorAction(ServiceViewContributor<?> contributor, ItemPresentation contributorPresentation) {
      myContributor = contributor;
      Presentation templatePresentation = getTemplatePresentation();
      templatePresentation.setText(ExecutionBundle.messagePointer("service.view.activate.tool.window.action.name",
                                                                  ServiceViewDragHelper.getDisplayName(contributorPresentation)));
      templatePresentation.setIcon(contributorPresentation.getIcon(false));
      templatePresentation.setDescription(ExecutionBundle.messagePointer("service.view.activate.tool.window.action.description"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      if (project == null) return;

      String toolWindowId = ServiceViewManager.getInstance(project).getToolWindowId(myContributor.getClass());
      if (toolWindowId == null) return;

      ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId);
      if (toolWindow != null) {
        toolWindow.activate(() -> {
          ServiceViewContentHolder holder =
            ((ServiceViewManagerImpl)ServiceViewManager.getInstance(project)).getContentHolder(myContributor.getClass());
          if (holder != null) {
            selectContentByContributor(holder.contentManager, myContributor);
          }
        });
      }
    }
  }

  private record ServiceViewContentHolder(ServiceView mainView,
                                          ContentManager contentManager,
                                          Collection<ServiceViewContributor<?>> rootContributors,
                                          String toolWindowId,
                                          Wrapper toolWindowHeaderSideComponent) {
    @Unmodifiable
    @NotNull
    List<ServiceView> getServiceViews() {
      List<ServiceView> views = ContainerUtil.mapNotNull(contentManager.getContents(), ServiceViewManagerImpl::getServiceView);
      if (views.isEmpty()) return new SmartList<>(mainView);

      if (!views.contains(mainView)) {
        views = ContainerUtil.prepend(views, mainView);
      }
      return views;
    }

    private void processAllModels(Consumer<? super ServiceViewModel> consumer) {
      List<ServiceViewModel> models = ContainerUtil.map(getServiceViews(), ServiceView::getModel);
      ServiceViewModel model = ContainerUtil.getFirstItem(models);
      if (model != null) {
        model.getInvoker().invokeLater(() -> {
          for (ServiceViewModel viewModel : models) {
            consumer.accept(viewModel);
          }
        });
      }
    }
  }
}
