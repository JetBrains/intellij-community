// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("JarBuilder")
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
package org.jetbrains.intellij.build.tasks

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.context.Context
import org.jetbrains.intellij.build.io.*
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.util.*
import java.util.concurrent.ForkJoinTask
import java.util.function.IntConsumer

private const val DO_NOT_EXPORT_TO_CONSOLE = "_CES_"

sealed interface Source {
  val sizeConsumer: IntConsumer?
}

private val USER_HOME = Path.of(System.getProperty("user.home"))
private val MAVEN_REPO = USER_HOME.resolve(".m2/repository")

data class ZipSource(val file: Path, override val sizeConsumer: IntConsumer? = null) : Source, Comparable<ZipSource> {
  override fun compareTo(other: ZipSource) = file.compareTo(other.file)

  override fun toString(): String {
    val shortPath = if (file.startsWith(MAVEN_REPO)) {
      MAVEN_REPO.relativize(file).toString()
    }
    else if (file.startsWith(USER_HOME)) {
      "~/" + USER_HOME.relativize(file)
    }
    else {
      file.toString()
    }
    return "zip(file=$shortPath)"
  }
}

data class DirSource(val dir: Path, val excludes: List<PathMatcher>, override val sizeConsumer: IntConsumer? = null) : Source {
  override fun toString(): String {
    val shortPath = if (dir.startsWith(USER_HOME)) {
      "~/" + USER_HOME.relativize(dir)
    }
    else {
      dir.toString()
    }
    return "dir(dir=$shortPath, excludes=${excludes.size})"
  }
}

data class InMemoryContentSource(val relativePath: String, val data: ByteArray, override val sizeConsumer: IntConsumer? = null) : Source {
  override fun toString() = "inMemory(relativePath=$relativePath)"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is InMemoryContentSource) return false

    if (relativePath != other.relativePath) return false
    if (!data.contentEquals(other.data)) return false
    if (sizeConsumer != other.sizeConsumer) return false

    return true
  }

  override fun hashCode(): Int {
    var result = relativePath.hashCode()
    result = 31 * result + data.contentHashCode()
    result = 31 * result + (sizeConsumer?.hashCode() ?: 0)
    return result
  }
}

fun createZipSource(file: Path, sizeConsumer: IntConsumer?): ZipSource = ZipSource(file = file, sizeConsumer = sizeConsumer)

fun buildJars(descriptors: List<Triple<Path, String, List<Source>>>, dryRun: Boolean) {
  val uniqueFiles = HashMap<Path, List<Source>>()
  for (descriptor in descriptors) {
    val existing = uniqueFiles.putIfAbsent(descriptor.first, descriptor.third)
    if (existing != null) {
      throw IllegalStateException(
        "File ${descriptor.first} is already associated." +
        "\nPrevious:\n  ${existing.joinToString(separator = "\n  ")}" +
        "\nCurrent:\n  ${descriptor.third.joinToString(separator = "\n  ")}"
      )
    }
  }

  val traceContext = Context.current()

  ForkJoinTask.invokeAll(descriptors.map { item ->
    ForkJoinTask.adapt {
      val file = item.first
      tracer.spanBuilder("build jar")
        .setParent(traceContext)
        .setAttribute(DO_NOT_EXPORT_TO_CONSOLE, true)
        .setAttribute("jar", file.toString())
        .setAttribute(AttributeKey.stringArrayKey("sources"), item.third.map { item.toString() })
        .startSpan()
        .use {
          buildJar(file, item.third, dryRun = dryRun)
        }

      // app.jar is combined later with other JARs and then re-ordered
      if (!dryRun && item.second.isNotEmpty() && item.second != "lib/app.jar") {
        reorderJar(relativePath = item.second, file = file, traceContext = traceContext)
      }
    }
  })
}

