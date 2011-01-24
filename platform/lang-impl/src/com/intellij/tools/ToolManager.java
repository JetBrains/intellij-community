
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.options.SchemesManagerFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

public class ToolManager implements ExportableApplicationComponent {

  
  private final ActionManagerEx myActionManager;
  private final SchemesManager<ToolsGroup,ToolsGroup> mySchemesManager;

  public static ToolManager getInstance() {
    return ApplicationManager.getApplication().getComponent(ToolManager.class);
  }

  public ToolManager(ActionManagerEx actionManagerEx, SchemesManagerFactory factory) {
    myActionManager = actionManagerEx;

    mySchemesManager = factory.createSchemesManager(
        "$ROOT_CONFIG$/tools", new ToolsProcessor(), RoamingType.PER_USER);

    mySchemesManager.loadSchemes();
    registerActions();
  }

  @Nullable
  public static String convertString(String s) {
    if (s != null && s.trim().length() == 0) return null;
    return s;
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{mySchemesManager.getRootDirectory()};
  }

  @NotNull
  public String getPresentableName() {
    return ToolsBundle.message("tools.settings");
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public Tool[] getTools() {
    ArrayList<Tool> result = new ArrayList<Tool>();
    for (ToolsGroup group : mySchemesManager.getAllSchemes()) {
      result.addAll(group.getElements());
    }
    return result.toArray(new Tool[result.size()]);
  }

  public Tool[] getTools(String group) {
    ArrayList<Tool> list = new ArrayList<Tool>();
    ToolsGroup groupByName = mySchemesManager.findSchemeByName(group);
    if (groupByName != null) {
      list.addAll(groupByName.getElements());
    }
    return list.toArray(new Tool[list.size()]);
  }

  /**
    * Get all not empty group names of tools in array
    */
  String[] getGroups(Tool[] tools) {
    ArrayList<String> list = new ArrayList<String>();
    for (int i = 0; i < tools.length; i++) {
      Tool tool = tools[i];
      if (!list.contains(tool.getGroup())) {
        list.add(tool.getGroup());
      }
    }
    return ArrayUtil.toStringArray(list);
  }

  public String getGroupByActionId(String actionId) {
    for (Tool tool : getTools()) {
      if (Comparing.equal(actionId, tool.getActionId())) {
        return tool.getGroup();
      }
    }
    return null;
  }

  public ToolsGroup[] getGroups() {
    Collection<ToolsGroup> groups = mySchemesManager.getAllSchemes();
    return groups.toArray(new ToolsGroup[groups.size()]);
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

    Tool[] tools = getTools();
    for (int i = 0; i < tools.length; i++) {
      Tool tool = tools[i];
      String actionId = tool.getActionId();

      if (!registeredIds.contains(actionId)) {
        registeredIds.add(actionId);
        myActionManager.registerAction(actionId, new ToolAction(tool));
      }
    }
  }

  private void unregisterActions() {
    // unregister Tool actions
    String[] oldIds = myActionManager.getActionIds(Tool.ACTION_ID_PREFIX);
    for (int i = 0; i < oldIds.length; i++) {
      String oldId = oldIds[i];
      myActionManager.unregisterAction(oldId);
    }
  }

  @NotNull
  public String getComponentName() {
    return "ToolManager";
  }

}
