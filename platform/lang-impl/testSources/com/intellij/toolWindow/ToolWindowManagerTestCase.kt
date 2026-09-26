// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("RAW_RUN_BLOCKING")

package com.intellij.toolWindow

import com.intellij.ide.ui.LafManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
import com.intellij.openapi.wm.ex.ToolWindowManagerListener.ToolWindowManagerEventType
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.SkipInHeadlessEnvironment
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking

@SkipInHeadlessEnvironment
abstract class ToolWindowManagerTestCase : LightPlatformTestCase() {
  @JvmField
  protected var manager: ToolWindowManagerImpl? = null

  final override fun runInDispatchThread() = false

  public override fun setUp() {
    super.setUp()

    runInEdtAndWait {
      LafManager.getInstance() // Maybe we need this in a superclass, but for now only tool window manager tests break if it's not available.
    }

    runBlocking {
      val project = project
      manager = object : ToolWindowManagerImpl(coroutineScope = (project as ComponentManagerEx).getCoroutineScope(), project = project) {
        override fun fireStateChanged(changeType: ToolWindowManagerEventType, toolWindow: ToolWindow?) {}
      }
      project.replaceService(ToolWindowManager::class.java, manager!!, testRootDisposable)

      val pane = ToolWindowPane.create(IdeFrameImpl(), WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID)

      val reopeningEditorJob = Job().also { it.complete() }
      manager!!.doInit(pane,
                       project.messageBus.connect(testRootDisposable),
                       reopeningEditorJob,
                       taskListDeferred = null)
    }
  }

  public override fun tearDown() {
    try {
      manager?.let { Disposer.dispose(it) }
      manager = null
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }
}