// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.execution.CommandLineUtil
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.ExecutionException
import com.intellij.execution.target.value.TargetValue
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.execution.ParametersListUtil
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.collectResults
import java.nio.charset.Charset
import java.util.concurrent.TimeoutException

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
                                               private val parameters: List<TargetValue<String>>,
                                               private val environment: Map<String, TargetValue<String>>,
                                               val isRedirectErrorStream: Boolean,
                                               val ptyOptions: PtyOptions?) {
  /**
   * [com.intellij.execution.configurations.GeneralCommandLine.getPreparedCommandLine]
   */
  @Throws(ExecutionException::class)
  fun getCommandPresentation(target: TargetEnvironment): String {
    val exePath = resolvePromise(exePath.targetValue, "exe path")
                  ?: throw ExecutionException(ExecutionBundle.message("targeted.command.line.exe.path.not.set"))
    val parameters: MutableList<String?> = ArrayList()
    for (parameter in this.parameters) {
      parameters.add(resolvePromise(parameter.targetValue, "parameter"))
    }
    return StringUtil.join(CommandLineUtil.toCommandLine(ParametersListUtil.escape(exePath), parameters, target.targetPlatform.platform),
                           " ")
  }

  @Throws(ExecutionException::class)
  fun collectCommandsSynchronously(): List<String> =
    try {
      collectCommands().blockingGet(0)!!
    }
    catch (e: java.util.concurrent.ExecutionException) {
      throw ExecutionException(ExecutionBundle.message("targeted.command.line.collector.failed"), e)
    }
    catch (e: TimeoutException) {
      throw ExecutionException(ExecutionBundle.message("targeted.command.line.collector.failed"), e)
    }

  fun collectCommands(): Promise<List<String>> {
    val promises: MutableList<Promise<String>> = ArrayList(parameters.size + 1)
    promises.add(exePath.targetValue.then { command: String? ->
      checkNotNull(command) { "Resolved value for exe path is null" }
      command
    })
    for (parameter in parameters) {
      promises.add(parameter.targetValue)
    }
    return promises.collectResults()
  }

  @get:Throws(ExecutionException::class)
  val workingDirectory: String?
    get() = resolvePromise(_workingDirectory.targetValue, "working directory")

  @get:Throws(ExecutionException::class)
  val inputFilePath: String?
    get() = resolvePromise(_inputFilePath.targetValue, "input file path")

  @get:Throws(ExecutionException::class)
  val environmentVariables: Map<String, String>
    get() {
      val result: MutableMap<String, String> = LinkedHashMap()
      for ((key, value) in environment) {
        result[key] = resolvePromise(value.targetValue, "environment variable $key")
                      ?: throw ExecutionException(ExecutionBundle.message("targeted.command.line.resolved.env.value.is.null", key))
      }
      return result
    }

  companion object {
    @Throws(ExecutionException::class)
    private fun resolvePromise(promise: Promise<String>, debugName: String): String? =
      try {
        promise.blockingGet(0)
      }
      catch (e: java.util.concurrent.ExecutionException) {
        throw ExecutionException(ExecutionBundle.message("targeted.command.line.resolver.failed.for", debugName), e)
      }
      catch (e: TimeoutException) {
        throw ExecutionException(ExecutionBundle.message("targeted.command.line.resolver.failed.for", debugName), e)
      }
  }
}