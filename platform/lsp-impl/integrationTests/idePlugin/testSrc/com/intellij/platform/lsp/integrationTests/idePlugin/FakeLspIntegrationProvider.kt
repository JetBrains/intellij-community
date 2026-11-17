// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.integrationTests.idePlugin

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.ProjectWideLspClientDescriptor
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspSemanticTokensCustomizer
import com.intellij.platform.lsp.api.customization.LspSemanticTokensDisabled

/**
 * Starts the server that a test configured through [LspIntegrationTestService], for the files with its extensions.
 * Does nothing until a test configures a server.
 */
internal class FakeLspIntegrationProvider : LspIntegrationProvider {
  override fun fileOpened(project: Project, file: VirtualFile, clientStarter: LspIntegrationProvider.LspClientStarter) {
    val service = LspIntegrationTestService.getInstance()
    val server = service.server ?: return
    if (file.extension in server.extensions) {
      clientStarter.ensureClientStarted(FakeLspClientDescriptor(project, server, service.clientCustomization))
    }
  }
}

private class FakeLspClientDescriptor(
  project: Project,
  private val server: FakeLspServerSettings,
  customization: FakeLspClientCustomization,
) : ProjectWideLspClientDescriptor(project, "FakeLspServer") {
  override val lspCustomization: LspCustomization = customization.create()

  override fun isSupportedFile(file: VirtualFile): Boolean = file.extension in server.extensions

  override fun createCommandLine(): GeneralCommandLine = GeneralCommandLine(server.command)
}

/** The client customizations that a test can select by name, see [LspIntegrationTestService.setClientCustomization]. */
internal enum class FakeLspClientCustomization {
  DEFAULT {
    override fun create(): LspCustomization = LspCustomization()
  },
  SEMANTIC_TOKENS_DISABLED {
    override fun create(): LspCustomization = object : LspCustomization() {
      override val semanticTokensCustomizer: LspSemanticTokensCustomizer = LspSemanticTokensDisabled
    }
  };

  abstract fun create(): LspCustomization
}

/**
 * Lets WolfTheProblemSolver track the files of the fake server.
 * Without a filter that accepts a file, the IDE ignores the problems that the LSP client reports for it.
 */
internal class FakeLspProblemFileHighlightFilter : Condition<VirtualFile> {
  override fun value(file: VirtualFile): Boolean {
    val server = LspIntegrationTestService.getInstance().server ?: return false
    return file.extension in server.extensions
  }
}
