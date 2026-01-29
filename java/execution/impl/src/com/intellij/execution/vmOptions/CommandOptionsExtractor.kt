// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.vmOptions

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessRunner
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessNotCreatedException
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.containers.CollectionFactory
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentMap

internal abstract class CommandOptionsExtractor {
  private val commandData: ConcurrentMap<String, CompletableFuture<JdkOptionsData>> =
    CollectionFactory.createConcurrentSoftValueMap()

  protected abstract val commandName: String

  /**
   * Command options used to print all available options
   */
  protected abstract val commandOptions: List<String>

  protected abstract fun getOptions(javaHome: String): JdkOptionsData

  fun getOrComputeOptions(
    javaHome: String,
  ): CompletableFuture<JdkOptionsData> {
    val future = commandData.computeIfAbsent(javaHome) { CompletableFuture.supplyAsync { getOptions(it) } }
    if (future.isDone) {
      // sometimes the timeout may appear and in order not to block the possibility to get the completion afterwards, it is better to retry
      val data = future.get()
      if (data == null) {
        commandData.remove(javaHome)
      }
    }
    return future
  }

  protected fun getCommandExecutablePath(javaHome: String): String {
    val vmExeName = if (SystemInfo.isWindows) "$commandName.exe" else commandName // do not use JavaW.exe because of issues with encoding
    return Path.of(getConvertedPath(javaHome), "bin", vmExeName).toString()
  }

  private fun getConvertedPath(javaHome: String): String {
    // it is copied from com.intellij.openapi.projectRoots.impl.JavaSdkImpl.getConvertedHomePath
    var systemDependentName = FileUtil.toSystemDependentName(javaHome)
    if (javaHome.endsWith(File.separator)) {
      systemDependentName += File.separator
    }
    return systemDependentName
  }

  protected fun opt(name: String, doc: String): VMOption {
    return VMOption(name, null, null, VMOptionKind.Standard, doc, VMOptionVariant.DASH, null)
  }

  object Java : CommandOptionsExtractor() {
    @JvmStatic
    @get:JvmName("getStandardOptionList")
    val STANDARD_OPTION_LIST: List<VMOption> = listOf(
      opt("ea", VMOptionsBundle.message("vm.option.enable.assertions.description")),
      opt("enableassertions", VMOptionsBundle.message("vm.option.enable.assertions.description")),
      opt("da", VMOptionsBundle.message("vm.option.disable.assertions.description")),
      opt("disableassertions", VMOptionsBundle.message("vm.option.disable.assertions.description")),
      opt("esa", VMOptionsBundle.message("vm.option.enable.system.assertions.description")),
      opt("enablesystemassertions", VMOptionsBundle.message("vm.option.enable.system.assertions.description")),
      opt("dsa", VMOptionsBundle.message("vm.option.disable.system.assertions.description")),
      opt("disablesystemassertions", VMOptionsBundle.message("vm.option.disable.system.assertions.description")),
      opt("agentpath:", VMOptionsBundle.message("vm.option.agentpath.description")),
      opt("agentlib:", VMOptionsBundle.message("vm.option.agentlib.description")),
      opt("javaagent:", VMOptionsBundle.message("vm.option.javaagent.description")),
      opt("D", VMOptionsBundle.message("vm.option.system.property.description")),
      opt("XX:", VMOptionsBundle.message("vm.option.advanced.option.description")),
    )

    override val commandName: String = "java"
    override val commandOptions: List<String> =
      listOf("-XX:+PrintFlagsFinal", "-XX:+UnlockDiagnosticVMOptions", "-XX:+UnlockExperimentalVMOptions", "-X")

    override fun getOptions(javaHome: String): JdkOptionsData {
      val options =
        getOptionsForJava(javaHome)
      return JdkOptionsData(options)
    }


    private fun getOptionsForJava(javaHome: String): List<VMOption> {
      val output = getProcessOutput(javaHome) ?: return STANDARD_OPTION_LIST

      val xxOptions = VMOptionsParser.parseXXOptions(output.stdout)
      val xOptions = VMOptionsParser.parseXOptions(output.stderr)
      if (xOptions != null) {
        return xOptions + xxOptions + STANDARD_OPTION_LIST
      }
      return xxOptions + STANDARD_OPTION_LIST
    }
  }


  object Javac : CommandOptionsExtractor() {
    val STANDARD_OPTIONS_LIST: List<VMOption> = listOf(
      opt("A", "vm.option.annotation.processing.description"),
      opt("g", "vm.option.generate.debug.information.description"),
      opt("g:", "vm.option.generate.choice.debug.information.description"),
      opt("g:none", "vm.option.generate.none.debug.information.description"),
      opt("h", "vm.option.native.header.description"),
      opt("J", "vm.option.j.description"),
      opt("d", "vm.option.directory.description"),
      opt("nowarn", "vm.option.nowarn.description"),
      opt("parameters", "vm.option.parameters.description"),
      opt("processor", "vm.option.processor.description"),
      opt("profile", "vm.option.profile.description"),
      opt("s", "vm.option.source.output.description"),
      opt("verbose", "vm.option.verbose.description"),
      opt("Werror", "vm.option.werror.description"),
      opt("proc:", "vm.option.proc.description"),
      opt("implicit:", "vm.option.implicit.description"),
      opt("encoding", "vm.option.encoding.description"),
      opt("endorseddirs", "vm.option.endorsed.dirs.description"),
      opt("extdirs", "vm.option.extension.dirs.description"),
    )

    override val commandName: String = "javac"
    override val commandOptions: List<String>
      get() = listOf("-help", "-X")

    override fun getOptions(javaHome: String): JdkOptionsData {
      return JdkOptionsData(getOptionsForJavac(javaHome))
    }

    private fun getOptionsForJavac(javaHome: String): List<VMOption> {
      val processOutput = getProcessOutput(javaHome) ?: return emptyList()
      val parsedStandardOptions = VMOptionsParser.parseJavacDoubleDashedOptions(processOutput.stdout)
      return if (parsedStandardOptions != null) parsedStandardOptions + STANDARD_OPTIONS_LIST else STANDARD_OPTIONS_LIST
    }
  }

  protected fun getProcessOutput(javaHome: String): ProcessOutput? {
    val vmPath = getCommandExecutablePath(javaHome)
    val generalCommandLine = GeneralCommandLine(vmPath)
    generalCommandLine.addParameters(commandOptions)
    val handler = try {
      OSProcessHandler(generalCommandLine)
    }
    catch (_: ProcessNotCreatedException) {
      return null
    }
    val runner = CapturingProcessRunner(handler)
    val output = runner.runProcess(1000)
    if (output.isTimeout) {
      return null
    }
    return output
  }
}

