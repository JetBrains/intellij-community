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
import org.jetbrains.intellij.build.dependencies.BuildDependenciesExtractOptions
import org.jetbrains.intellij.build.dependencies.BuildDependenciesManualRunOnly
import org.jetbrains.intellij.build.resolveAndExtractToCacheLocation
import org.jetbrains.intellij.build.resolveFileForReading
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat

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
    /**
     * The SHA-256 a preloaded downloads manifest declares for [source], or `null` for a file only the
     * download cache vouches for. Never computed here - see [inventoryId] for what identifies an
     * inventory when nothing has declared a digest.
     */
    @JvmField val sha256: String?,
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
    val root = BuildDependenciesDownloader.getDownloadCacheDirectory(communityRoot)
      .resolve("maven-libraries-$path-${inventoryId(libraryFiles)}")
    withContext(Dispatchers.IO) {
      Files.createDirectories(root)
      for ((fileName, source, _) in libraryFiles) {
        val targetFile = root.resolve(fileName)
        // the directory name already states which content belongs here, so all a warm call has to
        // establish is that every jar landed - one `stat` each, where comparing digests read them whole
        if (fileSizeOrNull(targetFile) == Files.size(source)) {
          continue
        }
        val tempFile = Files.createTempFile(root, fileName, ".tmp")
        try {
          Files.copy(source, tempFile, StandardCopyOption.REPLACE_EXISTING)
          Files.move(tempFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }
        finally {
          Files.deleteIfExists(tempFile)
        }
      }
      // maintain the FIFO cache: `CacheDirCleanup` reclaims a top-level entry by its own modification time
      Files.setLastModifiedTime(root, FileTime.from(Instant.now()))
    }
    return root
  }

  /**
   * Identifies an inventory without reading a byte of it.
   *
   * A preloaded input is identified by the digest its manifest declares - authoritative, and free.
   * Anything else is a download-cache entry whose file name
   * ([BuildDependenciesDownloader.getTargetFile]) is already derived from the artifact URL, so it
   * names the content just as precisely, also for free.
   */
  private fun inventoryId(libraryFiles: List<MavenLibraryFile>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    for ((fileName, source, sha256) in libraryFiles.sortedBy { it.fileName }) {
      digest.update(fileName.toByteArray())
      digest.update(0.toByte())
      digest.update((sha256 ?: source.fileName.toString()).toByteArray())
      digest.update(0.toByte())
    }
    // as short as `getTargetFile` keeps its own hash - a download cache lives under the community root,
    // where this repository has a Windows path-length budget to respect
    return HexFormat.of().formatHex(digest.digest()).substring(0, 16)
  }

  private fun fileSizeOrNull(file: Path): Long? {
    return try {
      Files.size(file)
    }
    catch (_: NoSuchFileException) {
      null
    }
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
          val resolved = resolveFileForReading(uri.toString(), communityRoot)
          MavenLibraryFile(fileName = fileName, source = resolved.file, sha256 = resolved.sha256)
        }
      }.awaitAll()
    }
  }

  fun downloadMavenDistributionSync(communityRoot: BuildDependenciesCommunityRoot): Path {
    return downloadMavenDistributionSync(communityRoot = communityRoot, useProjectLocalCache = false)
  }

  fun downloadMavenDistributionSync(communityRoot: BuildDependenciesCommunityRoot, useProjectLocalCache: Boolean): Path {
    return runBlocking(Dispatchers.Default) {
      downloadMavenDistribution(communityRoot = communityRoot, useProjectLocalCache = useProjectLocalCache)
    }
  }

  suspend fun downloadMavenDistribution(communityRoot: BuildDependenciesCommunityRoot): Path {
    return downloadMavenDistribution(communityRoot = communityRoot, useProjectLocalCache = false)
  }

  /**
   * Downloads and extracts the bundled Maven home.
   *
   * [useProjectLocalCache] is for an IDE unit test running from JPS module outputs, where this directory is the
   * embedded Maven home and VFS access is restricted to the IDE home. Build and dev-mode callers retain the shared,
   * content-addressed extraction cache.
   */
  suspend fun downloadMavenDistribution(communityRoot: BuildDependenciesCommunityRoot, useProjectLocalCache: Boolean): Path {
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
      if (!useProjectLocalCache) {
        return@withLock resolveAndExtractToCacheLocation(uri.toString(), communityRoot, BuildDependenciesExtractOptions.STRIP_ROOT)
      }

      val resolved = resolveFileForReading(url = uri.toString(), communityRoot = communityRoot)
      val mavenHome = BuildDependenciesDownloader.getDownloadCacheDirectory(communityRoot).resolve("apache-maven-$bundledMavenVersion")
      BuildDependenciesDownloader.extractFile(
        archiveFile = resolved.file,
        target = mavenHome,
        communityRoot = communityRoot,
        sha256 = resolved.sha256,
        options = arrayOf(BuildDependenciesExtractOptions.STRIP_ROOT),
      )
      mavenHome
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
