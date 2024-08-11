// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JarBuilder")
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.io.*
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.util.zip.Deflater
import kotlin.io.path.name

private const val listOfEntitiesFileName = "META-INF/listOfEntities.txt"


fun interface DistributionFileEntryProducer {
  fun consume(size: Int, hash: Long, targetFile: Path): DistributionFileEntry
}

internal interface NativeFileHandler {
  val sourceToNativeFiles: MutableMap<ZipSource, List<String>>

  fun isNative(name: String): Boolean

  suspend fun sign(name: String, dataSupplier: () -> ByteBuffer): Path?
}

suspend fun buildJar(targetFile: Path, sources: List<Source>, compress: Boolean = false) {
  buildJar(targetFile = targetFile, sources = sources, compress = compress, nativeFileHandler = null)
}

internal suspend fun buildJar(
  targetFile: Path,
  sources: List<Source>,
  compress: Boolean = false,
  notify: Boolean = true,
  nativeFileHandler: NativeFileHandler? = null,
) {
  val packageIndexBuilder = if (compress) null else PackageIndexBuilder()
  writeNewFile(targetFile) { outChannel ->
    ZipFileWriter(
      channel = outChannel,
      deflater = if (compress) Deflater(Deflater.DEFAULT_COMPRESSION, true) else null,
    ).use { zipCreator ->
      val uniqueNames = HashMap<String, Path>()

      val filesToMerge = mutableListOf<CharSequence>()

      for (source in sources) {
        val positionBefore = zipCreator.channelPosition
        writeSource(
          source = source,
          zipCreator = zipCreator,
          uniqueNames = uniqueNames,
          packageIndexBuilder = packageIndexBuilder,
          targetFile = targetFile,
          sources = sources,
          nativeFileHandler = nativeFileHandler,
          compress = compress,
          filesToMerge = filesToMerge,
        )

        if (notify) {
          source.size = (zipCreator.channelPosition - positionBefore).toInt()
          source.hash = 0
        }
      }

      if (filesToMerge.isNotEmpty()) {
        zipCreator.uncompressedData(nameString = listOfEntitiesFileName, data = filesToMerge.joinToString("\n") { it.trim() }, indexWriter = packageIndexBuilder?.indexWriter)
      }

      packageIndexBuilder?.writePackageIndex(zipCreator)
    }
  }
}

