package com.intellij.tools;

import com.intellij.openapi.keymap.KeymapExtension;
import com.intellij.openapi.keymap.KeymapGroup;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.impl.ui.Group;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.HashMap;

import javax.swing.*;
import java.util.Arrays;

public class ToolKeymapExtension implements KeymapExtension {
  private static final Icon TOOLS_ICON = IconLoader.getIcon("/nodes/keymapTools.png");
  private static final Icon TOOLS_OPEN_ICON = IconLoader.getIcon("/nodes/keymapToolsOpen.png");

  public KeymapGroup createGroup(final Condition<AnAction> filtered, final Project project) {
    final ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    String[] ids = actionManager.getActionIds(Tool.ACTION_ID_PREFIX);
    Arrays.sort(ids);
    Group group = new Group(KeyMapBundle.message("actions.tree.external.tools.group"), TOOLS_ICON, TOOLS_OPEN_ICON);

    ToolManager toolManager = ToolManager.getInstance();

    HashMap<String, Group> toolGroupNameToGroup = new HashMap<String, Group>();

    for (String id : ids) {
      if (filtered != null && !filtered.value(actionManager.getActionOrStub(id))) continue;
      String groupName = toolManager.getGroupByActionId(id);

      if (groupName != null && groupName.trim().length() == 0) {
        groupName = null;
      }

      Group subGroup = toolGroupNameToGroup.get(groupName);
      if (subGroup == null) {
        subGroup = new Group(groupName, null, null);
        toolGroupNameToGroup.put(groupName, subGroup);
        if (groupName != null) {
          group.addGroup(subGroup);
        }
      }

      subGroup.addActionId(id);
    }

    Group subGroup = toolGroupNameToGroup.get(null);
    if (subGroup != null) {
      group.addAll(subGroup);
    }

    return group;
  }
}