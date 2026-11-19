// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.integrationTests.util

import com.intellij.driver.client.Remote

/**
 * The application service of the test-only plugin in the IDE under test, see `LspIntegrationTestService` in
 * `community/platform/lsp-impl/integrationTests/idePlugin` for what each function does.
 */
@Remote("com.intellij.platform.lsp.integrationTests.idePlugin.LspIntegrationTestService",
        plugin = "com.intellij.platform.lsp.integrationTests")
internal interface LspIntegrationTestService {
  fun configureServer(commandLine: String, extensions: String)
  fun setClientCustomization(name: String)
  fun stopServers()
  fun isProblemFile(path: String): Boolean
}

/** The names of `FakeLspClientCustomization` in the plugin. */
internal object ClientCustomization {
  const val DEFAULT: String = "DEFAULT"
  const val SEMANTIC_TOKENS_DISABLED: String = "SEMANTIC_TOKENS_DISABLED"
}
