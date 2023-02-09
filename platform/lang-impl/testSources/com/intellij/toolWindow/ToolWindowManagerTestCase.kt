// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener.ToolWindowManagerEventType
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.openapi.wm.impl.ProjectFrameHelper
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.SkipInHeadlessEnvironment
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@SkipInHeadlessEnvironment
abstract class ToolWindowManagerTestCase : LightPlatformTestCase() {
  @JvmField
  protected var manager: ToolWindowManagerImpl? = null

  final override fun runInDispatchThread() = false

  public override fun setUp() {
    super.setUp()

    runBlocking {
      val project = project
      manager = object : ToolWindowManagerImpl(coroutineScope = project.coroutineScope, project = project) {
        override fun fireStateChanged(changeType: ToolWindowManagerEventType, toolWindow: ToolWindow?) {}
      }
      project.replaceService(ToolWindowManager::class.java, manager!!, testRootDisposable)

      val frame = withContext(Dispatchers.EDT) {
        val frame = ProjectFrameHelper(IdeFrameImpl())
        frame.init()
        frame
      }

      val reopeningEditorJob = Job().also { it.complete() }
      manager!!.doInit(frame, project.messageBus.connect(testRootDisposable), reopeningEditorJob, taskListDeferred = null)
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