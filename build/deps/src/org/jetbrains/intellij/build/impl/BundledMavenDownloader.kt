// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesConstants
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import org.jetbrains.intellij.build.dependencies.BuildDependenciesManualRunOnly
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil
import org.jetbrains.intellij.build.dependencies.extractFileToCacheLocation
import org.jetbrains.intellij.build.downloadFileToCacheLocation
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readBytes

private val maven4Libs: List<String> = listOf(
  // let's not bundle archetype plugin version 3 with maven version 4
  /*  "org.apache.maven.archetype:archetype-common:3.2.1",
    "org.apache.maven.archetype:archetype-catalog:3.2.1",
    "org.apache.maven.archetype:archetype-descriptor:3.2.1",
    "org.apache.maven.shared:maven-artifact-transfer:0.13.1",
    "org.jdom:jdom2:2.0.6.1",*/
)

private const val MAVEN_3_LIBRARIES_PROPERTY = "bundledMaven3Libraries"
private const val MAVEN_TELEMETRY_LIBRARIES_PROPERTY = "bundledMavenTelemetryLibraries"

object BundledMavenDownloader {
  private val mutex = Mutex()

  @JvmStatic
  fun main(args: Array<String>) {
    val communityRoot = BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory
    runBlocking(Dispatchers.Default) {
      val distRoot = downloadMavenDistribution(communityRoot)
      val mavenTelemetryDependencies = downloadMavenTelemetryDependencies(communityRoot)
      val maven3DownloadedLibs = downloadMaven3Libs(communityRoot)
      val maven4DownloadedLibs = downloadMaven4Libs(communityRoot)
      println("Maven distribution extracted at $distRoot")
      println("Maven telemetry dependencies at $mavenTelemetryDependencies")
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

  fun downloadMaven4LibsSync(communityRoot: BuildDependenciesCommunityRoot): Path {
    return runBlocking(Dispatchers.Default) {
      downloadMaven4Libs(communityRoot)
    }
  }

  suspend fun downloadMaven4Libs(communityRoot: BuildDependenciesCommunityRoot): Path {
    return downloadMavenLibs(communityRoot, "maven40-server-impl", maven4Libs)
  }

  fun downloadMaven3LibsSync(communityRoot: BuildDependenciesCommunityRoot): Path {
    return runBlocking(Dispatchers.Default) {
      downloadMaven3Libs(communityRoot)
    }
  }

  suspend fun downloadMaven3Libs(communityRoot: BuildDependenciesCommunityRoot): Path {
    val properties = BuildDependenciesDownloader.getDependencyProperties(communityRoot)
    return downloadMavenLibs(communityRoot, "maven3-server-common", parseLibraries(properties.property(MAVEN_3_LIBRARIES_PROPERTY)))
  }

  private suspend fun downloadMavenLibs(communityRoot: BuildDependenciesCommunityRoot, path: String, libs: List<String>): Path {
    val root = BuildDependenciesDownloader.getDownloadCacheDirectory(communityRoot).resolve("maven-libraries").resolve(path)
    withContext(Dispatchers.IO) {
      Files.createDirectories(root)
    }
    val targetFileToUris = libs.associate { coordinates ->
      val split = coordinates.split(':')
      check(split.size == 3) {
        "Expected exactly 3 coordinates: $coordinates"
      }
      val file = root.resolve("${split[1]}-${split[2]}.jar")
      val uri = BuildDependenciesDownloader.getUriForMavenArtifact(
        mavenRepository = BuildDependenciesConstants.MAVEN_CENTRAL_URL,
        groupId = split[0],
        artifactId = split[1],
        version = split[2],
        packaging = "jar"
      )
      file to uri
    }

    val targetToSourceFiles = coroutineScope {
      targetFileToUris.map { (targetFile, uri) ->
        async {
          targetFile to downloadFileToCacheLocation(uri.toString(), communityRoot)
        }
      }.awaitAll().toMap()
    }

    withContext(Dispatchers.IO) {
      mutex.withLock {
        root.listDirectoryEntries().forEach { file ->
          if (!targetFileToUris.containsKey(file)) {
            BuildDependenciesUtil.deleteFileOrFolder(file)
          }
        }
        for ((targetFile, sourceFile) in targetToSourceFiles) {
          if (Files.notExists(targetFile) || fileChecksum(sourceFile) != fileChecksum(targetFile)) {
            val tempFile = Files.createTempFile(root, targetFile.fileName.toString(), ".tmp")
            try {
              Files.copy(sourceFile, tempFile, StandardCopyOption.REPLACE_EXISTING)
              Files.move(tempFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }
            finally {
              Files.deleteIfExists(tempFile)
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
    val properties = BuildDependenciesDownloader.getDependencyProperties(communityRoot)
    val bundledMavenVersion = properties.property("bundledMavenVersion")
    return mutex.withLock {
      val uri = BuildDependenciesDownloader.getUriForMavenArtifact(
        mavenRepository = BuildDependenciesConstants.MAVEN_CENTRAL_URL,
        groupId = "org.apache.maven",
        artifactId = "apache-maven",
        version = bundledMavenVersion,
        classifier = "bin",
        packaging = "zip"
      )
      val zipPath = downloadFileToCacheLocation(uri.toString(), communityRoot)
      extractFileToCacheLocation(archiveFile = zipPath, communityRoot = communityRoot, stripRoot = true)
    }
  }

  suspend fun downloadMavenTelemetryDependencies(communityRoot: BuildDependenciesCommunityRoot): Path {
    val properties = BuildDependenciesDownloader.getDependencyProperties(communityRoot)
    return downloadMavenLibs(
      communityRoot,
      "maven-server-telemetry",
      parseLibraries(properties.property(MAVEN_TELEMETRY_LIBRARIES_PROPERTY)),
    )
  }

  private fun parseLibraries(value: String): List<String> {
    return value.split(',').map { it.trim() }.also { libraries ->
      check(libraries.isNotEmpty() && libraries.all { it.isNotEmpty() }) { "Maven library list is empty or malformed: '$value'" }
    }
  }
}
