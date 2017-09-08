/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.testFramework.MapDataContext;

import java.util.*;

public class HideAllToolWindowsTest extends ToolWindowManagerTestCase {
  public void testDontHideFloatingAndWindowedToolWindows() {
    ToolWindowEP[] extensions = Extensions.getExtensions(ToolWindowEP.EP_NAME);

    Map<String, ToolWindowType> types = new HashMap<>();
    Map<String, ToolWindowType> toRestore = new HashMap<>();
    List<ToolWindowType> cycle = new ArrayList<>(Arrays.asList(ToolWindowType.values()));
    try {
      for (int i = 0; i < extensions.length; i++) {
        ToolWindowEP extension = extensions[i];
        myManager.initToolWindow(extension);
        ToolWindow window = myManager.getToolWindow(extension.id);
        myManager.showToolWindow(extension.id);

        toRestore.put(extension.id, window.getType());

        ToolWindowType type = cycle.get(i % cycle.size());
        types.put(extension.id, type);
        myManager.setToolWindowType(extension.id, type);
      }
      MapDataContext context = new MapDataContext();
      context.put(CommonDataKeys.PROJECT, getProject());
      Set<String> visibleIds = new HashSet<>();
      for (String id : types.keySet()) {
        if (myManager.getToolWindow(id).isVisible()) {
          visibleIds.add(id);
        }
      }
      ActionManager.getInstance().getAction("HideAllWindows").actionPerformed(AnActionEvent.createFromDataContext("", null, context));

      for (String id : visibleIds) {
        ToolWindow window = myManager.getToolWindow(id);
        assertEquals(id + ":" + window.getType(),
                     types.get(id) == ToolWindowType.FLOATING || types.get(id) == ToolWindowType.WINDOWED,
                     window.isVisible());
      }
    }
    finally {
      for (Map.Entry<String, ToolWindowType> entry : toRestore.entrySet()) {
        myManager.getLayout().getInfo(entry.getKey(), false).setVisible(false);
        myManager.setToolWindowType(entry.getKey(), entry.getValue());
      }
    }
  }
}
