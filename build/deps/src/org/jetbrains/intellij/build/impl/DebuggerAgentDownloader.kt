// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesConstants
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import org.jetbrains.intellij.build.dependencies.BuildDependenciesManualRunOnly
import java.nio.file.Path

object DebuggerAgentDownloader {
  /**
   * See [github.com/JetBrains/debugger-agent](https://github.com/JetBrains/debugger-agent)
   */
  fun downloadDebuggerAgent(communityRoot: BuildDependenciesCommunityRoot): Path {
    val properties = BuildDependenciesDownloader.getDependenciesProperties(communityRoot)
    val debuggerAgentVersion = properties.property("debuggerAgent")

    val uri = BuildDependenciesDownloader.getUriForMavenArtifact(
      BuildDependenciesConstants.INTELLIJ_DEPENDENCIES_URL,
      "org.jetbrains.intellij.deps",
      "debugger-agent",
      debuggerAgentVersion,
      "jar"
    )
    return BuildDependenciesDownloader.downloadFileToCacheLocation(communityRoot, uri)
  }

  @JvmStatic
  fun main(args: Array<String>) {
    val communityRoot = BuildDependenciesManualRunOnly.getCommunityRootFromWorkingDirectory()
    println("Debugger agent is at " + downloadDebuggerAgent(communityRoot))
  }
}