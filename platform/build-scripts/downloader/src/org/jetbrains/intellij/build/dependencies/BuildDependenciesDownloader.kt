// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import com.github.luben.zstd.ZstdInputStreamNoFinalizer
import com.google.common.hash.Hashing
import com.google.common.util.concurrent.Striped
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.TracerProvider
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.cleanDirectory
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.extractTarBz2
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.extractTarGz
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.extractZip
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.listDirectory
import org.jetbrains.intellij.build.dependencies.TeamCityHelper.isUnderTeamCity
import org.jetbrains.intellij.build.dependencies.TeamCityHelper.systemProperties
import org.jetbrains.intellij.build.downloadFileToCacheLocationSync
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.math.BigInteger
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger
import kotlin.concurrent.withLock

@ApiStatus.Internal
object BuildDependenciesDownloader {
  data class Credentials(val username: String, val password: String)

  private val LOG = Logger.getLogger(BuildDependenciesDownloader::class.java.name)
  private val fileLocks = Striped.lock(1024)
  private val cleanupFlag = AtomicBoolean(false)

  // increment on semantic changes in extract code to invalidate all current caches
  private const val EXTRACT_CODE_VERSION = 4

  // increment on semantic changes in download code to invalidate all current caches,
  // e.g., when some issues in extraction code were fixed
  private const val DOWNLOAD_CODE_VERSION = 3

  /**
   * Set tracer to get telemetry. e.g. it's set for build scripts to get opentelemetry events
   */
  @Volatile
  var TRACER: Tracer = TracerProvider.noop().get("noop-build-dependencies")

  fun getDependencyProperties(communityRoot: BuildDependenciesCommunityRoot): DependenciesProperties {
    return DependenciesProperties(communityRoot)
  }

  @JvmStatic
  fun getUriForMavenArtifact(mavenRepository: String, groupId: String, artifactId: String, version: String, packaging: String): URI {
    return getUriForMavenArtifact(mavenRepository = mavenRepository,
                                  groupId = groupId,
                                  artifactId = artifactId,
                                  version = version,
                                  classifier = null,
                                  packaging = packaging)
  }

  @JvmStatic
  fun getUriForMavenArtifact(mavenRepository: String,
                             groupId: String,
                             artifactId: String,
                             version: String,
                             classifier: String?,
                             packaging: String): URI {
    val base = mavenRepository.trim('/')
    val groupStr = groupId.replace('.', '/')
    val classifierStr = if (classifier != null) "-${classifier}" else ""
    return URI.create("${base}/${groupStr}/${artifactId}/${version}/${artifactId}-${version}${classifierStr}.${packaging}")
  }

  private fun getProjectLocalDownloadCache(communityRoot: BuildDependenciesCommunityRoot): Path {
    return Files.createDirectories(communityRoot.communityRoot.resolve("build/download"))
  }

  @Throws(IOException::class)
  private fun getDownloadCachePath(communityRoot: BuildDependenciesCommunityRoot): Path {
    val path: Path = if (isUnderTeamCity) {
      val persistentCachePath = systemProperties["agent.persistent.cache"]
      check(!persistentCachePath.isNullOrBlank()) {
        "'agent.persistent.cache' system property is required under TeamCity"
      }
      Paths.get(persistentCachePath)
    }
    else {
      getProjectLocalDownloadCache(communityRoot)
    }
    Files.createDirectories(path)
    return path
  }

  @JvmStatic
  fun downloadFileToCacheLocation(communityRoot: BuildDependenciesCommunityRoot, uri: URI): Path {
    return downloadFileToCacheLocationSync(url = uri.toString(), communityRoot = communityRoot)
  }

  @JvmStatic
  fun downloadFileToCacheLocation(communityRoot: BuildDependenciesCommunityRoot, uri: URI, credentialsProvider: () -> Credentials): Path {
    return downloadFileToCacheLocationSync(url = uri.toString(), communityRoot = communityRoot, credentialsProvider)
  }

