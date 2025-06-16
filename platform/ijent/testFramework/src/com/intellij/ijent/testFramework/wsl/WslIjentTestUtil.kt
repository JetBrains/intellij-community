// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("WslIjentTestUtil")

package com.intellij.ijent.testFramework.wsl

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslIjentManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.util.Disposer
import com.intellij.platform.eel.impl.provider.EelNioBridgeServiceImpl
import com.intellij.platform.eel.provider.EelInitialization
import com.intellij.platform.eel.provider.EelNioBridgeService
import com.intellij.platform.eel.provider.EelProvider
import com.intellij.platform.ide.impl.wsl.ProductionWslIjentManager
import com.intellij.platform.ide.impl.wsl.WslEelProvider
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.registerExtension
import com.intellij.testFramework.replaceService
import com.intellij.util.asDisposable
import kotlinx.coroutines.*
import java.util.concurrent.CancellationException

fun replaceProductionWslIjentManager(disposable: Disposable) {
  replaceService(WslIjentManager::class.java, ::ProductionWslIjentManager, disposable)
}

fun replaceProductionWslIjentManager(newServiceScope: CoroutineScope) {
  replaceService(WslIjentManager::class.java, ::ProductionWslIjentManager, newServiceScope)
}

suspend fun replaceWslServicesAndRunWslEelInitialization(newServiceScope: CoroutineScope, wsl: WSLDistribution) {
  replaceProductionWslIjentManager(newServiceScope)
  replaceService(EelNioBridgeService::class.java, ::EelNioBridgeServiceImpl, newServiceScope)
  replaceExtension(newServiceScope, EelProvider.EP_NAME, WslEelProvider(newServiceScope))
  EelInitialization.runEelInitialization(wsl.getUNCRootPath().toString())
}

private fun <T : Any> replaceExtension(scope: CoroutineScope, name: BaseExtensionPointName<*>, instance: T) {
  ApplicationManager.getApplication().apply {
    extensionArea.getExtensionPoint<T>(name.name).unregisterExtension(instance.javaClass)
    registerExtension(name, instance, scope.asDisposable())
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