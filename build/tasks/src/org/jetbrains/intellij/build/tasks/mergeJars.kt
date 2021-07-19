// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("JarBuilder")
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build.tasks

import com.intellij.util.lang.ImmutableZipEntry
import com.intellij.util.lang.ImmutableZipFile
import org.jetbrains.intellij.build.io.*
import java.nio.channels.FileChannel
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.util.*
import java.util.function.IntConsumer
import java.util.zip.ZipEntry

sealed interface Source {
  val sizeConsumer: IntConsumer?
}

data class ZipSource(val file: Path, override val sizeConsumer: IntConsumer? = null) : Source, Comparable<ZipSource> {
  override fun compareTo(other: ZipSource) = file.compareTo(other.file)
}

data class DirSource(val dir: Path, val excludes: List<PathMatcher>, override val sizeConsumer: IntConsumer? = null) : Source

fun createZipSource(file: Path, sizeConsumer: IntConsumer?): Any = ZipSource(file = file, sizeConsumer = sizeConsumer)

@JvmOverloads
fun buildJar(targetFile: Path, sources: List<Source>, logger: System.Logger?, dryRun: Boolean = false) {
  logger?.debug {
    "Build JAR (targetFile=$targetFile, sources=$sources)"
  }

  if (dryRun) {
    for (source in sources) {
      source.sizeConsumer?.accept(0)
    }
    return
  }

  Files.createDirectories(targetFile.parent)
  FileChannel.open(targetFile, RW_CREATE_NEW).use { outChannel ->
    val packageIndexBuilder = PackageIndexBuilder()

    val zipCreator = ZipFileWriter(outChannel, deflater = null)
    val uniqueNames = HashSet<String>()
    for (source in sources) {
      val positionBefore = outChannel.position()
      if (source is DirSource) {
        val archiver = ZipArchiver(method = ZipEntry.STORED, zipCreator, fileAdded = {
          if (uniqueNames.add(it)) {
            packageIndexBuilder.addFile(it)
            true
          }
          else {
            false
          }
        })
        val normalizedDir = source.dir.toAbsolutePath().normalize()
        archiver.setRootDir(normalizedDir, "")
        compressDir(normalizedDir, archiver, excludes = source.excludes.takeIf { it.isNotEmpty() })
      }
      else {
        ImmutableZipFile.load((source as ZipSource).file).use { zipFile ->
          val entries = getFilteredEntries(zipFile, uniqueNames)
          writeEntries(entries, zipCreator, zipFile)
          packageIndexBuilder.add(entries)
        }
      }

      source.sizeConsumer?.accept((outChannel.position() - positionBefore).toInt())
    }
    packageIndexBuilder.writeDirs(zipCreator)
    packageIndexBuilder.writePackageIndex(zipCreator)
    zipCreator.finish()
  }
}

private fun getFilteredEntries(zipFile: ImmutableZipFile, uniqueNames: MutableSet<String>): List<ImmutableZipEntry> {
  return zipFile.entries.filter {
    val name = it.name

    @Suppress("SpellCheckingInspection")
    uniqueNames.add(name) &&
    !name.endsWith(".kotlin_metadata") &&
    name != "META-INF/MANIFEST.MF" && name != PACKAGE_INDEX_NAME &&
    name != "license" && !name.startsWith("license/") &&
    name != "META-INF/services/javax.xml.parsers.SAXParserFactory" &&
    name != "META-INF/services/javax.xml.stream.XMLEventFactory" &&
    name != "META-INF/services/javax.xml.parsers.DocumentBuilderFactory" &&
    name != "META-INF/services/javax.xml.datatype.DatatypeFactory" &&
    name != "native-image" && !name.startsWith("native-image/") &&
    name != "native" && !name.startsWith("native/") &&
    name != "licenses" && !name.startsWith("licenses/") &&
    name != ".gitkeep" &&
    name != "META-INF/CHANGES" &&
    name != "META-INF/DEPENDENCIES" &&
    name != "META-INF/LICENSE" &&
    name != "META-INF/LICENSE.txt" &&
    name != "META-INF/README.txt" &&
    name != "META-INF/README.md" &&
    name != "META-INF/NOTICE" &&
    name != "META-INF/NOTICE.txt" &&
    name != "LICENSE" &&
    name != "LICENSE.md" &&
    name != "module-info.class" &&
    name != "license.txt" &&
    name != "LICENSE.txt" &&
    name != "COPYING.txt" &&
    name != "about.html" &&
    name != "pom.xml" &&
    name != "THIRD_PARTY_LICENSES.txt" &&
    name != "NOTICE.txt" &&
    name != "NOTICE.md" &&
    name != "META-INF/maven" &&
    !name.startsWith("META-INF/maven/") &&
    !name.startsWith("META-INF/INDEX.LIST") &&
    (!name.startsWith("META-INF/") || (!name.endsWith(".DSA") && !name.endsWith(".SF") && !name.endsWith(".RSA")))
  }
}

