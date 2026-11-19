// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.integrationTests.idePlugin

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.util.execution.ParametersListUtil

/**
 * What an integration test of the LSP client tells the IDE under test before and between its test cases.
 * The test calls the service through a `@Remote` interface, so the parameters and results are strings and primitives.
 *
 * The plugin of this module is test-only: no product bundles it, and `intellij.platform.lsp.impl.integrationTests` adds it to the IDE that it starts.
 */
@Service(Service.Level.APP)
class LspIntegrationTestService {
  @Volatile
  internal var server: FakeLspServerSettings? = null
    private set

  @Volatile
  internal var clientCustomization: FakeLspClientCustomization = FakeLspClientCustomization.DEFAULT
    private set

  /**
   * From now on, a file with one of the comma-separated [extensions] starts a server with [commandLine],
   * which is split like a command line of a shell.
   */
  fun configureServer(commandLine: String, extensions: String) {
    server = FakeLspServerSettings(ParametersListUtil.parse(commandLine), extensions.split(',').filter { it.isNotEmpty() }.toSet())
  }

  /**
   * Selects what the next client that starts customizes: the name of a [FakeLspClientCustomization].
   * A test can't pass an `LspCustomization` object to the IDE, so each customization that a test needs is an entry there.
   */
  fun setClientCustomization(name: String) {
    clientCustomization = FakeLspClientCustomization.valueOf(name)
  }

  /** Stops the servers of this plugin in every open project. A server starts again when its next file opens. */
  fun stopServers() {
    for (project in ProjectManager.getInstance().openProjects) {
      LspClientManager.getInstance(project).stopClients(FakeLspIntegrationProvider::class.java)
    }
  }

  /** Whether WolfTheProblemSolver of an open project knows the file at [path] as a file with problems. */
  fun isProblemFile(path: String): Boolean {
    val file = LocalFileSystem.getInstance().findFileByPath(path) ?: return false
    return ProjectManager.getInstance().openProjects.any { WolfTheProblemSolver.getInstance(it).isProblemFile(file) }
  }

  internal companion object {
    fun getInstance(): LspIntegrationTestService = service()
  }
}

internal class FakeLspServerSettings(val command: List<String>, val extensions: Set<String>)