private suspend fun writeSource(
  source: Source,
  zipCreator: ZipFileWriter,
  uniqueNames: HashMap<String, Path>,
  packageIndexBuilder: PackageIndexBuilder?,
  targetFile: Path,
  sources: List<Source>,
  nativeFileHandler: NativeFileHandler?,
  compress: Boolean,
  filesToMerge: MutableList<CharSequence>,
) {
  when (source) {
    is DirSource -> {
      val archiver = ZipArchiver(zipCreator = zipCreator, fileAdded = { name, file ->
        if (name == listOfEntitiesFileName) {
          filesToMerge.add(Files.readString(file))
          false
        }
        else if (uniqueNames.putIfAbsent(name, source.dir) == null && (!source.removeModuleInfo || name != "module-info.class")) {
          packageIndexBuilder?.addFile(name)
          true
        }
        else {
          false
        }
      })
      val normalizedDir = source.dir.toAbsolutePath().normalize()
      archiver.setRootDir(normalizedDir, source.prefix)
      archiveDir(
        startDir = normalizedDir,
        archiver = archiver,
        indexWriter = packageIndexBuilder?.indexWriter,
        excludes = source.excludes.takeIf(List<PathMatcher>::isNotEmpty)
      )
    }

    is InMemoryContentSource -> {
      if (uniqueNames.putIfAbsent(source.relativePath, Path.of(source.relativePath)) != null) {
        throw IllegalStateException("in-memory source must always be first (targetFile=$targetFile, source=${source.relativePath}, sources=${sources.joinToString()})")
      }

      packageIndexBuilder?.addFile(source.relativePath)
      zipCreator.uncompressedData(
        nameString = source.relativePath,
        maxSize = source.data.size,
        indexWriter = packageIndexBuilder?.indexWriter,
        dataWriter = {
          it.put(source.data)
        },
      )
    }

    is FileSource -> {
      if (uniqueNames.putIfAbsent(source.relativePath, Path.of(source.relativePath)) != null) {
        throw IllegalStateException("fileSource source must always be first (targetFile=$targetFile, source=${source.relativePath}, sources=${sources.joinToString()})")
      }

      packageIndexBuilder?.addFile(source.relativePath)
      zipCreator.file(file = source.file, nameString = source.relativePath, indexWriter = packageIndexBuilder?.indexWriter)
    }

    is ZipSource -> {
      val sourceFile = source.file
      try {
        handleZipSource(
          source = source,
          sourceFile = sourceFile,
          nativeFileHandler = nativeFileHandler,
          uniqueNames = uniqueNames,
          sources = sources,
          packageIndexBuilder = packageIndexBuilder,
          zipCreator = zipCreator,
          compress = compress,
          targetFile = targetFile,
          filesToMerge = filesToMerge,
        )
      }
      finally {
        @Suppress("KotlinConstantConditions")
        if (sourceFile !== source.file) {
          Files.deleteIfExists(sourceFile)
        }
      }
    }

    is LazySource -> {
      for (subSource in source.getSources()) {
        require(subSource !== source)
        writeSource(
          source = subSource,
          zipCreator = zipCreator,
          uniqueNames = uniqueNames,
          packageIndexBuilder = packageIndexBuilder,
          targetFile = targetFile,
          sources = sources,
          nativeFileHandler = nativeFileHandler,
          compress = compress,
          filesToMerge = filesToMerge
        )
      }
    }
  }
}

private suspend fun handleZipSource(
  source: ZipSource,
  sourceFile: Path,
  nativeFileHandler: NativeFileHandler?,
  uniqueNames: MutableMap<String, Path>,
  sources: List<Source>,
  packageIndexBuilder: PackageIndexBuilder?,
  zipCreator: ZipFileWriter,
  compress: Boolean,
  targetFile: Path,
  filesToMerge: MutableList<CharSequence>,
) {
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

  // FileChannel is strongly required because only FileChannel provides `read(ByteBuffer dst, long position)` method -
  // ability to read data without setting channel position, as setting channel position will require synchronization
  suspendAwareReadZipFile(sourceFile) { name, dataSupplier ->
    if (name == listOfEntitiesFileName) {
      filesToMerge.add(Charsets.UTF_8.decode(dataSupplier()))
      return@suspendAwareReadZipFile
    }

    fun writeZipData(data: ByteBuffer) {
      if (compress) {
        zipCreator.compressedData(name, data)
      }
      else {
        zipCreator.uncompressedData(nameString = name, data = data, indexWriter = packageIndexBuilder?.indexWriter)
      }
    }

    if (checkCoverageAgentManifest(name = name, sourceFile = sourceFile, targetFile = targetFile, dataSupplier = dataSupplier, writeData = ::writeZipData)) {
      return@suspendAwareReadZipFile
    }

    val filter = source.filter
    val isIncluded = if (filter == null) {
      checkNameForZipSource(name = name, excludes = source.excludes, includeManifest = sources.size == 1)
    }
    else {
      filter(name)
    }

    if (!isIncluded || isDuplicated(uniqueNames = uniqueNames, name = name, sourceFile = sourceFile)) {
      return@suspendAwareReadZipFile
    }

    if (nativeFileHandler?.isNative(name) == true) {
      if (source.isPreSignedAndExtractedCandidate) {
        nativeFiles!!.value.add(name)
      }
      else {
        packageIndexBuilder?.addFile(name)

        // sign it
        val file = nativeFileHandler.sign(name, dataSupplier)
        if (file == null) {
          val data = dataSupplier()
          writeZipData(data)
        }
        else {
          zipCreator.file(name, file, indexWriter = packageIndexBuilder?.indexWriter)
          Files.delete(file)
        }
      }
    }
    else {
      packageIndexBuilder?.addFile(name)

      val data = dataSupplier()
      writeZipData(data)
    }
  }
}

