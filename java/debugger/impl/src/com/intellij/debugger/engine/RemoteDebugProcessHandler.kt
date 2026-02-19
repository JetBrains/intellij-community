// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.DebuggerManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.project.Project
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

open class RemoteDebugProcessHandler @JvmOverloads constructor(
  private val myProject: Project,
  private val myAutoRestart: Boolean = false,
) : ProcessHandler() {
  private val myClosedByUser = AtomicBoolean()

  override fun startNotify() {
    val listener: DebugProcessListener = object : DebugProcessAdapterImpl() {
      //executed in manager thread
      override fun processDetached(process: DebugProcessImpl, closedByUser: Boolean) {
        if (!myAutoRestart || closedByUser || myClosedByUser.get()) {
          process.removeDebugProcessListener(this)
          notifyProcessDetached()
        }
        else {
          process.reattach(process.session.debugEnvironment)
        }
      }
    }
    val debugProcess = DebuggerManager.getInstance(myProject).getDebugProcess(this)
    debugProcess.addDebugProcessListener(listener)
    try {
      super.startNotify()
    }
    finally {
      // in case we added our listener too late, we may have lost processDetached notification,
      // so check here if process is detached
      if (debugProcess.isDetached()) {
        debugProcess.removeDebugProcessListener(listener)
        notifyProcessDetached()
      }
    }
  }

  override fun destroyProcessImpl() {
    myClosedByUser.set(true)
    val debugProcess = DebuggerManager.getInstance(myProject).getDebugProcess(this)
    if (debugProcess != null) {
      debugProcess.stop(true)
    }
  }

  override fun detachProcessImpl() {
    myClosedByUser.set(true)
    val debugProcess = DebuggerManager.getInstance(myProject).getDebugProcess(this)
    if (debugProcess != null) {
      debugProcess.stop(false)
    }
  }

  override fun detachIsDefault(): Boolean = true

  override fun getProcessInput(): OutputStream? = null
}
