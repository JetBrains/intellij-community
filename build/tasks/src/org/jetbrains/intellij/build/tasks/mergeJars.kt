// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JarBuilder")
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "RAW_RUN_BLOCKING")

package org.jetbrains.intellij.build.tasks

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus.Obsolete
import org.jetbrains.intellij.build.io.*
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.util.function.IntConsumer
import java.util.zip.Deflater

const val UTIL_JAR = "util.jar"
const val UTIL_RT_JAR = "util_rt.jar"
const val UTIL_8_JAR = "util-8.jar"

sealed interface Source {
  val sizeConsumer: IntConsumer?

  val filter: ((String) -> Boolean)?
    get() = null
}

private val USER_HOME = Path.of(System.getProperty("user.home"))
val MAVEN_REPO: Path = USER_HOME.resolve(".m2/repository")

internal val isWindows: Boolean = System.getProperty("os.name").startsWith("windows", ignoreCase = true)

data class ZipSource(
  @JvmField val file: Path,
  @JvmField val excludes: List<Regex> = emptyList(),
  @JvmField val isPreSignedAndExtractedCandidate: Boolean = false,
  override val filter: ((String) -> Boolean)? = null,
  override val sizeConsumer: IntConsumer? = null,
) : Source, Comparable<ZipSource> {
  override fun compareTo(other: ZipSource): Int {
    return if (isWindows) file.toString().compareTo(other.file.toString()) else file.compareTo(other.file)
  }

  override fun toString(): String {
    val shortPath = when {
      file.startsWith(MAVEN_REPO) -> MAVEN_REPO.relativize(file).toString()
      file.startsWith(USER_HOME) -> "~/" + USER_HOME.relativize(file)
      else -> file.toString()
    }
    return "zip(file=$shortPath)"
  }
}

data class DirSource(@JvmField val dir: Path,
                     @JvmField val excludes: List<PathMatcher> = emptyList(),
                     override val sizeConsumer: IntConsumer? = null,
                     @JvmField val prefix: String = "",
                     @JvmField val removeModuleInfo: Boolean = true) : Source {
  override fun toString(): String {
    val shortPath = if (dir.startsWith(USER_HOME)) "~/${USER_HOME.relativize(dir)}" else dir.toString()
    return "dir(dir=$shortPath, excludes=${excludes.size})"
  }
}

data class InMemoryContentSource(@JvmField val relativePath: String,
                                 @JvmField val data: ByteArray, override val sizeConsumer: IntConsumer? = null) : Source {
  override fun toString() = "inMemory(relativePath=$relativePath)"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is InMemoryContentSource) return false

    if (relativePath != other.relativePath) return false
    if (!data.contentEquals(other.data)) return false
    return sizeConsumer == other.sizeConsumer
  }

  override fun hashCode(): Int {
    var result = relativePath.hashCode()
    result = 31 * result + data.contentHashCode()
    result = 31 * result + (sizeConsumer?.hashCode() ?: 0)
    return result
  }
}

interface NativeFileHandler {
  val sourceToNativeFiles: MutableMap<ZipSource, List<String>>

  suspend fun sign(name: String, data: ByteBuffer): Path?
}

@Obsolete
fun buildJarSync(targetFile: Path, sources: List<Source>) {
  runBlocking {
    buildJar(targetFile, sources)
  }
}

suspend fun buildJar(targetFile: Path,
                     sources: List<Source>,
                     compress: Boolean = false,
                     dryRun: Boolean = false,
                     nativeFileHandler: NativeFileHandler? = null) {
  if (dryRun) {
    for (source in sources) {
      source.sizeConsumer?.accept(0)
    }
    return
  }

  val packageIndexBuilder = if (compress) null else PackageIndexBuilder()
  writeNewFile(targetFile) { outChannel ->
    ZipFileWriter(outChannel, if (compress) Deflater(Deflater.DEFAULT_COMPRESSION, true) else null).use { zipCreator ->
      val uniqueNames = HashMap<String, Path>()

      for (source in sources) {
        val positionBefore = zipCreator.resultStream.getChannelPosition()
        when (source) {
          is DirSource -> {
            val archiver = ZipArchiver(zipCreator, fileAdded = {
              if (uniqueNames.putIfAbsent(it, source.dir) == null && (!source.removeModuleInfo || it != "module-info.class")) {
                packageIndexBuilder?.addFile(it)
                true
              }
              else {
                false
              }
            })
            val normalizedDir = source.dir.toAbsolutePath().normalize()
            archiver.setRootDir(normalizedDir, source.prefix)
            archiveDir(normalizedDir, archiver, excludes = source.excludes.takeIf(List<PathMatcher>::isNotEmpty))
          }

          is InMemoryContentSource -> {
            if (uniqueNames.putIfAbsent(source.relativePath, Path.of(source.relativePath)) != null) {
              throw IllegalStateException("in-memory source must always be first " +
                                          "(targetFile=$targetFile, source=${source.relativePath}, sources=${sources.joinToString()})")
            }

            packageIndexBuilder?.addFile(source.relativePath)
            zipCreator.uncompressedData(source.relativePath, source.data.size) {
              it.put(source.data)
            }
          }

          is ZipSource -> {
            handleZipSource(source = source,
                            targetFile = targetFile,
                            nativeFileHandler = nativeFileHandler,
                            uniqueNames = uniqueNames,
                            sources = sources,
                            packageIndexBuilder = packageIndexBuilder,
                            zipCreator = zipCreator)
          }
        }

        source.sizeConsumer?.accept((zipCreator.resultStream.getChannelPosition() - positionBefore).toInt())
      }

      packageIndexBuilder?.writePackageIndex(zipCreator)
    }
  }
}

