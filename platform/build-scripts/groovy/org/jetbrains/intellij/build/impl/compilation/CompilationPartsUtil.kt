// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl.compilation

import com.intellij.diagnostic.telemetry.forkJoinTask
import com.intellij.diagnostic.telemetry.useWithScope
import com.intellij.openapi.util.io.NioFiles
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.io.deleteDir
import org.jetbrains.intellij.build.io.zip
import java.io.OutputStream
import java.math.BigInteger
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.security.Provider
import java.util.*
import java.util.concurrent.*
import java.util.zip.GZIPOutputStream
import kotlin.io.path.name

fun packAndUploadToServer(context: CompilationContext, zipsLocation: Path) {
  val contexts = spanBuilder("pack classes").useWithScope {
    packCompilationResult(context, zipsLocation)
  }

  // 4MB block, x2 of FJP thread count - one buffer to source, another one for target
  val bufferPool = createBufferPool()
  try {
    spanBuilder("upload packed classes to the server").useWithScope {
      upload(zipsLocation, context.messages, contexts, bufferPool)
    }
  }
  finally {
    bufferPool.releaseAll()
  }
}

private fun createBufferPool(): DirectFixedSizeByteBufferPool {
  return DirectFixedSizeByteBufferPool(size = MAX_BUFFER_SIZE, maxPoolSize = ForkJoinPool.getCommonPoolParallelism() * 2)
}

private fun packCompilationResult(context: CompilationContext, zipsLocation: Path): List<PackAndUploadItem> {
  val incremental = context.options.incrementalCompilation
  if (!incremental) {
    NioFiles.deleteRecursively(zipsLocation)
  }
  Files.createDirectories(zipsLocation)

  val items = ArrayList<PackAndUploadItem>(2048)
  val span = Span.current()
  // production, test
  for (subRoot in Files.newDirectoryStream(context.projectOutputDirectory).use(DirectoryStream<Path>::toList)) {
    if (!Files.isDirectory(subRoot)) {
      continue
    }

    val subRootName = subRoot.name
    Files.createDirectories(zipsLocation.resolve(subRootName))

    Files.newDirectoryStream(subRoot).use { subRootStream ->
      for (module in subRootStream) {
        val name = "$subRootName/${module.name}"
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

        if (context.findModule(module.name) == null) {
          span.addEvent("skip module output from missing in project module", Attributes.of(
            AttributeKey.stringKey("module"), module.name,
          ))
          continue
        }

        items.add(PackAndUploadItem(output = module, name = name, archive = zipsLocation.resolve("$name.jar")))
      }
    }
  }

  spanBuilder("build zip archives").useWithScope {
    runUnderStatisticsTimer(context.messages, "compile-parts:pack:time") {
      ForkJoinTask.invokeAll(items.map { item ->
        forkJoinTask(spanBuilder("pack").setAttribute("name", item.name)) {
          // we compress the whole file using LZ4
          zip(targetFile = item.archive, dirs = mapOf(item.output to ""), compress = false, overwrite = true)
        }
      })
    }
  }
  return items
}

private fun isModuleOutputDirEmpty(moduleOutDir: Path): Boolean {
  Files.newDirectoryStream(moduleOutDir).use {
    for (child in it) {
      if (!child.endsWith("classpath.index") && !child.endsWith(".unmodified") && !child.endsWith(".DS_Store")) {
        return false
      }
    }
  }
  return true
}

// TODO: Remove hardcoded constant
internal const val uploadPrefix = "intellij-compile/v2"

