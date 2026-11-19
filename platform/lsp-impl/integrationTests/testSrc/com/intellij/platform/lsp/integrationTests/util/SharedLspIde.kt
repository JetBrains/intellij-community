// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.integrationTests.util

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.waitForProjectOpen
import com.intellij.ide.starter.coroutine.CommonScope
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.runner.Starter
import com.intellij.ide.starter.testContextCache.newTestContextFromCache
import com.intellij.platform.lsp.integrationTests.fakeServer.FakeLspIntegration
import com.intellij.platform.lsp.integrationTests.fakeServer.FakeLspServerHost
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import kotlin.time.Duration.Companion.minutes

/**
 * Gives every test class of this module one IDE with the test project open: the product of [LspIntegrationTestEnvironment]
 * with the test-only plugin. A test takes a [FakeLspIntegration] parameter to run against the fake LSP server,
 * or a [Driver] parameter for the IDE itself.
 *
 * The IDE starts before the first test of the run and closes after the last one. The project stays open the whole time.
 * Before each next test the extension closes the popups and the editors of the test before, and stops the LSP servers,
 * so a test gets a fresh server.
 *
 * `@ReusedRun` is not used, because it starts an IDE for each class and opens the project again before each test.
 *
 * Nothing restores the project files between tests. A test creates the files it needs in a directory of its own.
 */
internal class SharedLspIde : BeforeAllCallback, BeforeEachCallback, ParameterResolver {
  /**
   * Creates the host of the fake LSP server before the first test, and not inside it. The test framework reports the threads
   * that a test leaves behind, and a port forwarding of the host into a container keeps a thread until the run ends.
   * The IDE itself can't start here: a cached test context needs a running test.
   */
  override fun beforeAll(context: ExtensionContext) {
    fakeLspServerHost(context)
  }

  override fun beforeEach(context: ExtensionContext) {
    ide(context).prepareForNextTest()
  }

  override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
    parameterContext.parameter.type == Driver::class.java || parameterContext.parameter.type == FakeLspIntegration::class.java

  override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {
    val ide = ide(extensionContext)
    return if (parameterContext.parameter.type == FakeLspIntegration::class.java) ide.fakeLspIntegration else ide.run.driver
  }

  private fun fakeLspServerHost(context: ExtensionContext): FakeLspServerHost =
    checkNotNull(context.root.getStore(NAMESPACE).getOrComputeIfAbsent(FakeLspServerHost::class.java, { FakeLspServerHost() },
                                                                       FakeLspServerHost::class.java))

  private fun ide(context: ExtensionContext): Ide {
    val host = fakeLspServerHost(context)
    return checkNotNull(context.root.getStore(NAMESPACE).getOrComputeIfAbsent(Ide::class.java, { Ide(host) }, Ide::class.java))
  }

  private class Ide(fakeLspServerHost: FakeLspServerHost) : AutoCloseable {
    val run: BackgroundRun

    val fakeLspIntegration: FakeLspIntegration

    /** Whether the IDE had a test: a fresh IDE has nothing to reset. */
    private var hadTest = false

    init {
      // Starter stops the IDE of a test class when the class ends, unless the IDE runs in the scope of the whole test run.
      CommonScope.perSuiteScopeForIdeActivities()
      val context = Starter.newTestContextFromCache(lspTestProject)
      run = context.runIdeWithDriver(pauseOnIndicators = 5.minutes)
      run.driver.waitForProjectOpen()
      fakeLspIntegration = FakeLspIntegration(run.driver, fakeLspServerHost, context.resolvedProjectHome)
    }

    /** Brings the IDE to the state that the next test starts from: no editors and no servers. */
    fun prepareForNextTest() {
      if (hadTest) {
        run.driver.resetLspTestIde()
      }
      hadTest = true
    }

    override fun close() {
      run.closeIdeAndWait()
    }
  }

  private companion object {
    val NAMESPACE: ExtensionContext.Namespace = ExtensionContext.Namespace.create(SharedLspIde::class.java)
  }
}

/**
 * Closes the popups and the editors, saves the documents, and stops the LSP servers of the test-only plugin.
 * A server starts again when its next file opens. The client keeps an unsaved document open on the server, so without the save
 * the next server would get `didOpen` for a file of the test before.
 */
internal fun Driver.resetLspTestIde() {
  withContext {
    step("Close the popups and the editors, save the documents, and stop the LSP servers") {
      ideFrame { keyboard { escape() } }
      // The action is disabled, and the call fails, when no editor is open.
      runCatching { invokeAction("CloseAllEditors") }
      invokeAction("SaveAll")
      service(LspIntegrationTestService::class).stopServers()
    }
  }
}
