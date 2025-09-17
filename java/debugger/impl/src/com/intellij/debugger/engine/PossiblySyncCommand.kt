// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.impl.PrioritizedTask
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

  @Throws(Exception::class)
  final override fun contextAction(suspendContext: SuspendContextImpl) {
    if (shouldBeRescheduled(suspendContext)) return
    syncAction(suspendContext)
  }

  abstract fun syncAction(suspendContext: SuspendContextImpl)

  final override val priority: PrioritizedTask.Priority
    get() = PrioritizedTask.Priority.LOWEST

  private fun shouldBeRescheduled(suspendContext: SuspendContextImpl): Boolean {
    if (ApplicationManager.getApplication().isUnitTestMode()) return false

    val delay = Registry.intValue("debugger.sync.commands.reschedule.delay")
    if (delay < 0 || retries-- <= 0) return false

    val managerThread = suspendContext.managerThread
    // Let the dispatched commands with a possibly higher priority to run first
    if (managerThread.hasDispatchedCommands()) return true
    val virtualMachine = suspendContext.virtualMachineProxy.virtualMachine
    if (virtualMachine !is VirtualMachineImpl || !managerThread.hasAsyncCommands() && virtualMachine.isIdle) return false

    hold()
    // reschedule with a small delay
    managerThread.coroutineScope.launch {
      delay(delay.toLong())
      managerThread.schedule(this@PossiblySyncCommand)
    }
    return true
  }
}
