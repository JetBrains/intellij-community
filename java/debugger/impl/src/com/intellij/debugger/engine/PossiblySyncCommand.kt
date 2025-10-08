// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.jdi.VirtualMachineImpl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The commands inheriting this class are known to be synchronous, so they can prevent
 * other asynchronous commands to be executed efficiently.
 * To mitigate such cases, this command is trying to postpone until other async commands are complete.
 */
abstract class PossiblySyncCommand protected constructor(suspendContext: SuspendContextImpl?) : SuspendContextCommandImpl(suspendContext) {
  private var retries = Registry.intValue("debugger.sync.commands.max.retries")
  private val delay get() = Registry.intValue("debugger.sync.commands.reschedule.delay")

  @Throws(Exception::class)
  final override fun contextAction(suspendContext: SuspendContextImpl) {
    if (shouldBeRescheduled(suspendContext)) {
      hold()
      // reschedule with a small delay
      suspendContext.managerThread.coroutineScope.launch {
        delay(delay.toLong())
        suspendContext.managerThread.schedule(this@PossiblySyncCommand)
      }
    }
    else {
      syncAction(suspendContext)
    }
  }

  abstract fun syncAction(suspendContext: SuspendContextImpl)

  private fun shouldBeRescheduled(suspendContext: SuspendContextImpl): Boolean {
    if (ApplicationManager.getApplication().isUnitTestMode()) return false
    if (delay < 0 || retries-- <= 0) return false

    val virtualMachine = suspendContext.virtualMachineProxy.virtualMachine
    if (virtualMachine !is VirtualMachineImpl) return false

    val managerThread = suspendContext.managerThread
    return managerThread.hasAsyncCommands() || !virtualMachine.isIdle || managerThread.hasDispatchedCommands()
  }
}
