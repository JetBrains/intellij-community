// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import com.dynatrace.hash4j.hashing.Hashing
import com.github.luben.zstd.ZstdInputStreamNoFinalizer
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.TracerProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.intellij.build.IExceptionWithRetryPolicy
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
import kotlin.let

private val LOG = Logger.getLogger(BuildDependenciesDownloader::class.java.name)
private val fileLocks = StripedMutex(1024)
private val cleanupFlag = AtomicBoolean(false)

// increment on semantic changes in extract code to invalidate all current caches
private const val EXTRACT_CODE_VERSION = 6

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
    return getTargetFile(communityRoot = communityRoot, uriString = uriString, contentSha256 = null)
  }

  internal fun getTargetFile(communityRoot: BuildDependenciesCommunityRoot, uriString: String, contentSha256: String?): Path {
    val lastNameFromUri = uriString.substring(uriString.lastIndexOf('/') + 1)
    val cacheIdentity = if (contentSha256 == null) {
      "${uriString}V${DOWNLOAD_CODE_VERSION}"
    }
    else {
      "${uriString}V${DOWNLOAD_CODE_VERSION}S${contentSha256}"
    }
    val hashString = hashString(cacheIdentity).substring(0, 10)
    return getDownloadCachePath(communityRoot).resolve("${hashString}-${lastNameFromUri}")
  }

  /**
   * The project-local download cache (`<communityRoot>/build/download`), or its
   * [BuildDependenciesConstants.DOWNLOAD_CACHE_DIR_PROPERTY] override. Deliberately not the
   * TeamCity persistent cache: callers use this for extraction targets whose flag files are
   * project-local too, so both must move together or not at all.
   */
  fun getDownloadCacheDirectory(communityRoot: BuildDependenciesCommunityRoot): Path = getProjectLocalDownloadCache(communityRoot)

  /**
   * The blocking form of [extractToCacheLocation], for a Java caller or a build script that is not
   * a coroutine - the same shape as [downloadFileToCacheLocation] above.
   *
   * It delegates rather than repeating the extraction: sharing the striped `fileLocks` is what keeps
   * a blocking and a suspending extraction of the same directory out of each other's way. The object
   * monitor this used to hold did neither - it serialized every extraction in the process against
   * every other, while excluding nothing at all on the suspending path.
   */
  @JvmStatic
  fun extractFileToCacheLocation(
    communityRoot: BuildDependenciesCommunityRoot,
    archiveFile: Path,
    vararg options: BuildDependenciesExtractOptions,
  ): Path {
    return runBlocking {
      extractToCacheLocation(
        archiveFile = archiveFile,
        communityRoot = communityRoot,
        cacheKey = archiveCacheKey(archiveFile = archiveFile, sha256 = null),
        options = options,
      )
    }
  }

  @Suppress("DeprecatedCallableAddReplaceWith")
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
    extractFile(archiveFile = archiveFile, target = target, communityRoot = communityRoot, sha256 = null, options = options)
  }

  /**
   * Extracts into a caller-chosen [target], reusing an existing extraction when [sha256] - or, without
   * one, the archive path - still matches what the flag file records.
   */
  suspend fun extractFile(
    archiveFile: Path,
    target: Path,
    communityRoot: BuildDependenciesCommunityRoot,
    sha256: String?,
    options: Array<out BuildDependenciesExtractOptions>,
  ) {
    cleanUpIfRequired(communityRoot)
    fileLocks.getLock(target.toString()).withLock {
      extractFileWithFlagFileLocation(
        archiveFile = archiveFile,
        targetDirectory = target,
        flagFile = extractFlagFile(target, communityRoot),
        cacheKey = archiveCacheKey(archiveFile = archiveFile, sha256 = sha256),
        options = options,
      )
    }
  }

  // Extracting different archive files into the same target should overwrite the target each time.
  // That's why `flagFile` should be dependent only on the target location.
  private fun extractFlagFile(target: Path, communityRoot: BuildDependenciesCommunityRoot): Path {
    val hash = hashString(target.toString()).substring(0, 6)
    return getProjectLocalDownloadCache(communityRoot).resolve("${hash}-${target.fileName}.flag.txt")
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

  class HttpStatusException(message: String, @JvmField val statusCode: Int, val url: String) : IllegalStateException(message), IExceptionWithRetryPolicy {
    override fun toString(): String = "HttpStatusException(status=${statusCode}, url=${url}, message=${message})"
    override val isRetryAllowed: Boolean get() = statusCode != 404
  }
}

suspend fun extractFileToCacheLocation(archiveFile: Path, communityRoot: BuildDependenciesCommunityRoot, stripRoot: Boolean = false): Path {
  return extractToCacheLocation(
    archiveFile = archiveFile,
    communityRoot = communityRoot,
    cacheKey = archiveCacheKey(archiveFile = archiveFile, sha256 = null),
    options = if (stripRoot) STRIP_ROOT_OPTIONS else EMPTY_OPTIONS,
  )
}

/**
 * Extracts [archiveFile] into a directory of the download cache named after [cacheKey].
 *
 * Nothing is written beside [archiveFile], so the archive itself may live on a read-only filesystem -
 * a Bazel runfiles tree, or the read-only share the macOS UI-test VM mounts the host checkout through.
 */
