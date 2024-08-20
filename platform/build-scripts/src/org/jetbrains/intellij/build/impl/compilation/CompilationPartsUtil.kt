// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "UnstableApiUsage", "ReplacePutWithAssignment")
@file:OptIn(ExperimentalPathApi::class)

package org.jetbrains.intellij.build.impl.compilation

import com.intellij.platform.util.coroutines.forEachConcurrent
import com.intellij.platform.util.coroutines.mapConcurrent
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.io.AddDirEntriesMode
import org.jetbrains.intellij.build.io.zip
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.io.File
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPOutputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.listDirectoryEntries

// doesn't make sense to execute 64 tasks in parallel
private const val ioTaskParallelism = 8

internal const val uploadParallelism = 4
internal const val downloadParallelism = 4

private const val BRANCH_PROPERTY_NAME = "intellij.build.compiled.classes.branch"
private const val SERVER_URL = "intellij.build.compiled.classes.server.url"
private const val UPLOAD_PREFIX = "intellij.build.compiled.classes.upload.prefix"

class CompilationCacheUploadConfiguration(
  serverUrl: String? = null,
  val checkFiles: Boolean = true,
  val uploadOnly: Boolean = false,
  branch: String? = null,
  uploadPredix: String? = null,
) {
  val serverUrl: String by lazy { serverUrl ?: normalizeServerUrl() }

  val uploadPrefix: String by lazy {
    uploadPredix ?: System.getProperty(UPLOAD_PREFIX, "intellij-compile/v2").also {
      check(!it.isNullOrBlank()) {
        "$UPLOAD_PREFIX system property should not be blank."
      }
    }
  }

  val branch: String by lazy {
    branch ?: System.getProperty(BRANCH_PROPERTY_NAME).also {
      check(!it.isNullOrBlank()) {
        "Git branch is not defined. Please set $BRANCH_PROPERTY_NAME system property."
      }
    }
  }
}

private fun normalizeServerUrl(): String {
  val serverUrlPropertyName = SERVER_URL
  var result = System.getProperty(serverUrlPropertyName)?.trimEnd('/')
  check(!result.isNullOrBlank()) {
    "Compilation cache archive server url is not defined. Please set $serverUrlPropertyName system property."
  }
  if (!result.startsWith("http")) {
    @Suppress("HttpUrlsUsage")
    result = (if (result.startsWith("localhost:")) "http://" else "https://") + result
  }
  return result
}

private const val COMPILATION_CACHE_METADATA_JSON = "metadata.json"

suspend fun packAndUploadToServer(context: CompilationContext, zipDir: Path, config: CompilationCacheUploadConfiguration) {
  val items = if (config.uploadOnly) {
    Json.decodeFromString<CompilationPartsMetadata>(Files.readString(zipDir.resolve(COMPILATION_CACHE_METADATA_JSON))).files.map {
      val item = PackAndUploadItem(output = Path.of(""), name = it.key, archive = zipDir.resolve(it.key + ".jar"))
      item.hash = it.value
      item
    }
  }
  else {
    spanBuilder("pack classes").use {
      packCompilationResult(zipDir, context)
    }
  }

  spanBuilder("upload packed classes").use {
    upload(config = config, zipDir = zipDir, messages = context.messages, items = items)
  }
}

private fun createBufferPool(@Suppress("SameParameterValue") maxPoolSize: Int): DirectFixedSizeByteBufferPool {
  return DirectFixedSizeByteBufferPool(bufferSize = MAX_BUFFER_SIZE, maxPoolSize = maxPoolSize)
}

