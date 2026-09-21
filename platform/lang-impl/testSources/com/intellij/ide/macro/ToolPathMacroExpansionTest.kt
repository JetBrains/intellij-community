// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.macro

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.tools.Tool
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@TestApplication
class ToolPathMacroExpansionTest {

  companion object {
    private const val DOLLAR = "$"

    // A global path. It names the WSL target, and it is not valid inside that target.
    private const val GLOBAL_PATH = """\\wsl.localhost\Ubuntu\home\me\index.php"""

    private val projectFixture = projectFixture()
    private val moduleFixture = projectFixture.moduleFixture()

    // Two source roots so `$ModuleSourcePath$` / `$Projectpath$` / `$Sourcepath$`
    // expand to a [java.io.File.pathSeparator]-joined list "<root1><sep><root2>".
    private val sourceRoot1 = moduleFixture.sourceRootFixture()
    private val sourceRoot2 = moduleFixture.sourceRootFixture()
  }

  /**
   * Regression test for [IJPL-232998](https://youtrack.jetbrains.com/issue/IJPL-232998).
   * A macro of a module with two source roots expands to a path list. The tool must accept that list.
   */
  @ParameterizedTest
  @ValueSource(strings = ["ModuleSourcePath", "Projectpath", "Sourcepath"])
  fun `a path list macro does not break the command line`(macroName: String) {
    timeoutRunBlocking {
      // Ensure the module and both source roots are materialized before expansion.
      sourceRoot1.get()
      sourceRoot2.get()

      val macroText = macroReference(macroName)
      val commandLine = assertDoesNotThrow("$macroText must not break Tool.createCommandLine") {
        tool(macroText).createCommandLine(dataContext())
      }
      assertNotNull(commandLine, "$macroText must produce a command line")
      assertFalse(commandLine!!.parametersList.list.isEmpty(), "$macroText must produce a parameter")
    }
  }

  /**
   * Regression test for [IJPL-207641](https://youtrack.jetbrains.com/issue/IJPL-207641).
   * The tool must not convert a macro value. The launch converts the command line.
   *
   * @see com.intellij.execution.configurations.GeneralCommandLine
   */
  @Test
  fun `a path macro keeps the global path`(@TestDisposable disposable: Disposable) {
    timeoutRunBlocking {
      val macro = GlobalPathMacro()
      ExtensionTestUtil.addExtensions(Macro.EP_NAME, listOf(macro), disposable)

      val commandLine = tool(macroReference(macro.name)).createCommandLine(dataContext())
      assertNotNull(commandLine, "the tool must produce a command line")
      assertEquals(listOf(GLOBAL_PATH), commandLine!!.parametersList.list, "the parameter must keep the global path")
      assertNull(MacroManager.PATH_CONVERTER_KEY.getData(macro.context!!),
                 "the tool must add no path converter, because the launch converts the command line")
    }
  }

  private fun macroReference(name: String): String = DOLLAR + name + DOLLAR

  private fun tool(params: String): Tool = Tool().apply {
    name = "test tool"
    program = "node"
    parameters = params
  }

  private fun dataContext(): DataContext = SimpleDataContext.builder()
    .add(PlatformCoreDataKeys.MODULE, moduleFixture.get())
    .add(CommonDataKeys.PROJECT, projectFixture.get())
    .build()

  /** A macro that returns a [GLOBAL_PATH] and records the data context of the expansion. */
  private class GlobalPathMacro : Macro(), PathMacro {
    var context: DataContext? = null

    override fun getName(): String = "ToolTestGlobalPath"

    override fun getDescription(): String = "A test macro that returns a global path"

    override fun expand(dataContext: DataContext): String {
      context = dataContext
      return GLOBAL_PATH
    }
  }
}
