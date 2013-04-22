
/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.options.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public abstract class BaseToolManager<T extends Tool> implements ExportableApplicationComponent {

  private final ActionManagerEx myActionManager;
  private final SchemesManager<ToolsGroup<T>, ToolsGroup<T>> mySchemesManager;

  public BaseToolManager(ActionManagerEx actionManagerEx, SchemesManagerFactory factory) {
    myActionManager = actionManagerEx;

    mySchemesManager = factory.createSchemesManager(
      getSchemesPath(), createProcessor(), RoamingType.PER_USER);

    mySchemesManager.loadSchemes();
    registerActions();
  }

  protected abstract String getSchemesPath();

  protected abstract SchemeProcessor<ToolsGroup<T>> createProcessor();

  @Nullable
  public static String convertString(String s) {
    if (s != null && s.trim().length() == 0) return null;
    return s;
  }

  @Override
  @NotNull
  public File[] getExportFiles() {
    return new File[]{mySchemesManager.getRootDirectory()};
  }

  @Override
  @NotNull
  public String getPresentableName() {
    return ToolsBundle.message("tools.settings");
  }

  @Override
  public void disposeComponent() {
  }

  @Override
  public void initComponent() {
  }

  public List<T> getTools() {
    ArrayList<T> result = new ArrayList<T>();
    for (ToolsGroup group : mySchemesManager.getAllSchemes()) {
      result.addAll(group.getElements());
    }
    return result;
  }

  public List<T> getTools(String group) {
    ArrayList<T> list = new ArrayList<T>();
    ToolsGroup groupByName = mySchemesManager.findSchemeByName(group);
    if (groupByName != null) {
      list.addAll(groupByName.getElements());
    }
    return list;
  }

  /**
   * Get all not empty group names of tools in array
   */
  String[] getGroups(T[] tools) {
    ArrayList<String> list = new ArrayList<String>();
    for (int i = 0; i < tools.length; i++) {
      T tool = tools[i];
      if (!list.contains(tool.getGroup())) {
        list.add(tool.getGroup());
      }
    }
    return ArrayUtil.toStringArray(list);
  }

  public String getGroupByActionId(String actionId) {
    for (T tool : getTools()) {
      if (Comparing.equal(actionId, tool.getActionId())) {
        return tool.getGroup();
      }
    }
    return null;
  }

  public List<ToolsGroup<T>> getGroups() {
    return mySchemesManager.getAllSchemes();
  }

  public void setTools(ToolsGroup[] tools) {
    mySchemesManager.clearAllSchemes();
    for (ToolsGroup newGroup : tools) {
      mySchemesManager.addNewScheme(newGroup, true);
    }
    registerActions();
  }


  void registerActions() {
    unregisterActions();

    // register
    HashSet registeredIds = new HashSet(); // to prevent exception if 2 or more targets have the same name

    List<T> tools = getTools();
    for (T tool : tools) {
      String actionId = tool.getActionId();

      if (!registeredIds.contains(actionId)) {
        registeredIds.add(actionId);
        myActionManager.registerAction(actionId, createToolAction(tool));
      }
    }
  }

  protected ToolAction createToolAction(T tool) {
    return new ToolAction(tool);
  }

  protected abstract String getActionIdPrefix();

  private void unregisterActions() {
    // unregister Tool actions
    String[] oldIds = myActionManager.getActionIds(getActionIdPrefix());
    for (int i = 0; i < oldIds.length; i++) {
      String oldId = oldIds[i];
      myActionManager.unregisterAction(oldId);
    }
  }
}
