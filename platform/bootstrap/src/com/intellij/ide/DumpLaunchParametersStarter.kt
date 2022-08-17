// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.idea.AppExitCodes
import com.intellij.openapi.application.ModernApplicationStarter
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.system.exitProcess

internal class DumpLaunchParametersStarter : ModernApplicationStarter() {
  override val commandName: String
    get() = "dump-launch-parameters"

  override fun premain(args: List<String>) {
    val argsMap = args.fold(Pair(emptyMap<String, List<String>>(), "")) { (map, lastKey), elem ->
      if (elem.startsWith("-")) Pair(map + (elem to emptyList()), elem)
      else Pair(map + (lastKey to map.getOrDefault(lastKey, emptyList()) + elem), lastKey)
    }.first

    if (!argsMap.containsKey("-f") || argsMap["-f"]!!.isEmpty()) {
      exitProcess(AppExitCodes.STARTUP_EXCEPTION)
    }

    val outputDir = argsMap["-f"]!![0]

    val launchParametersDump = File(outputDir)
    writeVmOptionsInFile(launchParametersDump)
    writeCommandLineArgumentsInFile(launchParametersDump, args)

    if (argsMap.containsKey("-p") && !argsMap["-p"]!!.isEmpty()) {
      launchParametersDump.appendText("[javaSystemProperties]\n")
      argsMap["-p"]?.forEach { writeJavaSystemPropertyInFile(launchParametersDump, it) }
    }

    if (argsMap.containsKey("-e") && !argsMap["-e"]!!.isEmpty()) {
      launchParametersDump.appendText("[environmentVariables]\n")
      argsMap["-e"]?.forEach { writeEnvironmentVariableInFile(launchParametersDump, it) }
    }

    exitProcess(0)
  }

  override suspend fun start(args: List<String>) {
    exitProcess(0)
  }

  private fun writeVmOptionsInFile(dumpFile: File) {
    val vmOptions = ManagementFactory.getRuntimeMXBean().inputArguments

    dumpFile.appendText("[vmOptions]\n")
    vmOptions.forEachIndexed { i, vmOption ->
      dumpFile.appendText("vmOption.$i=$vmOption\n")
    }
  }

  private fun writeCommandLineArgumentsInFile(dumpFile: File, args: List<String>) {
    dumpFile.appendText("[arguments]\n")
    args.forEachIndexed { i, argument ->
      dumpFile.appendText("argument.$i=$argument\n")
    }
  }

  private fun writeJavaSystemPropertyInFile(dumpFile: File, property: String) {
    val javaProperty = System.getProperty(property)

    dumpFile.appendText("$property=$javaProperty\n")
  }

  private fun writeEnvironmentVariableInFile(dumpFile: File, envVariable: String) {
    val environmentVariable = System.getenv(envVariable)
    dumpFile.appendText("$envVariable=$environmentVariable\n")
  }
}