// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.vmOptions

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessRunner
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessNotCreatedException
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.containers.CollectionFactory
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentMap

class VMOptionsServiceImpl : VMOptionsService {
  companion object {
    private val ourData: ConcurrentMap<String, CompletableFuture<JdkOptionsData>> = CollectionFactory.createConcurrentSoftValueMap()

    private const val ENABLE_ASSERTIONS_DESCRIPTION = "Enables assertions with specified granularity."
    private const val DISABLE_ASSERTIONS_DESCRIPTION = "Disables assertions with specified granularity."
    private const val ENABLES_SYSTEM_ASSERTIONS_DESCRIPTION = "Enables system assertions."
    private const val DISABLES_SYSTEM_ASSERTIONS_DESCRIPTION = "Disables system assertions."

    @JvmStatic
    @get:JvmName("getStandardOptionList")
    val STANDARD_OPTION_LIST : List<VMOption> = listOf(
      opt("ea", ENABLE_ASSERTIONS_DESCRIPTION),
      opt("enableassertions", ENABLE_ASSERTIONS_DESCRIPTION),
      opt("da", DISABLE_ASSERTIONS_DESCRIPTION),
      opt("disableassertions", DISABLE_ASSERTIONS_DESCRIPTION),
      opt("esa", ENABLES_SYSTEM_ASSERTIONS_DESCRIPTION),
      opt("enablesystemassertions", ENABLES_SYSTEM_ASSERTIONS_DESCRIPTION),
      opt("dsa", DISABLES_SYSTEM_ASSERTIONS_DESCRIPTION),
      opt("disablesystemassertions", DISABLES_SYSTEM_ASSERTIONS_DESCRIPTION),
      opt("agentpath:", "Loads native agent library by &lt;pathname&gt;."),
      opt("agentlib:", "Loads native agent library by &lt;libname&gt;."),
      opt("javaagent:", "Loads Java programming language agent by &lt;jarpath&gt;."),
      opt("D", "Sets a system property in format &lt;name&gt;=&lt;value&gt;."),
      opt("XX:", "Specify non-standard JVM-specific option.")
    )

    private fun opt(name: String, doc: String): VMOption {
      return VMOption(name, null, null, VMOptionKind.Standard, doc, VMOptionVariant.DASH, null)
    }
  }

  override fun getOrComputeOptionsForJdk(javaHome: String): CompletableFuture<JdkOptionsData> {
    val future = ourData.computeIfAbsent(javaHome) { CompletableFuture.supplyAsync { computeOptionsData(it) } }
    if (future.isDone) {
      // sometimes the timeout may appear and in order not to block the possibility to get the completion afterwards, it is better to retry
      val data = future.get()
      if (data == null) {
        ourData.remove(javaHome)
      }
    }
    return future
  }

  override fun getStandardOptions(): JdkOptionsData = JdkOptionsData(STANDARD_OPTION_LIST)

  // when null is returned, it was a timeout
  private fun computeOptionsData(javaHome: String): JdkOptionsData {
    return JdkOptionsData(getOptionsForJdk(javaHome))
  }

  private fun getOptionsForJdk(javaHome: String): List<VMOption> {
    val vmPath = getVmPath(javaHome)
    val generalCommandLine = GeneralCommandLine(vmPath)
    generalCommandLine.addParameters("-XX:+PrintFlagsFinal", "-XX:+UnlockDiagnosticVMOptions", "-XX:+UnlockExperimentalVMOptions", "-X")
    val handler = try {
      OSProcessHandler(generalCommandLine)
    }
    catch (e: ProcessNotCreatedException) {
      return STANDARD_OPTION_LIST
    }
    val runner = CapturingProcessRunner(handler)
    val output = runner.runProcess(1000)
    if (output.isTimeout) {
      return STANDARD_OPTION_LIST
    }
    val xxOptions = VMOptionsParser.parseXXOptions(output.stdout)
    val xOptions = VMOptionsParser.parseXOptions(output.stderr)
    if (xOptions != null) {
      return xOptions + xxOptions + STANDARD_OPTION_LIST
    }
    return xxOptions + STANDARD_OPTION_LIST
  }

  private fun getVmPath(javaHome: String): String {
    val vmExeName = if (SystemInfo.isWindows) "java.exe" else "java" // do not use JavaW.exe because of issues with encoding
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
}