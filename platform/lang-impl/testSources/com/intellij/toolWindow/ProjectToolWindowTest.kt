// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.ProjectViewState
import com.intellij.openapi.application.EDT
import com.intellij.openapi.wm.ToolWindowEP
import com.intellij.openapi.wm.ToolWindowId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat

class ProjectToolWindowTest : ToolWindowManagerTestCase() {
  fun testProjectViewActivate() {
    runBlocking {
      withContext(Dispatchers.EDT) {
        for (extension in ToolWindowEP.EP_NAME.extensionList) {
          if (ToolWindowId.PROJECT_VIEW == extension.id) {
            manager!!.initToolWindow(extension)
          }
        }

        val layout = manager!!.getLayout()
        val info = layout.getInfo(ToolWindowId.PROJECT_VIEW)
        assertThat(info!!.isVisible).isFalse
        info.isVisible = true
        val window = manager!!.getToolWindow(ToolWindowId.PROJECT_VIEW)
        // because change is not applied from desktop
        assertThat(window!!.isVisible).isFalse
        manager!!.showToolWindow(ToolWindowId.PROJECT_VIEW)
        assertThat(window.isVisible).isTrue
        ProjectView.getInstance(project)
        ProjectViewState.getInstance(project).autoscrollFromSource = true
        try {
          window.activate(null)
        }
        finally {
          // cleanup
          info.isVisible = false
        }
      }
    }
  }
}