// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.toolWindow.ToolWindowEventSource
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl

class ProblemsViewToggleCurrentFileProblemsHiddenPanelTest : LightPlatformTestCase() {
  private lateinit var toolWindow: ToolWindow

  override fun setUp() {
    super.setUp()

    toolWindow = object : ToolWindowHeadlessManagerImpl.MockToolWindow(project) {
      override fun getId(): String = ToolWindowId.PROBLEMS_VIEW
    }

    val toolWindowManager = object : ToolWindowManagerImpl(
      project,
      (project as ComponentManagerEx).instanceCoroutineScope(ProblemsViewToggleCurrentFileProblemsHiddenPanelTest::class.java)
    ) {
      override fun getToolWindow(id: String?): ToolWindow? = if (id == ToolWindowId.PROBLEMS_VIEW) toolWindow else null

      override fun activateToolWindow(id: String, runnable: Runnable?, autoFocusContents: Boolean, source: ToolWindowEventSource?) {
        runnable?.run()
      }

      override fun hideToolWindow(id: String, hideSide: Boolean, moveFocus: Boolean, removeFromStripe: Boolean, source: ToolWindowEventSource?) {
      }
    }

    project.replaceService(ToolWindowManager::class.java, toolWindowManager, testRootDisposable)
  }

  fun testToggleCurrentFileProblemsUpdatesHiddenHighlightingPanel() {
    runInEdtAndWait {
      val panel = HighlightingPanel(project, ProblemsViewState.getInstance(project))
      toolWindow.contentManager.addContent(toolWindow.contentManager.factory.createContent(panel, "Current File", false))

      ProblemsViewToolWindowUtils.selectContent(toolWindow.contentManager, HighlightingPanel.ID)
      assertFalse("Precondition: HighlightingPanel must not be showing", panel.isShowing)

      val fileA = LightVirtualFile("GoodFile.java")
      val docA = EditorFactory.getInstance().createDocument("class GoodFile {}")
      val fileB = LightVirtualFile("BadFile.java")
      val docB = EditorFactory.getInstance().createDocument("class BadFile { int x = \"Incompatible types\"; }")

      panel.setCurrentFile(fileA, docA)
      assertEquals(fileA, panel.getCurrentFile())

      ProblemsView.toggleCurrentFileProblems(project, fileB, docB)

      assertEquals(fileB, panel.getCurrentFile())
    }
  }
}
