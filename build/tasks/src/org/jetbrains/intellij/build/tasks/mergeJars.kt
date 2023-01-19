// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JarBuilder")
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
package org.jetbrains.intellij.build.tasks

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
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

data class DirSource(@JvmField val dir: Path,
                     @JvmField val excludes: List<PathMatcher> = emptyList(),
                     override val sizeConsumer: IntConsumer? = null,
                     @JvmField val prefix: String = "") : Source {
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

data class InMemoryContentSource(@JvmField val relativePath: String,
                                 @JvmField val data: ByteArray, override val sizeConsumer: IntConsumer? = null) : Source {
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

  val packageIndexBuilder = if (compress) null else PackageIndexBuilder()
  writeNewFile(targetFile) { outChannel ->
    ZipFileWriter(outChannel, if (compress) Deflater(Deflater.DEFAULT_COMPRESSION, true) else null).use { zipCreator ->
      val uniqueNames = HashMap<String, Path>()

      for (source in sources) {
        val positionBefore = outChannel.position()
        when (source) {
          is DirSource -> {
            val archiver = ZipArchiver(zipCreator, fileAdded = {
              if (uniqueNames.putIfAbsent(it, source.dir) == null) {
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
            val sourceFile = source.file
            val requiresMavenFiles = targetFile.fileName.toString().startsWith("junixsocket-")
            readZipFile(sourceFile) { name, entry ->
              if (nativeFiles != null && isNative(name)) {
                if (isDuplicated(uniqueNames, name, sourceFile)) {
                  return@readZipFile
                }

                nativeFiles.computeIfAbsent(source) { mutableListOf() }.add(name)
              }
              else {
                val filter = source.filter
                val isIncluded = if (filter == null) {
                  checkName(name = name,
                            excludes = source.excludes,
                            includeManifest = sources.size == 1,
                            requiresMavenFiles = requiresMavenFiles)
                }
                else {
                  filter(name)
                }

                if (isIncluded) {
                  if (isDuplicated(uniqueNames, name, sourceFile)) {
                    return@readZipFile
                  }

                  packageIndexBuilder?.addFile(name)
                  zipCreator.uncompressedData(name, entry.getByteBuffer())
                }
              }
            }
          }
        }

        source.sizeConsumer?.accept((zipCreator.resultStream.getChannelPosition() - positionBefore).toInt())
      }

      packageIndexBuilder?.writePackageIndex(zipCreator)
    }
  }
}

private fun isDuplicated(uniqueNames: HashMap<String, Path>, name: String, sourceFile: Path): Boolean {
  val old = uniqueNames.putIfAbsent(name, sourceFile) ?: return false
  Span.current().addEvent("$name is duplicated and ignored", Attributes.of(
    AttributeKey.stringKey("firstSource"), old.toString(),
    AttributeKey.stringKey("secondSource"), sourceFile.toString(),
  ))
  return true
}

private fun isNative(name: String): Boolean {
  return name.endsWith(".jnilib") ||
         name.endsWith(".dylib") ||
         name.endsWith(".so") ||
         name.endsWith(".exe") ||
         name.endsWith(".dll") ||
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
  set.add("META-INF/services/javax.xml.parsers.SAXParserFactory")
  set.add("META-INF/services/javax.xml.stream.XMLEventFactory")
  set.add("META-INF/services/javax.xml.parsers.DocumentBuilderFactory")
  set.add("META-INF/services/javax.xml.datatype.DatatypeFactory")

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
  return set
}

private val ignoredNames = java.util.Set.copyOf(getIgnoredNames())

private fun checkName(name: String,
                      excludes: List<Regex>,
                      includeManifest: Boolean,
                      requiresMavenFiles: Boolean): Boolean {
  return !ignoredNames.contains(name) &&
         excludes.none { it.matches(name) } &&
         !name.endsWith(".kotlin_metadata") &&
         (includeManifest || name != "META-INF/MANIFEST.MF") &&
         !name.startsWith("license/") &&
         !name.startsWith("META-INF/license/") &&
         !name.startsWith("META-INF/LICENSE-") &&
         !name.startsWith("native-image/") &&
         !name.startsWith("native/") &&
         !name.startsWith("licenses/") &&
         (requiresMavenFiles || (name != "META-INF/maven" && !name.startsWith("META-INF/maven/"))) &&
         !name.startsWith("META-INF/INDEX.LIST") &&
         (!name.startsWith("META-INF/") || (!name.endsWith(".DSA") && !name.endsWith(".SF") && !name.endsWith(".RSA")))
}