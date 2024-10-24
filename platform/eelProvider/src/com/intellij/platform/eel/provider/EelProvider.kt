// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("EelProviderUtil")
package com.intellij.platform.eel.provider

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.LocalEelApi
import com.intellij.platform.eel.impl.local.LocalPosixEelApiImpl
import com.intellij.platform.eel.impl.local.LocalWindowsEelApiImpl
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

suspend fun Project?.getEelApi(): EelApi {
  if (this == null) return localEel
  val path = basePath ?: throw IllegalStateException("Cannot find base dir for project")
  return Path.of(path).getEelApi()
}

@RequiresBlockingContext
fun Project?.getEelApiBlocking(): EelApi {
  if (this == null) return localEel
  return runBlockingMaybeCancellable { getEelApi() }
}

fun Project?.getEelApiKey(): EelApiKey {
  if (this == null) return LocalEelKey
  val path = basePath ?: throw IllegalStateException("Cannot find base dir for project")
  return Path.of(path).getEelApiKey()
}

private val LOG by lazy { logger<EelProvider>() }

suspend fun Path.getEelApi(): EelApi {
  val eels = EP_NAME.extensionList.mapNotNull { it.getEelApi(this) }

  if (eels.size > 1) {
    LOG.error("Multiple EEL providers found for $this: $eels")
  }

  return eels.firstOrNull() ?: localEel
}

/**
 * TODO It is an obscuring API. It was created like that because it's the easiest way to implement lazy checkers.
 *
 * Returns some unique identity for Eel that corresponds to some path.
 * The only purpose of that identity is to compare it with other identities, in order to check if two paths belong to the same [EelApi].
 */
fun Path.getEelApiKey(): EelApiKey {
  val eels = EP_NAME.extensionList.mapNotNull { it.getEelApiKey(this) }

  if (eels.size > 1) {
    LOG.error("Multiple EEL providers found for $this: $eels")
  }

  return eels.firstOrNull() ?: LocalEelKey
}

val localEel: LocalEelApi by lazy {
  if (SystemInfo.isWindows) LocalWindowsEelApiImpl() else LocalPosixEelApiImpl()
}

abstract class EelApiKey {
  abstract override fun equals(other: Any?): Boolean
  abstract override fun hashCode(): Int
}

data object LocalEelKey : EelApiKey()

@RequiresBlockingContext
fun Path.getEelApiBlocking(): EelApi = runBlockingMaybeCancellable { getEelApi() }

@ApiStatus.Internal
interface EelProvider {
  suspend fun getEelApi(path: Path): EelApi?

  fun getEelApiKey(path: Path): EelApiKey?
}

private val EP_NAME = ExtensionPointName<EelProvider>("com.intellij.eelProvider")