  fun getTargetFile(communityRoot: BuildDependenciesCommunityRoot, uriString: String): Path {
    val lastNameFromUri = uriString.substring(uriString.lastIndexOf('/') + 1)
    val hashString = hashString("${uriString}V${DOWNLOAD_CODE_VERSION}").substring(0, 10)
    return getDownloadCachePath(communityRoot).resolve("${hashString}-${lastNameFromUri}")
  }

  @Synchronized
  fun extractFileToCacheLocation(communityRoot: BuildDependenciesCommunityRoot,
                                 archiveFile: Path,
                                 vararg options: BuildDependenciesExtractOptions): Path {
    cleanUpIfRequired(communityRoot)
    val cachePath = getDownloadCachePath(communityRoot)
    val hash = hashString(archiveFile.toString() + getExtractOptionsShortString(options) + EXTRACT_CODE_VERSION).substring(0, 6)
    val directoryName = "${archiveFile.fileName}.${hash}.d"
    val targetDirectory = cachePath.resolve(directoryName)
    val flagFile = cachePath.resolve("${directoryName}.flag")
    extractFileWithFlagFileLocation(archiveFile, targetDirectory, flagFile, options)
    return targetDirectory
  }

  private fun hashString(s: String): String {
    return BigInteger(1, Hashing.sha256().hashString(s, StandardCharsets.UTF_8).asBytes()).toString(36)
  }

  private fun getExpectedFlagFileContent(archiveFile: Path,
                                         targetDirectory: Path,
                                         options: Array<out BuildDependenciesExtractOptions>): ByteArray {
    var numberOfTopLevelEntries: Long
    Files.list(targetDirectory).use { stream -> numberOfTopLevelEntries = stream.count() }
    return """$EXTRACT_CODE_VERSION
${archiveFile.toRealPath(LinkOption.NOFOLLOW_LINKS)}
topLevelEntries:$numberOfTopLevelEntries
options:${getExtractOptionsShortString(options)}
""".toByteArray(StandardCharsets.UTF_8)
  }

  private fun checkFlagFile(archiveFile: Path,
                            flagFile: Path,
                            targetDirectory: Path,
                            options: Array<out BuildDependenciesExtractOptions>): Boolean {
    if (!Files.isRegularFile(flagFile) || !Files.isDirectory(targetDirectory)) {
      return false
    }
    val existingContent = Files.readAllBytes(flagFile)
    return existingContent.contentEquals(getExpectedFlagFileContent(archiveFile, targetDirectory, options))
  }