private suspend fun packCompilationResult(zipDir: Path, context: CompilationContext, addDirEntriesMode: AddDirEntriesMode = AddDirEntriesMode.ALL): List<PackAndUploadItem> {
  val incremental = context.options.incrementalCompilation
  if (!incremental) {
    try {
      zipDir.deleteRecursively()
    }
    catch (ignore: NoSuchFileException) {
    }
  }
  Files.createDirectories(zipDir)

  val items = ArrayList<PackAndUploadItem>(2048)
  spanBuilder("compute module list to pack").use { span ->
    // production, test
    for (subRoot in context.classesOutputDirectory.listDirectoryEntries()) {
      if (!Files.isDirectory(subRoot)) {
        continue
      }

      val subRootName = subRoot.fileName.toString()
      Files.createDirectories(zipDir.resolve(subRootName))

      Files.newDirectoryStream(subRoot).use { subRootStream ->
        for (module in subRootStream) {
          val fileName = module.fileName.toString()
          val name = "$subRootName/$fileName"
          try {
            if (isModuleOutputDirEmpty(module)) {
              span.addEvent("skip empty module", Attributes.of(AttributeKey.stringKey("name"), name))
              continue
            }
          }
          catch (ignore: FileSystemException) {
            continue
          }

          if (context.findModule(fileName) == null) {
            span.addEvent("skip module output from missing in project module", Attributes.of(AttributeKey.stringKey("module"), fileName))
          }
          else {
            items.add(PackAndUploadItem(output = module, name = name, archive = zipDir.resolve("$name.jar")))
          }
        }
      }
    }
  }

  spanBuilder("build zip archives").use(Dispatchers.IO) {
    items.forEachConcurrent(ioTaskParallelism) { item ->
      item.hash = packAndComputeHash(addDirEntriesMode = addDirEntriesMode, name = item.name, archive = item.archive, directory = item.output)
    }
  }
  return items
}

private suspend fun packAndComputeHash(
  addDirEntriesMode: AddDirEntriesMode,
  name: String,
  archive: Path,
  directory: Path,
): String {
  spanBuilder("pack").setAttribute("name", name).use {
    // we compress the whole file using ZSTD - no need to compress
    zip(
      targetFile = archive,
      dirs = mapOf(directory to ""),
      overwrite = true,
      fileFilter = { it != ".unmodified" && it != ".DS_Store" },
      addDirEntriesMode = addDirEntriesMode,
    )
  }
  return spanBuilder("compute hash").setAttribute("name", name).use {
    computeHash(archive)
  }
}

private fun isModuleOutputDirEmpty(moduleOutDir: Path): Boolean {
  Files.newDirectoryStream(moduleOutDir).use {
    for (child in it) {
      if (!child.endsWith(".unmodified") && !child.endsWith(".DS_Store")) {
        return false
      }
    }
  }
  return true
}

private suspend fun upload(
  config: CompilationCacheUploadConfiguration,
  zipDir: Path,
  messages: BuildMessages,
  items: List<PackAndUploadItem>,
) {
  // prepare metadata for writing into file
  val metadataJson = Json.encodeToString(
    CompilationPartsMetadata(
      serverUrl = config.serverUrl,
      branch = config.branch,
      prefix = config.uploadPrefix,
      files = items.associateTo(TreeMap()) { item ->
        item.name to item.hash!!
      },
    )
  )

  // save a metadata file
  if (!config.uploadOnly) {
    val metadataFile = zipDir.resolve(COMPILATION_CACHE_METADATA_JSON)
    val gzippedMetadataFile = zipDir.resolve("$COMPILATION_CACHE_METADATA_JSON.gz")
    Files.createDirectories(metadataFile.parent)
    Files.writeString(metadataFile, metadataJson)

    GZIPOutputStream(Files.newOutputStream(gzippedMetadataFile)).use { outputStream ->
      Files.copy(metadataFile, outputStream)
    }

    messages.artifactBuilt(metadataFile.toString())
    messages.artifactBuilt(gzippedMetadataFile.toString())
  }

  spanBuilder("upload archives").setAttribute(AttributeKey.stringArrayKey("items"), items.map(PackAndUploadItem::name)).use {
    createBufferPool(maxPoolSize = uploadParallelism * 2).use { bufferPool ->
      uploadArchives(
        reportStatisticValue = messages::reportStatisticValue,
        config = config,
        metadataJson = metadataJson,
        httpClient = httpClient,
        items = items,
        bufferPool = bufferPool,
      )
    }
  }
}

private fun getArchivesStorage(fallbackPersistentCacheRoot: Path): Path {
  return (System.getProperty("agent.persistent.cache")?.let { Path.of(it) } ?: fallbackPersistentCacheRoot)
    .resolve("idea-compile-parts-v2")
}

