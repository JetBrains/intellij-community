// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target

import com.intellij.execution.CommandLineUtil
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.ExecutionException
import com.intellij.execution.target.value.TargetValue
import com.intellij.util.execution.ParametersListUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.asPromise
import org.jetbrains.concurrency.await
import java.nio.charset.Charset
import java.util.concurrent.TimeoutException
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Command line that can be executed on any [TargetEnvironment].
 *
 *
 * Exe-path, working-directory and other properties are initialized in a lazy way,
 * that allows to create a command line before creating a target where it should be run.
 *
 * @see TargetedCommandLineBuilder
 */
class TargetedCommandLine internal constructor(private val exePath: TargetValue<String>,
                                               private val _workingDirectory: TargetValue<String>,
                                               private val _inputFilePath: TargetValue<String>,
                                               val charset: Charset,
                                               private val parameters: List<TargetValue<out String?>>,
                                               private val environment: Map<String, TargetValue<String>>,
                                               val isRedirectErrorStream: Boolean,
                                               val ptyOptions: PtyOptions?) {
  /**
   * [com.intellij.execution.configurations.GeneralCommandLine.getPreparedCommandLine]
   */
  @Throws(ExecutionException::class)
  fun getCommandPresentation(target: TargetEnvironment): String {
    val exePath = exePath.targetValue.resolve("exe path")
                  ?: throw ExecutionException(ExecutionBundle.message("targeted.command.line.exe.path.not.set"))
    val parameters = parameters.mapNotNull { it.targetValue.resolve("parameter") }
    val targetPlatform = target.targetPlatform.platform
    return CommandLineUtil.toCommandLine(ParametersListUtil.escape(exePath), parameters, targetPlatform).joinToString(separator = " ")
  }

  /**
   * Despite the word "synchronously", this method doesn't block current thread for unpredictable time.
   * It throws [ExecutionException] if any of the underlying futures haven't succeeded.
   */
  @Throws(ExecutionException::class)
  fun collectCommandsSynchronously(): List<String> = @Suppress("RAW_RUN_BLOCKING") runBlocking {
    try {
      withTimeout(1.nanoseconds) {
        collectCommandsImpl()
      }
    }
    catch (e: TimeoutCancellationException) {
      // By the time of writing this code, it wasn't know if there had been any caller that expects something from
      // causes of ExecutionException or not. Anyway, the class hierarchy persisted changes.
      throw ExecutionException(ExecutionBundle.message("targeted.command.line.collector.failed"), TimeoutException(e.message))
    }
    catch (e: RuntimeException) {
      throw e
    }
    catch (e: Exception) {
      // This wrapping into java.util.concurrent.ExecutionException is kept for possible backward compatibility,
      // though no usages of this structure have been checked.
      throw ExecutionException(
        ExecutionBundle.message("targeted.command.line.collector.failed"),
        java.util.concurrent.ExecutionException(e),
      )
    }
  }

  fun collectCommands(): Promise<List<String>> {
    // Dispatchers.Unconfined is used in order to make the returning Promise immediately completed if all underlying promises
    // have already been completed at the moment of the function call. Otherwise, `collectCommands().blockingGet(0)` wouldn't work.
    val dispatcher = Dispatchers.Unconfined

    @OptIn(DelicateCoroutinesApi::class) // When it used Futures, it didn't have any scope anyway.
    return GlobalScope.async(dispatcher) { collectCommandsImpl() }.asCompletableFuture().asPromise()
  }

  private suspend fun collectCommandsImpl(): List<String> =
    buildList {
      add(exePath.targetValue.await().also { command: String? ->
        checkNotNull(command) { "Resolved value for exe path is null" }
      })
      for (parameter in parameters) {
        val arg = parameter.targetValue.await()
        if (arg != null) {
          add(arg)
        }
      }
    }

  @get:Throws(ExecutionException::class)
  val workingDirectory: String?
    get() = _workingDirectory.targetValue.resolve("working directory")

  @get:Throws(ExecutionException::class)
  val inputFilePath: String?
    get() = _inputFilePath.targetValue.resolve("input file path")

  @get:Throws(ExecutionException::class)
  val environmentVariables: Map<String, String>
    get() = environment.mapValues { (name, value) ->
      value.targetValue.resolve("environment variable $name")
      ?: throw ExecutionException(ExecutionBundle.message("targeted.command.line.resolved.env.value.is.null", name))
    }

  override fun toString(): String =
    listOf(exePath).plus(parameters).joinToString(
      separator = " ",
      prefix = super.toString() + ": ",
      transform = { promise ->
        runCatching { promise.targetValue.blockingGet(0) }.getOrElse { if (it is TimeoutException) "..." else "<ERROR>" } ?: ""
      }
    )

  companion object {
    @Throws(ExecutionException::class)
    private fun Promise<out String?>.resolve(debugName: String): String? =
      try {
        blockingGet(0)
      }
      catch (e: java.util.concurrent.ExecutionException) {
        throw ExecutionException(ExecutionBundle.message("targeted.command.line.resolver.failed.for", debugName), e)
      }
      catch (e: TimeoutException) {
        throw ExecutionException(ExecutionBundle.message("targeted.command.line.resolver.failed.for", debugName), e)
      }
  }
}