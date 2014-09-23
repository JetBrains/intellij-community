/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.tools;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Eugene Belyaev
 */
public abstract class BaseExternalToolsGroup<T extends Tool> extends SimpleActionGroup implements DumbAware {
  @Override
  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    removeAll();
    String context = event.getPlace();
    Project project = event.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }
    presentation.setEnabled(true);
    List<ToolsGroup<T>> groups = getToolsGroups();
    for (ToolsGroup group : groups) {
      String groupName = group.getName();
      if (!StringUtil.isEmptyOrSpaces(groupName)) {
        SimpleActionGroup subgroup = new SimpleActionGroup();
        subgroup.getTemplatePresentation().setText(groupName, false);
        subgroup.setPopup(true);
        fillGroup(context, groupName, subgroup);
        if (subgroup.getChildrenCount() > 0) {
          add(subgroup);
        }
      }
      else {
        fillGroup(context, null, this);
      }
    }
    presentation.setVisible(getChildrenCount() > 0);
  }

  protected abstract List<ToolsGroup<T>> getToolsGroups();

  private void fillGroup(String context, @Nullable String groupName, SimpleActionGroup group) {
    List<T> tools = getToolsByGroupName(groupName);
    for (T tool : tools) {
      if (isToolVisible(tool, context)) {
        addToolToGroup(tool, group);
      }
    }
  }

  protected abstract List<T> getToolsByGroupName(String groupName);

  private void addToolToGroup(T tool, SimpleActionGroup group) {
    String id = tool.getActionId();
    AnAction action = ActionManager.getInstance().getAction(id);
    if (action == null) {
      action = createToolAction(tool);
    }

    group.add(action);
  }

  protected abstract ToolAction createToolAction(T tool);

  private boolean isToolVisible(T tool, String context) {
    if (!tool.isEnabled()) return false;
    if (ActionPlaces.EDITOR_POPUP.equals(context) ||
        ActionPlaces.EDITOR_TAB_POPUP.equals(context)) {
      return tool.isShownInEditor();
    }
    else if (
      ActionPlaces.PROJECT_VIEW_POPUP.equals(context) ||
      ActionPlaces.COMMANDER_POPUP.equals(context) ||
      ActionPlaces.J2EE_VIEW_POPUP.equals(context) ||
      ActionPlaces.TYPE_HIERARCHY_VIEW_POPUP.equals(context) ||
      ActionPlaces.CALL_HIERARCHY_VIEW_POPUP.equals(context) ||
      ActionPlaces.METHOD_HIERARCHY_VIEW_POPUP.equals(context) ||
      ActionPlaces.FAVORITES_VIEW_POPUP.equals(context) ||
      ActionPlaces.SCOPE_VIEW_POPUP.equals(context) ||
      ActionPlaces.NAVIGATION_BAR_POPUP.equals(context)
      ) {
      return tool.isShownInProjectViews();
    }
    else if (ActionPlaces.isMainMenuOrActionSearch(context)) {
      return tool.isShownInMainMenu();
    }
    else if (ActionPlaces.USAGE_VIEW_POPUP.equals(context)) {
      return tool.isShownInSearchResultsPopup();
    }
    return false;
  }
}
