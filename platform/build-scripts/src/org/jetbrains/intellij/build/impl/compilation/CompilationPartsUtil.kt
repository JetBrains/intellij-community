// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "UnstableApiUsage", "ReplacePutWithAssignment")
@file:OptIn(ExperimentalPathApi::class)

package org.jetbrains.intellij.build.impl.compilation

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.forEachConcurrent
import org.jetbrains.intellij.build.http2Client.withHttp2ClientConnectionFactory
import org.jetbrains.intellij.build.io.AddDirEntriesMode
import org.jetbrains.intellij.build.io.zip
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPOutputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.listDirectoryEntries

private val nettyMax = Runtime.getRuntime().availableProcessors() * 2
internal val uploadParallelism = nettyMax.coerceIn(4, 32)
// max not 32 as for upload because we write to disk (not read as upload)
internal val downloadParallelism = nettyMax.coerceIn(4, 24)

private const val BRANCH_PROPERTY_NAME = "intellij.build.compiled.classes.branch"
private const val SERVER_URL_PROPERTY = "intellij.build.compiled.classes.server.url"
private const val UPLOAD_PREFIX = "intellij.build.compiled.classes.upload.prefix"

class CompilationCacheUploadConfiguration(
  serverUrl: String? = null,
  val checkFiles: Boolean = true,
  val uploadOnly: Boolean = false,
  branch: String? = null,
  uploadPredix: String? = null,
) {
  val serverUrl: String by lazy(LazyThreadSafetyMode.NONE) { serverUrl ?: getAndNormalizeServerUrlBySystemProperty() }

  private val serverUri: URI by lazy(LazyThreadSafetyMode.NONE) { URI(serverUrl ?: getAndNormalizeServerUrlBySystemProperty()) }

  // even if empty, a final path must always start with `/` (otherwise, error like "client sent invalid :path header")
  val serverUrlPathPrefix: String by lazy(LazyThreadSafetyMode.NONE) { serverUri.path }

  val serverAddress: InetSocketAddress by lazy {
    InetSocketAddress.createUnresolved(serverUri.host, serverUri.port.let { if (it == -1) 443 else it })
  }

  val uploadUrlPathPrefix: String by lazy {
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

private fun getAndNormalizeServerUrlBySystemProperty(): String {
  var result = System.getProperty(SERVER_URL_PROPERTY)?.trimEnd('/')
  check(!result.isNullOrBlank()) {
    "Compilation cache archive server url is not defined. Please set $SERVER_URL_PROPERTY system property."
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

private suspend fun packCompilationResult(zipDir: Path, context: CompilationContext, addDirEntriesMode: AddDirEntriesMode = AddDirEntriesMode.ALL): List<PackAndUploadItem> {
  val incremental = context.options.incrementalCompilation
  if (!incremental) {
    try {
      zipDir.deleteRecursively()
    }
    catch (_: NoSuchFileException) {
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
          catch (_: FileSystemException) {
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
    items.forEachConcurrent { item ->
      item.hash = packAndComputeHash(addDirEntriesMode = addDirEntriesMode, name = item.name, archive = item.archive, directory = item.output)
    }
  }
  return items
}

internal suspend fun packAndComputeHash(
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
      prefix = config.uploadUrlPathPrefix,
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

  val serverAddress = config.serverAddress
  withHttp2ClientConnectionFactory(trustAll = serverAddress.hostString == "127.0.0.1") { client ->
    client.connect(serverAddress).use { connection ->
      spanBuilder("upload archives").setAttribute(AttributeKey.stringArrayKey("items"), items.map(PackAndUploadItem::name)).use {
        uploadArchives(
          reportStatisticValue = messages::reportStatisticValue,
          config = config,
          metadataJson = metadataJson,
          httpConnection = connection,
          items = items,
        )
      }
    }
  }
}

internal fun getArchiveStorage(fallbackPersistentCacheRoot: Path): Path {
  return (System.getProperty("agent.persistent.cache")?.let { Path.of(it) } ?: fallbackPersistentCacheRoot).resolve("idea-compile-parts-v2")
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
  val tempDownloadStorage = getArchiveStorage(classOutput.parent)

  val items = metadata.files.mapTo(ArrayList(metadata.files.size)) { entry ->
    FetchAndUnpackItem(
      name = entry.key,
      hash = entry.value,
      output = classOutput.resolve(entry.key),
      file = tempDownloadStorage.resolve("${entry.key}/${entry.value}.jar"),
    )
  }
  items.sortBy { it.name }

  val upToDate = ConcurrentHashMap.newKeySet<String>()
  var verifyTime = spanBuilder("check previously unpacked directories").use { span ->
    checkPreviouslyUnpackedDirectories(
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
  val toDownload = ConcurrentHashMap.newKeySet<FetchAndUnpackItem>()
  spanBuilder("check previously downloaded archives").use { span ->
    items.filter { item ->
      if (upToDate.contains(item.name)) {
        return@filter false
      }

      if (!skipUnpack) {
        toUnpack.add(item)
      }
      true
    }
      .forEachConcurrent(Runtime.getRuntime().availableProcessors().coerceAtLeast(4)) { item ->
        val file = item.file
        when {
          Files.notExists(file) -> toDownload.add(item)
          item.hash == computeHash(file) -> return@forEachConcurrent
          else -> {
            span.addEvent("file has unexpected hash, will refetch", Attributes.of(AttributeKey.stringKey("file"), "${item.name}/${item.hash}.jar"))
            Files.deleteIfExists(file)
            toDownload.add(item)
          }
        }
      }
  }
  verifyTime += System.nanoTime() - verifyStart

  // toUnpack is performed as part of download
  for (item in toDownload) {
    toUnpack.remove(item)
  }

  spanBuilder("cleanup outdated compiled class archives").use {
    cleanupOutdatedCompiledClassArchives(items = items, tempDownloadStorage = tempDownloadStorage, reportStatisticValue = reportStatisticValue)
  }
  reportStatisticValue("compile-parts:verify:time", TimeUnit.NANOSECONDS.toMillis(verifyTime).toString())

  spanBuilder("fetch compiled classes archives").use {
    val start = System.nanoTime()

    val downloadedBytes = AtomicLong()
    withHttp2ClientConnectionFactory(trustAll = metadata.serverUrl.contains("127.0.0.1")) { client ->
      downloadCompilationCache(
        client = client,
        serverUrl = if (metadata.prefix.trim('/').isEmpty()) URI(metadata.serverUrl) else URI(metadata.serverUrl.trimEnd('/') + '/' + metadata.prefix),
        toDownload = toDownload,
        downloadedBytes = downloadedBytes,
        skipUnpack = skipUnpack,
        saveHash = saveHash,
      )
    }

    reportStatisticValue("compile-parts:download:time", TimeUnit.NANOSECONDS.toMillis((System.nanoTime() - start)).toString())

    reportStatisticValue("compile-parts:downloaded:bytes", downloadedBytes.get().toString())
    reportStatisticValue("compile-parts:downloaded:count", toDownload.size.toString())
  }

  val start = System.nanoTime()
  spanBuilder("unpack compiled classes archives").use(Dispatchers.IO) {
    toUnpack.forEachConcurrent(Runtime.getRuntime().availableProcessors().coerceAtLeast(4)) { item ->
      spanBuilder("unpack").setAttribute("name", item.name).use {
        unpackCompilationPartArchive(item, saveHash)
      }
    }
  }
  reportStatisticValue("compile-parts:unpacked:bytes", toUnpack.sumOf { Files.size(it.file) }.toString())
  reportStatisticValue("compile-parts:unpacked:count", toUnpack.size.toString())
  reportStatisticValue("compile-parts:unpack:time", TimeUnit.NANOSECONDS.toMillis((System.nanoTime() - start)).toString())
}

private fun cleanupOutdatedCompiledClassArchives(
  items: List<FetchAndUnpackItem>,
  tempDownloadStorage: Path,
  reportStatisticValue: (key: String, value: String) -> Unit,
) {
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
    val name = "remove stalled directories not present in metadata"
    launch(CoroutineName(name)) {
      @Suppress("RemoveRedundantQualifierName")
      spanBuilder(name).setAttribute(AttributeKey.stringArrayKey("keys"), java.util.List.copyOf(metadata.files.keys)).use {
        removeStalledDirs(metadata, classOutput)
      }
    }

    items.forEachConcurrent { item ->
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
  return System.nanoTime() - start
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
      catch (_: NoSuchFileException) {
      }
    }
  }

  for (dir in stalledDirs) {
    launch(CoroutineName("delete stalled dir $dir")) {
      spanBuilder("delete stalled dir").setAttribute("dir", dir.toString()).use {
        dir.deleteRecursively()
      }
    }
  }
}

internal data class PackAndUploadItem(
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
internal data class CompilationPartsMetadata(
  @JvmField @SerialName("server-url") val serverUrl: String,
  @JvmField val branch: String,
  @JvmField val prefix: String,
  /**
   * Map compilation part path to a hash, for now SHA-256 is used.
   * `sha256(file)` == hash, though that may be changed in the future.
   */
  @JvmField val files: Map<String, String>,
)