// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.macro

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.tools.Tool
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.nio.file.InvalidPathException
import java.nio.file.Path

@TestApplication
class ToolPathMacroExpansionTest {

  companion object {
    private val projectFixture = projectFixture()
    private val moduleFixture = projectFixture.moduleFixture()

    // Two source roots so `$ModuleSourcePath$` / `$Projectpath$` / `$Sourcepath$`
    // expand to a [File.pathSeparator]-joined list "<root1><sep><root2>".
    private val sourceRoot1 = moduleFixture.sourceRootFixture()
    private val sourceRoot2 = moduleFixture.sourceRootFixture()
  }


  /**
   * Regression test for [IJPL-232998](https://youtrack.jetbrains.com/issue/IJPL-232998).
   */
  @ParameterizedTest
  @ValueSource(strings = ["ModuleSourcePath", "Projectpath", "Sourcepath"])
  fun `macro expansion with Tool's converter does not throw on multi-source-root module`(macroName: String) {
    timeoutRunBlocking {
      // Ensure the module and both source roots are materialized before expansion.
      sourceRoot1.get()
      sourceRoot2.get()
      val module = moduleFixture.get()
      val project = projectFixture.get()

      val dataContext = SimpleDataContext.builder()
        .add(PlatformCoreDataKeys.MODULE, module)
        .add(CommonDataKeys.PROJECT, project)
        .add(MacroManager.PATH_CONVERTER_KEY, Tool.createMacroConverter())
        .build()

      // Without the fix, this call throws java.nio.file.InvalidPathException from
      // Tool.EelMacroPathConverter.convertPath, because the macro is routed as PathMacro
      // and the whole "<root1>;<root2>" string is fed into Path.of(...) on Windows.
      val macroText = "\$$macroName\$"
      val expanded = assertDoesNotThrow("$macroText must not blow up Tool.EelMacroPathConverter") {
        MacroManager.getInstance().expandMacrosInString(macroText, true, dataContext)
      }
      assertNotNull(expanded, "$macroText must expand to a non-null value")
    }
  }
}
