// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.ExecutionException
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.execution.target.value.TargetValue
import com.intellij.testFramework.LoggedErrorProcessor
import org.assertj.core.api.Assertions
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.rejectedPromise
import org.jetbrains.concurrency.resolvedPromise
import org.junit.jupiter.api.Test
import java.util.*
import javax.naming.NoPermissionException

class TargetedCommandLineTest {

  @Test
  fun `collectCommandsSynchronously with completed promises`() = disableTestLoggerFailures {
    val cmdLine = TargetedCommandLineBuilder(LocalTargetEnvironmentRequest())
    cmdLine.exePath = TargetValue.fixed("program.exe")
    cmdLine.addParameter(TargetValue.fixed("argument"))
    val cmd = cmdLine.build()

    Assertions.assertThat(cmd.collectCommandsSynchronously())
      .isEqualTo(listOf("program.exe", "argument"))
  }

  @Test
  fun `collectCommandsSynchronously with completed promises and null argument`() = disableTestLoggerFailures {
    val cmdLine = TargetedCommandLineBuilder(LocalTargetEnvironmentRequest())
    cmdLine.exePath = TargetValue.fixed("program.exe")
    cmdLine.addParameter(TargetValue.fixed("argument1"))
    cmdLine.addParameter(TargetValue.create("oops", resolvedPromise(null)))
    cmdLine.addParameter(TargetValue.fixed("argument3"))
    val cmd = cmdLine.build()

    Assertions.assertThat(cmd.collectCommandsSynchronously())
      .isEqualTo(listOf("program.exe", "argument1", "argument3"))
  }

  @Test
  fun `collectCommandsSynchronously with pending exePath promise`() {
    val cmdLine = TargetedCommandLineBuilder(LocalTargetEnvironmentRequest())
    cmdLine.exePath = TargetValue.create("program.exe", AsyncPromise())
    cmdLine.addParameter(TargetValue.fixed("argument"))
    val cmd = cmdLine.build()

    Assertions.assertThatCode { cmd.collectCommandsSynchronously() }
      .isInstanceOf(com.intellij.execution.ExecutionException::class.java)
      .hasMessage(ExecutionBundle.message("targeted.command.line.collector.failed"))
      .hasCauseInstanceOf(java.util.concurrent.TimeoutException::class.java)
  }

  @Test
  fun `collectCommandsSynchronously with pending argument promise`() {
    val cmdLine = TargetedCommandLineBuilder(LocalTargetEnvironmentRequest())
    cmdLine.exePath = TargetValue.fixed("program.exe")
    cmdLine.addParameter(TargetValue.fixed("argument"))
    cmdLine.addParameter(TargetValue.create("oops", AsyncPromise()))
    val cmd = cmdLine.build()

    Assertions.assertThatCode { cmd.collectCommandsSynchronously() }
      .isInstanceOf(com.intellij.execution.ExecutionException::class.java)
      .hasMessage(ExecutionBundle.message("targeted.command.line.collector.failed"))
      .hasCauseInstanceOf(java.util.concurrent.TimeoutException::class.java)
  }

  @Test
  fun `collectCommandsSynchronously with failed by RuntimeException exePath promise`() = disableTestLoggerFailures {
    val cmdLine = TargetedCommandLineBuilder(LocalTargetEnvironmentRequest())
    cmdLine.exePath = TargetValue.create("program.exe", rejectedPromise(IllegalFormatWidthException(12345)))
    cmdLine.addParameter(TargetValue.fixed("argument"))
    val cmd = cmdLine.build()

    Assertions.assertThatCode { cmd.collectCommandsSynchronously() }
      .isInstanceOf(IllegalFormatWidthException::class.java)
  }

  @Test
  fun `collectCommandsSynchronously with failed by Exception exePath promise`() = disableTestLoggerFailures {
    val cmdLine = TargetedCommandLineBuilder(LocalTargetEnvironmentRequest())
    cmdLine.exePath = TargetValue.create("program.exe", rejectedPromise(NoPermissionException("oops")))
    cmdLine.addParameter(TargetValue.fixed("argument"))
    val cmd = cmdLine.build()

    Assertions.assertThatCode { cmd.collectCommandsSynchronously() }
      .isInstanceOf(ExecutionException::class.java)
      .hasMessage(ExecutionBundle.message("targeted.command.line.collector.failed"))
      .hasRootCauseInstanceOf(NoPermissionException::class.java)
  }

  @Test
  fun `collectCommandsSynchronously with failed argument promise`() = disableTestLoggerFailures {
    val cmdLine = TargetedCommandLineBuilder(LocalTargetEnvironmentRequest())
    cmdLine.exePath = TargetValue.fixed("program.exe")
    cmdLine.addParameter(TargetValue.fixed("argument"))
    cmdLine.addParameter(TargetValue.create("oops", rejectedPromise(IllegalFormatWidthException(12345))))
    val cmd = cmdLine.build()

    Assertions.assertThatCode { cmd.collectCommandsSynchronously() }
      .isInstanceOf(IllegalFormatWidthException::class.java)
  }

