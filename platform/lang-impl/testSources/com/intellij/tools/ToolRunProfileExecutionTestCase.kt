// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.TextConsoleBuilder
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.filters.TextConsoleBuilderImpl
import com.intellij.execution.process.SelfKiller
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.replaceService
import com.intellij.util.ui.EDT as EdtUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Runs an external tool with the runner that the platform selects for it.
 */
@TestApplication
abstract class ToolRunProfileExecutionTestCase {
  companion object {
    // the execution manager notifies the process start only for an open project
    private val projectFixture = projectFixture(openAfterCreation = true)

    // the ID of ToolProgramRunner, which is internal to its module
    private const val TOOL_RUNNER_ID = "ExternalToolRunner"
  }

  private val project: Project get() = projectFixture.get()

  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun processStartsOutsideEdt(): Unit = timeoutRunBlocking {
    val process = FakeProcess()
    val startedOnEdt = CompletableDeferred<Boolean>()
    val tool = toolWithProcess("Fake tool") {
      startedOnEdt.complete(EdtUtil.isCurrentThreadEdt())
      process
    }
    val consoleCreatedOnEdt = CompletableDeferred<Boolean>()
    ApplicationManager.getApplication().replaceService(TextConsoleBuilderFactory::class.java, object : TextConsoleBuilderFactory() {
      override fun createBuilder(project: Project): TextConsoleBuilder = createBuilder(project, GlobalSearchScope.allScope(project))

      override fun createBuilder(project: Project, scope: GlobalSearchScope): TextConsoleBuilder {
        return object : TextConsoleBuilderImpl(project, scope) {
          override fun createConsole(): ConsoleView {
            consoleCreatedOnEdt.complete(EdtUtil.isCurrentThreadEdt())
            return super.createConsole()
          }
        }
      }
    }, disposable)

    val runContent = run(tool).await()
    try {
      assertNotNull(runContent, "The runner shows no run content")
      assertFalse(startedOnEdt.await(), "The process starts on the EDT")
      assertTrue(consoleCreatedOnEdt.await(), "The console is not created on the EDT")
      process.exit()
      val processHandler = runContent!!.processHandler!!
      val terminated = withContext(Dispatchers.IO) { processHandler.waitFor(TimeUnit.SECONDS.toMillis(5)) }
      assertTrue(terminated, "The process handler does not terminate")
    }
    finally {
      if (runContent != null) {
        withContext(Dispatchers.EDT) { Disposer.dispose(runContent) }
      }
    }
  }

  @Test
  fun runnerSkipsSubclassOfToolRunProfile(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val context = SimpleDataContext.getProjectContext(project)
      val toolRunner = ProgramRunner.getRunner(DefaultRunExecutor.EXECUTOR_ID, ToolRunProfile(Tool(), context))
      assertEquals(TOOL_RUNNER_ID, toolRunner?.runnerId, "The tool runner does not run ToolRunProfile")
      // a subclass, such as the CLion profile, has its own state, so its runner does not change
      val subclassRunner = ProgramRunner.getRunner(DefaultRunExecutor.EXECUTOR_ID, object : ToolRunProfile(Tool(), context) {})
      assertNotEquals(TOOL_RUNNER_ID, subclassRunner?.runnerId, "The tool runner runs a subclass of ToolRunProfile")
    }
  }

  @Test
  fun startFailureIsReported(): Unit = timeoutRunBlocking {
    val tool = toolWithProcess("Failing tool") { throw IOException("The tool cannot start") }

    val logged = Collections.synchronizedList(ArrayList<Throwable>())
    val processor = object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> {
        logged.add(t ?: AssertionError(message))
        return Action.NONE
      }
    }
    // in the unit test mode, the execution manager logs the start error
    LoggedErrorProcessor.executeWith(processor).use {
      val error = runCatching { run(tool).await() }.exceptionOrNull()
      assertInstanceOf(ExecutionException::class.java, error)
      // the manager logs the error after the callback, in the same EDT event
      withContext(Dispatchers.EDT) {}
    }
    assertEquals(1, logged.size, "The manager logs an unexpected number of errors: $logged")
    assertInstanceOf(ExecutionException::class.java, logged.single())
  }

  private fun toolWithProcess(name: String, createProcess: () -> Process): Tool {
    val tool = object : Tool() {
      override fun createCommandLine(dataContext: DataContext): GeneralCommandLine {
        return GeneralCommandLine("tool").apply { setProcessCreator { createProcess() } }
      }
    }
    tool.name = name
    tool.isUseConsole = true
    return tool
  }

  private suspend fun run(tool: Tool): CompletableFuture<RunContentDescriptor?> {
    val descriptor = CompletableFuture<RunContentDescriptor?>()
    withContext(Dispatchers.EDT) {
      val profile = ToolRunProfile(tool, SimpleDataContext.getProjectContext(project))
      val environment = ExecutionEnvironmentBuilder.create(project, DefaultRunExecutor.getRunExecutorInstance(), profile)
        .build(object : ProgramRunner.Callback {
          override fun processStarted(started: RunContentDescriptor?) {
            descriptor.complete(started)
          }

          override fun processNotStarted(error: Throwable?) {
            descriptor.completeExceptionally(error ?: AssertionError("The process did not start"))
          }
        })
      environment.runner.execute(environment)
    }
    return descriptor
  }
}

/**
 * A process with empty streams, which runs until the test calls [exit] or the process handler destroys it.
 */
private class FakeProcess : Process(), SelfKiller {
  private val exitCode = CompletableFuture<Int>()

  fun exit() {
    exitCode.complete(0)
  }

  override fun destroy() {
    exitCode.complete(1)
  }

  override fun waitFor(): Int = exitCode.get()

  override fun exitValue(): Int = exitCode.getNow(null) ?: throw IllegalThreadStateException()

  override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()

  override fun getInputStream(): InputStream = InputStream.nullInputStream()

  override fun getErrorStream(): InputStream = InputStream.nullInputStream()
}
