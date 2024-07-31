// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "UnstableApiUsage")

package org.jetbrains.intellij.build.impl.compilation

import org.jetbrains.intellij.build.telemetry.use
import com.intellij.util.containers.ContainerUtil
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.io.AddDirEntriesMode
import org.jetbrains.intellij.build.io.deleteDir
import org.jetbrains.intellij.build.io.zip
import java.io.File
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPOutputStream
import kotlin.math.min

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

  companion object {
    private const val BRANCH_PROPERTY_NAME = "intellij.build.compiled.classes.branch"
    private const val SERVER_URL = "intellij.build.compiled.classes.server.url"
    private const val UPLOAD_PREFIX = "intellij.build.compiled.classes.upload.prefix"

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
  }
}

const val COMPILATION_CACHE_METADATA_JSON = "metadata.json"

fun packAndUploadToServer(context: CompilationContext, zipDir: Path, config: CompilationCacheUploadConfiguration) {
  val items = if (config.uploadOnly) {
    Json.decodeFromString<CompilationPartsMetadata>(Files.readString(zipDir.resolve(COMPILATION_CACHE_METADATA_JSON))).files.map {
      val item = PackAndUploadItem(output = Path.of(""), name = it.key, archive = zipDir.resolve(it.key + ".jar"))
      item.hash = it.value
      item
    }
  }
  else {
    spanBuilder("pack classes").use {
      packCompilationResult(context, zipDir)
    }
  }

  createBufferPool().use { bufferPool ->
    spanBuilder("upload packed classes").use {
      upload(config = config, zipDir = zipDir, messages = context.messages, items = items, bufferPool = bufferPool)
    }
  }
}

private fun createBufferPool(): DirectFixedSizeByteBufferPool {
  // 4MB block, x2 of FJP thread count - one buffer to source, another one for target
  return DirectFixedSizeByteBufferPool(size = MAX_BUFFER_SIZE, maxPoolSize = ForkJoinPool.getCommonPoolParallelism() * 2)
}

fun packCompilationResult(context: CompilationContext, zipDir: Path, addDirEntriesMode: AddDirEntriesMode = AddDirEntriesMode.ALL): List<PackAndUploadItem> {
  val incremental = context.options.incrementalCompilation
  if (!incremental) {
    try {
      deleteDir(zipDir)
    }
    catch (ignore: NoSuchFileException) {
    }
  }
  Files.createDirectories(zipDir)

  val items = ArrayList<PackAndUploadItem>(2048)
  spanBuilder("compute module list to pack").use { span ->
    // production, test
    for (subRoot in Files.newDirectoryStream(context.classesOutputDirectory).use(DirectoryStream<Path>::toList)) {
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
              span.addEvent("skip empty module", Attributes.of(
                AttributeKey.stringKey("name"), name,
              ))
              continue
            }
          }
          catch (ignore: FileSystemException) {
            continue
          }

          if (context.findModule(fileName) == null) {
            span.addEvent("skip module output from missing in project module", Attributes.of(
              AttributeKey.stringKey("module"), fileName,
            ))
            continue
          }

          items.add(PackAndUploadItem(output = module, name = name, archive = zipDir.resolve("$name.jar")))
        }
      }
    }
  }

  spanBuilder("build zip archives").use {
    val traceContext = Context.current()
    ForkJoinTask.invokeAll(items.map { item ->
      ForkJoinTask.adapt(Callable {
        item.hash = packAndComputeHash(traceContext, addDirEntriesMode, item.name, item.archive, item.output)
      })
    })
  }
  return items
}

