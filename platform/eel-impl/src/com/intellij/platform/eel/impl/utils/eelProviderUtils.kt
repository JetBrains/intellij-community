// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.utils

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.openapi.util.getOrCreateUserDataUnsafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.*
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
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
    projectDir?.toNioPath() ?: basePath
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

fun Project?.getEelDescriptor(): EelDescriptor {
  return computeProjectPath("Project?.getEelDescriptor")?.getEelDescriptor() ?: LocalEelDescriptor
}

private val LOG by lazy { logger<EelProvider>() }