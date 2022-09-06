// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.utils

import com.google.gson.GsonBuilder
import com.intellij.idea.AppExitCodes
import com.intellij.openapi.application.ModernApplicationStarter
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.writeText
import kotlin.system.exitProcess

/*
{
  "cmdArguments": [
    "/private/var/folders/jx/x1bytp2j53n6dj649pdpw7_m0000gr/T/1661441672/Contents/bin/xplat-launcher",
    "--output",
    "/private/var/folders/jx/x1bytp2j53n6dj649pdpw7_m0000gr/T/1661441672/Contents/bin/output.json"
  ],
  "vmOptions": [
    "-Didea.vendor.name\u003dJetBrains",
...
    "--add-opens\u003djdk.jdi/com.sun.tools.jdi\u003dALL-UNNAMED"
  ],
  "environmentVariables": {
    "PATH": "/Users/haze/Library/Python/3.8/bin:/opt/homebrew/bin:/opt/homebrew/sbin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:/Users/haze/.cargo/bin:/Users/haze/Library/Application Support/JetBrains/Toolbox/scripts:/opt/homebrew/opt/fzf/bin",
..
    "DYLD_FALLBACK_LIBRARY_PATH": "/Users/haze/work/intellij/master/community/native/XPlatLauncher/target/debug/deps:/Users/haze/work/intellij/master/community/native/XPlatLauncher/target/debug:/Users/haze/.rustup/toolchains/stable-aarch64-apple-darwin/lib/rustlib/aarch64-apple-darwin/lib:/Users/haze/.rustup/toolchains/stable-aarch64-apple-darwin/lib:/Users/haze/lib:/usr/local/lib:/usr/lib"
  },
  "systemProperties": {
    "java.specification.version": "17",
...
    "splash": "true"
  }
}
 */

data class DumpedLaunchParameters(
  val cmdArguments: List<String>,
  val vmOptions: List<String>,
  val environmentVariables: Map<String, String>,
  val systemProperties: Map<String, String>
)

internal class DumpLaunchParametersStarter : ModernApplicationStarter() {
  override val commandName: String
    get() = "dump-launch-parameters"

  override fun premain(args: List<String>) {
    val outputIndex = args.indexOfFirst { it == "-o" || it == "--output" } + 1
    if (outputIndex == 0) {
      System.err.println("Usage: -o/--output /path/to/output/file")
      System.err.println("Current args: ${args.joinToString(" ")}")
      exitProcess(AppExitCodes.STARTUP_EXCEPTION)
    }

    val outputFile = Path.of(args[outputIndex])
    Files.createDirectories(outputFile.parent)

    val gson = GsonBuilder().setPrettyPrinting().create()

    @Suppress("UNCHECKED_CAST")
    val dump = DumpedLaunchParameters(
      cmdArguments = args,
      vmOptions = ManagementFactory.getRuntimeMXBean().inputArguments,
      systemProperties = System.getProperties() as? Map<String, String>
        ?: error("Failed to cast System.getProperties() result to Map<String, String>"),
      environmentVariables = System.getenv()
    )

    val dumpJsonText = gson.toJson(dump)
    println(dumpJsonText)

    outputFile.writeText(dumpJsonText, Charsets.UTF_8)
    println("Dumped to ${outputFile.absolutePathString()}")

    exitProcess(0)
  }

  override suspend fun start(args: List<String>) {
    exitProcess(0)
  }
}