@ApiStatus.Internal
class ArchivedCompilationOutputsStorage(
  private val paths: BuildPaths,
  private val classesOutputDirectory: Path,
  val archivedOutputDirectory: Path = getArchivesStorage(classesOutputDirectory.parent),
) {
  private val unarchivedToArchivedMap = ConcurrentHashMap<Path, Path>()

  internal fun loadMetadataFile(metadataFile: Path) {
    val metadata = Json.decodeFromString<CompilationPartsMetadata>(Files.readString(metadataFile))
    for (entry in metadata.files) {
      unarchivedToArchivedMap.put(classesOutputDirectory.resolve(entry.key), archivedOutputDirectory.resolve(entry.key).resolve("${entry.value}.jar"))
    }
  }

  suspend fun getArchived(path: Path): Path {
    if (Files.isRegularFile(path) || !path.startsWith(classesOutputDirectory)) {
      return path
    }

    unarchivedToArchivedMap.get(path)?.let {
      return it
    }

    val archived = archive(path)
    return unarchivedToArchivedMap.putIfAbsent(path, archived) ?: archived
  }

  private suspend fun archive(path: Path): Path {
    val name = classesOutputDirectory.relativize(path).toString()

    val archive = Files.createTempFile(paths.tempDir, name.replace(File.separator, "_"), ".jar")
    Files.deleteIfExists(archive)
    val hash = packAndComputeHash(addDirEntriesMode = AddDirEntriesMode.ALL, name = name, archive = archive, directory = path)

    val result = archivedOutputDirectory.resolve(name).resolve("$hash.jar")
    Files.createDirectories(result.parent)
    Files.move(archive, result, StandardCopyOption.REPLACE_EXISTING)

    return result
  }

  internal fun getMapping(): List<Map.Entry<Path, Path>> = unarchivedToArchivedMap.entries.sortedBy { it.key.invariantSeparatorsPathString }
}

