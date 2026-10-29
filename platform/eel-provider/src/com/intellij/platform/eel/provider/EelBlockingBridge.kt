// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.util.annotations.VisibleToClasses
import org.jetbrains.annotations.ApiStatus
import java.util.ServiceLoader

/**
 * Runs a suspending EEL call from blocking code with the bridge of the remote file system implementation,
 * `fsBlocking` in `intellij.platform.ijent.community.impl`. That bridge serves a write action and the EDT, fails a
 * deployment started from inside it fast, and shows the dialog when the environment stops answering.
 *
 * The implementation is plugged in with [ServiceLoader]. A product without a remote file system has none.
 */
@ApiStatus.Internal
@VisibleToClasses(
  "com.intellij.platform.ijent.community.impl.nio.FsBlockingEelBlockingBridge",
)
interface EelBlockingBridge {
  fun <T> run(descriptor: EelDescriptor, body: suspend () -> T): T

  companion object {
    private val cached: EelBlockingBridge? by lazy {
      ServiceLoader.load(EelBlockingBridge::class.java, EelBlockingBridge::class.java.classLoader).firstOrNull()
    }

    @JvmStatic
    fun getInstanceOrNull(): EelBlockingBridge? = cached
  }
}