@Suppress("SpellCheckingInspection")
private val excludedFromMergeLibs = java.util.Set.of(
  "jna", "Log4J", "sqlite", "Slf4j", "async-profiler",
  "dexlib2", // android-only lib
  "intellij-coverage", "intellij-test-discovery", // used as agent
  "winp", "junixsocket-core", "pty4j", "grpc-netty-shaded", // contains native library
  "protobuf", // https://youtrack.jetbrains.com/issue/IDEA-268753
)

fun isLibraryMergeable(libName: String): Boolean {
  return !excludedFromMergeLibs.contains(libName) &&
         !libName.startsWith("kotlin") &&
         !libName.startsWith("projector-") &&
         !libName.contains("-agent-") &&
         !libName.startsWith("rd-") &&
         !libName.contains("annotations", ignoreCase = true) &&
         !libName.startsWith("junit", ignoreCase = true) &&
         !libName.startsWith("cucumber-", ignoreCase = true) &&
         !libName.contains("groovy", ignoreCase = true)
}

private val commonModuleExcludes = java.util.List.of(
  FileSystems.getDefault().getPathMatcher("glob:**/icon-robots.txt"),
  FileSystems.getDefault().getPathMatcher("glob:icon-robots.txt"),
  FileSystems.getDefault().getPathMatcher("glob:.unmodified"),
  FileSystems.getDefault().getPathMatcher("glob:classpath.index"),
)

fun addModuleSources(moduleName: String,
                     moduleNameToSize: MutableMap<String, Int>,
                     moduleOutputDir: Path,
                     modulePatches: Collection<Path>,
                     searchableOptionsRootDir: Path,
                     extraExcludes: Collection<String>,
                     sourceList: MutableList<Source>,
                     logger: System.Logger) {
  logger.debug { " include output of module '$moduleName'" }

  val sizeConsumer = IntConsumer {
    moduleNameToSize.merge(moduleName, it) { oldValue, value -> oldValue + value }
  }

  // must be before module output to override
  for (moduleOutputPatch in modulePatches) {
    sourceList.add(DirSource(moduleOutputPatch, Collections.emptyList(), sizeConsumer))
    logger.debug { " include $moduleOutputPatch with patches for module '$moduleName'" }
  }

  val searchableOptionsModuleDir = searchableOptionsRootDir.resolve(moduleName)
  if (Files.exists(searchableOptionsModuleDir)) {
    sourceList.add(DirSource(searchableOptionsModuleDir, Collections.emptyList(), sizeConsumer))
  }

  val excludes = if (extraExcludes.isEmpty()) {
    commonModuleExcludes
  }
  else {
    commonModuleExcludes.plus(extraExcludes.map { FileSystems.getDefault().getPathMatcher("glob:$it") })
  }
  sourceList.add(DirSource(dir = moduleOutputDir, excludes = excludes, sizeConsumer = sizeConsumer))
}