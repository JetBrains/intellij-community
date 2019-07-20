// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.SystemProperties
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleSourceRoot
import org.jetbrains.jps.util.JpsPathUtil
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.function.Consumer
import java.util.regex.Pattern
import java.util.stream.Stream
import java.util.stream.StreamSupport
import kotlin.collections.ArrayList

internal const val ROBOTS_FILE_NAME = "icon-robots.txt"

internal class ImagePaths(val id: String,
                          val sourceRoot: JpsModuleSourceRoot,
                          val phantom: Boolean) {
  private var flags: ImageFlags = ImageFlags()
  private var images: MutableList<Path> = ContainerUtil.createConcurrentList()

  fun addImage(file: Path, fileFlags: ImageFlags) {
    images.add(file)
    flags = mergeImageFlags(flags, fileFlags, file.toString())
  }

  val files: List<Path>
    get() = images

  fun getFiles(vararg types: ImageType): List<Path> = files.filter { ImageType.fromFile(it) in types }

  val file: Path?
    get() = getFiles(ImageType.BASIC)
      .sortedBy { ImageExtension.fromFile(it) }
      .firstOrNull()

  val presentablePath: Path
    get() = file ?: files.first() ?: Paths.get("<unknown>")

  val used: Boolean get() = flags.used
  val deprecated: Boolean get() = flags.deprecation != null
  val deprecation: DeprecationData? get() = flags.deprecation
  val scheduledForRemoval by lazy {
    flags.deprecation?.comment?.contains("to be removed") == true
  }
}

class ImageFlags(val skipped: Boolean,
                 val used: Boolean,
                 val deprecation: DeprecationData?) {
  constructor() : this(false, false, null)
}

internal class ImageSyncFlags(val skipSync: Boolean, val forceSync: Boolean)

data class DeprecationData(val comment: String?, val replacement: String?, val replacementContextClazz: String?, val replacementReference: String?)

internal class ImageCollector(private val projectHome: Path, private val iconsOnly: Boolean = true, val ignoreSkipTag: Boolean = false, private val className: String? = null) {
  // files processed in parallel, so, concurrent data structures must be used
  private val icons = ContainerUtil.newConcurrentMap<String, ImagePaths>()
  private val phantomIcons = ContainerUtil.newConcurrentMap<String, ImagePaths>()
  private val usedIconsRobots: MutableSet<Path> = ContainerUtil.newConcurrentSet()

  fun collect(module: JpsModule, includePhantom: Boolean = false): List<ImagePaths> {
    for (sourceRoot in module.sourceRoots) {
      if (sourceRoot.rootType == JavaResourceRootType.RESOURCE) {
        val rootDir = Paths.get(JpsPathUtil.urlToPath(sourceRoot.url))
        if (rootDir.fileName.toString() != "compatibilityResources") {
          processRoot(sourceRoot, rootDir)
        }
        else if (SystemProperties.getBooleanProperty("remove.extra.icon.robots.files", false)) {
          // under flag because not required for regular usage (to avoid FS call)
          try {
            Files.delete(rootDir.resolve(ROBOTS_FILE_NAME))
          }
          catch (ignored: NoSuchFileException) {
          }
        }
      }
    }

    val result = ArrayList(icons.values)
    if (includePhantom) {
      result.addAll(phantomIcons.values)
    }
    return result
  }

  fun printUsedIconRobots() {
    if (!usedIconsRobots.isEmpty()) {
      println(usedIconsRobots.joinToString(separator = "\n") { "Found icon-robots: $it" })
    }
  }

  private fun processRoot(sourceRoot: JpsModuleSourceRoot, rootDir: Path) {
    val attributes = try {
      Files.readAttributes(rootDir, BasicFileAttributes::class.java)
    }
    catch (ignored: NoSuchFileException) {
      return
    }

    val answer = downToRoot(rootDir, rootDir, attributes.isDirectory, null, IconRobotsData(), 0)
    val iconsRoot = (if (answer == null || Files.isDirectory(answer)) answer else answer.parent) ?: return

    val rootRobotData = upToProjectHome(rootDir)
    if (rootRobotData.isSkipped(rootDir)) return

    val robotData = rootRobotData.fork(iconsRoot, rootDir)

    processDirectory(iconsRoot, sourceRoot, robotData, emptyList(), 0)
    processPhantomIcons(iconsRoot, sourceRoot, robotData, emptyList())
  }

