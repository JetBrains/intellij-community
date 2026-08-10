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
import org.jetbrains.intellij.build.dependencies.extractFileToCacheLocation
import org.jetbrains.intellij.build.downloadFileToCacheLocation
import org.jetbrains.intellij.build.resolveFileForReading
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat
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
  data class MavenLibraryFile(
    @JvmField val fileName: String,
    @JvmField val source: Path,
    @JvmField val sha256: String,
  )

  private val distributionMutex = Mutex()

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
    val digest = MessageDigest.getInstance("SHA-256").digest(path.readBytes())
    return HexFormat.of().formatHex(digest)
  }

  fun downloadMaven4LibsSync(communityRoot: BuildDependenciesCommunityRoot): Path {
    return runBlocking(Dispatchers.Default) {
      downloadMaven4Libs(communityRoot)
    }
  }

  suspend fun downloadMaven4Libs(communityRoot: BuildDependenciesCommunityRoot): Path {
    return downloadMavenLibs(communityRoot, "maven40-server-impl", maven4Libs)
  }

  suspend fun resolveMaven4Libs(communityRoot: BuildDependenciesCommunityRoot): List<MavenLibraryFile> {
    return resolveMavenLibs(communityRoot, maven4Libs)
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

  suspend fun resolveMaven3Libs(communityRoot: BuildDependenciesCommunityRoot): List<MavenLibraryFile> {
    val properties = BuildDependenciesDownloader.getDependencyProperties(communityRoot)
    return resolveMavenLibs(communityRoot, parseLibraries(properties.property(MAVEN_3_LIBRARIES_PROPERTY)))
  }

  private suspend fun downloadMavenLibs(communityRoot: BuildDependenciesCommunityRoot, path: String, libs: List<String>): Path {
    val libraryFiles = resolveMavenLibs(communityRoot, libs)
    val inventoryDigest = MessageDigest.getInstance("SHA-256")
    for ((fileName, _, sha256) in libraryFiles.sortedBy { it.fileName }) {
      inventoryDigest.update(fileName.toByteArray())
      inventoryDigest.update(0.toByte())
      inventoryDigest.update(sha256.toByteArray())
      inventoryDigest.update(0.toByte())
    }
    val inventoryId = HexFormat.of().formatHex(inventoryDigest.digest())
    val root = BuildDependenciesDownloader.getDownloadCacheDirectory(communityRoot).resolve("maven-libraries-$path-$inventoryId")
    withContext(Dispatchers.IO) {
      Files.createDirectories(root)
      Files.setLastModifiedTime(root, FileTime.from(Instant.now()))
    }
    withContext(Dispatchers.IO) {
      for ((fileName, source, sha256) in libraryFiles) {
        val targetFile = root.resolve(fileName)
        if (Files.notExists(targetFile) || sha256 != fileChecksum(targetFile)) {
          val tempFile = Files.createTempFile(root, fileName, ".tmp")
          try {
            Files.copy(source, tempFile, StandardCopyOption.REPLACE_EXISTING)
            Files.move(tempFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
          }
          finally {
            Files.deleteIfExists(tempFile)
          }
        }
      }
      Files.setLastModifiedTime(root, FileTime.from(Instant.now()))
    }
    return root
  }

  private suspend fun resolveMavenLibs(communityRoot: BuildDependenciesCommunityRoot, libs: List<String>): List<MavenLibraryFile> {
    val fileNameToUri = libs.associate { coordinates ->
      val split = coordinates.split(':')
      check(split.size == 3) {
        "Expected exactly 3 coordinates: $coordinates"
      }
      val fileName = "${split[1]}-${split[2]}.jar"
      val uri = BuildDependenciesDownloader.getUriForMavenArtifact(
        mavenRepository = BuildDependenciesConstants.MAVEN_CENTRAL_URL,
        groupId = split[0],
        artifactId = split[1],
        version = split[2],
        packaging = "jar"
      )
      fileName to uri
    }

    return coroutineScope {
      fileNameToUri.map { (fileName, uri) ->
        async {
          val source = resolveFileForReading(uri.toString(), communityRoot)
          MavenLibraryFile(fileName = fileName, source = source, sha256 = fileChecksum(source))
        }
      }.awaitAll()
    }
  }

  fun downloadMavenDistributionSync(communityRoot: BuildDependenciesCommunityRoot): Path {
    return runBlocking(Dispatchers.Default) {
      downloadMavenDistribution(communityRoot)
    }
  }

  suspend fun downloadMavenDistribution(communityRoot: BuildDependenciesCommunityRoot): Path {
    val properties = BuildDependenciesDownloader.getDependencyProperties(communityRoot)
    val bundledMavenVersion = properties.property("bundledMavenVersion")
    return distributionMutex.withLock {
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

  suspend fun resolveMavenTelemetryDependencies(communityRoot: BuildDependenciesCommunityRoot): List<MavenLibraryFile> {
    val properties = BuildDependenciesDownloader.getDependencyProperties(communityRoot)
    return resolveMavenLibs(communityRoot, parseLibraries(properties.property(MAVEN_TELEMETRY_LIBRARIES_PROPERTY)))
  }

  private fun parseLibraries(value: String): List<String> {
    return value.split(',').map { it.trim() }.also { libraries ->
      check(libraries.isNotEmpty() && libraries.all { it.isNotEmpty() }) { "Maven library list is empty or malformed: '$value'" }
    }
  }
}
