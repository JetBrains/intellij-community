// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IjentNioFileSystemUtil")

package com.intellij.platform.ijent.community.impl.nio

import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.EelFsError
import com.intellij.platform.eel.fs.EelOpenedFile
import com.intellij.platform.eel.path.EelPath
import com.intellij.util.text.nullize
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.nio.file.*

internal fun Path.toEelPath(): EelPath =
  when {
    this is AbsoluteIjentNioPath -> eelPath
    else -> throw IllegalArgumentException("$this is not absolute IjentNioPath")
  }

/**
 * We need to use a plain `runBlocking` here.
 * The IO call is supposed to be fast (several milliseconds in the worst case),
 * so the cost of spawning and destroying an additional thread in Dispatchers.Default would be too big.
 * Also, IJent does not require any outer lock in its implementation, so a deadlock is not possible.
 *
 * In addition, we suppress work stealing in this `runBlocking`, as it should return as fast as it can on its own.
 */
@Suppress("SSBasedInspection")
internal fun <T> fsBlocking(body: suspend () -> T): T {
  return runBlocking(NestedBlockingEventLoop(Thread.currentThread())) {
    body()
  }
}

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "CANNOT_OVERRIDE_INVISIBLE_MEMBER", "ERROR_SUPPRESSION")
private class NestedBlockingEventLoop(override val thread: Thread) : kotlinx.coroutines.EventLoopImplBase() {
  override fun shouldBeProcessedFromContext(): Boolean = true
}