  private fun processDirectory(dir: Path, sourceRoot: JpsModuleSourceRoot, robotData: IconRobotsData, prefix: List<String>, level: Int) {
    // do not process in parallel for if level >= 3 because no sense - parents processed in parallel already
    dir.processChildren(level < 3) { file ->
      if (robotData.isSkipped(file)) {
        return@processChildren
      }

      if (Files.isDirectory(file)) {
        val root = Paths.get(JpsPathUtil.urlToPath(sourceRoot.url))
        val childRobotData = robotData.fork(file, root)
        val childPrefix = prefix + file.fileName.toString()
        processDirectory(file, sourceRoot, childRobotData, childPrefix, level + 1)

        if (childRobotData != robotData) {
          processPhantomIcons(file, sourceRoot, childRobotData, childPrefix)
        }
      }
      else if (isImage(file, iconsOnly)) {
        processImageFile(file, sourceRoot, robotData, prefix)
      }
    }
  }

  private fun processImageFile(file: Path, sourceRoot: JpsModuleSourceRoot, robotData: IconRobotsData, prefix: List<String>) {
    val flags = robotData.getImageFlags(file)
    if (flags.skipped) {
      return
    }

    val id = ImageType.getBasicName(file, prefix)
    val iconPaths = icons.computeIfAbsent(id) { ImagePaths(id, sourceRoot, false) }
    iconPaths.addImage(file, flags)
  }

  private fun processPhantomIcons(root: Path, sourceRoot: JpsModuleSourceRoot, robotData: IconRobotsData, prefix: List<String>) {
    for (icon in robotData.getOwnDeprecatedIcons()) {
      val id = ImageType.getBasicName(icon.first, prefix)
      if (icons.containsKey(id)) {
        continue
      }

      val iconFile = root.resolve(icon.first.removePrefix("/").removePrefix(File.separator))
      val paths = ImagePaths(id, sourceRoot, true)
      paths.addImage(iconFile, icon.second)

      if (phantomIcons.containsKey(id)) {
        throw Exception("Duplicated phantom icon found: $id\n$root/${ROBOTS_FILE_NAME}")
      }

      phantomIcons.put(id, paths)
    }
  }

  private fun upToProjectHome(dir: Path): IconRobotsData {
    if (dir == projectHome) {
      return IconRobotsData()
    }

    val parent = dir.parent ?: return IconRobotsData()
    return upToProjectHome(parent).fork(parent, projectHome)
  }

  private fun downToRoot(root: Path, file: Path, isFileDir: Boolean, common: Path?, robotData: IconRobotsData, level: Int): Path? {
    if (robotData.isSkipped(file)) {
      return common
    }

    when {
      isFileDir -> {
        if (level == 1) {
          val name = file.fileName.toString()
          if (name == "META-INF" || name == "intentionDescriptions" || name == "fileTemplates") {
            return common
          }
        }

        val childRobotData = robotData.fork(file, root)
        var childCommon = common
        Files.newDirectoryStream(file).use { stream ->
          stream.forEach {
            childCommon = downToRoot(root, it, Files.isDirectory(it), childCommon, childRobotData, level + 1)
          }
        }
        return childCommon
      }

      isImage(file, iconsOnly) -> {
        if (common == null) {
          return file
        }
        else {
          //todo[nik] remove explicit type when KT-25589 is fixed
          val ancestor: File? = FileUtil.findAncestor(common.toFile(), file.toFile())
          return ancestor?.toPath()
        }
      }
      else -> return common
    }
  }

  private data class DeprecatedEntry(val matcher: Pattern, val data: DeprecationData)
  private data class OwnDeprecatedIcon(val relativeFile: String, val data: DeprecationData)

