// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JarBuilder")
package org.jetbrains.intellij.build

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.io.AddDirEntriesMode
import org.jetbrains.intellij.build.io.MANIFEST_ENTRY_NAME
import org.jetbrains.intellij.build.io.PackageIndexBuilder
import org.jetbrains.intellij.build.io.ZipArchiver
import org.jetbrains.intellij.build.io.ZipFileWriter
import org.jetbrains.intellij.build.io.archiveDir
import org.jetbrains.intellij.build.io.ZipEntryProcessorResult
import org.jetbrains.intellij.build.io.readZipFile
import org.jetbrains.intellij.build.io.zipWriter
import org.jetbrains.intellij.build.productLayout.LIB_MODULE_PREFIX
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.util.jar.Manifest
import java.util.zip.Deflater

private const val listOfEntitiesFileName = "META-INF/listOfEntities.txt"

/** Describes the distribution entry of a library jar once the asset that holds it is packed. */
fun interface DistributionFileEntryProducer {
  fun produce(): DistributionFileEntry
}

internal interface NativeFileHandler {
  val sourceToNativeFiles: MutableMap<ZipSource, List<String>>

  fun isNative(name: String): Boolean

  fun isCompatibleWithTargetPlatform(name: String): Boolean

  fun sign(name: String, dataSupplier: () -> ByteBuffer): Path?
}

fun buildJar(targetFile: Path, sources: List<Source>, compress: Boolean = false, jarName: String = targetFile.fileName.toString()) {
  buildJar(targetFile = targetFile, sources = sources, nativeFileHandler = null, compress = compress, jarName = jarName)
}

/**
 * Writes the jar [targetFile] from [sources]. [jarName] is the file name the jar has in the distribution. It differs
 * from the name of [targetFile] when a jar cache writes the jar into a temporary sibling file first.
 */
internal fun buildJar(
  targetFile: Path,
  sources: Collection<Source>,
  nativeFileHandler: NativeFileHandler?,
  compress: Boolean = false,
  jarName: String = targetFile.fileName.toString(),
) {
  val packageIndexBuilder = if (compress) null else PackageIndexBuilder(AddDirEntriesMode.NONE)
  Files.createDirectories(targetFile.parent)
  ZipFileWriter(
    zipWriter(targetFile, packageIndexBuilder),
    deflater = if (compress) Deflater(Deflater.DEFAULT_COMPRESSION, true) else null,
  ).use { zipCreator ->
    val uniqueNames = HashMap<String, Path>()
    val moduleManifestCheck = ModuleManifestCheck(targetFile = targetFile, jarName = jarName)

    val filesToMerge = mutableListOf<CharSequence>()

    for (source in sources) {
      writeSource(
        source = source,
        zipCreator = zipCreator,
        uniqueNames = uniqueNames,
        moduleManifestCheck = moduleManifestCheck,
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

private fun writeSource(
  source: Source,
  zipCreator: ZipFileWriter,
  uniqueNames: HashMap<String, Path>,
  moduleManifestCheck: ModuleManifestCheck,
  packageIndexBuilder: PackageIndexBuilder?,
  targetFile: Path,
  sources: Collection<Source>,
  nativeFileHandler: NativeFileHandler?,
  compress: Boolean,
  filesToMerge: MutableList<CharSequence>,
) {
  when (source) {
    is DirSource -> {
      val isModuleOutput = source.moduleName != null
      val includeManifest = isModuleOutput || sources.size == 1
      val archiver = ZipArchiver(fileAdded = { name, file ->
        if (name == listOfEntitiesFileName) {
          filesToMerge.add(Files.readString(file))
          false
        }
        else if (name == MANIFEST_ENTRY_NAME && !includeManifest) {
          false
        }
        else {
          if (name == MANIFEST_ENTRY_NAME && isModuleOutput) {
            moduleManifestCheck.check(source, ByteBuffer.wrap(Files.readAllBytes(file)))
          }
          if (uniqueNames.putIfAbsent(name, source.dir) == null) {
            packageIndexBuilder?.addFile(name)
            true
          }
          else {
            false
          }
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
          moduleManifestCheck = moduleManifestCheck,
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
    }

    is LazySource -> {
      for (subSource in source.getSources()) {
        require(subSource !== source)
        writeSource(
          source = subSource,
          zipCreator = zipCreator,
          uniqueNames = uniqueNames,
          moduleManifestCheck = moduleManifestCheck,
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

private fun handleZipSource(
  source: ZipSource,
  sourceFile: Path,
  nativeFileHandler: NativeFileHandler?,
  uniqueNames: MutableMap<String, Path>,
  moduleManifestCheck: ModuleManifestCheck,
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

  readZipFile(sourceFile) { name, dataSupplier ->
    if (name == listOfEntitiesFileName) {
      filesToMerge.add(Charsets.UTF_8.decode(dataSupplier()))
      return@readZipFile ZipEntryProcessorResult.CONTINUE
    }

    fun writeZipData(data: ByteBuffer) {
      if (compress) {
        zipCreator.compressedData(name, data)
      }
      else {
        zipCreator.uncompressedData(name, data)
      }
    }

    val isManifest = name == MANIFEST_ENTRY_NAME
    val isModuleManifest = isManifest && source.moduleName != null
    val isIncluded = source.filter(name) && (!isManifest || isModuleManifest || sources.count { !isLibModuleSource(it) } == 1)
    if (!isIncluded) {
      return@readZipFile ZipEntryProcessorResult.CONTINUE
    }

    if (isModuleManifest) {
      moduleManifestCheck.check(source, dataSupplier())
    }
    if (isDuplicated(uniqueNames = uniqueNames, name = name, sourceFile = sourceFile)) {
      return@readZipFile ZipEntryProcessorResult.CONTINUE
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
    ZipEntryProcessorResult.CONTINUE
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
 * Checks the manifests of the module outputs in one jar.
 *
 * The jar keeps at most one module manifest.
 * If that manifest has the `Boot-Class-Path` main attribute, the value must be [jarName], the name of the jar in the
 * distribution. [targetFile] names the jar in a message only, because a jar cache can write a temporary sibling file.
 */
private class ModuleManifestCheck(private val targetFile: Path, private val jarName: String) {
  private var manifestSource: Source? = null

  fun check(source: Source, data: ByteBuffer) {
    val firstSource = manifestSource
    if (firstSource != null) {
      error("$targetFile gets a module manifest from two sources: $firstSource and $source")
    }
    manifestSource = source

    val bytes = ByteArray(data.remaining())
    data.duplicate().get(bytes)
    val manifest = try {
      Manifest(ByteArrayInputStream(bytes))
    }
    catch (e: IOException) {
      throw IllegalStateException("$targetFile gets a module manifest from $source that is not a valid manifest: ${e.message}", e)
    }
    val bootClassPath = manifest.mainAttributes.getValue("Boot-Class-Path") ?: return
    if (bootClassPath != jarName) {
      error("$targetFile gets a module manifest from $source with Boot-Class-Path '$bootClassPath'. The value must be '$jarName'.")
    }
  }
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
