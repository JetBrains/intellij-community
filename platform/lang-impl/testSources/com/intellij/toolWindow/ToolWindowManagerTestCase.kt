// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.application.EDT
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
      manager = object : ToolWindowManagerImpl(project) {
        override fun fireStateChanged(changeType: ToolWindowManagerEventType) {}
      }
      project.replaceService(ToolWindowManager::class.java, manager!!, testRootDisposable)

      val frame = withContext(Dispatchers.EDT) {
        val frame = ProjectFrameHelper(IdeFrameImpl(), null)
        frame.init()
        frame
      }

      val reopeningEditorsJob = Job().also { it.complete() }
      manager!!.doInit(frame, project.messageBus.connect(testRootDisposable), reopeningEditorsJob)
    }
  }

  public override fun tearDown() {
    try {
      manager!!.projectClosed()
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