private suspend fun handleZipSource(source: ZipSource,
                                    targetFile: Path,
                                    nativeFileHandler: NativeFileHandler?,
                                    uniqueNames: MutableMap<String, Path>,
                                    sources: List<Source>,
                                    packageIndexBuilder: PackageIndexBuilder?,
                                    zipCreator: ZipFileWriter) {
  val requiresMavenFiles = targetFile.fileName.toString().startsWith("junixsocket-")
  val nativeFiles = if (nativeFileHandler == null) {
    null
  }
  else {
    lazy(LazyThreadSafetyMode.NONE) {
      val list = mutableListOf<String>()
      check(nativeFileHandler.sourceToNativeFiles.put(source, list) == null)
      list
    }
  }

  val sourceFile = source.file
  // FileChannel is strongly required because only FileChannel provides `read(ByteBuffer dst, long position)` method -
  // ability to read data without setting channel position, as setting channel position will require synchronization
  suspendAwareReadZipFile(sourceFile) { name: String, dataSupplier: () -> ByteBuffer ->
    val filter = source.filter
    val isIncluded = if (filter == null) {
      checkNameForZipSource(name = name,
                            excludes = source.excludes,
                            includeManifest = sources.size == 1,
                            requiresMavenFiles = requiresMavenFiles)
    }
    else {
      filter(name)
    }

    if (isIncluded && !isDuplicated(uniqueNames, name, sourceFile)) {
      if (nativeFileHandler != null && isNative(name)) {
        if (source.isPreSignedAndExtractedCandidate) {
          nativeFiles!!.value.add(name)
        }
        else {
          packageIndexBuilder?.addFile(name)

          // sign it
          val data = dataSupplier()
          val file = nativeFileHandler.sign(name, data)
          if (file == null) {
            zipCreator.uncompressedData(name, dataSupplier())
          }
          else {
            zipCreator.file(name, file)
            Files.delete(file)
          }
        }
      }
      else {
        packageIndexBuilder?.addFile(name)

        zipCreator.uncompressedData(name, dataSupplier())
      }
    }
  }
}

private fun isDuplicated(uniqueNames: MutableMap<String, Path>, name: String, sourceFile: Path): Boolean {
  val old = uniqueNames.putIfAbsent(name, sourceFile) ?: return false
  Span.current().addEvent("$name is duplicated and ignored", Attributes.of(
    AttributeKey.stringKey("firstSource"), old.toString(),
    AttributeKey.stringKey("secondSource"), sourceFile.toString(),
  ))
  return true
}

fun isNative(name: String): Boolean {
  return name.endsWith(".jnilib") ||
         name.endsWith(".dylib") ||
         name.endsWith(".so") ||
         name.endsWith(".exe") ||
         name.endsWith(".dll") ||
         name.endsWith(".node") ||
         name.endsWith(".tbd")
}

@Suppress("SpellCheckingInspection")
private fun getIgnoredNames(): Set<String> {
  val set = HashSet<String>()
  // compilation cache on TC
  set.add(".hash")
  set.add("classpath.index")
  @Suppress("SpellCheckingInspection")
  set.add(".gitattributes")
  set.add("pom.xml")
  set.add("about.html")
  set.add("module-info.class")
  set.add("META-INF/versions/9/module-info.class")
  // default is ok (modules not used)
  set.add("META-INF/versions/9/kotlin/reflect/jvm/internal/impl/serialization/deserialization/builtins/BuiltInsResourceLoader.class")
  set.add("META-INF/versions/9/org/apache/xmlbeans/impl/tool/MavenPluginResolver.class")
  set.add("META-INF/services/javax.xml.parsers.SAXParserFactory")
  set.add("META-INF/services/javax.xml.stream.XMLEventFactory")
  set.add("META-INF/services/javax.xml.parsers.DocumentBuilderFactory")
  set.add("META-INF/services/javax.xml.datatype.DatatypeFactory")

  set.add("META-INF/services/com.fasterxml.jackson.core.ObjectCodec")
  set.add("META-INF/services/com.fasterxml.jackson.core.JsonFactory")
  set.add("META-INF/services/reactor.blockhound.integration.BlockHoundIntegration")

  set.add("META-INF/io.netty.versions.properties")

  set.add("com/sun/jna/aix-ppc/libjnidispatch.a")
  set.add("com/sun/jna/aix-ppc64/libjnidispatch.a")

  // duplicates in maven-resolver-transport-http and maven-resolver-transport-file
  set.add("META-INF/sisu/javax.inject.Named")
  // duplicates in recommenders-jayes-io-2.5.5 and recommenders-jayes-2.5.5.jar
  set.add("OSGI-INF/l10n/bundle.properties")
  // groovy
  set.add("META-INF/groovy-release-info.properties")

  set.add("native-image")
  set.add("native")
  set.add("licenses")
  set.add("META-INF/LGPL2.1")
  set.add("META-INF/AL2.0")
  @Suppress("SpellCheckingInspection")
  set.add(".gitkeep")
  set.add(INDEX_FILENAME)
  for (originalName in listOf("NOTICE", "README", "LICENSE", "DEPENDENCIES", "CHANGES", "THIRD_PARTY_LICENSES", "COPYING")) {
    for (name in listOf(originalName, originalName.lowercase())) {
      set.add(name)
      set.add("$name.txt")
      set.add("$name.md")
      set.add("META-INF/$name")
      set.add("META-INF/$name.txt")
      set.add("META-INF/$name.md")
    }
  }
  return java.util.Set.copyOf(set)
}

