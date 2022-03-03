// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfo
import com.intellij.util.SystemProperties
import com.intellij.util.execution.ParametersListUtil
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import org.jetbrains.intellij.build.dependencies.BuildDependenciesExtractOptions
import org.jetbrains.intellij.build.dependencies.BuildDependenciesManualRunOnly

import java.nio.file.Files
import java.nio.file.Path

@CompileStatic
class ToolboxLiteGen {
  static Path downloadToolboxLiteGen(BuildDependenciesCommunityRoot communityRoot, String liteGenVersion) {
    URI liteGenUri = new URI("https://repo.labs.intellij.net/toolbox/lite-gen/lite-gen-${liteGenVersion}.zip")
    Path zip = BuildDependenciesDownloader.downloadFileToCacheLocation(communityRoot, liteGenUri)
    Path path = BuildDependenciesDownloader.extractFileToCacheLocation(communityRoot, zip, BuildDependenciesExtractOptions.STRIP_ROOT)
    return path
  }

  static void runToolboxLiteGen(BuildDependenciesCommunityRoot communityRoot, BuildMessages messages, String liteGenVersion, String... args) {
    if (!SystemInfo.isUnix) {
      throw new IllegalStateException("Currently, lite gen runs only on Unix")
    }

    Path liteGenPath = downloadToolboxLiteGen(communityRoot, liteGenVersion)
    messages.info("Toolbox LiteGen is at $liteGenPath")

    Path binPath = liteGenPath.resolve("bin/lite")
    if (!Files.isExecutable(binPath)) {
      throw new IllegalStateException("File at '$binPath' is missing or not executable")
    }

    List<String> command = new ArrayList<>()
    command.add(binPath.toString())
    command.addAll(args.toList())

    messages.info("Running ${ParametersListUtil.join(command)}")

    ProcessBuilder processBuilder = new ProcessBuilder(command)
    processBuilder.directory(liteGenPath.toFile())
    processBuilder.environment().put("JAVA_HOME", SystemProperties.javaHome)
    def process = processBuilder.start()
    process.consumeProcessOutputStream((OutputStream)System.out)
    process.consumeProcessErrorStream((OutputStream)System.err)
    int rc = process.waitFor()
    if (rc != 0) {
      throw new IllegalStateException("'${command.join(" ")}' exited with exit code $rc")
    }
  }

  // debug only
  static void main(String[] args) {
    Path path = downloadToolboxLiteGen(BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory, "1.2.1553")
    println("litegen is at $path")
  }
}
