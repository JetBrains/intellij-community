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
fun Path.getEelDescriptor(): EelDescriptor {
  val eels = EP_NAME.extensionList.mapNotNull { it.getEelDescriptor(this) }

  if (eels.size > 1) {
    LOG.error("Multiple EEL providers found for $this: $eels")
  }

  return eels.firstOrNull() ?: LocalEelDescriptor
}

val localEel: LocalEelApi by lazy {
  if (SystemInfo.isWindows) ApplicationManager.getApplication().service<LocalWindowsEelApi>() else ApplicationManager.getApplication().service<LocalPosixEelApi>()
}

/**
 * A descriptor of an environment where [EelApi] may exist.
 *
 * ## Examples
 * 1. There is a singleton [LocalEelDescriptor] which always exists, and it denotes the environment where the IDE runs
 * 2. On Windows, there can be [EelDescriptor] that corresponds to a WSL distribution.
 * Each distribution gives rise to a unique [EelDescriptor]
 * 3. Each separate Docker container has its own [EelDescriptor]
 * 4. Each SSH host has its own [EelDescriptor]
 *
 * ## Purpose
 * [EelDescriptor] is a marker of an environment, that is
 * - **Lightweight**: it is opposed to [EelApi], which is a heavy object that takes considerable amount of resources to initialize.
 * While it is not free to obtain [EelDescriptor] (i.e., you may need to interact with WSL services and Docker daemon), it is much cheaper than
 * preparing an environment for deep interaction (i.e., running a WSL Distribution or a Docker container).
 * - **Durable**: There is no guarantee that an instance of [EelApi] would be alive for a long time.
 * For example, an SSH connection can be interrupted, and a Docker container can be restarted. These events do not affect the lifetime of [EelDescriptor].
 *
 * ## Usage
 * You are free to compare and store [EelDescriptor].
 * TODO: In the future, [EelDescriptor] may also be serializable.
 * If you need to access the remote environment, you can use the method [upgrade], which can suspend for some time before returning a working instance of [EelApi]
 */
interface EelDescriptor {

  /**
   * Retrieves an instance of [EelApi] corresponding to this [EelDescriptor].
   * This method may run a container, so it could suspend for a long time.
   */
  suspend fun upgrade(): EelApi
}

data object LocalEelDescriptor : EelDescriptor {
  override suspend fun upgrade(): EelApi {
    return localEel
  }
}

@RequiresBlockingContext
fun Path.getEelApiBlocking(): EelApi = runBlockingMaybeCancellable { getEelApi() }

@ApiStatus.Internal
interface EelProvider {
  suspend fun getEelApi(path: Path): EelApi?

  fun getEelDescriptor(path: Path): EelDescriptor?

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