private val ignoredNames = java.util.Set.copyOf(getIgnoredNames())

private fun checkNameForZipSource(name: String,
                                  excludes: List<Regex>,
                                  includeManifest: Boolean,
                                  requiresMavenFiles: Boolean): Boolean {
  @Suppress("SpellCheckingInspection")
  return !ignoredNames.contains(name) &&
         excludes.none { it.matches(name) } &&
         !name.endsWith(".kotlin_metadata") &&
         (includeManifest || name != "META-INF/MANIFEST.MF") &&
         !name.startsWith("license/") &&
         !name.startsWith("META-INF/license/") &&
         !name.startsWith("META-INF/LICENSE-") &&
         !name.startsWith("native-image/") &&

         //  Class 'jakarta.json.JsonValue' not found while looking for field 'jakarta.json.JsonValue NULL'
         //!name.startsWith("com/jayway/jsonpath/spi/json/JakartaJsonProvider") &&
         //!name.startsWith("com/jayway/jsonpath/spi/json/JsonOrgJsonProvider") &&
         //!name.startsWith("com/jayway/jsonpath/spi/json/TapestryJsonProvider") &&
         //
         //!name.startsWith("com/jayway/jsonpath/spi/mapper/JakartaMappingProvider") &&
         //!name.startsWith("com/jayway/jsonpath/spi/mapper/JsonOrgMappingProvider") &&
         //!name.startsWith("com/jayway/jsonpath/spi/mapper/TapestryMappingProvider") &&

         //!name.startsWith("io/opentelemetry/exporter/internal/grpc/") &&
         //!name.startsWith("io/opentelemetry/exporter/internal/okhttp/") &&
         //// com.thaiopensource.datatype.xsd.regex.xerces2 is used instead
         //!name.startsWith("com/thaiopensource/datatype/xsd/regex/xerces/RegexEngineImpl") &&
         //!name.startsWith("com/thaiopensource/relaxng/util/JingTask") &&
         //!name.startsWith("com/thaiopensource/validate/schematron") &&
         //!name.startsWith("com/thoughtworks/xstream/core/util/ISO8601JodaTimeConverter") &&
         //!name.startsWith("com/thoughtworks/xstream/io/xml/BEAStaxDriver") &&
         //!name.startsWith("com/thoughtworks/xstream/io/xml/AbstractXppDomDriver") &&
         //!name.startsWith("com/thoughtworks/xstream/io/xml/Xom") &&
         //!name.startsWith("com/thoughtworks/xstream/io/xml/Dom4") &&
         //!name.startsWith("com/thoughtworks/xstream/io/xml/JDom2") &&
         //!name.startsWith("com/thoughtworks/xstream/io/xml/KXml2") &&
         //!name.startsWith("com/thoughtworks/xstream/io/xml/Wstx") &&
         //!name.startsWith("com/thoughtworks/xstream/io/xml/Xpp3") &&
         //!name.startsWith("com/thoughtworks/xstream/io/xml/xppdom") &&
         //!name.startsWith("com/thoughtworks/xstream/io/xml/XppDom") &&
         //!name.startsWith("com/michaelbaranov/microba/jgrpah/birdview/Birdview") &&

         // XmlRPC lib
         !name.startsWith("org/xml/sax/") &&

         !name.startsWith("META-INF/versions/9/org/apache/logging/log4j/") &&
         !name.startsWith("META-INF/versions/9/org/bouncycastle/") &&
         !name.startsWith("META-INF/versions/10/org/bouncycastle/") &&
         !name.startsWith("META-INF/versions/15/org/bouncycastle/") &&

         !name.startsWith("native/") &&
         !name.startsWith("licenses/") &&
         (requiresMavenFiles || (name != "META-INF/maven" && !name.startsWith("META-INF/maven/"))) &&
         !name.startsWith("META-INF/INDEX.LIST") &&
         (!name.startsWith("META-INF/") || (!name.endsWith(".DSA") && !name.endsWith(".SF") && !name.endsWith(".RSA"))) &&
         // we replace lib class by our own patched version
         !name.startsWith("net/sf/cglib/core/AbstractClassGenerator")
}