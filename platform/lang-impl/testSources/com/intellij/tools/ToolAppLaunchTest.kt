// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
class ToolAppLaunchTest {

  companion object {
    private val projectFixture = projectFixture()
  }

  /**
   * Regression test for [IJPL-202354](https://youtrack.jetbrains.com/issue/IJPL-202354).
   *
   * A macOS `.app` bundle is a directory and cannot be executed directly (`error=13, Permission denied`).
   * It must be launched via the `open -a` command instead.
   */
  @Test
  fun `app bundle is launched via open command`(@TempDir tempDir: Path) {
    timeoutRunBlocking {
      val project = projectFixture.get()

      val appPath = tempDir.resolve("iTerm.app")
      Files.createDirectories(appPath)

      val tool = Tool()
      tool.program = appPath.toString()
      tool.workingDirectory = tempDir.toString()

      val dataContext = SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .build()

      val commandLine = tool.createCommandLine(dataContext)
      assertNotNull(commandLine, "command line must be created for an .app bundle")

      assertEquals("open", commandLine!!.exePath,
                   "an .app bundle must be launched via the macOS 'open' command")
      val parameters = commandLine.parametersList.parameters
      assertTrue(parameters.contains("-a"), "'open' must be invoked with the '-a' flag, got: $parameters")
      assertTrue(parameters.contains(appPath.toString()),
                 "'open -a' must reference the .app bundle path, got: $parameters")
    }
  }
}