  internal inner class IconRobotsData(private val parent: IconRobotsData? = null) {
    private val skip: MutableList<Pattern> = ArrayList()
    private val used: MutableList<Pattern> = ArrayList()
    private val deprecated: MutableList<DeprecatedEntry> = ArrayList()
    private val skipSync: MutableList<Pattern> = ArrayList()
    private val forceSync: MutableList<Pattern> = ArrayList()

    private val ownDeprecatedIcons: MutableList<OwnDeprecatedIcon> = ArrayList()

    fun getImageFlags(file: Path): ImageFlags {
      val isSkipped = !ignoreSkipTag && matches(file, skip)
      val isUsed = matches(file, used)
      val deprecationData = findDeprecatedData(file)
      val flags = ImageFlags(isSkipped, isUsed, deprecationData)
      val parentFlags = parent?.getImageFlags(file) ?: return flags
      return mergeImageFlags(flags, parentFlags, file.toString())
    }

    fun getImageSyncFlags(file: Path) = ImageSyncFlags(skipSync = matches(file, skipSync), forceSync = matches(file, forceSync))

    fun getOwnDeprecatedIcons(): List<Pair<String, ImageFlags>> {
      return ownDeprecatedIcons.map { Pair(it.relativeFile, ImageFlags(false, false, it.data)) }
    }

    fun isSkipped(file: Path): Boolean {
      if (!ignoreSkipTag && matches(file, skip)) {
        return true
      }
      else {
        return parent?.isSkipped(file) ?: return false
      }
    }

    fun fork(dir: Path, root: Path): IconRobotsData {
      val robots = dir.resolve(ROBOTS_FILE_NAME)
      if (!Files.exists(robots)) {
        return this
      }

      usedIconsRobots.add(robots)

      val answer = IconRobotsData(this)
      parse(robots,
            RobotFileHandler("skip:") { value -> answer.skip.add(compilePattern(dir, root, value)) },
            RobotFileHandler("used:") { value -> answer.used.add(compilePattern(dir, root, value)) },
            RobotFileHandler("deprecated:") { value ->
              val comment = StringUtil.nullize(value.substringAfter(";", "").trim())
              val valueWithoutComment = value.substringBefore(";")
              val pattern = valueWithoutComment.substringBefore("->").trim()
              val replacementString = StringUtil.nullize(valueWithoutComment.substringAfter("->", "").trim())
              val replacement = replacementString?.substringAfter('@')?.trim()
              val replacementContextClazz = StringUtil.nullize(replacementString?.substringBefore('@', "")?.trim())

              val deprecatedData = DeprecationData(comment, replacement, replacementContextClazz, replacementReference = computeReplacementReference(comment))
              answer.deprecated.add(DeprecatedEntry(compilePattern(dir, root, pattern), deprecatedData))

              if (!pattern.contains('*') && !pattern.startsWith('/')) {
                answer.ownDeprecatedIcons.add(OwnDeprecatedIcon(pattern, deprecatedData))
              }
            },
            RobotFileHandler("name:") { }, // ignore directive for IconsClassGenerator
            RobotFileHandler("#") { }, // comment
            RobotFileHandler("forceSync:") { value -> answer.forceSync.add(compilePattern(dir, root, value)) },
            RobotFileHandler("skipSync:") { value -> answer.skipSync.add(compilePattern(dir, root, value)) }
      )
      return answer
    }

    private fun computeReplacementReference(comment: String?): String? {
      if (className == null) {
        return null
      }

      val result = StringUtil.nullize(comment?.substringAfter(" - use $className.", "")?.substringBefore(' ')?.trim()) ?: return null
      return "$className.$result"
    }

    private fun parse(robots: Path, vararg handlers: RobotFileHandler) {
      Files.lines(robots).forEach { line ->
        if (line.isBlank()) return@forEach
        for (h in handlers) {
          if (line.startsWith(h.start)) {
            h.handler(StringUtil.trimStart(line, h.start))
            return@forEach
          }
        }
        throw Exception("Can't parse $robots. Line: $line")
      }
    }

    private fun compilePattern(dir: Path, root: Path, value: String): Pattern {
      var pattern = value.trim()

      if (pattern.startsWith('/')) {
        pattern = """${root.toAbsolutePath()}$pattern"""
      }
      else {
        pattern = "${dir.toAbsolutePath()}/$pattern"
      }

      val regExp = FileUtil.convertAntToRegexp(pattern, false)
      try {
        return Pattern.compile(regExp)
      }
      catch (e: Exception) {
        throw Exception("Cannot compile pattern: $pattern. Built on based in $dir/$ROBOTS_FILE_NAME")
      }
    }

    private fun findDeprecatedData(file: Path): DeprecationData? {
      val basicPath = getBasicPath(file)
      return deprecated.find { it.matcher.matcher(basicPath).matches() }?.data
    }

    private fun matches(file: Path, matchers: List<Pattern>): Boolean {
      if (matchers.isEmpty()) {
        return false
      }

      val basicPath = getBasicPath(file)
      return matchers.any { matcher ->
        try {
          matcher.matcher(basicPath).matches()
        }
        catch (e: Exception) {
          throw RuntimeException("cannot reset matcher ${matcher} with a new input $basicPath: $e")
        }
      }
    }

    private fun getBasicPath(file: Path): String {
      val path = FileUtil.toSystemIndependentName(file.toAbsolutePath().toString())

      val pathWithoutExtension = FileUtilRt.getNameWithoutExtension(path)
      val extension = FileUtilRt.getExtension(path)

      val basicPathWithoutExtension = ImageType.stripSuffix(pathWithoutExtension)
      return basicPathWithoutExtension + if (extension.isNotEmpty()) ".$extension" else ""
    }
  }
}