/**
 * Coverage agent uses the Boot-Class-Path jar attribute to instrument class from any class loader.
 * For the correct work, it is required that the attribute value is the same as the simple jar name.
 * Here the attribute value is replaced with the target jar name.
 */
private fun checkCoverageAgentManifest(
  name: String,
  sourceFile: Path,
  targetFile: Path,
  dataSupplier: () -> ByteBuffer,
  writeData: (ByteBuffer) -> Unit,
): Boolean {
  if (name != "META-INF/MANIFEST.MF") {
    return false
  }

  val coveragePlatformAgentModuleName = "intellij.platform.coverage.agent"
  if (!targetFile.name.contains(coveragePlatformAgentModuleName)) {
    return false
  }

  val agentPrefix = "intellij-coverage-agent"
  if (!sourceFile.name.startsWith(agentPrefix)) {
    return false
  }

  val manifestContent = Charsets.UTF_8.decode(dataSupplier()).let {
    val bootAttribute = "Boot-Class-Path:"
    it.replace("$bootAttribute $agentPrefix-\\d+(\\.\\d+)*\\.jar".toRegex(), "$bootAttribute $coveragePlatformAgentModuleName.jar")
  }
  writeData(ByteBuffer.wrap(manifestContent.toByteArray()))
  return true
}

private fun isDuplicated(uniqueNames: MutableMap<String, Path>, name: String, sourceFile: Path): Boolean {
  val old = uniqueNames.putIfAbsent(name, sourceFile) ?: return false
  Span.current().addEvent(
    "$name is duplicated and ignored", Attributes.of(
    AttributeKey.stringKey("firstSource"), old.toString(),
    AttributeKey.stringKey("secondSource"), sourceFile.toString(),
  )
  )
  return true
}

@Suppress("SpellCheckingInspection")
private fun getIgnoredNames(): Set<String> {
  val set = mutableListOf<String>()
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
  for (originalName in sequenceOf("NOTICE", "README", "LICENSE", "DEPENDENCIES", "CHANGES", "THIRD_PARTY_LICENSES", "COPYING")) {
    for (name in sequenceOf(originalName, originalName.lowercase())) {
      set.add(name)
      set.add("$name.txt")
      set.add("$name.md")
      set.add("META-INF/$name")
      set.add("META-INF/$name.txt")
      set.add("META-INF/$name.md")
    }
  }
  set.add("kotlinx/coroutines/debug/internal/ByteBuddyDynamicAttach.class")
  set.add("kotlin/coroutines/jvm/internal/DebugProbesKt.class")
  /**
   * merging build politic breaks Graal VM Truffle-based plugins in an inconsistant way, so it's better
   * to provide a correctly merged version in plugin.
   */
  set.add("META-INF/services/com.oracle.truffle.api.TruffleLanguage${'$'}Provider")
  return java.util.Set.copyOf(set)
}

private val ignoredNames = getIgnoredNames()

private fun checkNameForZipSource(name: String, excludes: List<Regex>, includeManifest: Boolean): Boolean {
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

         !name.startsWith("kotlinx/coroutines/repackaged/") &&

         !name.startsWith("native/") &&
         !name.startsWith("licenses/") &&
         !name.startsWith("META-INF/INDEX.LIST") &&
         (!name.startsWith("META-INF/") || (!name.endsWith(".DSA") && !name.endsWith(".SF") && !name.endsWith(".RSA"))) &&
         // we replace lib class by our own patched version
         !name.startsWith("net/sf/cglib/core/AbstractClassGenerator")
}
