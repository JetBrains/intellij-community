// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("WslIjentTestUtil")
package com.intellij.ijent.testFramework.wsl

import com.intellij.execution.wsl.ProductionWslIjentManager
import com.intellij.execution.wsl.WslIjentManager
import com.intellij.execution.wsl.ijent.nio.toggle.IjentWslNioFsToggler
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.platform.eel.impl.provider.EelNioBridgeServiceImpl
import com.intellij.platform.eel.provider.EelNioBridgeService
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import java.util.concurrent.CancellationException

fun replaceProductionWslIjentManager(disposable: Disposable) {
  replaceService(WslIjentManager::class.java, ::ProductionWslIjentManager, disposable)
}

fun replaceProductionWslIjentManager(newServiceScope: CoroutineScope) {
  replaceService(WslIjentManager::class.java, ::ProductionWslIjentManager, newServiceScope)
}

fun replaceIjentWslNioFsToggler(newServiceScope: CoroutineScope) {
  replaceService(IjentWslNioFsToggler::class.java, ::IjentWslNioFsToggler, newServiceScope)
}

fun temporarilyResetEelNioBridge(serviceScope: CoroutineScope) {
  val guard = (EelNioBridgeService.getInstanceSync() as EelNioBridgeServiceImpl).temporarilyResetState()
  serviceScope.coroutineContext.job.invokeOnCompletion {
    guard.close()
  }
}

private fun <T : Any> replaceService(iface: Class<T>, constructor: (CoroutineScope) -> T, newServiceScope: CoroutineScope) {
  val disposable = Disposer.newDisposable(newServiceScope.toString())
  newServiceScope.coroutineContext.job.invokeOnCompletion {
    Disposer.dispose(disposable)
  }
  ApplicationManager.getApplication().replaceService(
    iface,
    constructor(newServiceScope),
    disposable,
  )
}

@OptIn(DelicateCoroutinesApi::class)
private fun <T : Any> replaceService(iface: Class<T>, constructor: (CoroutineScope) -> T, disposable: Disposable) {
  val newServiceScope = GlobalScope.childScope("Disposable $disposable", supervisor = true)
  Disposer.register(disposable) {
    newServiceScope.cancel(CancellationException("Disposed $disposable"))
  }
  ApplicationManager.getApplication().replaceService(
    iface,
    constructor(newServiceScope),
    disposable,
  )
}