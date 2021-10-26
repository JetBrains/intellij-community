// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.kotlin

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.dependencies.BuildDependenciesManualRunOnly

import java.nio.file.Path

import static org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.downloadFileToCacheLocation
import static org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.extractFileToCacheLocation
import static org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.getDependenciesProperties
import static org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.getUriForMavenArtifact

@CompileStatic
class KotlinCompilerDependencyDownloader {
  static Path downloadAndExtractKotlinPlugin(Path communityRoot) {
    String kotlinCompilerBuild = getDependenciesProperties(communityRoot).getProperty("kotlinCompilerBuild")

    String pluginsMavenRepository = "https://cache-redirector.jetbrains.com/plugins.jetbrains.com/maven"

    def pluginChannelSeparator = kotlinCompilerBuild.indexOf(":")
    def groupId = pluginChannelSeparator == -1 ? "com.jetbrains.plugins"
                                               : "${kotlinCompilerBuild.substring(pluginChannelSeparator + 1)}.com.jetbrains.plugins"
    def pluginArtifactVersion = pluginChannelSeparator == -1 ? kotlinCompilerBuild : kotlinCompilerBuild.substring(0, pluginChannelSeparator)
    URI pluginZipUrl = getUriForMavenArtifact(
      pluginsMavenRepository,
      groupId.toString(),
      "org.jetbrains.kotlin",
      pluginArtifactVersion,
      "zip"
    )

    Path pluginZip = downloadFileToCacheLocation(communityRoot, pluginZipUrl)
    return extractFileToCacheLocation(communityRoot, pluginZip)
  }

  static void main(String[] args) {
    Path path = downloadAndExtractKotlinPlugin(BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory)
    println("Extracted Kotlin compiler is at " + path)
  }
}