fun buildJar(targetFile: Path, sources: List<Source>, dryRun: Boolean = false) {
  if (dryRun) {
    for (source in sources) {
      source.sizeConsumer?.accept(0)
    }
    return
  }

  val forbidNativeFiles = targetFile.fileName.toString() == "app.jar"
  val packageIndexBuilder = PackageIndexBuilder()
  writeNewFile(targetFile) { outChannel ->
    ZipFileWriter(outChannel).use { zipCreator ->
      val uniqueNames = HashSet<String>()
      for (source in sources) {
        val positionBefore = outChannel.position()
        when (source) {
          is DirSource -> {
            val archiver = ZipArchiver(zipCreator, fileAdded = {
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
          is InMemoryContentSource -> {
            if (!uniqueNames.add(source.relativePath)) {
              throw IllegalStateException("in-memory source must always be first " +
                                          "(targetFile=$targetFile, source=${source.relativePath}, sources=${sources.joinToString()})")
            }

            packageIndexBuilder.addFile(source.relativePath)
            zipCreator.uncompressedData(source.relativePath, source.data.size) {
              it.put(source.data)
            }
          }
          else -> {
            val sourceFile = (source as ZipSource).file
            val requiresMavenFiles = targetFile.fileName.toString().startsWith("junixsocket-")
            readZipFile(sourceFile) { name, entry ->
              if (forbidNativeFiles && (name.endsWith(".jnilib") || name.endsWith(".dylib") || name.endsWith(".so"))) {
                throw IllegalStateException("Library with native files must be packed separately " +
                                            "(sourceFile=$sourceFile, targetFile=$targetFile, fileName=${name})")
              }

              if (checkName(name, uniqueNames, includeManifest = sources.size == 1, requiresMavenFiles = requiresMavenFiles)) {
                packageIndexBuilder.addFile(name)
                zipCreator.uncompressedData(name, entry.getByteBuffer())
              }
            }
          }
        }

        source.sizeConsumer?.accept((zipCreator.resultStream.getChannelPosition() - positionBefore).toInt())
      }
      packageIndexBuilder.writePackageIndex(zipCreator)
    }
  }
}

private fun getIgnoredNames(): Set<String> {
  val set = HashSet<String>()
  // compilation cache on TC
  set.add(".hash")
  @Suppress("SpellCheckingInspection")
  set.add(".gitattributes")
  set.add("pom.xml")
  set.add("about.html")
  set.add("module-info.class")
  set.add("META-INF/services/javax.xml.parsers.SAXParserFactory")
  set.add("META-INF/services/javax.xml.stream.XMLEventFactory")
  set.add("META-INF/services/javax.xml.parsers.DocumentBuilderFactory")
  set.add("META-INF/services/javax.xml.datatype.DatatypeFactory")
  set.add("native-image")
  set.add("native")
  set.add("licenses")
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
  return set
}

private val ignoredNames = java.util.Set.copyOf(getIgnoredNames())

private fun checkName(name: String,
                      uniqueNames: MutableSet<String>,
                      includeManifest: Boolean,
                      requiresMavenFiles: Boolean): Boolean {
  return !ignoredNames.contains(name) &&
         uniqueNames.add(name) &&
         !name.endsWith(".kotlin_metadata") &&
         (includeManifest || name != "META-INF/MANIFEST.MF") &&
         !name.startsWith("license/") &&
         !name.startsWith("native-image/") &&
         !name.startsWith("native/") &&
         !name.startsWith("licenses/") &&
         (requiresMavenFiles || (name != "META-INF/maven" && !name.startsWith("META-INF/maven/"))) &&
         !name.startsWith("META-INF/INDEX.LIST") &&
         (!name.startsWith("META-INF/") || (!name.endsWith(".DSA") && !name.endsWith(".SF") && !name.endsWith(".RSA")))
}

@Suppress("SpellCheckingInspection")
private val excludedFromMergeLibs = java.util.Set.of(
  "sqlite", "async-profiler",
  "dexlib2", // android-only lib
  "intellij-test-discovery", // used as agent
  "winp", "junixsocket-core", "pty4j", "grpc-netty-shaded", // contains native library
  "protobuf", // https://youtrack.jetbrains.com/issue/IDEA-268753
)

fun isLibraryMergeable(libName: String): Boolean {
  return !excludedFromMergeLibs.contains(libName) &&
         !libName.startsWith("kotlin-") &&
         !libName.startsWith("kotlinc.") &&
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
  // compilation cache on TC
  FileSystems.getDefault().getPathMatcher("glob:.hash"),
  FileSystems.getDefault().getPathMatcher("glob:classpath.index"),
)

fun addModuleSources(moduleName: String,
                     moduleNameToSize: MutableMap<String, Int>,
                     moduleOutputDir: Path,
                     modulePatches: Collection<Path>,
                     modulePatchContents: Map<String, ByteArray>,
                     searchableOptionsRootDir: Path,
                     extraExcludes: Collection<String>,
                     sourceList: MutableList<Source>) {
  val sizeConsumer = IntConsumer {
    moduleNameToSize.merge(moduleName, it) { oldValue, value -> oldValue + value }
  }

  for (entry in modulePatchContents) {
    sourceList.add(InMemoryContentSource(entry.key, entry.value, sizeConsumer))
  }
  // must be before module output to override
  for (moduleOutputPatch in modulePatches) {
    sourceList.add(DirSource(moduleOutputPatch, Collections.emptyList(), sizeConsumer))
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