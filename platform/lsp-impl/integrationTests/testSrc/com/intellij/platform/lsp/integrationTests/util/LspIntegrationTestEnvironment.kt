// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.integrationTests.util

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.tools.ide.starter.product.idea.community.IdeaCommunity
import java.util.ServiceLoader

/**
 * Where the tests of this module run: which product they start, and how a process started by the IDE reaches the test process.
 *
 * Without an implementation on the classpath the tests start IDEA Community on the machine of the test process.
 * A module that runs the same tests in another product, or with the IDE in a container, depends on this module and registers
 * its implementation in `META-INF/services`. At most one implementation can be on the classpath.
 */
interface LspIntegrationTestEnvironment {
  /** The product that the tests start. The tests add their test-only plugin to it. */
  val ide: IdeInfo

  /** Settings of the IDE that the product needs. Runs once, when the test context is prepared. */
  fun configureIde(context: IDETestContext) {}

  /**
   * Makes [port] of the loopback interface of the test machine reachable for a process on the machine of the IDE.
   * Returns `null` when the IDE runs on the test machine, so the loopback address works as it is.
   *
   * Called before the IDE starts.
   */
  fun exposeTestProcessPort(port: Int): ExposedPort?

  /** [hostName] is what a process on the machine of the IDE connects to. [close] ends the forwarding. */
  class ExposedPort(val hostName: String, private val forwarding: AutoCloseable) : AutoCloseable {
    override fun close() {
      forwarding.close()
    }
  }

  companion object {
    val current: LspIntegrationTestEnvironment by lazy {
      val implementations = ServiceLoader.load(LspIntegrationTestEnvironment::class.java).toList()
      check(implementations.size <= 1) { "More than one LspIntegrationTestEnvironment is on the classpath: $implementations" }
      implementations.firstOrNull() ?: IdeaCommunityOnTestMachine
    }
  }
}

private object IdeaCommunityOnTestMachine : LspIntegrationTestEnvironment {
  override val ide: IdeInfo get() = IdeInfo.IdeaCommunity

  override fun exposeTestProcessPort(port: Int): LspIntegrationTestEnvironment.ExposedPort? = null
}
