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

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.usageView.impl.UsageViewManagerImpl;
import com.intellij.util.JdomKt;

import java.util.Arrays;

/**
 * @author Vassiliy Kudryashov
 */


public class HiddenSidebarButtonTest extends ToolWindowManagerTestCase {
  private static final String LAYOUT = "<layout>" +
                                 "<window_info id=\"TODO\" active=\"false\" anchor=\"bottom\" auto_hide=\"false\" internal_type=\"DOCKED\" type=\"DOCKED\" visible=\"false\" show_stripe_button=\"false\" weight=\"0.42947903\" sideWeight=\"0.4874552\" order=\"6\" side_tool=\"false\" content_ui=\"tabs\" x=\"119\" y=\"106\" width=\"619\" height=\"748\"/>" +
                                 "<window_info id=\"Find\" active=\"false\" anchor=\"bottom\" auto_hide=\"false\" internal_type=\"DOCKED\" type=\"DOCKED\" visible=\"false\" show_stripe_button=\"true\" weight=\"0.47013977\" sideWeight=\"0.5\" order=\"1\" side_tool=\"false\" content_ui=\"tabs\" x=\"443\" y=\"301\" width=\"702\" height=\"388\"/>" +
                                 "<window_info id=\"Project\" active=\"false\" anchor=\"left\" auto_hide=\"false\" internal_type=\"DOCKED\" type=\"DOCKED\" visible=\"false\" show_stripe_button=\"true\" weight=\"0.37235227\" sideWeight=\"0.6060991\" order=\"0\" side_tool=\"false\" content_ui=\"tabs\" x=\"116\" y=\"80\" width=\"487\" height=\"787\"/>" +
                                 "</layout>";

  private static final String[] IDS = {ToolWindowId.TODO_VIEW, ToolWindowId.FIND, ToolWindowId.PROJECT_VIEW};
  private static final boolean[] ESTIMATED_TO_SHOW = {false, true, true};
  private static final boolean[] ESTIMATED_VISIBILITY = {false, false, true};

  public void testHiddenButton() throws Exception {
    DesktopLayout layout = myManager.getLayout();
    layout.readExternal(JdomKt.loadElement(LAYOUT));
    for (String ID : IDS) {
      assertFalse(layout.isToolWindowRegistered(ID));
      assertTrue(layout.isToolWindowUnregistered(ID));
    }

    ToolWindowEP[] extensions = Extensions.getExtensions(ToolWindowEP.EP_NAME);
    for (ToolWindowEP extension : extensions) {
      if (Arrays.asList(ToolWindowId.TODO_VIEW, ToolWindowId.FIND, ToolWindowId.PROJECT_VIEW).contains(extension.id)) {
        myManager.initToolWindow(extension);
      }
    }
    new UsageViewManagerImpl(myManager.getProject(), myManager);

    for (int i = 0; i < IDS.length; i++) {
      assertTrue(layout.isToolWindowRegistered(IDS[i]));
      assertFalse(layout.isToolWindowUnregistered(IDS[i]));
      assertTrue(ESTIMATED_TO_SHOW[i] == layout.getInfo(IDS[i], true).isShowStripeButton());
      assertTrue(ESTIMATED_VISIBILITY[i] == myManager.getStripeButton(IDS[i]).isVisible());
    }
  }
}
