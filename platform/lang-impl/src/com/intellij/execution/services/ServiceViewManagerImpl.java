// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.services;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.xmlb.annotations.Property;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

@State(
  name = "ServiceViewManager",
  storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
public class ServiceViewManagerImpl implements ServiceViewManager, PersistentStateComponent<ServiceViewManagerImpl.State> {
  static final ExtensionPointName<ServiceViewContributor> EP_NAME =
    ExtensionPointName.create("com.intellij.serviceViewContributor");

  // TODO [konstantin.aleev] provide help id
  @NonNls private static final String HELP_ID = "run-dashboard.reference";

  @NotNull private final Project myProject;

  @NotNull private State myState = new State();
  private ServiceView myServiceView;

  public ServiceViewManagerImpl(@NotNull Project project) {
    myProject = project;
    myProject.getMessageBus().connect(myProject).subscribe(ServiceViewContributor.TOPIC, this::updateToolWindow);
  }

  boolean hasServices() {
    for (@SuppressWarnings("unchecked") ServiceViewContributor<Object, Object, Object> contributor : EP_NAME.getExtensions()) {
      if (!contributor.getNodes(myProject).isEmpty()) return true;
    }
    return false;
  }

  void createToolWindowContent(@NotNull ToolWindow toolWindow) {
    myServiceView = new ServiceView(myProject, myState.viewState);

    Content toolWindowContent = ContentFactory.SERVICE.getInstance().createContent(myServiceView, null, false);
    toolWindowContent.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);
    toolWindowContent.setHelpId(getToolWindowContextHelpId());
    toolWindowContent.setCloseable(false);

    Disposer.register(toolWindowContent, myServiceView);
    Disposer.register(toolWindowContent, () -> myServiceView = null);

    toolWindow.getContentManager().addContent(toolWindowContent);
  }

  @Override
  public void selectNode(Object node) {
    AppUIUtil.invokeLaterIfProjectAlive(myProject, () -> {
      ServiceView content = myServiceView;
      if (content != null) {
        content.selectNode(node);
      }
    });
  }

  private void updateToolWindow(ServiceViewContributor.ServiceEvent event) {
    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    if (toolWindowManager == null) return;

    toolWindowManager.invokeLater(() -> {
      if (myProject.isDisposed()) {
        return;
      }

      boolean available = hasServices();
      ToolWindow toolWindow = toolWindowManager.getToolWindow(ToolWindowId.SERVICES);
      if (toolWindow == null) {
        toolWindow = createToolWindow(toolWindowManager, available);
        if (available) {
          toolWindow.show(null);
        }
        return;
      }

      boolean doShow = !toolWindow.isAvailable() && available;
      toolWindow.setAvailable(available, null);
      if (doShow) {
        toolWindow.show(null);
      }
    });
  }

  private ToolWindow createToolWindow(ToolWindowManager toolWindowManager, boolean available) {
    ToolWindow toolWindow = toolWindowManager.registerToolWindow(getToolWindowId(), true, ToolWindowAnchor.BOTTOM,
                                                                 myProject, true);
    toolWindow.setIcon(getToolWindowIcon());
    toolWindow.setAvailable(available, null);
    createToolWindowContent(toolWindow);
    return toolWindow;
  }

  @NotNull
  @Override
  public State getState() {
    ServiceView serviceView = myServiceView;
    if (serviceView != null) {
      myState.viewState = serviceView.getState();
      myState.viewState.treeStateElement = new Element("root");
      myState.viewState.treeState.writeExternal(myState.viewState.treeStateElement);
    }
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
    myState.viewState.treeState = TreeState.createFrom(myState.viewState.treeStateElement);
  }

  static ServiceView getServiceView(@NotNull Project project) {
    return ((ServiceViewManagerImpl)ServiceViewManager.getInstance(project)).myServiceView;
  }

  static class State {
    @Property(surroundWithTag = false)
    public ServiceViewState viewState = new ServiceViewState();
  }

  private static String getToolWindowId() {
    return ToolWindowId.SERVICES;
  }

  private static Icon getToolWindowIcon() {
    return AllIcons.Toolwindows.ToolWindowRun;
  }

  static String getToolWindowContextHelpId() {
    return HELP_ID;
  }
}
