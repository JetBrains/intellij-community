// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JarBuilder")
package org.jetbrains.intellij.build

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.io.AddDirEntriesMode
import org.jetbrains.intellij.build.io.INDEX_FILENAME
import org.jetbrains.intellij.build.io.PackageIndexBuilder
import org.jetbrains.intellij.build.io.ZipArchiver
import org.jetbrains.intellij.build.io.ZipFileWriter
import org.jetbrains.intellij.build.io.archiveDir
import org.jetbrains.intellij.build.io.suspendAwareReadZipFile
import org.jetbrains.intellij.build.io.zipWriter
import org.jetbrains.intellij.build.productLayout.LIB_MODULE_PREFIX
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.util.zip.Deflater

private const val listOfEntitiesFileName = "META-INF/listOfEntities.txt"

fun interface DistributionFileEntryProducer {
  fun consume(size: Int, hash: Long, targetFile: Path): DistributionFileEntry
}

internal interface NativeFileHandler {
  val sourceToNativeFiles: MutableMap<ZipSource, List<String>>

  fun isNative(name: String): Boolean

  fun isCompatibleWithTargetPlatform(name: String): Boolean

  suspend fun sign(name: String, dataSupplier: () -> ByteBuffer): Path?
}

suspend fun buildUncompressJarWithDirEntries(targetFile: Path, sources: List<Source>) {
  // addDirEntries=true has no effect when compress=true
  buildJar(targetFile = targetFile, sources = sources, nativeFileHandler = null, addDirEntries = true, compress = false)
}

suspend fun buildJar(targetFile: Path, sources: List<Source>, compress: Boolean = false) {
  buildJar(targetFile = targetFile, sources = sources, nativeFileHandler = null, addDirEntries = false, compress = compress)
}

internal suspend fun buildJar(
  targetFile: Path,
  sources: Collection<Source>,
  nativeFileHandler: NativeFileHandler?,
  addDirEntries: Boolean,
  compress: Boolean = false,
) {
  val packageIndexBuilder = if (compress) null else PackageIndexBuilder(if (addDirEntries) AddDirEntriesMode.ALL else AddDirEntriesMode.NONE)
  Files.createDirectories(targetFile.parent)
  ZipFileWriter(
    zipWriter(targetFile, packageIndexBuilder),
    deflater = if (compress) Deflater(Deflater.DEFAULT_COMPRESSION, true) else null,
  ).use { zipCreator ->
    val uniqueNames = HashMap<String, Path>()

    val filesToMerge = mutableListOf<CharSequence>()

    for (source in sources) {
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
    }

    if (filesToMerge.isNotEmpty()) {
      zipCreator.uncompressedData(nameString = listOfEntitiesFileName, data = filesToMerge.joinToString("\n") { it.trim() })
    }
  }
}

private suspend fun writeSource(
  source: Source,
  zipCreator: ZipFileWriter,
  uniqueNames: HashMap<String, Path>,
  packageIndexBuilder: PackageIndexBuilder?,
  targetFile: Path,
  sources: Collection<Source>,
  nativeFileHandler: NativeFileHandler?,
  compress: Boolean,
  filesToMerge: MutableList<CharSequence>,
) {
  when (source) {
    is DirSource -> {
      val includeManifest = sources.size == 1
      val archiver = ZipArchiver(fileAdded = { name, file ->
        if (name == listOfEntitiesFileName) {
          filesToMerge.add(Files.readString(file))
          false
        }
        else if (uniqueNames.putIfAbsent(name, source.dir) == null && (includeManifest || name != "META-INF/MANIFEST.MF")) {
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
        addFile = { archiver.addFile(it, zipCreator) },
        excludes = source.excludes.takeIf(List<PathMatcher>::isNotEmpty)
      )
    }

    is InMemoryContentSource -> {
      if (uniqueNames.putIfAbsent(source.relativePath, Path.of(source.relativePath)) != null) {
        throw IllegalStateException("in-memory source must always be first (targetFile=$targetFile, source=${source.relativePath}, sources=${sources.joinToString()})")
      }

      packageIndexBuilder?.addFile(source.relativePath)
      zipCreator.uncompressedData(path = source.relativePath, data = source.data)
    }

    is FileSource -> {
      if (uniqueNames.putIfAbsent(source.relativePath, Path.of(source.relativePath)) != null) {
        throw IllegalStateException("fileSource source must always be first (targetFile=$targetFile, source=${source.relativePath}, sources=${sources.joinToString()})")
      }

      packageIndexBuilder?.addFile(source.relativePath)
      zipCreator.file(file = source.file, nameString = source.relativePath)
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
      catch (e: IOException) {
        if (e.message?.contains("No space left on device") == true) {
          throw NoDiskSpaceLeftException("No space left while including $sourceFile into $targetFile", e)
        }
        else {
          throw IOException("Failed to include $sourceFile to $targetFile", e)
        }
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
          filesToMerge = filesToMerge,
        )
      }
    }

    is UnpackedZipSource -> {
      throw UnsupportedOperationException("UnpackedZipSource is not supported")
    }

    is CustomAssetShimSource -> {
      throw UnsupportedOperationException("CustomAssetShimSource is not supported")
    }
  }
}

private suspend fun handleZipSource(
  source: ZipSource,
  sourceFile: Path,
  nativeFileHandler: NativeFileHandler?,
  uniqueNames: MutableMap<String, Path>,
  sources: Collection<Source>,
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
        zipCreator.uncompressedData(name, data)
      }
    }

    if (checkCoverageAgentManifest(name = name, sourceFile = sourceFile, targetFile = targetFile, dataSupplier = dataSupplier, writeData = ::writeZipData)) {
      return@suspendAwareReadZipFile
    }

    val includeManifest = sources.count { !isLibModuleSource(it) } == 1
    val isIncluded = source.filter(name) && (includeManifest || name != "META-INF/MANIFEST.MF")

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
          zipCreator.file(name, file)
          Files.delete(file)
        }
      }
    }
    else {
      packageIndexBuilder?.addFile(name)
      writeZipData(dataSupplier())
    }
  }
}

private fun isLibModuleSource(source: Source): Boolean {
  if (source is DirSource) {
    return source.moduleName != null && source.moduleName.startsWith(LIB_MODULE_PREFIX)
  }
  else {
    return source is ZipSource && source.moduleName != null && source.moduleName.startsWith(LIB_MODULE_PREFIX)
  }
}

/**
 * Coverage agent uses the Boot-Class-Path jar attribute to an instrument class from any class loader.
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
  if (!targetFile.fileName.toString().contains(coveragePlatformAgentModuleName)) {
    return false
  }

  val agentPrefix = "intellij-coverage-agent"
  if (!sourceFile.fileName.toString().startsWith(agentPrefix)) {
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
