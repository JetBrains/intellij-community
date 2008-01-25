
package com.intellij.tools;

import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

public class ToolManager implements ExportableApplicationComponent {
  private final ArrayList<Tool> myTools = new ArrayList<Tool>();
  private ActionManagerEx myActionManager;

  public static ToolManager getInstance() {
    return ApplicationManager.getApplication().getComponent(ToolManager.class);
  }

  public ToolManager(ActionManagerEx actionManagerEx) {
    myActionManager = actionManagerEx;

    Tool[] tools = new ToolSettings().loadTools();
    setTools(tools);
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{ToolSettings.getToolDirectory()};
  }

  @NotNull
  public String getPresentableName() {
    return ToolsBundle.message("tools.settings");
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public Tool[] getTools() {
    return myTools.toArray(new Tool[myTools.size()]);
  }

  public Tool[] getTools(String group) {
    ArrayList<Tool> list = new ArrayList<Tool>();
    for (Iterator<Tool> iterator = myTools.iterator(); iterator.hasNext();) {
      Tool tool = iterator.next();
      if (Comparing.equal(group, tool.getGroup())) {
        list.add(tool);
      }
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
    return list.toArray(new String[list.size()]);
  }

  public String getGroupByActionId(String actionId) {
    for (Iterator<Tool> iterator = myTools.iterator(); iterator.hasNext();) {
      Tool tool = iterator.next();
      if (Comparing.equal(actionId, tool.getActionId())) {
        return tool.getGroup();
      }
    }
    return null;
  }

  public String[] getGroups() {
    return getGroups(myTools.toArray(new Tool[myTools.size()]));
  }

  public void setTools(Tool[] tools) {
    myTools.clear();
    for (int i = 0; i < tools.length; i++) {
      Tool tool = tools[i];
      myTools.add(tool);
    }
    registerActions();
  }

  void writeTools() throws IOException{
    new ToolSettings().saveTools(getTools());
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

  public String getComponentName() {
    return "ToolManager";
  }

}
