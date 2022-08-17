// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.dependencies.*
import java.nio.file.Files
import java.nio.file.Path

object BundledMavenDownloader {
  private val mavenCommonLibs: List<String> = listOf(
    "org.apache.maven.archetype:archetype-common:2.2",
    "org.apache.maven.archetype:archetype-catalog:2.2",
    "org.apache.maven.archetype:archetype-descriptor:2.2",
    "org.apache.maven.shared:maven-dependency-tree:1.2",
    "org.sonatype.nexus:nexus-indexer:3.0.4",
    "org.sonatype.nexus:nexus-indexer-artifact:1.0.1",
    "org.apache.lucene:lucene-core:2.4.1",
  )

  @JvmStatic
  fun main(args: Array<String>) {
    val communityRoot = BuildDependenciesManualRunOnly.getCommunityRootFromWorkingDirectory()
    val distRoot = downloadMavenDistribution(communityRoot)
    val commonLibs = downloadMavenCommonLibs(communityRoot)

    println("Maven distribution extracted at $distRoot")
    println("Maven common libs at $commonLibs")
  }

  fun downloadMavenCommonLibs(communityRoot: BuildDependenciesCommunityRoot): Path {
    val root = communityRoot.communityRoot.resolve("plugins/maven/maven3-server-common/lib")
    BuildDependenciesUtil.cleanDirectory(root)

    for (coordinates in mavenCommonLibs) {
      val split = coordinates.split(':')
      check(split.size == 3) {
        "Expected exactly 3 coordinates: $coordinates"
      }

      val uri = BuildDependenciesDownloader.getUriForMavenArtifact(
        BuildDependenciesConstants.MAVEN_CENTRAL_URL,
        split[0], split[1], split[2], "jar"
      )

      val file = BuildDependenciesDownloader.downloadFileToCacheLocation(communityRoot, uri)
      Files.copy(file, root.resolve("${split[1]}-${split[2]}.jar"))
    }
    return root
  }

  fun downloadMavenDistribution(communityRoot: BuildDependenciesCommunityRoot): Path {
    val properties = BuildDependenciesDownloader.getDependenciesProperties(communityRoot)
    val bundledMavenVersion = properties.property("bundledMavenVersion")

    val uri = BuildDependenciesDownloader.getUriForMavenArtifact(
      BuildDependenciesConstants.MAVEN_CENTRAL_URL,
      "org.apache.maven",
      "apache-maven",
      bundledMavenVersion,
      "bin",
      "zip"
    )
    val zipPath = BuildDependenciesDownloader.downloadFileToCacheLocation(communityRoot, uri)

    val extractDir = communityRoot.communityRoot.resolve("plugins/maven/maven36-server-impl/lib/maven3")
    BuildDependenciesDownloader.extractFile(zipPath, extractDir, communityRoot, BuildDependenciesExtractOptions.STRIP_ROOT)

    return extractDir
  }
}