private data class RobotFileHandler(val start: String, val handler: (String) -> Unit)

private fun mergeImageFlags(flags1: ImageFlags, flags2: ImageFlags, comment: String): ImageFlags {
  return ImageFlags(flags1.skipped || flags2.skipped,
                    flags1.used || flags2.used,
                    mergeDeprecations(flags1.deprecation, flags2.deprecation, comment))
}

private fun mergeDeprecations(data1: DeprecationData?,
                              data2: DeprecationData?,
                              comment: String): DeprecationData? {
  if (data1 == null) return data2
  if (data2 == null) return data1
  if (data1 == data2) return data1

  throw AssertionError("Different deprecation statements found for icon: $comment\n$data1\n$data2")
}

fun Path.processChildren(isParallel: Boolean = true, consumer: (Path) -> Unit) {
  DirectorySpliterator.list(this, isParallel).use {
    it.forEach(consumer)
  }
}

// https://stackoverflow.com/a/34351591/1910191
private class DirectorySpliterator private constructor(iterator: Iterator<Path>, private var estimation: Long) : Spliterator<Path> {
  private var iterator: Iterator<Path>? = iterator

  companion object {
    // opposite to JDK, you don't need to close
    @Throws(IOException::class)
    fun list(parent: Path, isParallel: Boolean = true): Stream<Path> {
      val directoryStream = Files.newDirectoryStream(parent)
      val splitSize = Runtime.getRuntime().availableProcessors() + 1
      return StreamSupport.stream(DirectorySpliterator(directoryStream.iterator(), splitSize.toLong()), isParallel)
        .onClose {
          directoryStream.close()
        }
    }
  }

  override fun tryAdvance(action: Consumer<in Path>): Boolean {
    val iterator = iterator
    if (iterator == null) {
      return false
    }

    val path = synchronized(iterator) {
      if (!iterator.hasNext()) {
        this.iterator = null
        return false
      }
      iterator.next()
    }

    action.accept(path)
    return true
  }

  override fun trySplit(): Spliterator<Path>? {
    val iterator = iterator
    if (iterator == null || estimation == 1L) {
      return null
    }

    val e = this.estimation.ushr(1)
    this.estimation -= e
    return DirectorySpliterator(iterator, e)
  }

  override fun estimateSize() = estimation

  override fun characteristics() = Spliterator.DISTINCT or Spliterator.NONNULL or Spliterator.IMMUTABLE
}
