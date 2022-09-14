// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JarBuilder")
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
package org.jetbrains.intellij.build.tasks

import org.jetbrains.intellij.build.io.*
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.util.function.IntConsumer
import java.util.zip.Deflater

sealed interface Source {
  val sizeConsumer: IntConsumer?

  val filter: ((String) -> Boolean)?
    get() = null
}

private val USER_HOME = Path.of(System.getProperty("user.home"))
private val MAVEN_REPO = USER_HOME.resolve(".m2/repository")

data class ZipSource(val file: Path,
                     val excludes: List<Regex> = emptyList(),
                     override val filter: ((String) -> Boolean)? = null,
                     override val sizeConsumer: IntConsumer? = null) : Source, Comparable<ZipSource> {
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

data class DirSource(val dir: Path,
                     val excludes: List<PathMatcher> = emptyList(),
                     override val sizeConsumer: IntConsumer? = null,
                     val prefix: String = "") : Source {
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

@JvmOverloads
fun buildJar(targetFile: Path,
             sources: List<Source>,
             compress: Boolean = false,
             dryRun: Boolean = false,
             nativeFiles: MutableMap<ZipSource, MutableList<String>>? = null) {
  if (dryRun) {
    for (source in sources) {
      source.sizeConsumer?.accept(0)
    }
    return
  }

  val packageIndexBuilder = if (!compress) PackageIndexBuilder() else null
  writeNewFile(targetFile) { outChannel ->
    ZipFileWriter(outChannel, if (compress) Deflater(Deflater.DEFAULT_COMPRESSION, true) else null).use { zipCreator ->
      val uniqueNames = HashSet<String>()

      for (source in sources) {
        val positionBefore = outChannel.position()
        when (source) {
          is DirSource -> {
            val archiver = ZipArchiver(zipCreator, fileAdded = {
              if (uniqueNames.add(it)) {
                packageIndexBuilder?.addFile(it)
                true
              }
              else {
                false
              }
            })
            val normalizedDir = source.dir.toAbsolutePath().normalize()
            archiver.setRootDir(normalizedDir, source.prefix)
            compressDir(normalizedDir, archiver, excludes = source.excludes.takeIf(List<PathMatcher>::isNotEmpty))
          }

          is InMemoryContentSource -> {
            if (!uniqueNames.add(source.relativePath)) {
              throw IllegalStateException("in-memory source must always be first " +
                                          "(targetFile=$targetFile, source=${source.relativePath}, sources=${sources.joinToString()})")
            }

            packageIndexBuilder?.addFile(source.relativePath)
            zipCreator.uncompressedData(source.relativePath, source.data.size) {
              it.put(source.data)
            }
          }

          is ZipSource -> {
            val sourceFile = source.file
            val requiresMavenFiles = targetFile.fileName.toString().startsWith("junixsocket-")
            readZipFile(sourceFile) { name, entry ->
              if (nativeFiles != null && isNative(name)) {
                nativeFiles.computeIfAbsent(source) { mutableListOf() }.add(name)
              }
              else {
                val filter = source.filter
                val isIncluded = if (filter == null) {
                  checkName(name = name,
                            uniqueNames = uniqueNames,
                            excludes = source.excludes,
                            includeManifest = sources.size == 1,
                            requiresMavenFiles = requiresMavenFiles)
                }
                else {
                  filter(name)
                }

                if (isIncluded) {
                  packageIndexBuilder?.addFile(name)
                  zipCreator.uncompressedData(name, entry.getByteBuffer())
                }
              }
            }
          }
        }.let { } // sealed when

        source.sizeConsumer?.accept((zipCreator.resultStream.getChannelPosition() - positionBefore).toInt())
      }

      packageIndexBuilder?.writePackageIndex(zipCreator)
    }
  }
}

private fun isNative(name: String): Boolean {
  return name.endsWith(".jnilib") ||
         name.endsWith(".dylib") ||
         name.endsWith(".so") ||
         name.endsWith(".tbd")
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
                      excludes: List<Regex>,
                      includeManifest: Boolean,
                      requiresMavenFiles: Boolean): Boolean {
  return !ignoredNames.contains(name) &&
         excludes.none { it.matches(name) } &&
         !name.endsWith(".kotlin_metadata") &&
         (includeManifest || name != "META-INF/MANIFEST.MF") &&
         !name.startsWith("license/") &&
         !name.startsWith("META-INF/license/") &&
         !name.startsWith("native-image/") &&
         !name.startsWith("native/") &&
         !name.startsWith("licenses/") &&
         (requiresMavenFiles || (name != "META-INF/maven" && !name.startsWith("META-INF/maven/"))) &&
         !name.startsWith("META-INF/INDEX.LIST") &&
         (!name.startsWith("META-INF/") || (!name.endsWith(".DSA") && !name.endsWith(".SF") && !name.endsWith(".RSA"))) &&
         uniqueNames.add(name)
}