// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("WslIjentTestUtil")

package com.intellij.ijent.testFramework.wsl

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslIjentManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Disposer
import com.intellij.platform.eel.provider.EelInitialization
import com.intellij.platform.eel.provider.EelProvider
import com.intellij.platform.eel.provider.MultiRoutingFileSystemBackend
import com.intellij.platform.ide.impl.wsl.EelWslMrfsBackend
import com.intellij.platform.ide.impl.wsl.ProductionWslIjentManager
import com.intellij.platform.ide.impl.wsl.WslEelProvider
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.replaceService
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import java.util.concurrent.CancellationException

fun replaceProductionWslIjentManager(disposable: Disposable) {
  replaceService(WslIjentManager::class.java, ::ProductionWslIjentManager, disposable)
}

fun replaceProductionWslIjentManager(newServiceScope: CoroutineScope) {
  replaceService(WslIjentManager::class.java, ::ProductionWslIjentManager, newServiceScope)
}

suspend fun replaceWslServicesAndRunWslEelInitialization(newServiceScope: CoroutineScope, wsl: WSLDistribution) {
  replaceProductionWslIjentManager(newServiceScope)
  replaceExtension(newServiceScope, MultiRoutingFileSystemBackend.EP_NAME, EelWslMrfsBackend(newServiceScope))
  replaceExtension(newServiceScope, EelProvider.EP_NAME, WslEelProvider())
  EelInitialization.runEelInitialization(wsl.getUNCRootPath().toString())
}

private fun <T : Any> replaceExtension(scope: CoroutineScope, name: BaseExtensionPointName<*>, instance: T) {
  ApplicationManager.getApplication().apply {
    val epName = ExtensionPointName.create<T>(name.name)
    ExtensionTestUtil.maskExtensions(
      epName,
      listOf(instance),
      scope.asDisposable()
    )
  }
}

private fun <T : Any> replaceService(iface: Class<T>, constructor: (CoroutineScope) -> T, newServiceScope: CoroutineScope) {
  ApplicationManager.getApplication().replaceService(
    iface,
    constructor(newServiceScope),
    newServiceScope.asDisposable(),
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