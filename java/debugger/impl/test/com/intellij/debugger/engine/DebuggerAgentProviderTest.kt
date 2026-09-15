// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.execution.configurations.JavaParameters
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.registerOrReplaceServiceInstance
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
internal class DebuggerAgentProviderTest {
  @TestDisposable
  lateinit var disposable: Disposable

  @BeforeEach
  fun setUp() {
    val application = ApplicationManager.getApplication()
    application.registerOrReplaceServiceInstance(DebuggerSettings::class.java, DebuggerSettings(), disposable)

    val extensionArea = application.extensionArea
    val extensionPointName = DebuggerAgentProvider.EP_NAME.name
    if (!extensionArea.hasExtensionPoint(extensionPointName)) {
      extensionArea.registerExtensionPoint(
        extensionPointName,
        DebuggerAgentProvider::class.java.name,
        ExtensionPoint.Kind.INTERFACE,
        false,
      )
      Disposer.register(disposable) {
        extensionArea.unregisterExtensionPoint(extensionPointName)
      }
    }
  }

  @Test
  fun `agent is disabled without provider`() {
    withInstrumentingAgentSetting(true) {
      ExtensionTestUtil.maskExtensions(DebuggerAgentProvider.EP_NAME, emptyList(), disposable)

      assertFalse(AsyncStacksUtils.isAgentAvailable())
      assertFalse(AsyncStacksUtils.isAgentEnabled())

      val parameters = JavaParameters()
      AsyncStacksUtils.addDebuggerAgent(parameters, null, false)
      assertTrue(parameters.vmParametersList.parameters.isEmpty())
    }
  }

  @Test
  fun `agent needs enabled setting`() {
    ExtensionTestUtil.maskExtensions(DebuggerAgentProvider.EP_NAME, listOf(TestProvider), disposable)

    withInstrumentingAgentSetting(false) {
      assertTrue(AsyncStacksUtils.isAgentAvailable())
      assertFalse(AsyncStacksUtils.isAgentEnabled())
    }

    withInstrumentingAgentSetting(true) {
      assertTrue(AsyncStacksUtils.isAgentEnabled())
    }
  }

  @Test
  fun `missing provider artifact does not change parameters`() {
    ExtensionTestUtil.maskExtensions(DebuggerAgentProvider.EP_NAME, listOf(TestProvider), disposable)

    withInstrumentingAgentSetting(true) {
      val parameters = JavaParameters()
      AsyncStacksUtils.addDebuggerAgent(parameters, null, false)

      assertTrue(parameters.vmParametersList.parameters.isEmpty())
    }
  }

  @Test
  fun `multiple providers are rejected`() {
    ExtensionTestUtil.maskExtensions(DebuggerAgentProvider.EP_NAME, listOf(TestProvider, TestProvider), disposable)

    assertThrows(IllegalStateException::class.java) {
      DebuggerAgentProvider.getInstance()
    }
  }

  private fun withInstrumentingAgentSetting(value: Boolean, action: () -> Unit) {
    val settings = DebuggerSettings.getInstance()
    val oldValue = settings.INSTRUMENTING_AGENT
    try {
      settings.INSTRUMENTING_AGENT = value
      action()
    }
    finally {
      settings.INSTRUMENTING_AGENT = oldValue
    }
  }

  private object TestProvider : DebuggerAgentProvider {
    override fun getAgentArtifactPath(project: Project?, disposable: Disposable?): Path? = null
  }
}