  @Test
  fun `collectCommands with completed promises responds on blockingGet immediately`() {
    val cmdLine = TargetedCommandLineBuilder(LocalTargetEnvironmentRequest())
    cmdLine.exePath = TargetValue.fixed("program.exe")
    cmdLine.addParameter(TargetValue.fixed("argument"))
    val cmd = cmdLine.build()

    Assertions.assertThat(cmd.collectCommands().blockingGet(0))
      .isEqualTo(listOf("program.exe", "argument"))
  }

  @Test
  fun `collectCommands with completed promises and null argument responds on blockingGet immediately`() {
    val cmdLine = TargetedCommandLineBuilder(LocalTargetEnvironmentRequest())
    cmdLine.exePath = TargetValue.fixed("program.exe")
    cmdLine.addParameter(TargetValue.fixed("argument1"))
    cmdLine.addParameter(TargetValue.create("oops", resolvedPromise(null)))
    cmdLine.addParameter(TargetValue.fixed("argument3"))
    val cmd = cmdLine.build()

    Assertions.assertThat(cmd.collectCommands().blockingGet(0))
      .isEqualTo(listOf("program.exe", "argument1", "argument3"))
  }

  @Test
  fun `collectCommands with pending exePath promise`() {
    val cmdLine = TargetedCommandLineBuilder(LocalTargetEnvironmentRequest())
    cmdLine.exePath = TargetValue.create("program.exe", AsyncPromise())
    cmdLine.addParameter(TargetValue.fixed("argument"))
    val cmd = cmdLine.build()

    Assertions.assertThatCode { cmd.collectCommands().blockingGet(0) }
      .isInstanceOf(java.util.concurrent.TimeoutException::class.java)
  }

  @Test
  fun `collectCommands with pending argument promise`() {
    val cmdLine = TargetedCommandLineBuilder(LocalTargetEnvironmentRequest())
    cmdLine.exePath = TargetValue.fixed("program.exe")
    cmdLine.addParameter(TargetValue.fixed("argument"))
    cmdLine.addParameter(TargetValue.create("oops", AsyncPromise()))
    val cmd = cmdLine.build()

    Assertions.assertThatCode { cmd.collectCommands().blockingGet(0) }
      .isInstanceOf(java.util.concurrent.TimeoutException::class.java)
  }

  @Test
  fun `collectCommands with failed by RuntimeException exePath promise`() = disableTestLoggerFailures {
    val cmdLine = TargetedCommandLineBuilder(LocalTargetEnvironmentRequest())
    cmdLine.exePath = TargetValue.create("program.exe", rejectedPromise(IllegalFormatWidthException(12345)))
    cmdLine.addParameter(TargetValue.fixed("argument"))
    val cmd = cmdLine.build()

    Assertions.assertThatCode { cmd.collectCommands().blockingGet(0) }
      .isInstanceOf(IllegalFormatWidthException::class.java)
  }

  @Test
  fun `collectCommands with failed by Exception exePath promise`() = disableTestLoggerFailures {
    val cmdLine = TargetedCommandLineBuilder(LocalTargetEnvironmentRequest())
    cmdLine.exePath = TargetValue.create("program.exe", rejectedPromise(NoPermissionException("oops")))
    cmdLine.addParameter(TargetValue.fixed("argument"))
    val cmd = cmdLine.build()

    Assertions.assertThatCode { cmd.collectCommands().blockingGet(0) }
      .isInstanceOf(java.util.concurrent.ExecutionException::class.java)
      .hasMessage("javax.naming.NoPermissionException: oops")
      .hasRootCauseInstanceOf(NoPermissionException::class.java)
  }

  @Test
  fun `collectCommands with failed argument promise`() = disableTestLoggerFailures {
    val cmdLine = TargetedCommandLineBuilder(LocalTargetEnvironmentRequest())
    cmdLine.exePath = TargetValue.fixed("program.exe")
    cmdLine.addParameter(TargetValue.fixed("argument"))
    cmdLine.addParameter(TargetValue.create("oops", rejectedPromise(IllegalFormatWidthException(12345))))
    val cmd = cmdLine.build()

    Assertions.assertThatCode { cmd.collectCommands().blockingGet(0) }
      .isInstanceOf(IllegalFormatWidthException::class.java)
  }

  private fun disableTestLoggerFailures(handler: () -> Unit) {
    val noOpProcessor = object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> = emptySet()
    }
    LoggedErrorProcessor.executeWith<Exception>(noOpProcessor) { handler() }
  }
}