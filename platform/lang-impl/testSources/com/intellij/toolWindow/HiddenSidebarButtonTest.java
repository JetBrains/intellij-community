// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.impl.DesktopLayout;
import com.intellij.usageView.impl.UsageViewContentManagerImpl;

import java.util.Arrays;

/**
 * @author Vassiliy Kudryashov
 */
public class HiddenSidebarButtonTest extends ToolWindowManagerTestCase {
  public void testHiddenButton() throws Exception {
    String[] toolWindows = {ToolWindowId.TODO_VIEW, ToolWindowId.FIND, ToolWindowId.PROJECT_VIEW};
    boolean[] expectedStripes = {false, true, true};
    boolean[] expectedVisibility = {false, false, true};

    DesktopLayout layout = manager.getLayout();
    layout.readExternal(JDOMUtil.load(
      "<layout>" +
      "<window_info id=\"TODO\" active=\"false\" anchor=\"bottom\" auto_hide=\"false\" internal_type=\"DOCKED\" type=\"DOCKED\" visible=\"false\"" +
      " show_stripe_button=\"false\" weight=\"0.42947903\" sideWeight=\"0.4874552\" order=\"6\" side_tool=\"false\" content_ui=\"tabs\" x=\"119\"" +
      " y=\"106\" width=\"619\" height=\"748\"/>" +
      "<window_info id=\"Find\" active=\"false\" anchor=\"bottom\" auto_hide=\"false\" internal_type=\"DOCKED\" type=\"DOCKED\" visible=\"false\"" +
      " show_stripe_button=\"true\" weight=\"0.47013977\" sideWeight=\"0.5\" order=\"1\" side_tool=\"false\" content_ui=\"tabs\" x=\"443\" y=\"301\"" +
      " width=\"702\" height=\"388\"/>" +
      "<window_info id=\"Project\" active=\"false\" anchor=\"left\" auto_hide=\"false\" internal_type=\"DOCKED\" type=\"DOCKED\" visible=\"false\"" +
      " show_stripe_button=\"true\" weight=\"0.37235227\" sideWeight=\"0.6060991\" order=\"0\" side_tool=\"false\" content_ui=\"tabs\" x=\"116\"" +
      " y=\"80\" width=\"487\" height=\"787\"/>" +
      "</layout>"), false, false);

    for (ToolWindowEP extension : ToolWindowEP.EP_NAME.getExtensionList()) {
      if (Arrays.asList(ToolWindowId.TODO_VIEW, ToolWindowId.FIND, ToolWindowId.PROJECT_VIEW).contains(extension.id)) {
        manager.initToolWindow(extension);
      }
    }
    new UsageViewContentManagerImpl(manager.getProject(), manager);

    for (int i = 0; i < toolWindows.length; i++) {
      String toolWindow = toolWindows[i];
      assertTrue(manager.isToolWindowRegistered(toolWindow));
      assertEquals(expectedStripes[i], layout.getInfo(toolWindow).isShowStripeButton());
      TestHelperKt.testStripeButton(toolWindow, manager, expectedVisibility[i]);
    }
  }
}