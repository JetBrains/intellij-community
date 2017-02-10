/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.execution.dashboard;

import com.intellij.execution.dashboard.tree.ConfigurationTypeGroupingRule;
import com.intellij.execution.dashboard.tree.FolderGroupingRule;
import com.intellij.execution.dashboard.tree.Grouper;
import com.intellij.execution.dashboard.tree.StatusGroupingRule;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentUI;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author konstantin.aleev
 */
@State(
  name = "RuntimeDashboard",
  storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
public class RuntimeDashboardManagerImpl implements RuntimeDashboardManager, PersistentStateComponent<Element> {
  @NonNls private static final String GROUPERS_TAG = "groupers";
  @NonNls private static final String GROUPER_TAG = "grouper";
  @NonNls private static final String NAME_ATTR = "name";
  @NonNls private static final String ENABLED_ATTR = "enabled";

  @NotNull private final ContentManager myContentManager;
  private List<Grouper> myGroupers = new ArrayList<>();

  public RuntimeDashboardManagerImpl(@NotNull final Project project) {
    ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
    ContentUI contentUI = new PanelContentUI();
    myContentManager = contentFactory.createContentManager(contentUI, false, project);

    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    ToolWindow toolWindow = toolWindowManager.registerToolWindow(getToolWindowId(), false, ToolWindowAnchor.BOTTOM,
                                                                 project, true);
    toolWindow.setIcon(getToolWindowIcon());
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      myGroupers.add(new Grouper(new ConfigurationTypeGroupingRule()));
      myGroupers.add(new Grouper(new StatusGroupingRule()));
      myGroupers.add(new Grouper(new FolderGroupingRule()));

      RuntimeDashboardContent dashboardContent = new RuntimeDashboardContent(project, myContentManager, myGroupers);
      Content content = contentFactory.createContent(dashboardContent, null, false);
      Disposer.register(content, dashboardContent);
      toolWindow.getContentManager().addContent(content);
    }

    if (!Registry.is("ide.runtime.dashboard")) {
      toolWindow.setAvailable(false, null);
    }

    // TODO [konstantin.aleev] control tool window availability and visibility.
  }

  @Override
  public ContentManager getDashboardContentManager() {
    return myContentManager;
  }

  @Override
  public String getToolWindowId() {
    return ToolWindowId.RUNTIME_DASHBOARD;
  }

  @Override
  public Icon getToolWindowIcon() {
    return AllIcons.Toolwindows.ToolWindowRun; // TODO [konstantin.aleev] provide new icon
  }

  @Nullable
  @Override
  public Element getState() {
    final Element element = new Element("state");
    final Element groupers = new Element(GROUPERS_TAG);
    element.addContent(groupers);
    myGroupers.forEach(grouper -> groupers.addContent(writeGrouperState(grouper)));
    return element;
  }

  private static Element writeGrouperState(Grouper grouper) {
    Element element = new Element(GROUPER_TAG);
    element.setAttribute(NAME_ATTR, grouper.getRule().getName());
    element.setAttribute(ENABLED_ATTR, Boolean.toString(grouper.isEnabled()));
    return element;
  }

  @Override
  public void loadState(Element element) {
    Element groupersElement = element.getChild(GROUPERS_TAG);
    if (groupersElement != null) {
      List<Element> groupers =  groupersElement.getChildren(GROUPER_TAG);
      groupers.forEach(this::readGrouperState);
    }
  }

  private void readGrouperState(Element grouperElement) {
    String id = grouperElement.getAttributeValue(NAME_ATTR);
    for (Grouper grouper : myGroupers) {
      if (grouper.getRule().getName().equals(id)) {
        grouper.setEnabled(Boolean.valueOf(grouperElement.getAttributeValue(ENABLED_ATTR, "true")));
        return;
      }
    }
  }
}