@VisibleForTesting
suspend fun fetchAndUnpackCompiledClasses(
  reportStatisticValue: (key: String, value: String) -> Unit,
  classOutput: Path,
  metadataFile: Path,
  skipUnpack: Boolean,
  saveHash: Boolean,
) {
  val metadata = Json.decodeFromString<CompilationPartsMetadata>(Files.readString(metadataFile))
  val tempDownloadStorage = getArchivesStorage(classOutput.parent)

  val items = metadata.files.mapTo(ArrayList(metadata.files.size)) { entry ->
    FetchAndUnpackItem(
      name = entry.key,
      hash = entry.value,
      output = classOutput.resolve(entry.key),
      file = tempDownloadStorage.resolve("${entry.key}/${entry.value}.jar"),
    )
  }
  items.sortBy { it.name }

  var verifyTime = 0L
  val upToDate = ConcurrentHashMap.newKeySet<String>()
  spanBuilder("check previously unpacked directories").use { span ->
    verifyTime += checkPreviouslyUnpackedDirectories(
      items = items,
      span = span,
      upToDate = upToDate,
      metadata = metadata,
      classOutput = classOutput,
    )
  }
  reportStatisticValue("compile-parts:up-to-date:count", upToDate.size.toString())

  val toUnpack = LinkedHashSet<FetchAndUnpackItem>(items.size)
  val verifyStart = System.nanoTime()
  val toDownload = spanBuilder("check previously downloaded archives").use(Dispatchers.IO) { span ->
    items
      .filter { item ->
        if (upToDate.contains(item.name)) {
          return@filter false
        }

        if (!skipUnpack) {
          toUnpack.add(item)
        }
        true
      }
      .mapConcurrent(ioTaskParallelism) { item ->
        val file = item.file
        when {
          Files.notExists(file) -> item
          item.hash == computeHash(file) -> null
          else -> {
            span.addEvent("file has unexpected hash, will refetch", Attributes.of(AttributeKey.stringKey("file"), "${item.name}/${item.hash}.jar"))
            Files.deleteIfExists(file)
            item
          }
        }
      }
  }.filterNotNull()
  verifyTime += TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - verifyStart)

  // toUnpack is performed as part of download
  for (item in toDownload) {
    toUnpack.remove(item)
  }

  spanBuilder("cleanup outdated compiled class archives").use {
    val start = System.nanoTime()
    var count = 0
    var bytes = 0L
    try {
      val preserve = items.mapTo(HashSet<Path>(items.size)) { it.file }
      val epoch = FileTime.fromMillis(0)
      val daysAgo = FileTime.fromMillis(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(4))
      Files.createDirectories(tempDownloadStorage)
      // we need to traverse with depth 3 since the first level is [production, test], second level is module name, third is a file
      Files.walk(tempDownloadStorage, 3, FileVisitOption.FOLLOW_LINKS).use { stream ->
        stream
          .filter { !preserve.contains(it) }
          .forEach { file ->
            val attr = Files.readAttributes(file, BasicFileAttributes::class.java)
            if (attr.isRegularFile) {
              val lastAccessTime = attr.lastAccessTime()
              if (lastAccessTime > epoch && lastAccessTime < daysAgo) {
                count++
                bytes += attr.size()
                Files.deleteIfExists(file)
              }
            }
          }
      }
    }
    catch (e: Throwable) {
      Span.current().addEvent("failed to cleanup outdated archives", Attributes.of(AttributeKey.stringKey("error"), e.message ?: ""))
    }

    reportStatisticValue("compile-parts:cleanup:time", TimeUnit.NANOSECONDS.toMillis((System.nanoTime() - start)).toString())
    reportStatisticValue("compile-parts:removed:bytes", bytes.toString())
    reportStatisticValue("compile-parts:removed:count", count.toString())
  }

  spanBuilder("fetch compiled classes archives").use {
    val start = System.nanoTime()

    val prefix = metadata.prefix
    val serverUrl = metadata.serverUrl

    val downloadedBytes = AtomicLong()
    val failed: List<Throwable> = if (toDownload.isEmpty()) {
      emptyList()
    }
    else {
      val httpClientWithoutFollowingRedirects = httpClient.newBuilder().followRedirects(false).build()
      // 4MB block, x2 of thread count - one buffer to source, another one for target
      createBufferPool(downloadParallelism * 2).use { bufferPool ->
        downloadCompilationCache(
          serverUrl = serverUrl,
          prefix = prefix,
          toDownload = toDownload,
          client = httpClientWithoutFollowingRedirects,
          bufferPool = bufferPool,
          downloadedBytes = downloadedBytes,
          skipUnpack = skipUnpack,
          saveHash = saveHash,
        )
      }
    }

    reportStatisticValue("compile-parts:download:time", TimeUnit.NANOSECONDS.toMillis((System.nanoTime() - start)).toString())

    reportStatisticValue("compile-parts:downloaded:bytes", downloadedBytes.get().toString())
    reportStatisticValue("compile-parts:downloaded:count", (toDownload.size - failed.size).toString())
    reportStatisticValue("compile-parts:failed:count", failed.size.toString())

    if (!failed.isEmpty()) {
      error("Failed to fetch ${failed.size} file${if (failed.size > 1) "s" else ""}, see details above or in a trace file")
    }
  }

  val start = System.nanoTime()
  spanBuilder("unpack compiled classes archives").use(Dispatchers.IO) {
    toUnpack.forEachConcurrent(ioTaskParallelism) { item ->
      spanBuilder("unpack").setAttribute("name", item.name).use {
        unpackArchive(item, saveHash)
      }
    }
  }
  reportStatisticValue("compile-parts:unpacked:bytes", toUnpack.sumOf { Files.size(it.file) }.toString())
  reportStatisticValue("compile-parts:unpacked:count", toUnpack.size.toString())
  reportStatisticValue("compile-parts:unpack:time", TimeUnit.NANOSECONDS.toMillis((System.nanoTime() - start)).toString())
}

