// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.intellij.build.dependencies.*
import org.jetbrains.intellij.build.downloadFileToCacheLocation
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readBytes

private val maven3Libs: List<String> = listOf(
  "org.apache.maven.archetype:archetype-common:2.2",
  "org.apache.maven.archetype:archetype-catalog:2.2",
  "org.apache.maven.archetype:archetype-descriptor:2.2",
  "org.apache.maven.shared:maven-dependency-tree:1.2",
  "org.sonatype.nexus:nexus-indexer:3.0.4",
  "org.sonatype.nexus:nexus-indexer-artifact:1.0.1",
  "org.apache.lucene:lucene-core:2.4.1",
)

private val maven4Libs: List<String> = listOf(
  // let's not bundle archetype plugin version 3 with maven version 4
/*  "org.apache.maven.archetype:archetype-common:3.2.1",
  "org.apache.maven.archetype:archetype-catalog:3.2.1",
  "org.apache.maven.archetype:archetype-descriptor:3.2.1",
  "org.apache.maven.shared:maven-artifact-transfer:0.13.1",
  "org.jdom:jdom2:2.0.6.1",*/
)

object BundledMavenDownloader {
  private val mutex = Mutex()

  @JvmStatic
  fun main(args: Array<String>) {
    val communityRoot = BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory
    runBlocking(Dispatchers.Default) {
      val distRoot = downloadMavenDistribution(communityRoot)
      val maven3DownloadedLibs = downloadMaven3Libs(communityRoot)
      val maven4DownloadedLibs = downloadMaven4Libs(communityRoot)
      println("Maven distribution extracted at $distRoot")
      println("Maven 3 libs at $maven3DownloadedLibs")
      println("Maven 4 libs at $maven4DownloadedLibs")
    }
  }

  private fun fileChecksum(path: Path): String {
    val md5 = MessageDigest.getInstance("MD5")
    md5.update(path.readBytes())
    val digest = md5.digest()
    return BigInteger(1, digest).toString(32)
  }

  fun downloadMaven4LibsSync(communityRoot: BuildDependenciesCommunityRoot): Path =
    runBlocking(Dispatchers.Default) {
      downloadMaven4Libs(communityRoot)
    }

  suspend fun downloadMaven4Libs(communityRoot: BuildDependenciesCommunityRoot): Path =
    downloadMavenLibs(communityRoot, "plugins/maven/maven40-server-impl/lib", maven4Libs)

  fun downloadMaven3LibsSync(communityRoot: BuildDependenciesCommunityRoot): Path =
    runBlocking(Dispatchers.Default) {
      downloadMaven3Libs(communityRoot)
    }

  suspend fun downloadMaven3Libs(communityRoot: BuildDependenciesCommunityRoot): Path =
    downloadMavenLibs(communityRoot, "plugins/maven/maven3-server-common/lib", maven3Libs)

  @OptIn(ExperimentalCoroutinesApi::class)
  private suspend fun downloadMavenLibs(communityRoot: BuildDependenciesCommunityRoot, path: String, libs: List<String>): Path {
    val root = communityRoot.communityRoot.resolve(path)
    Files.createDirectories(root)
    val targetToSourceFiles = coroutineScope {
      libs.map { coordinates ->
        async {
          val split = coordinates.split(':')
          check(split.size == 3) {
            "Expected exactly 3 coordinates: $coordinates"
          }
          val targetFile = root.resolve("${split[1]}-${split[2]}.jar")
          val uri = BuildDependenciesDownloader.getUriForMavenArtifact(
            mavenRepository = BuildDependenciesConstants.MAVEN_CENTRAL_URL,
            groupId = split[0],
            artifactId = split[1],
            version = split[2],
            packaging = "jar"
          )
          targetFile to downloadFileToCacheLocation(uri.toString(), communityRoot)
        }
      }
    }.asSequence().map { it.getCompleted() }.toMap()

    root.listDirectoryEntries().forEach {  file ->
      if (!targetToSourceFiles.containsKey(file)) {
        BuildDependenciesUtil.deleteFileOrFolder(file)
      }
    }

    withContext(Dispatchers.IO) {
      for (targetFile in targetToSourceFiles.keys) {
        val sourceFile = targetToSourceFiles.getValue(targetFile)
        launch {
          mutex.withLock {
            if (Files.notExists(targetFile)) {
              Files.copy(sourceFile, targetFile)
            }
            else {
              val sourceCheckSum = fileChecksum(sourceFile)
              val targetCheckSum = fileChecksum(targetFile)
              if (sourceCheckSum != targetCheckSum) {
                Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING)
              }
            }
          }
        }
      }
    }
    return root
  }

  fun downloadMavenDistributionSync(communityRoot: BuildDependenciesCommunityRoot): Path {
    return runBlocking(Dispatchers.Default) {
      downloadMavenDistribution(communityRoot)
    }
  }

  suspend fun downloadMavenDistribution(communityRoot: BuildDependenciesCommunityRoot): Path {
    val extractDir = communityRoot.communityRoot.resolve("plugins/maven/maven36-server-impl/lib/maven3")
    val properties = BuildDependenciesDownloader.getDependencyProperties(communityRoot)
    val bundledMavenVersion = properties.property("bundledMavenVersion")
    mutex.withLock {
      val uri = BuildDependenciesDownloader.getUriForMavenArtifact(
        mavenRepository = BuildDependenciesConstants.MAVEN_CENTRAL_URL,
        groupId = "org.apache.maven",
        artifactId = "apache-maven",
        version = bundledMavenVersion,
        classifier = "bin",
        packaging = "zip"
      )
      val zipPath = downloadFileToCacheLocation(uri.toString(), communityRoot)
      BuildDependenciesDownloader.extractFile(zipPath, extractDir, communityRoot, BuildDependenciesExtractOptions.STRIP_ROOT)
    }
    return extractDir
  }
}
