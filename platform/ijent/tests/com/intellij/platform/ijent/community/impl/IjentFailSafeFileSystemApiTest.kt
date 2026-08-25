// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.ijent.IjentCalledContextElement
import com.intellij.platform.ijent.IjentCallerContext
import com.intellij.platform.ijent.fs.IjentFileSystemApi
import com.intellij.platform.ijent.fs.IjentFileSystemPosixApi
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * Tests for the fail-fast gating in [ijentFailSafeFileSystemApi] (IJPL-245001).
 *
 * The delegate deploys in a detached scope, so the check runs on the awaiting side and must:
 * - throw only for an uninitialized environment awaited from inside fsBlocking (marker present),
 * - never throw for an initialized environment or for callers without the marker,
 * - never throw when [ijentFailSafeFileSystemApi] is created without `deploymentMayRequireUserInteraction`,
 * - leave the lazy delegate usable for later callers after a fail-fast.
 *
 * A deployment that actually starts deterministically fails in the unit test environment (there is
 * no application, so machine resolution throws). The tests distinguish "the check threw before the
 * deployment" from "the deployment was attempted" by whether the failure is the IJPL-245001 fail-fast.
 */
class IjentFailSafeFileSystemApiTest {
  private val scope = CoroutineScope(SupervisorJob())

  private val descriptor = object : EelDescriptor {
    override val name: String = "IjentFailSafeFileSystemApiTest descriptor"
    override val osFamily: EelOsFamily = EelOsFamily.Posix
  }

  @AfterEach
  fun tearDown() {
    scope.cancel()
  }

  private fun fsBlockingMarker(): IjentCalledContextElement =
    IjentCalledContextElement(IjentCallerContext(isRead = false, isWrite = false, isDispatchThread = true))

  private suspend fun IjentFileSystemApi.touch() {
    (this as IjentFileSystemPosixApi).listDirectory(EelPath.parse("/", descriptor))
  }

  @Test
  fun `uninitialized interactive environment fails fast inside fsBlocking`(): Unit = runBlocking {
    val fs = ijentFailSafeFileSystemApi(scope, descriptor, checkIsIjentInitialized = { false }, deploymentMayRequireUserInteraction = true)
    withContext(fsBlockingMarker()) {
      val err = shouldThrow<IllegalStateException> { fs.touch() }
      err.message shouldContain "IJPL-245001"
    }
  }

  @Test
  fun `initialized interactive environment is awaited despite the marker`(): Unit = runBlocking {
    val fs = ijentFailSafeFileSystemApi(scope, descriptor, checkIsIjentInitialized = { true }, deploymentMayRequireUserInteraction = true)
    withContext(fsBlockingMarker()) {
      val err = shouldThrowAny { fs.touch() }
      (err.message ?: "") shouldNotContain "IJPL-245001"
    }
  }

  @Test
  fun `uninitialized interactive environment deploys outside of fsBlocking`(): Unit = runBlocking {
    val fs = ijentFailSafeFileSystemApi(scope, descriptor, checkIsIjentInitialized = { false }, deploymentMayRequireUserInteraction = true)
    val err = shouldThrowAny { fs.touch() }
    (err.message ?: "") shouldNotContain "IJPL-245001"
  }

  @Test
  fun `non-interactive environment ignores the marker`(): Unit = runBlocking {
    val fs = ijentFailSafeFileSystemApi(scope, descriptor, checkIsIjentInitialized = null)
    withContext(fsBlockingMarker()) {
      val err = shouldThrowAny { fs.touch() }
      (err.message ?: "") shouldNotContain "IJPL-245001"
    }
  }

  @Test
  fun `delegate stays usable for a safe caller after a fail-fast`(): Unit = runBlocking {
    val fs = ijentFailSafeFileSystemApi(scope, descriptor, checkIsIjentInitialized = { false }, deploymentMayRequireUserInteraction = true)
    withContext(fsBlockingMarker()) {
      shouldThrow<IllegalStateException> { fs.touch() }.message shouldContain "IJPL-245001"
    }
    val err = shouldThrowAny { fs.touch() }
    (err.message ?: "") shouldNotContain "IJPL-245001"
  }
}
