// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("RAW_RUN_BLOCKING")

package com.intellij.platform.eel.impl.fs

import com.intellij.platform.eel.EelSharedSecrets
import com.intellij.platform.eel.impl.fs.EelFilesAccessorImpl.Companion.shouldInvokeOriginal
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.toEelApi
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

internal class EelPlatformUtilAccessorImpl : EelSharedSecrets.PlatformUtilAccessor {
  private val fallback by lazy {
    EelSharedSecrets.platformUtilAccessors().filter { it.priority < priority }.maxBy { it.priority }
  }

  override val priority: Int = 1000

  override fun deleteRecursively(fileOrDirectory: Path) {
    if (shouldInvokeOriginal(fileOrDirectory)) {
      fallback.deleteRecursively(fileOrDirectory)
      return
    }

    val eelPath = fileOrDirectory.asEelPath()
    runBlocking {
      eelPath.descriptor.toEelApi().fs.delete(eelPath, removeContent = true)
    }
  }
}