// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.testFramework.MapDataContext;
import gnu.trove.THashMap;
import gnu.trove.THashSet;

import java.util.*;

public class HideAllToolWindowsTest extends ToolWindowManagerTestCase {
  public void testDontHideFloatingAndWindowedToolWindows() {
    List<ToolWindowEP> extensions = ToolWindowEP.EP_NAME.getExtensionList();

    Map<String, ToolWindowType> types = new THashMap<>();
    List<ToolWindowType> cycle = new ArrayList<>(Arrays.asList(ToolWindowType.values()));
    for (int i = 0; i < extensions.size(); i++) {
      ToolWindowEP extension = extensions.get(i);
      String id = extension.id;
      if (id.equals("Structure") || id.equals("Favorites") || id.equals("Ant")) {
        continue;
      }

      manager.initToolWindow(extension);
      // if not applicable, then will be not registered
      if (!manager.isToolWindowRegistered(id)) {
        continue;
      }

      manager.showToolWindow(id);

      ToolWindowType type = cycle.get(i % cycle.size());
      types.put(id, type);
      manager.setToolWindowType(id, type);
    }

    MapDataContext context = new MapDataContext();
    context.put(CommonDataKeys.PROJECT, getProject());
    Set<String> visibleIds = new THashSet<>();
    for (String id : types.keySet()) {
      if (manager.getToolWindow(id).isVisible()) {
        visibleIds.add(id);
      }
    }
    ActionManager.getInstance().getAction("HideAllWindows").actionPerformed(AnActionEvent.createFromDataContext("", null, context));

    for (String id : visibleIds) {
      ToolWindow window = manager.getToolWindow(id);
      assertEquals(id + ":" + window.getType(),
                   types.get(id) == ToolWindowType.FLOATING || types.get(id) == ToolWindowType.WINDOWED,
                   window.isVisible());
    }
  }
}