@ApiStatus.Internal
suspend fun extractToCacheLocation(
  archiveFile: Path,
  communityRoot: BuildDependenciesCommunityRoot,
  cacheKey: String,
  options: Array<out BuildDependenciesExtractOptions>,
): Path = withContext(Dispatchers.IO) {
  cleanUpIfRequired(communityRoot)

  fileLocks.getLockByHash(Hashing.xxh3_64().hashBytesToLong(cacheKey.encodeToByteArray())).withLock {
    val location = extractCacheLocation(
      cachePath = getDownloadCachePath(communityRoot),
      archiveFile = archiveFile,
      cacheKey = cacheKey,
      options = options,
    )
    extractFileWithFlagFileLocation(
      archiveFile = archiveFile,
      targetDirectory = location.targetDirectory,
      flagFile = location.flagFile,
      cacheKey = cacheKey,
      options = options,
    )
    return@withLock location.targetDirectory
  }
}

/**
 * What identifies an archive for extraction caching: its SHA-256 where the build knows it - a
 * preloaded Bazel input, whose runfiles path differs per test target and per sandbox and so cannot
 * key anything - and its resolved path otherwise.
 *
 * The path is resolved, not merely taken as given, so that two presentations of one archive
 * (`dir/./a.zip`, `dir/../dir/a.zip`) stay one cache entry.
 */
@ApiStatus.Internal
fun archiveCacheKey(archiveFile: Path, sha256: String?): String {
  return sha256 ?: archiveFile.toRealPath(LinkOption.NOFOLLOW_LINKS).invariantSeparatorsPathString
}

private class ExtractCacheLocation(@JvmField val targetDirectory: Path, @JvmField val flagFile: Path)

private fun extractCacheLocation(
  cachePath: Path,
  archiveFile: Path,
  cacheKey: String,
  options: Array<out BuildDependenciesExtractOptions>,
): ExtractCacheLocation {
  val hash = Hashing.xxh3_64().hashStream()
    .putString(cacheKey)
    .putString(getExtractOptionsShortString(options))
    .putInt(EXTRACT_CODE_VERSION)
    .asLong
  val dirName = "${archiveFile.fileName}.${Long.toUnsignedString(hash, Character.MAX_RADIX)}.d"
  return ExtractCacheLocation(targetDirectory = cachePath.resolve(dirName), flagFile = cachePath.resolve("$dirName.flag"))
}

private val EMPTY_OPTIONS = emptyArray<BuildDependenciesExtractOptions>()
private val STRIP_ROOT_OPTIONS = arrayOf(BuildDependenciesExtractOptions.STRIP_ROOT)

private fun downloadCacheDirOverride(): Path? {
  return System.getProperty(BuildDependenciesConstants.DOWNLOAD_CACHE_DIR_PROPERTY)?.let { Path.of(it) }
}

private fun getProjectLocalDownloadCache(communityRoot: BuildDependenciesCommunityRoot): Path {
  val cacheDir = downloadCacheDirOverride() ?: communityRoot.communityRoot.resolve("build/download")
  return Files.createDirectories(cacheDir)
}

private fun getDownloadCachePath(communityRoot: BuildDependenciesCommunityRoot): Path {
  val path: Path = if (downloadCacheDirOverride() == null && TeamCityHelper.isUnderTeamCity) {
    TeamCityHelper.persistentCachePath ?: error("'agent.persistent.cache' system property is required under TeamCity")
  }
  else {
    getProjectLocalDownloadCache(communityRoot)
  }
  Files.createDirectories(path)
  return path
}

private fun getExpectedFlagFileContent(
  cacheKey: String,
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
$cacheKey
fileCount:$fileCount
fileSizeSum:$fileSizeSum
options:${getExtractOptionsShortString(options)}
""".encodeToByteArray()
}

private fun checkFlagFile(
  cacheKey: String,
  flagFile: Path,
  targetDirectory: Path,
  options: Array<out BuildDependenciesExtractOptions>,
): Boolean {
  if (!Files.isRegularFile(flagFile) || !Files.isDirectory(targetDirectory)) {
    return false
  }
  val existingContent = Files.readAllBytes(flagFile)
  return existingContent.contentEquals(getExpectedFlagFileContent(cacheKey, targetDirectory, options))
}

// assumes a file at `archiveFile` is immutable, and treats it as read-only: nothing is written to it or beside it
private fun extractFileWithFlagFileLocation(
  archiveFile: Path,
  targetDirectory: Path,
  flagFile: Path,
  cacheKey: String,
  options: Array<out BuildDependenciesExtractOptions>,
) {
  if (checkFlagFile(cacheKey, flagFile, targetDirectory, options)) {
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
      // beside the target rather than beside the archive: a preloaded archive lives in the runfiles tree, which is read-only
      val unwrappedArchiveFile = Files.createTempFile(targetDirectory.parent, archiveFile.fileName.toString(), ".unwrapped")
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
  // the expected content is computed once and compared against what landed: recomputing it through `checkFlagFile`
  // would walk the whole extracted tree a second time, which for a JBR-sized one is not free
  val expectedFlagFileContent = getExpectedFlagFileContent(cacheKey, targetDirectory, options)
  Files.write(flagFile, expectedFlagFileContent)
  check(Files.readAllBytes(flagFile).contentEquals(expectedFlagFileContent)) {
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
