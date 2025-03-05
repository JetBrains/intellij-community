// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry.Companion.intValue
import com.intellij.util.concurrency.AppExecutorUtil
import com.jetbrains.jdi.VirtualMachineImpl
import java.util.concurrent.TimeUnit

/**
 * The commands inheriting this class are known to be synchronous, so they can prevent
 * other asynchronous commands to be executed efficiently.
 * To mitigate such cases, this command is trying to postpone until other async commands are complete.
 */
abstract class PossiblySyncCommand protected constructor(suspendContext: SuspendContextImpl?) : SuspendContextCommandImpl(suspendContext) {
  private var myRetries = intValue("debugger.sync.commands.max.retries")

  @Throws(Exception::class)
  final override fun contextAction(suspendContext: SuspendContextImpl) {
    if (shouldBeRescheduled(suspendContext)) return
    syncAction(suspendContext)
  }

  abstract fun syncAction(suspendContext: SuspendContextImpl)

  private fun shouldBeRescheduled(suspendContext: SuspendContextImpl): Boolean {
    if (ApplicationManager.getApplication().isUnitTestMode()) return false

    val delay = intValue("debugger.sync.commands.reschedule.delay")
    if (delay < 0 || myRetries-- <= 0) return false

    val managerThread = suspendContext.managerThread
    val virtualMachine = suspendContext.virtualMachineProxy.virtualMachine
    if (virtualMachine !is VirtualMachineImpl || !managerThread.hasAsyncCommands() && virtualMachine.isIdle) return false

    hold()
    // reschedule with a small delay
    AppExecutorUtil.getAppScheduledExecutorService().schedule({ managerThread.schedule(this) }, delay.toLong(), TimeUnit.MILLISECONDS)
    return true
  }
}
