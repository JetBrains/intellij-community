// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.diagnostic.LoadingState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.Decompressor
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.write
import com.intellij.util.lang.JavaVersion
import com.intellij.util.system.CpuArch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import org.jetbrains.jps.model.java.JdkVersionDetector
import org.tukaani.xz.XZInputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/** describes vendor + product part of the UI **/
@Internal
data class JdkProduct(
  val vendor: @NlsSafe String,
  val product: @NlsSafe String?,
  val flavour: @NlsSafe String?
) {
  val packagePresentationText: String
    get() = buildString {
      append(vendor)
      if (product != null) {
        append(" ")
        append(product)
      }

      if (flavour != null) {
        append(" (")
        append(flavour)
        append(")")
      }
    }
}

/** describes an item behind the version as well as download info **/
@Internal
data class JdkItem(
  val product: JdkProduct,

  val isDefaultItem: Boolean = false,

  /** there are some JdkList items that are not shown in the downloader but suggested for JdkAuto **/
  val isVisibleOnUI: Boolean,

  val jdkMajorVersion: Int,
  @NlsSafe
  val jdkVersion: String,
  val jdkVendorVersion: String?,
  val suggestedSdkName: String,

  val os: String,
  /**
   * @see presentableArchIfNeeded
   */
  @NlsSafe
  val arch: String,
  val packageType: JdkPackageType,
  val url: String,
  val sha256: String,

  val archiveSize: Long,
  val unpackedSize: Long,

  // we should only extract items that has the given prefix removing the prefix
  val packageRootPrefix: String,
  // the path from the package root to the java home directory (where bin/java is)
  val packageToBinJavaPrefix: String,

  val archiveFileName: String,
  val installFolderName: String,

  val sharedIndexAliases: List<String>,

  private val saveToFile: (Path) -> Unit
) {
  val archiveSizeInMB: String = String.format("%.1f", archiveSize.toDouble() / 1024 / 1024)

  fun writeMarkerFile(file: Path) {
    saveToFile(file)
  }

  override fun toString(): String = "JdkItem($fullPresentationText, $url)"

  override fun hashCode(): Int = sha256.hashCode()
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as JdkItem

    if (jdkVersion != other.jdkVersion) return false
    if (url != other.url) return false
    if (sha256 != other.sha256) return false

    return true
  }

  /**
   * the Java Home folder (which contains the `bin` folder and `bin/java` path
   * may be deep inside a JDK package, e.g. on macOS
   * This method helps to find a traditional Java Home
   * from a JDK install directory
   */
  fun resolveJavaHome(installDir: Path): Path {
    val packageToBinJavaPrefix = packageToBinJavaPrefix
    if (packageToBinJavaPrefix.isBlank()) return installDir
    return installDir.resolve(packageToBinJavaPrefix)
  }

  private val vendorPrefix
    get() = suggestedSdkName.split("-").dropLast(1).joinToString("-")

  fun matchesVendor(predicate: String) : Boolean {
    val cases = sequence {
      yield(product.vendor)

      yield(vendorPrefix)
      if (product.product != null) {
        yield(product.product)
        yield("${product.vendor}-${product.product}")
        if (product.flavour != null) {
          yield("${product.product}-${product.flavour}")
          yield("${product.vendor}-${product.product}-${product.flavour}")
        }
      }
    }

    val match = predicate.trim()
    return cases.any { it.equals(match, ignoreCase = true) }
  }

  fun detectVariant(): JdkVersionDetector.Variant {
    return detectVariant(fullPresentationWithVendorText)
  }

  /**
   * Returns versionString for the Java Sdk object in specific format
   */
  val versionString: String
    get() = JavaVersion.tryParse(jdkVersion)?.let(JdkVersionDetector::formatVersionString) ?: jdkVersion

  val presentableVersionString: String
    get() = JavaVersion.tryParse(jdkVersion)?.toFeatureMinorUpdateString() ?: jdkVersion

  val presentableMajorVersionString: String
    get() = JavaVersion.tryParse(jdkVersion)?.toFeatureString() ?: jdkMajorVersion.toString()

  val versionPresentationText: String
    get() = jdkVersion

  val downloadSizePresentationText: String
    get() = StringUtil.formatFileSize(archiveSize)

  /**
   * returns Arch if it's expected to be shown, `null` otherwise
   */
  val presentableArchIfNeeded: @NlsSafe String?
    get() = if (arch != "x86_64") arch else null

  val fullPresentationText: @NlsSafe String
    get() = product.packagePresentationText + " " + jdkVersion + (presentableArchIfNeeded?.let {" ($it)" } ?: "")

  val fullPresentationWithVendorText: @NlsSafe String
    get() = product.packagePresentationText + " " + (jdkVendorVersion ?: jdkVersion) + (presentableArchIfNeeded?.let {" ($it)" } ?: "")

  companion object {
    fun detectVariant(vendorText: @NlsSafe String): JdkVersionDetector.Variant {
      if (vendorText.contains("Oracle OpenJDK")) return JdkVersionDetector.Variant.Oracle
      if (vendorText.contains("Corretto")) return JdkVersionDetector.Variant.Corretto
      if (vendorText.contains("BellSoft")) return JdkVersionDetector.Variant.Liberica
      if (vendorText.contains("Azul")) return JdkVersionDetector.Variant.Zulu
      if (vendorText.contains("SAP")) return JdkVersionDetector.Variant.SapMachine
      if (vendorText.contains("Temurin")) return JdkVersionDetector.Variant.Temurin
      if (vendorText.contains("Semeru")) return JdkVersionDetector.Variant.Semeru
      if (vendorText.contains("GraalVM Community")) return JdkVersionDetector.Variant.GraalVMCE
      if (vendorText.contains("GraalVM")) return JdkVersionDetector.Variant.GraalVM
      if (vendorText.contains("JetBrains Runtime")) return JdkVersionDetector.Variant.JBR
      return JdkVersionDetector.Variant.Unknown
    }
  }
}

