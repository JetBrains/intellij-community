// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("EelProviderUtil")

package com.intellij.platform.eel.provider

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.LocalEelApi
import com.intellij.platform.eel.impl.local.LocalPosixEelApiImpl
import com.intellij.platform.eel.impl.local.LocalWindowsEelApiImpl
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.Path

private val CannotGuessProjectDirLoggingKey = Key.create<MutableSet<String>>("Eel.CannotGuessProjectDirLoggingKey")

private fun Project.guessProjectDirAndLogWarn(callLocation: String): VirtualFile? {
  val dirResult = runCatching { guessProjectDir() }
  val dir = dirResult.getOrNull()

  if (dir == null) {
    val shouldLog = (if (this is UserDataHolderEx) {
      getOrCreateUserData(CannotGuessProjectDirLoggingKey) { mutableSetOf<String>() }
    }
    else getOrCreateUserDataUnsafe(CannotGuessProjectDirLoggingKey) { mutableSetOf<String>() }).add(callLocation)

    if (shouldLog) {
      LOG.warn("$callLocation: Cannot guess project dir for $this", dirResult.exceptionOrNull())
    }
  }

  return dir
}

private fun Project?.computeProjectPath(callLocation: String): Path? {
  if (this == null || this.isDefault) return null

  val projectDir = guessProjectDirAndLogWarn(callLocation)
  val basePath = basePath?.let(::Path)

  return try {
    projectDir?.toNioPath() ?:basePath
  }
  catch (e: UnsupportedOperationException) {
    basePath
  }
}

suspend fun Project?.getEelApi(): EelApi {
  return computeProjectPath("Project?.getEelApi")?.getEelApi() ?: localEel
}

@RequiresBlockingContext
fun Project?.getEelApiBlocking(): EelApi {
  if (this == null) return localEel
  return runBlockingMaybeCancellable { getEelApi() }
}

fun Project?.getEelApiKey(): EelApiKey {
  return computeProjectPath("Project?.getEelApiKey")?.getEelApiKey() ?: LocalEelKey
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