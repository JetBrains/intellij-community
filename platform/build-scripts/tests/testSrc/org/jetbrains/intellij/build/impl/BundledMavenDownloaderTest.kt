// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesConstants
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil
import org.junit.Assert
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BundledMavenDownloaderTest {
  @Test
  fun `all Maven inputs materialize in the download cache`() = runBlocking(Dispatchers.Default) {
    val root = Files.createTempDirectory("bundled-maven-cache-test")
    val community = root.resolve("community")
    val cache = root.resolve("cache")
    val runfiles = root.resolve("runfiles")
    Files.createDirectories(community.resolve("build/dependencies"))
    Files.createDirectories(runfiles)
    Files.createFile(community.resolve("intellij.idea.community.main.iml"))
    Files.writeString(
      community.resolve("build/dependencies/dependencies.properties"),
      """
        bundledMaven3Libraries=org.example:one:1,org.example:two:2
        bundledMavenTelemetryLibraries=org.example:telemetry:3
        bundledMavenVersion=1.0
      """.trimIndent()
    )

    val distributionUrl = BuildDependenciesDownloader.getUriForMavenArtifact(
      BuildDependenciesConstants.MAVEN_CENTRAL_URL,
      "org.apache.maven",
      "apache-maven",
      "1.0",
      "bin",
      "zip",
    ).toString()
    val urls = listOf(
      distributionUrl,
      BuildDependenciesDownloader.getUriForMavenArtifact(
        BuildDependenciesConstants.MAVEN_CENTRAL_URL, "org.example", "one", "1", "jar"
      ).toString(),
      BuildDependenciesDownloader.getUriForMavenArtifact(
        BuildDependenciesConstants.MAVEN_CENTRAL_URL, "org.example", "two", "2", "jar"
      ).toString(),
      BuildDependenciesDownloader.getUriForMavenArtifact(
        BuildDependenciesConstants.MAVEN_CENTRAL_URL, "org.example", "telemetry", "3", "jar"
      ).toString(),
    )
    val rows = ArrayList<String>()
    for ((index, url) in urls.withIndex()) {
      val name = url.substringAfterLast('/')
      val source = runfiles.resolve(name)
      if (url == distributionUrl) {
        createMavenZip(source)
      }
      else {
        Files.writeString(source, "content-$index")
      }
      rows.add("$name\t${(index + 1).toString(16).padStart(64, '0')}\t$url")
    }
    val manifest = runfiles.resolve("preloaded-downloads-v1.tsv")
    Files.writeString(manifest, "intellij-build-downloads\t1\n${rows.joinToString("\n")}\n")

    val oldCache = System.getProperty(BuildDependenciesConstants.DOWNLOAD_CACHE_DIR_PROPERTY)
    val oldManifest = System.getProperty(BuildDependenciesConstants.PRELOADED_DOWNLOADS_MANIFEST_PROPERTY)
    System.setProperty(BuildDependenciesConstants.DOWNLOAD_CACHE_DIR_PROPERTY, cache.toString())
    System.setProperty(BuildDependenciesConstants.PRELOADED_DOWNLOADS_MANIFEST_PROPERTY, manifest.toString())
    try {
      val communityRoot = BuildDependenciesCommunityRoot(community)
      val resolvedMaven3 = BundledMavenDownloader.resolveMaven3Libs(communityRoot)
      val resolvedTelemetry = BundledMavenDownloader.resolveMavenTelemetryDependencies(communityRoot)
      val resolvedMaven4 = BundledMavenDownloader.resolveMaven4Libs(communityRoot)

      Assert.assertEquals(setOf("one-1.jar", "two-2.jar"), resolvedMaven3.map { it.fileName }.toSet())
      Assert.assertTrue(resolvedMaven3.all { it.source.startsWith(runfiles) })
      Assert.assertEquals(listOf("telemetry-3.jar"), resolvedTelemetry.map { it.fileName })
      Assert.assertTrue(resolvedTelemetry.single().source.startsWith(runfiles))
      Assert.assertTrue(resolvedMaven4.isEmpty())
      Assert.assertFalse("Resolving preloaded inputs must not create $cache", Files.exists(cache))

      val distribution = BundledMavenDownloader.downloadMavenDistribution(communityRoot)
      val maven3 = BundledMavenDownloader.downloadMaven3Libs(communityRoot)
      val telemetry = BundledMavenDownloader.downloadMavenTelemetryDependencies(communityRoot)
      val maven4 = BundledMavenDownloader.downloadMaven4Libs(communityRoot)

      for (path in listOf(distribution, maven3, telemetry, maven4)) {
        Assert.assertTrue("$path must be under $cache", path.startsWith(cache))
      }
      Assert.assertTrue(Files.isRegularFile(distribution.resolve("lib/maven-core-1.0.jar")))
      Assert.assertEquals(setOf("one-1.jar", "two-2.jar"), Files.list(maven3).use { it.map { file -> file.fileName.toString() }.toList().toSet() })
      Assert.assertEquals(setOf("telemetry-3.jar"), Files.list(telemetry).use { it.map { file -> file.fileName.toString() }.toList().toSet() })
      Assert.assertTrue(Files.list(maven4).use { it.findAny().isEmpty })
      Assert.assertEquals(cache, maven3.parent)
      Assert.assertTrue(maven3.fileName.toString().startsWith("maven-libraries-maven3-server-common-"))
      Assert.assertFalse(Files.exists(cache.resolve("maven-libraries")))
      Assert.assertFalse(Files.exists(community.resolve("plugins/maven/maven36-server-impl/lib/maven3")))
      Assert.assertFalse(Files.exists(community.resolve("plugins/maven/maven3-server-common/lib")))

      Assert.assertEquals(distribution, BundledMavenDownloader.downloadMavenDistribution(communityRoot))
      Assert.assertEquals(maven3, BundledMavenDownloader.downloadMaven3Libs(communityRoot))
      val concurrentMaven3 = List(4) {
        async { BundledMavenDownloader.downloadMaven3Libs(communityRoot) }
      }.awaitAll()
      Assert.assertEquals(setOf(maven3), concurrentMaven3.toSet())

      // the manifest is authoritative, so re-pinning it - not the bytes behind it - is what re-identifies the inventory
      Assert.assertEquals("content-1", Files.readString(maven3.resolve("one-1.jar")))
      Files.writeString(runfiles.resolve("one-1.jar"), "changed-content")
      Assert.assertEquals(maven3, BundledMavenDownloader.downloadMaven3Libs(communityRoot))

      rows[1] = "one-1.jar\t${"f".repeat(64)}\t${urls[1]}"
      Files.writeString(manifest, "intellij-build-downloads\t1\n${rows.joinToString("\n")}\n")
      val changedMaven3 = BundledMavenDownloader.downloadMaven3Libs(communityRoot)
      Assert.assertNotEquals(maven3, changedMaven3)
      Assert.assertEquals("changed-content", Files.readString(changedMaven3.resolve("one-1.jar")))
      Assert.assertFalse(
        Files.walk(cache).use { files -> files.anyMatch { file -> file.fileName.toString().endsWith(".tmp") } }
      )
    }
    finally {
      restoreProperty(BuildDependenciesConstants.DOWNLOAD_CACHE_DIR_PROPERTY, oldCache)
      restoreProperty(BuildDependenciesConstants.PRELOADED_DOWNLOADS_MANIFEST_PROPERTY, oldManifest)
      BuildDependenciesUtil.deleteFileOrFolder(root)
    }
  }

  private fun createMavenZip(target: Path) {
    ZipOutputStream(Files.newOutputStream(target)).use { zip ->
      zip.putNextEntry(ZipEntry("apache-maven-1.0/lib/maven-core-1.0.jar"))
      zip.write("maven".toByteArray())
      zip.closeEntry()
    }
  }

  private fun restoreProperty(name: String, value: String?) {
    if (value == null) {
      System.clearProperty(name)
    }
    else {
      System.setProperty(name, value)
    }
  }
}
