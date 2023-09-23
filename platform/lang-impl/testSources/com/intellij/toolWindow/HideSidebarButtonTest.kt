// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("RAW_RUN_BLOCKING")

package com.intellij.toolWindow

import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.wm.ToolWindowEP
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.usageView.impl.UsageViewContentManagerImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class HideSidebarButtonTest : ToolWindowManagerTestCase() {
  fun testHiddenButton() {
    runBlocking {
      withContext(Dispatchers.EDT) {
        val toolWindows = arrayOf(ToolWindowId.TODO_VIEW, ToolWindowId.FIND, ToolWindowId.PROJECT_VIEW)
        val expectedStripes = booleanArrayOf(false, true, true)
        val expectedVisibility = booleanArrayOf(false, false, true)
        val layout = manager!!.getLayout()
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
          "</layout>"), false, false)
        for (extension in ToolWindowEP.EP_NAME.extensionList) {
          if (listOf(ToolWindowId.TODO_VIEW, ToolWindowId.FIND, ToolWindowId.PROJECT_VIEW).contains(extension.id)) {
            manager!!.initToolWindow(extension)
          }
        }
        UsageViewContentManagerImpl(manager!!.project, manager!!)
        for (i in toolWindows.indices) {
          val toolWindow = toolWindows[i]
          assertTrue(manager!!.isToolWindowRegistered(toolWindow))
          assertEquals(expectedStripes[i], layout.getInfo(toolWindow)!!.isShowStripeButton)
          testStripeButton(toolWindow, manager!!, expectedVisibility[i])
        }
      }
    }
  }
}