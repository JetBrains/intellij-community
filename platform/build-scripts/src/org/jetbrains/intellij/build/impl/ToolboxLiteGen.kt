// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfo
import com.intellij.util.SystemProperties
import com.intellij.util.execution.ParametersListUtil
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import org.jetbrains.intellij.build.dependencies.BuildDependenciesExtractOptions
import org.jetbrains.intellij.build.dependencies.BuildDependenciesManualRunOnly
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

internal object ToolboxLiteGen {
  private fun downloadToolboxLiteGen(communityRoot: BuildDependenciesCommunityRoot, liteGenVersion: String): Path {
    val liteGenUri = URI("https://repo.labs.intellij.net/toolbox/lite-gen/lite-gen-$liteGenVersion.zip")
    val zip = BuildDependenciesDownloader.downloadFileToCacheLocation(communityRoot, liteGenUri)
    return BuildDependenciesDownloader.extractFileToCacheLocation(communityRoot, zip, BuildDependenciesExtractOptions.STRIP_ROOT)
  }

  fun runToolboxLiteGen(communityRoot: BuildDependenciesCommunityRoot,
                        messages: BuildMessages,
                        liteGenVersion: String,
                        vararg args: String) {
    check(SystemInfo.isUnix) { "Currently, lite gen runs only on Unix" }
    val liteGenPath = downloadToolboxLiteGen(communityRoot, liteGenVersion)
    messages.info("Toolbox LiteGen is at $liteGenPath")
    val binPath = liteGenPath.resolve("bin/lite")
    check(Files.isExecutable(binPath)) { "File at \'$binPath\' is missing or not executable" }
    val command: MutableList<String?> = ArrayList()
    command.add(binPath.toString())
    command.addAll(args)
    messages.info("Running " + ParametersListUtil.join(command))
    val processBuilder = ProcessBuilder(command)
    processBuilder.directory(liteGenPath.toFile())
    @Suppress("ReplacePutWithAssignment")
    processBuilder.environment().put("JAVA_HOME", SystemProperties.getJavaHome())
    processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT)
    processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
    val process = processBuilder.start()
    val rc = process.waitFor()
    check(rc == 0) { "\'${command.joinToString(separator = " ")}\' exited with exit code $rc" }
  }

  @JvmStatic
  fun main(args: Array<String>) {
    val path = downloadToolboxLiteGen(BuildDependenciesManualRunOnly.getCommunityRootFromWorkingDirectory(), "1.2.1553")
    println("litegen is at $path")
  }
}