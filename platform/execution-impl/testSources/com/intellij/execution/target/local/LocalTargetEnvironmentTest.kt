// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target.local

import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.IoTestUtil
import org.junit.Assert
import org.junit.Test

class LocalTargetEnvironmentTest {
  val LOG: Logger = Logger.getInstance(LocalTargetEnvironmentTest::class.java)

  @Test
  fun `create general command line on Windows`() {
    IoTestUtil.assumeWindows()

    val exePath = "C:\\Path\\To\\Some Executable.exe"

    val parameters = listOf(
      "-option:Value with Spaces",
      "D:\\Path\\To\\Some\\Data File.txt",
      "OptionWithoutSpaces",
      "OptionWith\"Quotes\"",
      "Option with \"Quotes\" and Spaces"
    )

    val request = LocalTargetEnvironmentRequest()
    val targetedCommandLineBuilder = TargetedCommandLineBuilder(request).apply {
      setExePath(exePath)
      parameters.forEach { parameter ->
        addParameter(parameter)
      }
    }

    val localTargetEnvironment = LocalTargetEnvironment(request)
    val generalCommandLine = localTargetEnvironment.createGeneralCommandLine(targetedCommandLineBuilder.build())
    LOG.debug(generalCommandLine.commandLineString)

    Assert.assertEquals(
      listOf("C:\\Path\\To\\Some Executable.exe",
             "-option:Value with Spaces",
             "D:\\Path\\To\\Some\\Data File.txt",
             "OptionWithoutSpaces",
             "OptionWith\"Quotes\"",
             "Option with \"Quotes\" and Spaces"),
      generalCommandLine.getCommandLineList(null)
    )
  }

  @Test
  fun `create general command line on UNIX`() {
    IoTestUtil.assumeUnix()

    val exePath = "/path/to/some executable"

    val parameters = listOf(
      "-option:Value with Spaces",
      "/path/to/Some file with data.csv",
      "option_without_spaces",
      "option_with_\"quotes\"",
      "Option With \"Quotes\" and Spaces"
    )

    val request = LocalTargetEnvironmentRequest()
    val targetedCommandLineBuilder = TargetedCommandLineBuilder(request).apply {
      setExePath(exePath)
      parameters.forEach { parameter ->
        addParameter(parameter)
      }
    }

    val localTargetEnvironment = LocalTargetEnvironment(request)
    val generalCommandLine = localTargetEnvironment.createGeneralCommandLine(targetedCommandLineBuilder.build())
    LOG.debug(generalCommandLine.commandLineString)

    Assert.assertEquals(
      listOf("/path/to/some executable",
             "-option:Value with Spaces",
             "/path/to/Some file with data.csv",
             "option_without_spaces",
             "option_with_\"quotes\"",
             "Option With \"Quotes\" and Spaces"
      ),
      generalCommandLine.getCommandLineList(null)
    )
  }
}