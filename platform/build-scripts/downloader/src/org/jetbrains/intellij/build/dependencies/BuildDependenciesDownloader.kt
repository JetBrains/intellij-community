// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import com.dynatrace.hash4j.hashing.Hashing
import com.github.luben.zstd.ZstdInputStreamNoFinalizer
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.TracerProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.intellij.build.StripedMutex
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.cleanUpIfRequired
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.cleanDirectory
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.extractTarBz2
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.extractTarGz
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.extractZip
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.listDirectory
import org.jetbrains.intellij.build.downloadFileToCacheLocationSync
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.Long
import java.math.BigInteger
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.security.Provider
import java.time.Instant
import java.util.EnumSet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger
import kotlin.Array
import kotlin.Boolean
import kotlin.ByteArray
import kotlin.Deprecated
import kotlin.DeprecationLevel
import kotlin.IllegalArgumentException
import kotlin.IllegalStateException
import kotlin.Int
import kotlin.LazyThreadSafetyMode
import kotlin.String
import kotlin.Suppress
import kotlin.Throwable
import kotlin.arrayOf
import kotlin.check
import kotlin.emptyArray
import kotlin.error
import kotlin.getValue
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.lazy

private val LOG = Logger.getLogger(BuildDependenciesDownloader::class.java.name)
private val fileLocks = StripedMutex(1024)
private val cleanupFlag = AtomicBoolean(false)

// increment on semantic changes in extract code to invalidate all current caches
private const val EXTRACT_CODE_VERSION = 5

// increment on semantic changes in download code to invalidate all current caches,
// e.g., when some issues in extraction code were fixed
private const val DOWNLOAD_CODE_VERSION = 3

private val extractCount = AtomicInteger()

private val READ_OPERATION = EnumSet.of(StandardOpenOption.READ)

@ApiStatus.Internal
object BuildDependenciesDownloader {
  data class Credentials(@JvmField val username: String, @JvmField val password: String)

  /**
   * Sets a tracer to get telemetry. E.g., it is set for build scripts to get opentelemetry events.
   */
  @Volatile
  var TRACER: Tracer = TracerProvider.noop().get("noop-build-dependencies")

  fun getDependencyProperties(communityRoot: BuildDependenciesCommunityRoot): DependenciesProperties = DependenciesProperties(communityRoot)

  @JvmStatic
  fun getUriForMavenArtifact(mavenRepository: String, groupId: String, artifactId: String, version: String, packaging: String): URI {
    return getUriForMavenArtifact(mavenRepository = mavenRepository, groupId = groupId, artifactId = artifactId, version = version, classifier = null, packaging = packaging)
  }

  @JvmStatic
  fun getUriForMavenArtifact(
    mavenRepository: String,
    groupId: String,
    artifactId: String,
    version: String,
    classifier: String?,
    packaging: String,
  ): URI {
    val base = mavenRepository.trim('/')
    val groupStr = groupId.replace('.', '/')
    val classifierStr = if (classifier != null) "-${classifier}" else ""
    return URI.create("${base}/${groupStr}/${artifactId}/${version}/${artifactId}-${version}${classifierStr}.${packaging}")
  }

  @JvmStatic
  fun downloadFileToCacheLocation(communityRoot: BuildDependenciesCommunityRoot, uri: URI): Path {
    return downloadFileToCacheLocationSync(uri.toString(), communityRoot)
  }

  @JvmStatic
  fun downloadFileToCacheLocation(communityRoot: BuildDependenciesCommunityRoot, uri: URI, credentialsProvider: () -> Credentials): Path {
    return downloadFileToCacheLocationSync(uri.toString(), communityRoot, credentialsProvider)
  }

  fun getTargetFile(communityRoot: BuildDependenciesCommunityRoot, uriString: String): Path {
    val lastNameFromUri = uriString.substring(uriString.lastIndexOf('/') + 1)
    val hashString = hashString("${uriString}V${DOWNLOAD_CODE_VERSION}").substring(0, 10)
    return getDownloadCachePath(communityRoot).resolve("${hashString}-${lastNameFromUri}")
  }