private suspend fun checkPreviouslyUnpackedDirectories(
  items: List<FetchAndUnpackItem>,
  span: Span,
  upToDate: MutableSet<String>,
  metadata: CompilationPartsMetadata,
  classOutput: Path,
): Long {
  if (Files.notExists(classOutput)) {
    return 0
  }

  val start = System.nanoTime()
  withContext(Dispatchers.IO) {
    launch {
      spanBuilder("remove stalled directories not present in metadata").setAttribute(AttributeKey.stringArrayKey("keys"), java.util.List.copyOf(metadata.files.keys)).use {
        removeStalledDirs(metadata, classOutput)
      }
    }

    items.forEachConcurrent(ioTaskParallelism) { item ->
      val out = item.output
      if (Files.notExists(out)) {
        span.addEvent("output directory doesn't exist", Attributes.of(AttributeKey.stringKey("name"), item.name, AttributeKey.stringKey("outDir"), out.toString()))
        return@forEachConcurrent
      }

      val hashFile = out.resolve(".hash")
      if (!Files.isRegularFile(hashFile)) {
        span.addEvent("no .hash file in output directory", Attributes.of(AttributeKey.stringKey("name"), item.name))
        out.deleteRecursively()
        return@forEachConcurrent
      }

      try {
        val actual = Files.readString(hashFile)
        if (actual == item.hash) {
          upToDate.add(item.name)
        }
        else {
          span.addEvent(
            "output directory hash mismatch",
            Attributes.of(
              AttributeKey.stringKey("name"), item.name,
              AttributeKey.stringKey("expected"), item.hash,
              AttributeKey.stringKey("actual"), actual,
            )
          )
          out.deleteRecursively()
        }
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        span.addEvent("output directory hash calculation failed", Attributes.of(AttributeKey.stringKey("name"), item.name))
        span.recordException(e, Attributes.of(AttributeKey.stringKey("name"), item.name))
        out.deleteRecursively()
      }
    }
  }
  return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
}

private fun CoroutineScope.removeStalledDirs(
  metadata: CompilationPartsMetadata,
  classOutput: Path,
) {
  val expectedDirectories = HashSet(metadata.files.keys)
  // we need to traverse with depth 2 since the first level is [production, test]
  val stalledDirs = mutableListOf<Path>()
  Files.newDirectoryStream(classOutput).use { rootStream ->
    for (subRoot in rootStream) {
      if (!Files.isDirectory(subRoot)) {
        continue
      }

      try {
        Files.newDirectoryStream(subRoot).use { subRootStream ->
          for (module in subRootStream) {
            val name = "${subRoot.fileName}/${module.fileName}"
            if (!expectedDirectories.contains(name)) {
              stalledDirs.add(module)
            }
          }
        }
      }
      catch (ignore: NoSuchFileException) {
      }
    }
  }

  for (dir in stalledDirs) {
    launch {
      spanBuilder("delete stalled dir").setAttribute("dir", dir.toString()).use {
        dir.deleteRecursively()
      }
    }
  }
}

private val sharedDigest = MessageDigest.getInstance("SHA-256", java.security.Security.getProvider("SUN"))
internal fun sha256() = sharedDigest.clone() as MessageDigest

private fun computeHash(file: Path): String {
  val messageDigest = sha256()
  FileChannel.open(file, READ_OPERATION).use { channel ->
    val fileSize = channel.size()
    // java message digest doesn't support native buffer (copies to a heap byte array in any case)
    val bufferSize = 256 * 1024
    val buffer = ByteBuffer.allocate(bufferSize)
    var offset = 0L
    var readBytes: Int
    while (offset < fileSize) {
      buffer.clear()
      readBytes = channel.read(buffer, offset)
      if (readBytes <= 0) {
        break
      }

      messageDigest.update(buffer.array(), 0, readBytes)
      offset += readBytes
    }
  }
  return digestToString(messageDigest)
}

// we cannot change file extension or prefix, so, add suffix
internal fun digestToString(digest: MessageDigest): String = BigInteger(1, digest.digest()).toString(36) + "-z"

data class PackAndUploadItem(
  @JvmField val output: Path,
  @JvmField val name: String,
  @JvmField val archive: Path,
) {
  @JvmField var hash: String? = null
}

internal data class FetchAndUnpackItem(
  @JvmField val name: String,
  @JvmField val hash: String,
  @JvmField val output: Path,
  @JvmField val file: Path,
)

/**
 * Configuration on which compilation parts to download and from where.
 * <br/>
 * URL for each part should be constructed like: <pre>${serverUrl}/${prefix}/${files.key}/${files.value}.jar</pre>
 */
@Serializable
private data class CompilationPartsMetadata(
  @JvmField @SerialName("server-url") val serverUrl: String,
  @JvmField val branch: String,
  @JvmField val prefix: String,
  /**
   * Map compilation part path to a hash, for now SHA-256 is used.
   * `sha256(file)` == hash, though that may be changed in the future.
   */
  @JvmField val files: Map<String, String>,
)