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
package com.intellij.openapi.wm.impl;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.ProjectViewSharedSettings;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.openapi.wm.ToolWindowId;

/**
 * @author Dmitry Avdeev
 */
public class ProjectToolWindowTest extends ToolWindowManagerTestCase {

  public void testProjectViewActivate() {

    ToolWindowEP[] extensions = Extensions.getExtensions(ToolWindowEP.EP_NAME);
    for (ToolWindowEP extension : extensions) {
      if (ToolWindowId.PROJECT_VIEW.equals(extension.id)) {
        myManager.initToolWindow(extension);
      }
    }

    DesktopLayout layout = myManager.getLayout();
    WindowInfoImpl info = layout.getInfo(ToolWindowId.PROJECT_VIEW, false);
    assertFalse(info.isVisible());
    info.setVisible(true);

    ToolWindow window = myManager.getToolWindow(ToolWindowId.PROJECT_VIEW);
    assertTrue(window.isVisible());

    ProjectView.getInstance(getProject());

    ProjectViewSharedSettings.Companion.getInstance().setAutoscrollFromSource(true);

    try {
      window.activate(null);
    }
    finally {
      // cleanup
      info.setVisible(false);
    }
  }
}