  @Synchronized
  fun extractFileToCacheLocation(
    communityRoot: BuildDependenciesCommunityRoot,
    archiveFile: Path,
    vararg options: BuildDependenciesExtractOptions,
  ): Path {
    cleanUpIfRequired(communityRoot)
    val cachePath = getDownloadCachePath(communityRoot)
    val hash = hashString(archiveFile.toString() + getExtractOptionsShortString(options) + EXTRACT_CODE_VERSION).substring(0, 6)
    val directoryName = "${archiveFile.fileName}.${hash}.d"
    val targetDirectory = cachePath.resolve(directoryName)
    val flagFile = cachePath.resolve("${directoryName}.flag")
    extractFileWithFlagFileLocation(archiveFile, targetDirectory, flagFile, options)
    return targetDirectory
  }

  @Deprecated("Use BuildDependenciesDownloader.extractFile(communityRoot, archiveFile, options)", level = DeprecationLevel.ERROR)
  fun extractFileSync(archiveFile: Path, target: Path, communityRoot: BuildDependenciesCommunityRoot) {
    runBlocking {
      extractFile(archiveFile, target, communityRoot)
    }
  }

  suspend fun extractFile(
    archiveFile: Path,
    target: Path,
    communityRoot: BuildDependenciesCommunityRoot,
    vararg options: BuildDependenciesExtractOptions,
  ) {
    cleanUpIfRequired(communityRoot)
    fileLocks.getLock(target.toString()).withLock {
      // Extracting different archive files into the same target should overwrite the target each time.
      // That's why `flagFile` should be dependent only on the target location.
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
    if (TeamCityHelper.isUnderTeamCity) {
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

  @TestOnly
  fun getExtractCount(): Int = extractCount.get()

  class HttpStatusException(message: String, @JvmField val statusCode: Int, val url: String) : IllegalStateException(message) {
    override fun toString(): String = "HttpStatusException(status=${statusCode}, url=${url}, message=${message})"
  }
}

suspend fun extractFileToCacheLocation(archiveFile: Path, communityRoot: BuildDependenciesCommunityRoot, stripRoot: Boolean = false): Path {
  cleanUpIfRequired(communityRoot)

  val archivePath = archiveFile.invariantSeparatorsPathString
  val archivePathHash = Hashing.xxh3_64().hashBytesToLong(archivePath.encodeToByteArray())

  fileLocks.getLockByHash(archivePathHash).withLock {
    val cachePath = getDownloadCachePath(communityRoot)

    val hasher = Hashing.xxh3_64().hashStream()
      .putLong(archivePathHash)
      .putInt(archivePath.length)
      .putBoolean(stripRoot)
      .putInt(EXTRACT_CODE_VERSION)

    val dirName = "${archiveFile.fileName}.${Long.toUnsignedString(hasher.asLong, Character.MAX_RADIX)}.d"
    val targetDir = cachePath.resolve(dirName)
    val flagFile = cachePath.resolve("$dirName.flag")
    extractFileWithFlagFileLocation(
      archiveFile = archiveFile,
      targetDirectory = targetDir,
      flagFile = flagFile,
      options = if (stripRoot) arrayOf(BuildDependenciesExtractOptions.STRIP_ROOT) else EMPTY_OPTIONS,
    )
    return targetDir
  }
}

private val EMPTY_OPTIONS = emptyArray<BuildDependenciesExtractOptions>()

private fun getProjectLocalDownloadCache(communityRoot: BuildDependenciesCommunityRoot): Path {
  return Files.createDirectories(communityRoot.communityRoot.resolve("build/download"))
}

private fun getDownloadCachePath(communityRoot: BuildDependenciesCommunityRoot): Path {
  val path: Path = if (TeamCityHelper.isUnderTeamCity) {
    TeamCityHelper.persistentCachePath ?: error("'agent.persistent.cache' system property is required under TeamCity")
  }
  else {
    getProjectLocalDownloadCache(communityRoot)
  }
  Files.createDirectories(path)
  return path
}

private fun getExpectedFlagFileContent(
  archiveFile: Path,
  targetDirectory: Path,
  options: Array<out BuildDependenciesExtractOptions>,
): ByteArray {
  var fileCount = 0L
  var fileSizeSum = 0L

  Files.walkFileTree(targetDirectory, object : SimpleFileVisitor<Path>() {
    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      fileCount++
      fileSizeSum += attrs.size()
      return FileVisitResult.CONTINUE
    }
  })

  return """$EXTRACT_CODE_VERSION
${archiveFile.toRealPath(LinkOption.NOFOLLOW_LINKS)}
fileCount:$fileCount
fileSizeSum:$fileSizeSum
options:${getExtractOptionsShortString(options)}
""".encodeToByteArray()
}

private fun checkFlagFile(
  archiveFile: Path,
  flagFile: Path,
  targetDirectory: Path,
  options: Array<out BuildDependenciesExtractOptions>,
): Boolean {
  if (!Files.isRegularFile(flagFile) || !Files.isDirectory(targetDirectory)) {
    return false
  }
  val existingContent = Files.readAllBytes(flagFile)
  return existingContent.contentEquals(getExpectedFlagFileContent(archiveFile, targetDirectory, options))
}

// assumes a file at `archiveFile` is immutable
private fun extractFileWithFlagFileLocation(
  archiveFile: Path,
  targetDirectory: Path,
  flagFile: Path,
  options: Array<out BuildDependenciesExtractOptions>,
) {
  if (checkFlagFile(archiveFile, flagFile, targetDirectory, options)) {
    LOG.fine("Skipping extract to $targetDirectory since flag file $flagFile is correct")

    // update file modification time to maintain FIFO caches, i.e., in a persistent cache dir on TeamCity agent
    val now = FileTime.from(Instant.now())
    try {
      Files.setLastModifiedTime(targetDirectory, now)
    }
    catch (e: IOException) {
      LOG.fine("Error targetDirectory.setLastModifiedTime: $e")
    }

    try {
      Files.setLastModifiedTime(flagFile, now)
    }
    catch (e: IOException) {
      LOG.fine("Error flagFile.setLastModifiedTime: $e")
    }
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
  FileChannel.open(archiveFile, READ_OPERATION).use { it.read(start, 0) }
  start.flip()
  check(start.remaining() == 4) { "File $archiveFile is smaller than 4 bytes, could not be extracted" }
  val stripRoot = options.any { it == BuildDependenciesExtractOptions.STRIP_ROOT }
  val magicNumber = start.order(ByteOrder.LITTLE_ENDIAN).getInt(0)
  when {
    magicNumber == -0x2d04ad8 -> {
      val unwrappedArchiveFile = archiveFile.parent.resolve(archiveFile.fileName.toString() + ".unwrapped")
      try {
        Files.newOutputStream(unwrappedArchiveFile).use { out ->
          ZstdInputStreamNoFinalizer(Files.newInputStream(archiveFile)).use { input ->
            input.transferTo(out)
          }
        }
        extractZip(unwrappedArchiveFile, targetDirectory, stripRoot)
      }
      finally {
        Files.deleteIfExists(unwrappedArchiveFile)
      }
    }
    start[0] == 0x50.toByte() && start[1] == 0x4B.toByte() -> {
      extractZip(archiveFile, targetDirectory, stripRoot)
    }
    start[0] == 0x1F.toByte() && start[1] == 0x8B.toByte() -> {
      extractTarGz(archiveFile, targetDirectory, stripRoot)
    }
    start[0] == 0x42.toByte() && start[1] == 0x5A.toByte() -> {
      extractTarBz2(archiveFile, targetDirectory, stripRoot)
    }
    else -> {
      throw IllegalStateException(
        "Unknown archive format at ${archiveFile}." +
        " Magic number (little endian hex): ${Integer.toHexString(magicNumber)}." +
        " Currently only .tar.gz or .zip are supported"
      )
    }
  }
  Files.write(flagFile, getExpectedFlagFileContent(archiveFile, targetDirectory, options))
  check(checkFlagFile(archiveFile, flagFile, targetDirectory, options)) {
    "'checkFlagFile' must be true right after extracting the archive. flagFile:${flagFile} archiveFile:${archiveFile} target:${targetDirectory}"
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

internal val sha2_256 by lazy(LazyThreadSafetyMode.PUBLICATION) { getMessageDigest("SHA-256") }
private val sunSecurityProvider: Provider = java.security.Security.getProvider("SUN")
private fun getMessageDigest(@Suppress("SameParameterValue") algorithm: String): MessageDigest {
  return MessageDigest.getInstance(algorithm, sunSecurityProvider)
}

private fun hashString(s: String): String = BigInteger(1, cloneDigest(sha2_256).digest(s.toByteArray())).toString(36)

/**
 * Digest cloning is faster than requesting a new one from [MessageDigest.getInstance].
 * This approach is used in Guava as well.
 */
internal fun cloneDigest(digest: MessageDigest): MessageDigest {
  try {
    return digest.clone() as MessageDigest
  }
  catch (_: CloneNotSupportedException) {
    throw IllegalArgumentException("Message digest is not cloneable: $digest")
  }
}