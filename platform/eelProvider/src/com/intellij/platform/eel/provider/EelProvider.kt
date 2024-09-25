// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.impl.local.LocalPosixEelApiImpl
import com.intellij.platform.eel.impl.local.LocalWindowsEelApiImpl
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

suspend fun Project.getEelApi(): EelApi {
  val path = basePath ?: throw IllegalStateException("Cannot find base dir for project")
  return Path.of(path).getEelApi()
}

fun Project.getEelApiBlocking() = runBlockingMaybeCancellable { getEelApi() }

private val LOG by lazy { logger<EelProvider>() }

suspend fun Path.getEelApi(): EelApi {
  val eels = EP_NAME.extensionList.mapNotNull { it.getEelApi(this) }

  if (eels.size > 1) {
    LOG.error("Multiple EEL providers found for $this: $eels")
  }

  return eels.firstOrNull() ?: if (SystemInfo.isWindows) LocalWindowsEelApiImpl() else LocalPosixEelApiImpl()
}

fun Path.getEelApiBlocking() = runBlockingMaybeCancellable { getEelApi() }

@ApiStatus.Internal
interface EelProvider {
  suspend fun getEelApi(path: Path): EelApi?
}

private val EP_NAME = ExtensionPointName<EelProvider>("com.intellij.eelProvider")