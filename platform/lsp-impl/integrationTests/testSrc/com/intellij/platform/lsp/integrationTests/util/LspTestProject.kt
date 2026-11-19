// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.integrationTests.util

import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.project.ReusableLocalProjectInfo
import com.intellij.ide.starter.project.TestCaseTemplate
import com.intellij.ide.starter.testContextCache.CachedProjectConfiguration
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/** The module with the test-only plugin, which has the LSP integration for the fake server and the service that the tests call. */
private const val TEST_PLUGIN_MODULE = "intellij.platform.lsp.impl.integrationTests.idePlugin"

/**
 * The product of [LspIntegrationTestEnvironment] with the test-only plugin.
 * No product bundles that plugin, so the tests need an IDE built from sources.
 */
private val LspTestIde: IdeInfo by lazy {
  // The module of the test-only plugin is a test module: its classes and its plugin.xml are in the test output. The dev build of the IDE
  // under test packs a test module only when this property of the build scripts is on (`BuildOptions.USE_TEST_COMPILATION_OUTPUT_PROPERTY`).
  // The lambda test framework turns it on the same way.
  System.setProperty("idea.build.pack.test.source.enabled", true.toString())
  val ide = LspIntegrationTestEnvironment.current.ide
  ide.copy(additionalModules = ide.additionalModules + TEST_PLUGIN_MODULE)
}

/**
 * The name of the project directory. Each test creates its files in a directory of its own inside.
 *
 * The name is long on purpose. A cached context keeps the IDE `system` directory in
 * `out/ide-tests/cache/<IDE>/<project name>/system`. The IDE locks its directories with a Unix socket file in `system`,
 * and moves that file to `/tmp` only when its path is over 100 characters. In a Docker Desktop container `system` is a
 * bind mount, where `chmod` of a socket file fails, and the IDE doesn't start.
 */
private const val PROJECT_NAME = "lsp-integration-tests-project"

/**
 * The prepared context of the test project. Every test class of this module uses this instance,
 * because a separate instance is a separate cache entry.
 */
internal val lspTestProject: CachedProjectConfiguration by lazy {
  // An empty directory, created here and not kept in the repository. Starter copies it for each run, so a test can add files.
  val template = GlobalPaths.instance.getCacheDirectoryFor("lsp-integration-tests").resolve(PROJECT_NAME).createDirectories()
  CachedProjectConfiguration(
    testCase = (object : TestCaseTemplate(LspTestIde) {}).withProject(ReusableLocalProjectInfo(projectDir = template)),
    setup = {
      LspIntegrationTestEnvironment.current.configureIde(this)
      // A Linux machine or container without libsecret makes the IDE log an error about it, and Starter then spends
      // 20-40 seconds after the IDE closes on the analysis of that error.
      paths.configDir.resolve("options").createDirectories().resolve("security.xml").writeText(IN_MEMORY_PASSWORD_STORE)
      applyVMOptionsPatch {
        addSystemProperty("idea.trust.all.projects", "true")
        // No "Add File to Git" dialog when a test creates a file. The copy of the project is inside the checkout.
        addSystemProperty("vcs.files.processing.do.nothing", "true")
      }
    },
  )
}

private const val IN_MEMORY_PASSWORD_STORE = """<application>
  <component name="PasswordSafe">
    <option name="PROVIDER" value="MEMORY_ONLY" />
  </component>
</application>
"""