enum class JdkPackageType(@NonNls val type: String) {
  ZIP("zip") {
    override fun openDecompressor(archiveFile: Path): Decompressor = Decompressor.Zip(archiveFile).withZipExtensions()
  },

  @Suppress("SpellCheckingInspection")
  TAR_GZ("targz") {
    override fun openDecompressor(archiveFile: Path): Decompressor = Decompressor.Tar(archiveFile)
  };

  abstract fun openDecompressor(archiveFile: Path): Decompressor

  companion object {
    fun findType(jsonText: String): JdkPackageType? = entries.firstOrNull { it.type.equals(jsonText, ignoreCase = true) }
  }
}

@Internal
data class JdkPlatform(
  val os: String,
  val arch: String,
)

@Internal
data class JdkPredicate(
  private val ideBuildNumber: BuildNumber?,
  private val supportedPlatforms: Set<JdkPlatform>,
) {
  companion object {
    fun none(): JdkPredicate = JdkPredicate(null, emptySet())

    fun default(): JdkPredicate = createInstance(forWsl = false)
    fun forWSL(buildNumber: BuildNumber? = ApplicationInfoImpl.getShadowInstance().build): JdkPredicate = createInstance(forWsl = true, buildNumber)

    /**
     * Selects only JDKs that are for the same OS and CPU arch as the current Java process.
     */
    fun forCurrentProcess(): JdkPredicate = JdkPredicate(null, setOf(JdkPlatform(currentOS, currentArch)))

    private fun createInstance(forWsl: Boolean = false, buildNumber: BuildNumber? = ApplicationInfoImpl.getShadowInstance().build): JdkPredicate {
      val x86_64 = "x86_64"
      val defaultPlatform = JdkPlatform(currentOS, x86_64)
      val platforms = when {
        SystemInfo.isWindows && forWsl && CpuArch.isArm64() -> listOf(defaultPlatform.copy(os = "linux", arch = "aarch64"))
        SystemInfo.isWindows && forWsl && !CpuArch.isArm64() -> listOf(defaultPlatform.copy(os = "linux"))
        SystemInfo.isLinux && CpuArch.isArm64() -> listOf(defaultPlatform.copy(arch = "aarch64"))
        (SystemInfo.isMac || SystemInfo.isWindows) && CpuArch.isArm64() -> listOf(defaultPlatform, defaultPlatform.copy(arch = "aarch64"))
        !SystemInfo.isWindows && forWsl -> listOf()
        else -> listOf(defaultPlatform)
      }

      return JdkPredicate(buildNumber, platforms.toSet())
    }

    val currentOS: String = when {
      SystemInfo.isWindows -> "windows"
      SystemInfo.isMac -> "macOS"
      SystemInfo.isLinux -> "linux"
      else -> error("Unsupported OS")
    }

    val currentArch: String = when {
      CpuArch.isArm64() -> "aarch64"
      else -> "x86_64"
    }
  }

  fun testJdkProduct(product: JsonObject): Boolean = testPredicate(product.get("filter")) == true

  fun testJdkPackage(pkg: JsonObject): Boolean {
    val os = (pkg["os"] as? JsonPrimitive)?.contentOrNull ?: return false
    val arch = (pkg["arch"] as? JsonPrimitive)?.contentOrNull ?: return false
    if (JdkPlatform(os, arch) !in supportedPlatforms) {
      return false
    }

    if ((pkg["package_type"] as? JsonPrimitive)?.contentOrNull?.let(JdkPackageType.Companion::findType) == null) {
      return false
    }
    return testPredicate(pkg["filter"]) == true
  }

  /**
   * tests the predicate from the `filter` or `default` elements an JDK product
   * against current IDE instance
   *
   * returns `null` if there was something unknown detected in the filter
   *
   * It supports the following predicates with `type` equal to `build_number_range`, `and`, `or`, `not`, e.g.
   *         { "type": "build_number_range", "since": "192.34", "until": "194.123" }
   * or
   *         { "type": "or"|"and", "items": [ {same as before}, ...] }
   * or
   *         { "type": "not", "item": { same as before } }
   * or
   *         { "type": "const", "value": true | false  }
   * or (from 2020.3.1)
   *         { "type": "supports_arch" }
   */
  fun testPredicate(filter: JsonElement?): Boolean? {
    //no filter means predicate is true
    if (filter == null) {
      return true
    }

    // used in "default" element
    (filter as? JsonPrimitive)?.booleanOrNull?.let {
      return it
    }

    if (filter !is JsonObject) {
      return null
    }

    val type = (filter["type"] as? JsonPrimitive)?.contentOrNull ?: return null
    if (type == "or") {
      return foldSubPredicates(filter = filter, emptyResult = false, op = Boolean::or)
    }

    if (type == "and") {
      return foldSubPredicates(filter = filter, emptyResult = true, op = Boolean::and)
    }

    if (type == "not") {
      val subResult = testPredicate(filter["item"]) ?: return null
      return !subResult
    }

    if (type == "const") {
      return (filter["value"] as? JsonPrimitive)?.booleanOrNull
    }

    if (type == "build_number_range" && ideBuildNumber != null) {
      val fromBuild = (filter["since"] as? JsonPrimitive)?.contentOrNull
      val untilBuild = (filter["until"] as? JsonPrimitive)?.contentOrNull

      if (fromBuild == null && untilBuild == null) {
        return true
      }

      if (fromBuild != null) {
        val fromBuildSafe = BuildNumber.fromStringOrNull(fromBuild) ?: return null
        if (fromBuildSafe > ideBuildNumber) {
          return false
        }
      }

      if (untilBuild != null) {
        val untilBuildSafe = BuildNumber.fromStringOrNull(untilBuild) ?: return null
        if (ideBuildNumber > untilBuildSafe) {
          return false
        }
      }

      return true
    }

    if (type == "supports_arch") {
      // the main fact is that we support that filter,
      // the actual test is implemented when the IDE compares
      // the actual arch and os attributes
      // the older IDEs does not support that predicate and
      // ignores the entire element
      return true
    }

    return null
  }

  private fun foldSubPredicates(
    filter: JsonObject,
    emptyResult: Boolean,
    op: (acc: Boolean, Boolean) -> Boolean,
  ): Boolean? {
    val items = filter.get("items") as? JsonArray ?: return null
    if (items.isEmpty()) {
      return false
    }

    return items.fold(emptyResult) { acc, subFilter ->
      val subResult = testPredicate(subFilter) ?: return null
      op(acc, subResult)
    }
  }
}

