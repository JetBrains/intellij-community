// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl.compilation

import com.intellij.diagnostic.telemetry.forkJoinTask
import com.intellij.diagnostic.telemetry.use
import com.intellij.diagnostic.telemetry.useWithScope
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.io.Decompressor
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import net.jpountz.lz4.LZ4Factory
import net.jpountz.lz4.LZ4FrameInputStream
import net.jpountz.xxhash.XXHashFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.io.deleteDir
import org.jetbrains.intellij.build.io.zip
import java.io.IOException
import java.io.OutputStream
import java.math.BigInteger
import java.net.HttpURLConnection
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.security.Provider
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPOutputStream
import kotlin.io.path.name

fun packAndUploadToServer(context: CompilationContext, zipsLocation: Path) {
  val contexts = spanBuilder("pack classes").useWithScope {
    packCompilationResult(context, zipsLocation)
  }
  spanBuilder("upload packed classes to the server").useWithScope {
    upload(zipsLocation, context.messages, contexts)
  }
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
private const val uploadPrefix = "intellij-compile/v2"

private fun upload(zipsLocation: Path, messages: BuildMessages, contexts: List<PackAndUploadItem>) {
  val serverUrlPropertyName = "intellij.build.compiled.classes.server.url"
  val serverUrl = System.getProperty(serverUrlPropertyName)?.trimEnd('/')
  check(!serverUrl.isNullOrBlank()) {
    "Compile Parts archive server url is not defined. \nPlease set $serverUrlPropertyName system property."
  }

  val artifactBranchPropertyName = "intellij.build.compiled.classes.branch"
  val branch = System.getProperty(artifactBranchPropertyName)
  check(!branch.isNullOrBlank()) {
    messages.error("Unable to determine current git branch, assuming 'master'. \nPlease set '$artifactBranchPropertyName' system property")
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
                   hashes = hashes)
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

private fun uploadArchives(messages: BuildMessages,
                           serverUrl: String,
                           metadataJson: String,
                           httpClient: OkHttpClient,
                           contexts: List<PackAndUploadItem>,
                           hashes: TreeMap<String, String>) {
  val uploadedCount = AtomicInteger()
  val uploadedBytes = AtomicLong()
  val reusedCount = AtomicInteger()
  val reusedBytes = AtomicLong()

  runUnderStatisticsTimer(messages, "compile-parts:upload:time") {
    val alreadyUploaded = HashSet<String>()
    val fallbackToHeads = try {
      val files = spanBuilder("fetch info about already uploaded files").use {
        getFoundAndMissingFiles(metadataJson, serverUrl, httpClient)
      }
      alreadyUploaded.addAll(files.found)
      false
    }
    catch (e: Throwable) {
      Span.current().addEvent("failed to fetch info about already uploaded files, will fallback to HEAD requests")
      true
    }

    ForkJoinTask.invokeAll(contexts.mapNotNull { item ->
      if (alreadyUploaded.contains(item.name)) {
        reusedCount.getAndIncrement()
        reusedBytes.getAndAdd(Files.size(item.archive))
        return@mapNotNull null
      }

      forkJoinTask(spanBuilder("upload archive").setAttribute("name", item.name)) {
        val hash = hashes.get(item.name)
        // do not use `.lz4` extension - server uses hard-coded `.jar` extension
        // see https://jetbrains.team/p/iji/repositories/intellij-compile-artifacts/files/d91706d68b22502de56c78cdd6218eab3b395b3f/main-server/batch-files-checker/main.go?tab=source&line=62
        if (uploadFile(url = "$serverUrl/$uploadPrefix/${item.name}/${hash}.jar",
                       file = item.archive,
                       useHead = fallbackToHeads,
                       span = Span.current(),
                       httpClient = httpClient)) {
          uploadedCount.getAndIncrement()
          uploadedBytes.getAndAdd(Files.size(item.archive))
        }
        else {
          reusedCount.getAndIncrement()
          reusedBytes.getAndAdd(Files.size(item.archive))
        }
      }
    })
  }

  messages.info("Upload complete: reused ${reusedCount.get()} parts, uploaded ${uploadedCount.get()} parts")
  messages.reportStatisticValue("compile-parts:reused:bytes", reusedBytes.get().toString())
  messages.reportStatisticValue("compile-parts:reused:count", reusedCount.get().toString())
  messages.reportStatisticValue("compile-parts:uploaded:bytes", uploadedBytes.get().toString())
  messages.reportStatisticValue("compile-parts:uploaded:count", uploadedCount.get().toString())
  messages.reportStatisticValue("compile-parts:total:bytes", (reusedBytes.get() + uploadedBytes.get()).toString())
  messages.reportStatisticValue("compile-parts:total:count", (reusedCount.get() + uploadedCount.get()).toString())
}

fun fetchAndUnpackCompiledClasses(messages: BuildMessages, classesOutput: Path, options: BuildOptions) {
  val metadataFile = options.pathToCompiledClassesArchivesMetadata?.let { Path.of(it) }
  check(metadataFile != null && !Files.isRegularFile(metadataFile)) {
    "Cannot fetch compiled classes: metadata file not found at ${options.pathToCompiledClassesArchivesMetadata}"
  }

  val forInstallers = System.getProperty("intellij.fetch.compiled.classes.for.installers", "false").toBoolean()
  val metadata = Json.decodeFromString<CompilationPartsMetadata>(Files.readString(metadataFile))
  val persistentCache = System.getProperty("agent.persistent.cache")
  val cache = if (persistentCache == null) classesOutput.parent else Path.of(persistentCache).toAbsolutePath().normalize()
  val tempDownloadsStorage = cache.resolve("idea-compile-parts-v2")

  val contexts = metadata.files.mapTo(ArrayList(metadata.files.size)) { entry ->
    FetchAndUnpackContext(name = entry.key,
                          hash = entry.value,
                          output = classesOutput.resolve(entry.key),
                          saveHash = !forInstallers,
                          jar = tempDownloadsStorage.resolve("${entry.key}/${entry.value}.jar"))
  }
  contexts.sortBy { it.name }

  var verifyTime = 0L

  val upToDate = Collections.newSetFromMap<String>(ConcurrentHashMap())

  spanBuilder("check previously unpacked directories").useWithScope { span ->
    val start = System.nanoTime()
    ForkJoinTask.invokeAll(contexts.map { item ->
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
      Files.newDirectoryStream(classesOutput).use { rootStream ->
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
    verifyTime += TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
  }

  messages.reportStatisticValue("compile-parts:up-to-date:count", upToDate.size.toString())

  val toUnpack = ArrayList<FetchAndUnpackContext>(contexts.size)
  val toDownload = spanBuilder("check previously downloaded archives").useWithScope { span ->
    val start = System.nanoTime()
    val result = ForkJoinTask.invokeAll(contexts.mapNotNull { item ->
      if (upToDate.contains(item.name)) {
        return@mapNotNull null
      }

      val file = item.jar
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
    }).mapNotNull { it.rawResult }
    verifyTime += TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
    result
  }

  spanBuilder("cleanup outdated compiled classes archives").useWithScope {
    val start = System.nanoTime()
    var count = 0
    var bytes = 0L
    try {
      val preserve = HashSet(contexts.map { it.jar })
      val epoch = FileTime.fromMillis(0)
      val daysAgo = FileTime.fromMillis(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(4))
      Files.createDirectories(tempDownloadsStorage)
      // We need to traverse with depth 3 since first level is [production, test], second level is module name, third is file.
      Files.walk(tempDownloadsStorage, 3, FileVisitOption.FOLLOW_LINKS).use { stream ->
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
      messages.warning("Failed to cleanup outdated archives: ${e.message}")
    }

    messages.reportStatisticValue("compile-parts:cleanup:time", TimeUnit.NANOSECONDS.toMillis((System.nanoTime() - start)).toString())
    messages.reportStatisticValue("compile-parts:removed:bytes", bytes.toString())
    messages.reportStatisticValue("compile-parts:removed:count", count.toString())
  }

  spanBuilder("fetch compiled classes archives").useWithScope { span ->
    val start = System.nanoTime()

    val prefix = metadata.prefix
    val serverUrl = metadata.serverUrl

    val failed = if (toDownload.isEmpty()) {
      emptyList()
    }
    else {
      val client = createHttpClient("Parts Downloader", followRedirects = false)
      try {
        download(serverUrl = serverUrl, prefix = prefix, toDownload = toDownload, client = client)
      }
      finally {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
      }
    }

    messages.reportStatisticValue("compile-parts:download:time", TimeUnit.NANOSECONDS.toMillis((System.nanoTime() - start)).toString())

    val downloadedBytes = toDownload.sumOf { Files.size(it.jar) }

    messages.reportStatisticValue("compile-parts:downloaded:bytes", downloadedBytes.toString())
    messages.reportStatisticValue("compile-parts:downloaded:count", toDownload.size.toString())

    if (!failed.isEmpty()) {
      span.addEvent("failed to fetch", Attributes.of(
        AttributeKey.stringArrayKey("items"), failed.map { "${it.first.name}/${it.first.jar.fileName}', status code: ${it.second}" }
      ))
      throw RuntimeException("failed to fetch ${failed.size} file${if (failed.size > 1) "s" else ""}, see details above or in a trace file")
    }
  }

  spanBuilder("verify downloaded archives").useWithScope { span ->
    val start = System.nanoTime()
    // todo: retry download if hash verification failed
    val failed = ForkJoinTask.invokeAll(toDownload.map { item ->
      ForkJoinTask.adapt(Callable {
        val computed = computeHash(item.jar)
        val expected = item.hash
        if (expected == computed) {
          true
        }
        else {
          span.addEvent("hash mismatch", Attributes.of(
            AttributeKey.stringKey("name"), item.jar.name,
            AttributeKey.stringKey("expected"), expected,
            AttributeKey.stringKey("computed"), computed ?: "",
          ))
          false
        }
      })
    }).filter { !it.rawResult }

    verifyTime += TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
    messages.reportStatisticValue("compile-parts:verify:time", verifyTime.toString())
    if (!failed.isEmpty()) {
      messages.error("Hash mismatch for ${failed.size} downloaded files, see details above or in a trace file")
    }
  }

  spanBuilder("unpack compiled classes archives").useWithScope {
    val start = System.nanoTime()

    ForkJoinTask.invokeAll(toUnpack.map { item ->
      forkJoinTask(spanBuilder("unpack").setAttribute("name", item.name)) {
        Files.createDirectories(item.output)
        Decompressor.Zip(item.jar).overwrite(true).extract(item.output)
        if (item.saveHash) {
          // save actual hash
          Files.writeString(item.output.resolve(".hash"), item.hash)
        }
      }
    })

    messages.reportStatisticValue("compile-parts:unpacked:bytes", toUnpack.sumOf { Files.size(it.jar) }.toString())
    messages.reportStatisticValue("compile-parts:unpacked:count", toUnpack.size.toString())
    messages.reportStatisticValue("compile-parts:unpack:time", TimeUnit.NANOSECONDS.toMillis((System.nanoTime() - start)).toString())
  }
}

private fun download(serverUrl: String,
                     prefix: String,
                     toDownload: List<FetchAndUnpackContext>,
                     client: OkHttpClient): List<Pair<FetchAndUnpackContext, Int>> {
  var urlWithPrefix = "$serverUrl/$prefix/"
  // first let's check for initial redirect (mirror selection)
  spanBuilder("mirror selection").useWithScope { span ->
    client.newCall(Request.Builder()
                     .url(urlWithPrefix)
                     .head()
                     .build()).execute().use { response ->
      val statusCode = response.code
      val locationHeader = response.header("location")
      if ((statusCode == HttpURLConnection.HTTP_MOVED_TEMP ||
           statusCode == HttpURLConnection.HTTP_MOVED_PERM ||
           statusCode == 307 ||
           statusCode == HttpURLConnection.HTTP_SEE_OTHER)
          && locationHeader != null) {
        urlWithPrefix = locationHeader
        span.addEvent("redirected to mirror", Attributes.of(AttributeKey.stringKey("url"), urlWithPrefix))
      }
      else {
        span.addEvent("origin server will be used", Attributes.of(AttributeKey.stringKey("url"), urlWithPrefix))
      }
    }
  }

  val lz4Decompressor = LZ4Factory.fastestJavaInstance().safeDecompressor()
  val xxHash32 = XXHashFactory.fastestInstance().hash32()
  return ForkJoinTask.invokeAll(toDownload.map { item ->
    forkJoinTask(spanBuilder("download").setAttribute("name", item.name)) {
      val jar = item.jar
      Files.createDirectories(jar.parent)
      client.newCall(Request.Builder()
                       .url("${urlWithPrefix}${item.name}/${jar.fileName}")
                       .build()).execute().use { response ->
        if (!response.isSuccessful) {
          Pair(item, response.code)
        }
        else {
          // do not use files.copy - replace in place if exists
          Files.newOutputStream(jar).use { output ->
            LZ4FrameInputStream(response.body.byteStream(), lz4Decompressor, xxHash32).use { input ->
              input.transferTo(output)
            }
        }
        null
        }
      }
    }
  }).mapNotNull { it.rawResult }
}

private val sunSecurityProvider: Provider = java.security.Security.getProvider("SUN")

private fun computeHash(file: Path): String? {
  if (!Files.exists(file)) {
    return null
  }

  val messageDigest = MessageDigest.getInstance("SHA-256", sunSecurityProvider)
  Files.copy(file, DigestOutputStream(messageDigest))
  return BigInteger(1, messageDigest.digest()).toString(36)
}

private class DigestOutputStream(private val digest: MessageDigest) : OutputStream() {
  override fun write(b: Int) {
    digest.update(b.toByte())
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    digest.update(b, off, len)
  }

  override fun toString() = "[Digest Output Stream] $digest"
}

private data class PackAndUploadItem(
  val output: Path,
  val name: String,
  val archive: Path,
)

// based on org.jetbrains.intellij.build.impl.logging.BuildMessagesImpl.block
private inline fun <V> runUnderStatisticsTimer(messages: BuildMessages, name: String, body: () -> V): V {
  val start = System.nanoTime()
  try {
    return body()
  }
  finally {
    val time = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
    messages.reportStatisticValue(name, time.toString())
  }
}

private data class FetchAndUnpackContext(
  val name: String,
  val hash: String,
  val output: Path,
  val saveHash: Boolean,
  val jar: Path
)

@Serializable
private data class CheckFilesResponse(
  val found: List<String> = emptyList(),
  val missing: List<String> = emptyList()
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

private val MEDIA_TYPE_JSON = "application/json".toMediaType()

private fun getFoundAndMissingFiles(metadataJson: String, serverUrl: String, client: OkHttpClient): CheckFilesResponse {
  val request = Request.Builder()
    .url("$serverUrl/check-files")
    .post(metadataJson.toRequestBody(MEDIA_TYPE_JSON))
    .build()

  return client.newCall(request).execute().use { response ->
    if (!response.isSuccessful) {
      throw IOException("Failed to check for found and missing files: $response")
    }

    Json.decodeFromStream(response.body.byteStream())
  }
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