private fun upload(zipsLocation: Path, messages: BuildMessages, contexts: List<PackAndUploadItem>, bufferPool: DirectFixedSizeByteBufferPool) {
  val serverUrlPropertyName = "intellij.build.compiled.classes.server.url"
  val serverUrl = System.getProperty(serverUrlPropertyName)?.trimEnd('/')
  check(!serverUrl.isNullOrBlank()) {
    "Compile Parts archive server url is not defined. \nPlease set $serverUrlPropertyName system property."
  }

  val artifactBranchPropertyName = "intellij.build.compiled.classes.branch"
  val branch = System.getProperty(artifactBranchPropertyName)
  check(!branch.isNullOrBlank()) {
    "Unable to determine current git branch, assuming 'master'. \nPlease set '$artifactBranchPropertyName' system property"
  }

  val hashes = spanBuilder("compute archives checksums").useWithScope {
    runUnderStatisticsTimer(messages, "compile-parts:checksum:time") {
      ForkJoinTask.invokeAll(contexts.map { item ->
        ForkJoinTask.adapt(Callable { item.name to computeHash(item.archive)!! })
      }).associateTo(TreeMap()) { it.rawResult }
    }
  }

  val httpClient = createHttpClient("Parts Uploader")

  // prepare metadata for writing into file
  val metadataJson = Json.encodeToString(CompilationPartsMetadata(
    serverUrl = serverUrl,
    branch = branch,
    prefix = uploadPrefix,
    files = hashes,
  ))

  spanBuilder("upload archives").useWithScope {
    uploadArchives(messages = messages,
                   serverUrl = serverUrl,
                   metadataJson = metadataJson,
                   httpClient = httpClient,
                   contexts = contexts,
                   hashes = hashes,
                   bufferPool = bufferPool)
  }

  // save and publish metadata file
  val metadataFile = zipsLocation.resolve("metadata.json")
  Files.createDirectories(metadataFile.parent)
  Files.writeString(metadataFile, metadataJson)
  messages.artifactBuilt(metadataFile.toString())

  val gzippedMetadataFile = zipsLocation.resolve("metadata.json.gz")
  GZIPOutputStream(Files.newOutputStream(gzippedMetadataFile)).use { outputStream ->
    Files.copy(metadataFile, outputStream)
  }
  messages.artifactBuilt(gzippedMetadataFile.toString())
}