  // assumes file at `archiveFile` is immutable
  private fun extractFileWithFlagFileLocation(archiveFile: Path,
                                              targetDirectory: Path,
                                              flagFile: Path,
                                              options: Array<out BuildDependenciesExtractOptions>) {
    if (checkFlagFile(archiveFile, flagFile, targetDirectory, options)) {
      LOG.fine("Skipping extract to $targetDirectory since flag file $flagFile is correct")

      // update file modification time to maintain FIFO caches, i.e., in persistent cache folder on TeamCity agent
      val now = FileTime.from(Instant.now())
      Files.setLastModifiedTime(targetDirectory, now)
      Files.setLastModifiedTime(flagFile, now)
      return
    }
    if (Files.exists(targetDirectory)) {
      check(Files.isDirectory(targetDirectory)) { "Target '$targetDirectory' exists, but it's not a directory. Please delete it manually" }
      cleanDirectory(targetDirectory)
    }
    LOG.info(" * Extracting $archiveFile to $targetDirectory")
    extractCount.incrementAndGet()
    Files.createDirectories(targetDirectory)
    val filesAfterCleaning = listDirectory(targetDirectory)
    check(filesAfterCleaning.isEmpty()) {
      "Target directory ${targetDirectory} is not empty after cleaning: ${filesAfterCleaning.joinToString(" ")}"
    }
    val start = ByteBuffer.allocate(4)
    FileChannel.open(archiveFile).use { channel -> channel.read(start, 0) }
    start.flip()
    check(start.remaining() == 4) { "File $archiveFile is smaller than 4 bytes, could not be extracted" }
    val stripRoot = options.any { it == BuildDependenciesExtractOptions.STRIP_ROOT }
    val magicNumber = start.order(ByteOrder.LITTLE_ENDIAN).getInt(0)
    if (magicNumber == -0x2d04ad8) {
      val unwrappedArchiveFile = archiveFile.parent.resolve(archiveFile.fileName.toString() + ".unwrapped")
      try {
        Files.newOutputStream(unwrappedArchiveFile).use { out ->
          ZstdInputStreamNoFinalizer(Files.newInputStream(archiveFile)).use {
            input -> input.transferTo(out)
          }
        }
        extractZip(unwrappedArchiveFile, targetDirectory, stripRoot)
      }
      finally {
        Files.deleteIfExists(unwrappedArchiveFile)
      }
    }
    else if (start[0] == 0x50.toByte() && start[1] == 0x4B.toByte()) {
      extractZip(archiveFile, targetDirectory, stripRoot)
    }
    else if (start[0] == 0x1F.toByte() && start[1] == 0x8B.toByte()) {
      extractTarGz(archiveFile, targetDirectory, stripRoot)
    }
    else if (start[0] == 0x42.toByte() && start[1] == 0x5A.toByte()) {
      extractTarBz2(archiveFile, targetDirectory, stripRoot)
    }
    else {
      throw IllegalStateException("Unknown archive format at ${archiveFile}." +
                                  " Magic number (little endian hex): ${Integer.toHexString(magicNumber)}." +
                                  " Currently only .tar.gz or .zip are supported")
    }
    Files.write(flagFile, getExpectedFlagFileContent(archiveFile, targetDirectory, options))
    check(checkFlagFile(archiveFile, flagFile, targetDirectory, options)) {
      "'checkFlagFile' must be true right after extracting the archive. flagFile:${flagFile} archiveFile:${archiveFile} target:${targetDirectory}"
    }
  }

  fun extractFile(archiveFile: Path,
                  target: Path,
                  communityRoot: BuildDependenciesCommunityRoot,
                  vararg options: BuildDependenciesExtractOptions) {
    cleanUpIfRequired(communityRoot)
    fileLocks.get(target).withLock {
      // Extracting different archive files into the same target should overwrite target each time
      // That's why flagFile should be dependent only on target location
      val hash = hashString(target.toString()).substring(0, 6)
      val flagFile = getProjectLocalDownloadCache(communityRoot).resolve("${hash}-${target.fileName}.flag.txt")
      extractFileWithFlagFileLocation(archiveFile, target, flagFile, options)
    }
  }

  fun cleanUpIfRequired(communityRoot: BuildDependenciesCommunityRoot) {
    if (!cleanupFlag.getAndSet(true)) {
      // run only once per process
      return
    }
    if (isUnderTeamCity) {
      // Cleanup on TeamCity is handled by TeamCity
      return
    }
    val cacheDir = getProjectLocalDownloadCache(communityRoot)
    try {
      CacheDirCleanup(cacheDir).runCleanupIfRequired()
    }
    catch (t: Throwable) {
      val writer = StringWriter()
      t.printStackTrace(PrintWriter(writer))
      LOG.warning("Cleaning up failed for the directory '$cacheDir'\n$writer")
    }
  }

  private fun getExtractOptionsShortString(options: Array<out BuildDependenciesExtractOptions>): String {
    if (options.isEmpty()) {
      return ""
    }
    val sb = StringBuilder()
    for (option in options) {
      if (option === BuildDependenciesExtractOptions.STRIP_ROOT) {
        sb.append("s")
      }
      else {
        throw IllegalStateException("Unhandled case: $option")
      }
    }
    return sb.toString()
  }

  private val extractCount = AtomicInteger()

  @TestOnly fun getExtractCount(): Int = extractCount.get()

  class HttpStatusException(message: String, @JvmField val statusCode: Int, val url: String) : IllegalStateException(message) {
    override fun toString(): String = "HttpStatusException(status=${statusCode}, url=${url}, message=${message})"
  }
}
