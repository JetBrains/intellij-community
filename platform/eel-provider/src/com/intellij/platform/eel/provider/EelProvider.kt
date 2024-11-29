// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("EelProviderUtil")

package com.intellij.platform.eel.provider

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.*
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelPosixApi
import com.intellij.platform.eel.EelWindowsApi
import com.intellij.platform.eel.LocalEelApi
import com.intellij.platform.util.coroutines.forEachConcurrent
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path


interface LocalWindowsEelApi : LocalEelApi, EelWindowsApi
interface LocalPosixEelApi : LocalEelApi, EelPosixApi

private val LOG by lazy { logger<EelProvider>() }

suspend fun Path.getEelApi(): EelApi {
  val eels = EP_NAME.extensionList.mapNotNull { it.getEelApi(this) }

  if (eels.size > 1) {
    LOG.error("Multiple EEL providers found for $this: $eels")
  }

  return eels.firstOrNull() ?: localEel
}

object EelInitialization {
  suspend fun runEelInitialization(project: Project) {
    val eels = EP_NAME.extensionList
    eels.forEachConcurrent { eelProvider ->
      eelProvider.tryInitialize(project)
    }
  }
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
  if (SystemInfo.isWindows) ApplicationManager.getApplication().service<LocalWindowsEelApi>() else ApplicationManager.getApplication().service<LocalPosixEelApi>()
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

  /**
   * Runs an initialization process for [EelApi] relevant to [project] during the process of its opening.
   *
   * This function runs **early**, so implementors need to be careful with performance.
   * This function is called for every opening [Project],
   * so the implementation is expected to exit quickly if it decides that it is not responsible for [project].
   */
  suspend fun tryInitialize(project: Project)
}

private val EP_NAME = ExtensionPointName<EelProvider>("com.intellij.eelProvider")