@VisibleForTesting
fun fetchAndUnpackCompiledClasses(reportStatisticValue: (key: String, value: String) -> Unit,
                                  classOutput: Path,
                                  metadataFile: Path,
                                  saveHash: Boolean) {
  spanBuilder("fetch and unpack compiled classes").useWithScope {
    val metadata = Json.decodeFromString<CompilationPartsMetadata>(Files.readString(metadataFile))
    val tempDownloadStorage = (System.getProperty("agent.persistent.cache")?.let { Path.of(it) } ?: classOutput.parent)
      .resolve("idea-compile-parts-v2")

    val items = metadata.files.mapTo(ArrayList(metadata.files.size)) { entry ->
      FetchAndUnpackItem(name = entry.key,
                         hash = entry.value,
                         output = classOutput.resolve(entry.key),
                         file = tempDownloadStorage.resolve("${entry.key}/${entry.value}.jar"))
    }
    items.sortBy { it.name }

    var verifyTime = 0L
    val upToDate = Collections.newSetFromMap<String>(ConcurrentHashMap())
    spanBuilder("check previously unpacked directories").useWithScope { span ->
      verifyTime += checkPreviouslyUnpackedDirectories(items = items,
                                                       span = span,
                                                       upToDate = upToDate,
                                                       metadata = metadata,
                                                       classOutput = classOutput)
    }
    reportStatisticValue("compile-parts:up-to-date:count", upToDate.size.toString())

    val toUnpack = LinkedHashSet<FetchAndUnpackItem>(items.size)
    val toDownload = spanBuilder("check previously downloaded archives").useWithScope { span ->
      val start = System.nanoTime()
      val result = ForkJoinTask.invokeAll(items.mapNotNull { item ->
        if (upToDate.contains(item.name)) {
          return@mapNotNull null
        }

        val file = item.file
        toUnpack.add(item)
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

    spanBuilder("cleanup outdated compiled class archives").useWithScope {
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

    spanBuilder("fetch compiled classes archives").useWithScope {
      val start = System.nanoTime()

      val prefix = metadata.prefix
      val serverUrl = metadata.serverUrl

      val failed = if (toDownload.isEmpty()) {
        emptyList()
      }
      else {
        val client = createHttpClient("Parts Downloader", followRedirects = false)
        val bufferPool = createBufferPool()
        try {
          downloadCompilationCache(serverUrl = serverUrl,
                                   prefix = prefix,
                                   toDownload = toDownload,
                                   client = client,
                                   bufferPool = bufferPool,
                                   saveHash = saveHash)
        }
        finally {
          bufferPool.releaseAll()
          client.dispatcher.executorService.shutdown()
          client.connectionPool.evictAll()
        }
      }

      reportStatisticValue("compile-parts:download:time", TimeUnit.NANOSECONDS.toMillis((System.nanoTime() - start)).toString())

      val downloadedBytes = toDownload.sumOf { Files.size(it.file) }

      reportStatisticValue("compile-parts:downloaded:bytes", downloadedBytes.toString())
      reportStatisticValue("compile-parts:downloaded:count", toDownload.size.toString())

      if (!failed.isEmpty()) {
        throw RuntimeException("Failed to fetch ${failed.size} file${if (failed.size > 1) "s" else ""}, see details above or in a trace file")
      }
    }

    spanBuilder("unpack compiled classes archives").useWithScope {
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
      val hashFile = out.resolve(".hash")
      if (!Files.isRegularFile(hashFile)) {
        span.addEvent("no .hash file in output directory", Attributes.of(
          AttributeKey.stringKey("name"), item.name,
        ))
        NioFiles.deleteRecursively(out)
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
          NioFiles.deleteRecursively(out)
        }
      }
      catch (e: Throwable) {
        span.addEvent("output directory hash calculation failed", Attributes.of(
          AttributeKey.stringKey("name"), item.name,
        ))
        span.recordException(e, Attributes.of(
          AttributeKey.stringKey("name"), item.name,
        ))
        NioFiles.deleteRecursively(out)
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
              val name = "${subRoot.name}/${module.name}"
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

internal val sunSecurityProvider: Provider = java.security.Security.getProvider("SUN")

private fun computeHash(file: Path): String? {
  if (!Files.exists(file)) {
    return null
  }

  val messageDigest = MessageDigest.getInstance("SHA-256", sunSecurityProvider)
  Files.copy(file, DigestOutputStream(messageDigest))
  return digestToString(messageDigest)
}

internal fun digestToString(digest: MessageDigest): String = BigInteger(1, digest.digest()).toString(36)

private class DigestOutputStream(private val digest: MessageDigest) : OutputStream() {
  override fun write(b: Int) {
    digest.update(b.toByte())
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    digest.update(b, off, len)
  }

  override fun toString() = "[Digest Output Stream] $digest"
}

internal data class PackAndUploadItem(
  val output: Path,
  val name: String,
  val archive: Path,
)

// based on org.jetbrains.intellij.build.impl.logging.BuildMessagesImpl.block
internal inline fun <V> runUnderStatisticsTimer(messages: BuildMessages, name: String, body: () -> V): V {
  val start = System.nanoTime()
  try {
    return body()
  }
  finally {
    val time = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
    messages.reportStatisticValue(name, time.toString())
  }
}

internal data class FetchAndUnpackItem(
  val name: String,
  val hash: String,
  val output: Path,
  val file: Path
)

private fun createHttpClient(userAgent: String, followRedirects: Boolean = true): OkHttpClient {
  return OkHttpClient.Builder()
    .addInterceptor { chain ->
      chain.proceed(chain.request()
                      .newBuilder()
                      .header("User-Agent", userAgent)
                      .build())
    }
    .addInterceptor { chain ->
      val request = chain.request()
      var response = chain.proceed(request)
      var tryCount = 0
      while (response.code >= 500 && tryCount < 3) {
        response.close()
        tryCount++
        response = chain.proceed(request)
      }
      response
    }
    .followRedirects(followRedirects)
    .build()
}

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