@Internal
object JdkListParser {
  fun readTree(rawData: String): JsonObject = Json.decodeFromString<JsonElement>(rawData).jsonObject

  fun parseJdkList(tree: JsonObject, filters: JdkPredicate): List<JdkItem> {
    val items = tree.get("jdks")?.jsonArray ?: error("`jdks` element is missing")
    return items
      .asSequence()
      .filterIsInstance<JsonObject>()
      .flatMap { parseJdkItem(item = it, filters = filters) }
      .toList()
  }

  @OptIn(ExperimentalSerializationApi::class)
  fun parseJdkItem(item: JsonObject, filters: JdkPredicate): List<JdkItem> {
    // check this package is OK to show for that instance of the IDE
    if (!filters.testJdkProduct(item)) {
      return emptyList()
    }

    val packages = item["packages"] as? JsonArray ?: return emptyList()
    val product = JdkProduct(
      vendor = (item["vendor"] as? JsonPrimitive)?.contentOrNull ?: return emptyList(),
      product = (item["product"] as? JsonPrimitive)?.contentOrNull,
      flavour = (item["flavour"] as? JsonPrimitive)?.contentOrNull,
    )

    val contents = jsonFormat.encodeToString(item)
    return packages.filterIsInstance<JsonObject>().filter(filters::testJdkPackage).map { pkg ->
      JdkItem(
        product = product,
        isDefaultItem = item["default"]?.let { filters.testPredicate(it) == true } ?: false,
        isVisibleOnUI = item["listed"]?.let { filters.testPredicate(it) == true } ?: true,

        jdkMajorVersion = (item["jdk_version_major"] as? JsonPrimitive)?.intOrNull ?: return emptyList(),
        jdkVersion = (item["jdk_version"] as? JsonPrimitive)?.contentOrNull ?: return emptyList(),
        jdkVendorVersion = (item["jdk_vendor_version"] as? JsonPrimitive)?.contentOrNull,
        suggestedSdkName = (item["suggested_sdk_name"] as? JsonPrimitive)?.contentOrNull ?: return emptyList(),

        os = (pkg["os"] as? JsonPrimitive)?.contentOrNull ?: return emptyList(),
        arch = (pkg["arch"] as? JsonPrimitive)?.contentOrNull ?: return emptyList(),
        packageType = (pkg["package_type"] as? JsonPrimitive)?.contentOrNull?.let(JdkPackageType.Companion::findType) ?: return emptyList(),
        url = (pkg["url"] as? JsonPrimitive)?.contentOrNull ?: return emptyList(),
        sha256 = (pkg["sha256"] as? JsonPrimitive)?.contentOrNull ?: return emptyList(),
        archiveSize = (pkg["archive_size"] as? JsonPrimitive)?.longOrNull ?: return emptyList(),
        archiveFileName = (pkg["archive_file_name"] as? JsonPrimitive)?.contentOrNull ?: return emptyList(),
        packageRootPrefix = (pkg["package_root_prefix"] as? JsonPrimitive)?.contentOrNull ?: return emptyList(),
        packageToBinJavaPrefix = (pkg["package_to_java_home_prefix"] as? JsonPrimitive)?.contentOrNull ?: return emptyList(),

        unpackedSize = (pkg["unpacked_size"] as? JsonPrimitive)?.longOrNull ?: return emptyList(),
        installFolderName = (pkg["install_folder_name"] as? JsonPrimitive)?.contentOrNull ?: return emptyList(),

        sharedIndexAliases = (item["shared_index_aliases"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: listOf(),

        saveToFile = { file -> file.write(contents) },
      )
    }
  }
}

@ExperimentalSerializationApi
private val jsonFormat = Json {
  prettyPrint = true
  prettyPrintIndent = "  "
}

@Service
class JdkListDownloader : JdkListDownloaderBase() {
  companion object {
    @JvmStatic
    fun getInstance(): JdkListDownloader = service<JdkListDownloader>()
  }

  override val feedUrl: String
    get() {
      if (LoadingState.COMPONENTS_LOADED.isOccurred) {
        val registry = runCatching { Registry.get("jdk.downloader.url").asString() }.getOrNull()
        if (!registry.isNullOrBlank()) {
          return registry
        }
      }
      return "https://download.jetbrains.com/jdk/feed/v1/jdks.json.xz"
    }
}

@Internal
abstract class JdkListDownloaderBase {
  protected abstract val feedUrl: String

  private fun downloadJdkList(feedUrl: String, progress: ProgressIndicator?) =
    HttpRequests
      .request(feedUrl)
      .productNameAsUserAgent()
      //timeouts are handled inside
      .readBytes(progress)

  /**
   * Returns a list of entries for JDK automatic installation. That set of entries normally
   * contains few more entries than the result of the [downloadForUI] call.
   * Entries are sorter from the best suggested to the worst suggested items.
   */
  fun downloadModelForJdkInstaller(progress: ProgressIndicator?): List<JdkItem> = downloadModelForJdkInstaller(progress, JdkPredicate.default())

  /**
   * Returns a list of entries for JDK automatic installation. That set of entries normally
   * contains few more entries than the result of the [downloadForUI] call.
   * Entries are sorter from the best suggested to the worst suggested items.
   */
  fun downloadModelForJdkInstaller(progress: ProgressIndicator?, predicate: JdkPredicate): List<JdkItem> {
    if (predicate == JdkPredicate.none()) {
      return listOf()
    }

    return jdksListCache.getOrCompute(feedUrl, EmptyRawJdkList) {
      downloadJdksListNoCache(feedUrl, progress)
    }.getJdks(predicate)
  }

  /**
   * Lists all entries suitable for UI download, there can be some unlisted entries that are ignored here by intent
   */
  fun downloadForUI(progress: ProgressIndicator?, feedUrl: String? = null) : List<JdkItem> = downloadForUI(progress, feedUrl, JdkPredicate.default())

  /**
   * Lists all entries suitable for UI download, there can be some unlisted entries that are ignored here by intent
   */
  fun downloadForUI(progress: ProgressIndicator?, feedUrl: String? = null, predicate: JdkPredicate) : List<JdkItem> {
    //we intentionally disable cache here for all user UI requests, as of IDEA-252237
    val url = feedUrl ?: this.feedUrl
    val raw = downloadJdksListNoCache(url, progress)

    //setting value to the cache, just in case
    jdksListCache.setValue(url, raw)

    val list = raw.getJdks(predicate)
    if (ApplicationManager.getApplication().isInternal) {
      return list
    }

    return list.filter { it.isVisibleOnUI }
  }

  private val jdksListCache = CachedValueWithTTL<RawJdkList>(15 to TimeUnit.MINUTES)

  private fun downloadJdksListNoCache(feedUrl: String, progress: ProgressIndicator?): RawJdkList {
    // download XZ packed version of the data (several KBs packed, several dozen KBs unpacked) and process it in-memory
    val rawDataXZ = try {
      downloadJdkList(feedUrl, progress)
    }
    catch (t: IOException) {
      thisLogger().warn("Failed to download the list of available JDKs from $feedUrl. ${t.message}")
      return EmptyRawJdkList
    }

    val rawData = try {
      ByteArrayInputStream(rawDataXZ).use { input ->
        XZInputStream(input).use {
          it.readBytes()
        }
      }
    }
    catch (e: Throwable) {
      throw RuntimeException("Failed to unpack the list of available JDKs from $feedUrl. ${e.message}", e)
    }

    val json = try {
      JdkListParser.readTree(rawData.decodeToString())
    }
    catch (t: Throwable) {
      throw RuntimeException("Failed to parse the downloaded list of available JDKs. ${t.message}", t)
    }

    return RawJdkListImpl(feedUrl, json)
  }
}

private interface RawJdkList {
  fun getJdks(predicate: JdkPredicate) : List<JdkItem>
}

private object EmptyRawJdkList : RawJdkList {
  override fun getJdks(predicate: JdkPredicate) : List<JdkItem> = listOf()
}

private class RawJdkListImpl(
  private val feedUrl: String,
  private val json: JsonObject,
) : RawJdkList {
  private val cache = ConcurrentHashMap<JdkPredicate, () -> List<JdkItem>>()

  override fun getJdks(predicate: JdkPredicate) = cache.computeIfAbsent(predicate) { parseJson(it) }()

  private fun parseJson(predicate: JdkPredicate) : () -> List<JdkItem> {
    val result = runCatching {
      try {
        JdkListParser.parseJdkList(json, predicate)
      }
      catch (t: Throwable) {
        throw RuntimeException("Failed to process the downloaded list of available JDKs from $feedUrl. ${t.message}", t)
      }
    }

    return { result.getOrThrow() }
  }
}

private class CachedValueWithTTL<T : Any>(
  private val ttl: Pair<Int, TimeUnit>
) {
  private val lock = ReentrantReadWriteLock()
  private var cachedUrl: String? = null
  private var value: T? = null
  private var computed = 0L

  private fun now() = System.currentTimeMillis()
  private operator fun Long.plus(ttl: Pair<Int, TimeUnit>): Long = this + ttl.second.toMillis(ttl.first.toLong())

  private inline fun readValueOrNull(expectedUrl: String, onValue: (T) -> Unit) {
    if (cachedUrl != expectedUrl) {
      return
    }

    val value = this.value
    if (value != null && computed + ttl > now()) {
      onValue(value)
    }
  }

  fun getOrCompute(url: String, defaultOrFailure: T, compute: () -> T): T {
    lock.read {
      readValueOrNull(url) { return it }
    }

    lock.write {
      // double-checked
      readValueOrNull(url) { return it }

      val value = runCatching(compute).getOrElse {
        if (it is ProcessCanceledException) {
          throw it
        }
        Logger.getInstance(javaClass).warn("Failed to compute value. ${it.message}", it)
        defaultOrFailure
      }

      ProgressManager.checkCanceled()
      return setValue(url, value)
    }
  }

  fun setValue(url: String, value: T): T = lock.write {
    this.value = value
    computed = now()
    cachedUrl = url
    return value
  }
}
