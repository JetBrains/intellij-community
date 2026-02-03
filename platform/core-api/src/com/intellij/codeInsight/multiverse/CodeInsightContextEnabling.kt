// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("CodeInsightContextEnabling")

package com.intellij.codeInsight.multiverse

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.containers.CollectionFactory
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

internal fun isSharedSourceSupportEnabledImpl(project: Project): Boolean {
  project.getUserData(multiverse_enabler_key)?.let { cachedValue ->
    return cachedValue
  }

  val lock = multiverseLockMap.computeIfAbsent(project) { Any() }
  synchronized(lock) {
    project.getUserData(multiverse_enabler_key)?.let { cachedValue ->
      return cachedValue
    }

    val result = computeSharedSourceEnabled(project)
    project.putUserData(multiverse_enabler_key, result)
    if (logMultiverseState) {
      log.info("multiverse is ${if (result) "enabled" else "disabled"}")
    }
    return result
  }
}

private val multiverseLockMap = CollectionFactory.createConcurrentWeakMap<Project, Any>()

@TestOnly
@ApiStatus.Internal
object MultiverseTestEnabler {
  private val value = AtomicBoolean()

  fun enableSharedSourcesForTheNextProject() {
    val prev = value.getAndSet(true)
    if (prev) {
      throw IllegalStateException("multiverse is already enabled")
    }
  }

  internal fun getValueAndErase(): Boolean {
    val prev = value.getAndSet(false)
    return prev
  }
}

private fun computeSharedSourceEnabled(project: Project): Boolean {
  @Suppress("TestOnlyProblems")
  if (ApplicationManager.getApplication().isUnitTestMode && MultiverseTestEnabler.getValueAndErase()) {
    return true
  }

  val result = MULTIVERSE_ENABLER_EP_NAME.extensionList.any { enabler ->
    runSafely { enabler.enableMultiverse(project) } == true
  }
  return result
}

private inline fun <T : Any> runSafely(block: () -> T): T? {
  try {
    return block()
  }
  catch (e: Throwable) {
    if (e is CancellationException) throw e
    log.error(e)
    return null
  }
}

private val multiverse_enabler_key = Key.create<Boolean>("shared.source.support.enabled")

private val MULTIVERSE_ENABLER_EP_NAME: ExtensionPointName<MultiverseEnabler> = ExtensionPointName.create("com.intellij.multiverseEnabler")

/**
 * LSP-202
 */
@ApiStatus.Internal
var logMultiverseState: Boolean = true

private val log = fileLogger()
