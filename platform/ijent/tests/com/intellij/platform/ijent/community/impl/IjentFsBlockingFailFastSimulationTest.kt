// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.ijent.community.impl.nio.fsBlocking
import com.intellij.platform.ijent.community.impl.nio.fsBlockingWithoutParallelismCompensation
import com.intellij.platform.ijent.fs.IjentFileSystemPosixApi
import com.intellij.testFramework.junit5.TestApplication
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import javax.swing.SwingUtilities

/**
 * End-to-end simulation of the IJPL-245001 scenario without a real SSH machine:
 * a thread enters the real synchronous nio bridge ([fsBlockingWithoutParallelismCompensation],
 * which computes the caller context from the live application and installs the marker), the file
 * system operation goes through the real fail-safe wrapper, and reaching a not-yet-deployed
 * interactive environment must produce an exception instead of blocking the thread on a deployment
 * that would need user interaction.
 *
 * The deployment itself is the only simulated part: in this environment an actually started
 * deployment deterministically fails during machine resolution, so "the deployment was attempted"
 * and "the bridge failed fast" are distinguished by the IJPL-245001 message.
 *
 * The freeze itself is deliberately NOT reproduced here: doing so would require a deployment that
 * calls invokeAndWait while the EDT is blocked inside the bridge, and since the bridge has no
 * timeout, a regression would hang the whole suite instead of failing an assertion. These tests
 * only pin that the guard fires before the bridge starts waiting for a deployment; the freeze
 * evidence is the thread dump and the reproducers in IJPL-245001.
 */
@TestApplication
class IjentFsBlockingFailFastSimulationTest {
  private val scope = CoroutineScope(SupervisorJob())

  private val descriptor = object : EelDescriptor {
    override val name: String = "IjentFsBlockingFailFastSimulationTest descriptor"
    override val osFamily: EelOsFamily = EelOsFamily.Posix
  }

  @AfterEach
  fun tearDown() {
    scope.cancel()
  }

  private fun interactiveFs(initialized: Boolean): IjentFileSystemPosixApi =
    ijentFailSafeFileSystemApi(
      scope,
      descriptor,
      checkIsIjentInitialized = { initialized },
      deploymentMayRequireUserInteraction = true,
    ) as IjentFileSystemPosixApi

  private fun listRootThroughFsBlocking(fs: IjentFileSystemPosixApi) {
    fs.fsBlocking {
      fs.listDirectory(EelPath.parse("/", descriptor))
    }
  }

  @Test
  fun `fs operation on EDT fails fast before the deployment is awaited`() {
    lateinit var err: IllegalStateException
    SwingUtilities.invokeAndWait {
      err = shouldThrow<IllegalStateException> {
        listRootThroughFsBlocking(interactiveFs(initialized = false))
      }
    }
    err.message shouldContain "IJPL-245001"
  }

  @Test
  fun `fs operation on a background thread fails fast as well`() {
    val err = shouldThrow<IllegalStateException> {
      listRootThroughFsBlocking(interactiveFs(initialized = false))
    }
    err.message shouldContain "IJPL-245001"
  }

  @Test
  fun `fs operation on EDT against an initialized environment is not blocked`() {
    lateinit var err: Throwable
    SwingUtilities.invokeAndWait {
      err = shouldThrowAny {
        listRootThroughFsBlocking(interactiveFs(initialized = true))
      }
    }
    (err.message ?: "") shouldNotContain "IJPL-245001"
  }
}
