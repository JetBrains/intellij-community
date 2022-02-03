// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.dependencies.*

import java.nio.file.Files
import java.nio.file.Path

@CompileStatic
class BundledMavenDownloader {
  private static List<String> mavenCommonLibs = [
    'org.apache.maven.archetype:archetype-common:2.2',
    'org.apache.maven.archetype:archetype-catalog:2.2',
    'org.apache.maven.archetype:archetype-descriptor:2.2',
    'org.apache.maven.shared:maven-dependency-tree:1.2',
    'org.sonatype.nexus:nexus-indexer:3.0.4',
    'org.sonatype.nexus:nexus-indexer-artifact:1.0.1',
    'org.apache.lucene:lucene-core:2.4.1',
  ]

  static Path downloadMavenCommonLibs(BuildDependenciesCommunityRoot communityRoot) {
    Path root = communityRoot.communityRoot.resolve("plugins/maven/maven3-server-common/lib")
    BuildDependenciesUtil.cleanDirectory(root)

    for (String coordinates : mavenCommonLibs) {
      String[] split = coordinates.split(":")
      if (split.length != 3) {
        throw new IllegalArgumentException("Expected exactly 3 coordinates: " + coordinates);
      }

      URI uri = BuildDependenciesDownloader.getUriForMavenArtifact(
        BuildDependenciesConstants.MAVEN_CENTRAL_URL,
        split[0], split[1], split[2], "jar"
      )

      Path file = BuildDependenciesDownloader.downloadFileToCacheLocation(communityRoot, uri)
      Files.copy(file, root.resolve(split[1] + "-" + split[2] + ".jar"))
    }

    return root
  }

  static Path downloadMavenDistribution(BuildDependenciesCommunityRoot communityRoot) {
    Properties properties = BuildDependenciesDownloader.getDependenciesProperties(communityRoot)
    String bundledMavenVersion = properties.getProperty("bundledMavenVersion")

    URI uri = BuildDependenciesDownloader.getUriForMavenArtifact(
      BuildDependenciesConstants.MAVEN_CENTRAL_URL,
      "org.apache.maven",
      "apache-maven",
      bundledMavenVersion,
      "bin",
      "zip"
    )
    Path zipPath = BuildDependenciesDownloader.downloadFileToCacheLocation(communityRoot, uri)

    Path extractDir = communityRoot.communityRoot.resolve("plugins/maven/maven36-server-impl/lib/maven3")
    BuildDependenciesDownloader.extractFile(zipPath, extractDir, communityRoot, BuildDependenciesExtractOptions.STRIP_ROOT)

    return extractDir
  }

  static void main(String[] args) {
    Path distRoot = downloadMavenDistribution(BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory)
    Path commonLibs = downloadMavenCommonLibs(BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory)

    println "Maven distribution extracted at $distRoot"
    println "Maven common libs at $commonLibs"
  }
}