private fun packAndComputeHash(traceContext: Context,
                               addDirEntriesMode: AddDirEntriesMode,
                               name: String,
                               archive: Path,
                               directory: Path): String {
  spanBuilder("pack").setParent(traceContext).setAttribute("name", name).use {
    // we compress the whole file using ZSTD
    zip(
      targetFile = archive,
      dirs = mapOf(directory to ""),
      overwrite = true,
      fileFilter = { it != ".unmodified" && it != ".DS_Store" },
      addDirEntriesMode = addDirEntriesMode
    )
  }
  return spanBuilder("compute hash").setParent(traceContext).setAttribute("name", name).use {
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

private fun upload(config: CompilationCacheUploadConfiguration,
                   zipDir: Path,
                   messages: BuildMessages,
                   items: List<PackAndUploadItem>,
                   bufferPool: DirectFixedSizeByteBufferPool) {
  // prepare metadata for writing into file
  val metadataJson = Json.encodeToString(CompilationPartsMetadata(
    serverUrl = config.serverUrl,
    branch = config.branch,
    prefix = config.uploadPrefix,
    files = items.associateTo(TreeMap()) { item ->
      item.name to item.hash!!
    },
  ))

  // save metadata file
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

  spanBuilder("upload archives").setAttribute(AttributeKey.stringArrayKey("items"),
                                              items.map(PackAndUploadItem::name)).use {
    uploadArchives(reportStatisticValue = messages::reportStatisticValue,
                   config = config,
                   metadataJson = metadataJson,
                   httpClient = httpClient,
                   items = items,
                   bufferPool = bufferPool)
  }
}

private fun getArchivesStorage(fallbackPersistentCacheRoot: Path): Path =
  (System.getProperty("agent.persistent.cache")?.let { Path.of(it) } ?: fallbackPersistentCacheRoot)
    .resolve("idea-compile-parts-v2")

@ApiStatus.Internal
class ArchivedCompilationOutputsStorage(
  private val paths: BuildPaths,
  private val classesOutputDirectory: Path,
  val archivedOutputDirectory: Path = getArchivesStorage(classesOutputDirectory.parent),
) {
  private val unarchivedToArchivedMap = ConcurrentHashMap<Path, Path>()

  internal fun loadMetadataFile(metadataFile: Path) {
    val metadata = Json.decodeFromString<CompilationPartsMetadata>(Files.readString(metadataFile))
    metadata.files.forEach { entry ->
      unarchivedToArchivedMap[classesOutputDirectory.resolve(entry.key)] = archivedOutputDirectory.resolve(entry.key).resolve("${entry.value}.jar")
    }
  }

  fun getArchived(path: Path): Path {
    if (Files.isRegularFile(path)) {
      return path
    }
    if (!path.startsWith(classesOutputDirectory)) {
      return path
    }
    return unarchivedToArchivedMap.computeIfAbsent(path) {
      archive(path)
    }
  }

  private fun archive(path: Path): Path {
    val name = classesOutputDirectory.relativize(path).toString()

    val archive = Files.createTempFile(paths.tempDir, name.replace(File.separator, "_"), ".jar")
    Files.deleteIfExists(archive)
    val hash: String = packAndComputeHash(Context.current(), AddDirEntriesMode.ALL, name, archive, path)

    val result = archivedOutputDirectory.resolve(name).resolve("$hash.jar")
    Files.createDirectories(result.parent)
    Files.move(archive, result, StandardCopyOption.REPLACE_EXISTING)

    return result
  }

  fun getMapping(): Map<Path, Path> {
    return Collections.unmodifiableMap(unarchivedToArchivedMap)
  }
}

@VisibleForTesting
fun fetchAndUnpackCompiledClasses(reportStatisticValue: (key: String, value: String) -> Unit,
                                  withScope: (name: String, operation: () -> Unit) -> Unit,
                                  classOutput: Path,
                                  metadataFile: Path,
                                  skipUnpack: Boolean,
                                  saveHash: Boolean) {
  withScope("fetch and unpack compiled classes") {
    val metadata = Json.decodeFromString<CompilationPartsMetadata>(Files.readString(metadataFile))
    val tempDownloadStorage = getArchivesStorage(classOutput.parent)

    val items = metadata.files.mapTo(ArrayList(metadata.files.size)) { entry ->
      FetchAndUnpackItem(name = entry.key,
                         hash = entry.value,
                         output = classOutput.resolve(entry.key),
                         file = tempDownloadStorage.resolve("${entry.key}/${entry.value}.jar"))
    }
    items.sortBy { it.name }

    var verifyTime = 0L
    val upToDate = ContainerUtil.newConcurrentSet<String>()
    spanBuilder("check previously unpacked directories").use { span ->
      verifyTime += checkPreviouslyUnpackedDirectories(items = items,
                                                       span = span,
                                                       upToDate = upToDate,
                                                       metadata = metadata,
                                                       classOutput = classOutput)
    }
    reportStatisticValue("compile-parts:up-to-date:count", upToDate.size.toString())

    val toUnpack = LinkedHashSet<FetchAndUnpackItem>(items.size)
    val toDownload = spanBuilder("check previously downloaded archives").use { span ->
      val start = System.nanoTime()
      val result = ForkJoinTask.invokeAll(items.mapNotNull { item ->
        if (upToDate.contains(item.name)) {
          return@mapNotNull null
        }

        val file = item.file
        if (!skipUnpack) {
          toUnpack.add(item)
        }
        ForkJoinTask.adapt(Callable {
          when {
            !Files.exists(file) -> item
            item.hash == computeHash(file) -> null
            else -> {
              span.addEvent("file has unexpected hash, will refetch", Attributes.of(
                AttributeKey.stringKey("file"), "${item.name}/${item.hash}.jar",
              ))
              Files.deleteIfExists(file)
              item
            }
          }
        })
      }).mapNotNull {
        it.rawResult
      }
      verifyTime += TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
      result
    }

    // toUnpack is performed as part of download
    toDownload.forEach(toUnpack::remove)

    withScope("cleanup outdated compiled class archives") {
      val start = System.nanoTime()
      var count = 0
      var bytes = 0L
      try {
        val preserve = items.mapTo(HashSet<Path>(items.size)) { it.file }
        val epoch = FileTime.fromMillis(0)
        val daysAgo = FileTime.fromMillis(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(4))
        Files.createDirectories(tempDownloadStorage)
        // we need to traverse with depth 3 since first level is [production, test], second level is module name, third is a file
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

    withScope("fetch compiled classes archives") {
      val start = System.nanoTime()

      val prefix = metadata.prefix
      val serverUrl = metadata.serverUrl

      val downloadedBytes = AtomicLong()
      val failed: List<Throwable> = if (toDownload.isEmpty()) {
        emptyList()
      }
      else {
        val httpClientWithoutFollowingRedirects = httpClient.newBuilder().followRedirects(false).build()
        createBufferPool().use { bufferPool ->
          downloadCompilationCache(serverUrl = serverUrl,
                                   prefix = prefix,
                                   toDownload = toDownload,
                                   client = httpClientWithoutFollowingRedirects,
                                   bufferPool = bufferPool,
                                   downloadedBytes = downloadedBytes,
                                   skipUnpack = skipUnpack,
                                   saveHash = saveHash)
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

    withScope("unpack compiled classes archives") {
      val start = System.nanoTime()

      ForkJoinTask.invokeAll(toUnpack.map { item ->
        forkJoinTask(spanBuilder("unpack").setAttribute("name", item.name)) {
          unpackArchive(item, saveHash)
        }
      })

      reportStatisticValue("compile-parts:unpacked:bytes", toUnpack.sumOf { Files.size(it.file) }.toString())
      reportStatisticValue("compile-parts:unpacked:count", toUnpack.size.toString())
      reportStatisticValue("compile-parts:unpack:time", TimeUnit.NANOSECONDS.toMillis((System.nanoTime() - start)).toString())
    }
  }
}

private fun checkPreviouslyUnpackedDirectories(items: List<FetchAndUnpackItem>,
                                               span: Span,
                                               upToDate: MutableSet<String>,
                                               metadata: CompilationPartsMetadata,
                                               classOutput: Path): Long {
  if (!Files.exists(classOutput)) {
    return 0
  }

  val start = System.nanoTime()
  ForkJoinTask.invokeAll(items.map { item ->
    ForkJoinTask.adapt {
      val out = item.output
      if (!Files.exists(out)) {
        span.addEvent("output directory doesn't exist", Attributes.of(
          AttributeKey.stringKey("name"), item.name,
          AttributeKey.stringKey("outDir"), out.toString(),
        ))
        return@adapt
      }

      val hashFile = out.resolve(".hash")
      if (!Files.isRegularFile(hashFile)) {
        span.addEvent("no .hash file in output directory", Attributes.of(
          AttributeKey.stringKey("name"), item.name,
        ))
        deleteDir(out)
        return@adapt
      }

      try {
        val actual = Files.readString(hashFile)
        if (actual == item.hash) {
          upToDate.add(item.name)
        }
        else {
          span.addEvent("output directory hash mismatch", Attributes.of(
            AttributeKey.stringKey("name"), item.name,
            AttributeKey.stringKey("expected"), item.hash,
            AttributeKey.stringKey("actual"), actual,
          ))
          deleteDir(out)
        }
      }
      catch (e: Throwable) {
        span.addEvent("output directory hash calculation failed", Attributes.of(
          AttributeKey.stringKey("name"), item.name,
        ))
        span.recordException(e, Attributes.of(
          AttributeKey.stringKey("name"), item.name,
        ))
        deleteDir(out)
      }
    }
  } + forkJoinTask(spanBuilder("remove stalled directories not present in metadata")
                     .setAttribute(AttributeKey.stringArrayKey("keys"),
                                   java.util.List.copyOf(metadata.files.keys))) {
    val expectedDirectories = HashSet(metadata.files.keys)
    // we need to traverse with depth 2 since first level is [production,test]
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

    ForkJoinTask.invokeAll(stalledDirs.map { dir ->
      forkJoinTask(spanBuilder("delete stalled dir").setAttribute("dir", dir.toString())) { deleteDir(dir) }
    })
  }
  )
  return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
}

private val sharedDigest = MessageDigest.getInstance("SHA-256", java.security.Security.getProvider("SUN"))
internal fun sha256() = sharedDigest.clone() as MessageDigest

private fun computeHash(file: Path): String {
  val messageDigest = sha256()
  FileChannel.open(file, READ_OPERATION).use { channel ->
    val fileSize = channel.size()
    // java message digest doesn't support native buffer (copies to heap byte array in any case)
    val bufferSize = 256 * 1024
    val sourceBuffer = ByteBuffer.allocate(bufferSize)
    var offset = 0L
    while (offset < fileSize) {
      sourceBuffer.limit(min((fileSize - offset).toInt(), bufferSize))
      do {
        offset += channel.read(sourceBuffer, offset)
      }
      while (sourceBuffer.hasRemaining())

      messageDigest.update(sourceBuffer.array(), 0, sourceBuffer.limit())
      sourceBuffer.position(0)
    }
  }
  return digestToString(messageDigest)
}

// we cannot change file extension or prefix, so, add suffix
internal fun digestToString(digest: MessageDigest): String = BigInteger(1, digest.digest()).toString(36) + "-z"

data class PackAndUploadItem(
  val output: Path,
  val name: String,
  val archive: Path,
) {
  var hash: String? = null
}

internal data class FetchAndUnpackItem(
  val name: String,
  val hash: String,
  val output: Path,
  val file: Path
)

/**
 * Configuration on which compilation parts to download and from where.
 * <br/>
 * URL for each part should be constructed like: <pre>${serverUrl}/${prefix}/${files.key}/${files.value}.jar</pre>
 */
@Serializable
private data class CompilationPartsMetadata(
  @SerialName("server-url") val serverUrl: String,
  val branch: String,
  val prefix: String,
  /**
   * Map compilation part path to a hash, for now SHA-256 is used.
   * sha256(file) == hash, though that may be changed in the future.
   */
  val files: Map<String, String>,
)

@PublishedApi
internal val THREAD_NAME: AttributeKey<String> = AttributeKey.stringKey("thread.name")
@PublishedApi
internal val THREAD_ID: AttributeKey<Long> = AttributeKey.longKey("thread.id")

/**
 * Returns a new [ForkJoinTask] that performs the given function as its action within a trace, and returns
 * a null result upon [ForkJoinTask.join].
 *
 * See [Span](https://opentelemetry.io/docs/reference/specification).
 */
inline fun <T> forkJoinTask(spanBuilder: SpanBuilder, crossinline operation: (Span) -> T): ForkJoinTask<T> {
  val context = Context.current()
  return ForkJoinTask.adapt(Callable {
    val thread = Thread.currentThread()
    spanBuilder
      .setParent(context)
      .setAttribute(THREAD_NAME, thread.name)
      .setAttribute(THREAD_ID, thread.id)
      .use { span ->
        operation(span)
      }
  })
}