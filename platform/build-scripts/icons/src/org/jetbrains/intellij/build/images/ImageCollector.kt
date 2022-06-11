// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images

import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.icons.ImageDescriptor
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleSourceRoot
import org.jetbrains.jps.util.JpsPathUtil
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.regex.Pattern
import java.util.stream.Stream
import java.util.stream.StreamSupport

internal const val ROBOTS_FILE_NAME = "icon-robots.txt"

private val EMPTY_IMAGE_FLAGS = ImageFlags(used = false, deprecation = null)

internal class ImageInfo(val id: String,
                         val sourceRoot: JpsModuleSourceRoot,
                         val phantom: Boolean) {
  private var flags = EMPTY_IMAGE_FLAGS
  private val images = ArrayList<Path>()

  val files: List<Path>
    get() = images

  @Synchronized
  fun addImage(file: Path, fileFlags: ImageFlags) {
    images.add(file)
    flags = mergeImageFlags(flags, fileFlags, file)
  }

  fun getFiles(vararg types: ImageType) = files.filter { ImageType.fromFile(it) in types }

  fun getFlags(): Int {
    var result = 0
    for (image in images) {
      val path = image.toString()
      when {
        path.contains("@2x.") -> {
          result = result or ImageDescriptor.HAS_2x
        }
        path.contains("@2x_dark.") -> {
          result = result or ImageDescriptor.HAS_DARK_2x
        }
        path.contains("_dark.") -> {
          result = result or ImageDescriptor.HAS_DARK
        }
      }
    }
    return result
  }

  val basicFile: Path?
    get() {
      return getFiles(ImageType.BASIC).minByOrNull { ImageExtension.fromFile(it)!! }
    }

  val presentablePath: Path
    get() = basicFile ?: files.first()

  val used: Boolean
    get() = flags.used
  val deprecated: Boolean
    get() = flags.deprecation != null
  val deprecation: DeprecationData?
    get() = flags.deprecation

  val scheduledForRemoval by lazy {
    flags.deprecation?.comment?.contains("to be removed") == true
  }
}

internal data class ImageFlags(val used: Boolean, val deprecation: DeprecationData?)

internal class ImageSyncFlags(val skipSync: Boolean, val forceSync: Boolean)

internal data class DeprecationData(val comment: String?,
                                    val replacement: String?,
                                    val replacementContextClazz: String?,
                                    val replacementReference: String?)

internal class ImageCollector(private val projectHome: Path,
                              private val iconsOnly: Boolean = true,
                              private val ignoreSkipTag: Boolean = false,
                              private val moduleConfig: IntellijIconClassGeneratorModuleConfig?) {
  // files processed in parallel, so, concurrent data structures must be used
  private val icons = ConcurrentHashMap<String, ImageInfo>()
  private val phantomIcons = ConcurrentHashMap<String, ImageInfo>()
  private val usedIconsRobots: MutableSet<Path> = Collections.newSetFromMap(ConcurrentHashMap())

  fun collect(module: JpsModule, includePhantom: Boolean = false): Collection<ImageInfo> {
    for (sourceRoot in module.sourceRoots) {
      if (sourceRoot.rootType != JavaResourceRootType.RESOURCE) {
        continue
      }
      val rootDir = Path.of(JpsPathUtil.urlToPath(sourceRoot.url))

      val iconDirectory = moduleConfig?.iconDirectory
      if (iconDirectory != null) {
        val rootRobotData = upToProjectHome(rootDir)
        val iconRoot = rootDir.resolve(iconDirectory).normalize()
        processDirectory(iconRoot, rootDir, sourceRoot, rootRobotData, "", 0)
        processPhantomIcons(iconRoot, sourceRoot, rootRobotData, "")
        break
      }

      if (rootDir.fileName.toString() != "compatibilityResources") {
        processRoot(sourceRoot, rootDir)
      }
      else if (java.lang.Boolean.getBoolean("remove.extra.icon.robots.files")) {
        // under flag because not required for regular usage (to avoid FS call)
        try {
          Files.delete(rootDir.resolve(ROBOTS_FILE_NAME))
        }
        catch (ignored: NoSuchFileException) {
        }
      }
    }

    if (includePhantom) {
      return icons.values + phantomIcons.values
    }
    else {
      return icons.values
    }
  }

  fun collectSubDir(sourceRoot: JpsModuleSourceRoot, name: String, includePhantom: Boolean = false): List<ImageInfo> {
    processRoot(sourceRoot, Path.of(JpsPathUtil.urlToPath(sourceRoot.url)).resolve(name))
    val result = ArrayList(icons.values)
    if (includePhantom) {
      result.addAll(phantomIcons.values)
    }
    return result
  }

  fun printUsedIconRobots() {
    if (usedIconsRobots.isNotEmpty()) {
      println(usedIconsRobots.joinToString(separator = "\n") { "Found icon-robots: $it" })
    }
  }

  private fun processRoot(sourceRoot: JpsModuleSourceRoot, rootDir: Path) {
    val rootRobotData = upToProjectHome(rootDir)
    if (rootRobotData.isSkipped(rootDir)) {
      return
    }

    val attributes = try {
      Files.readAttributes(rootDir, BasicFileAttributes::class.java)
    }
    catch (ignored: NoSuchFileException) {
      return
    }

    val iconRoot = downToRoot(rootDir, rootDir, attributes.isDirectory, null, IconRobotsData(null, ignoreSkipTag, usedIconsRobots), 0)
                   ?: return
    val robotData = rootRobotData.fork(iconRoot, rootDir)

    processDirectory(iconRoot, rootDir, sourceRoot, robotData, "", 0)
    processPhantomIcons(iconRoot, sourceRoot, robotData, "")
  }

  private fun processDirectory(dir: Path,
                               rootDir: Path,
                               sourceRoot: JpsModuleSourceRoot,
                               robotData: IconRobotsData,
                               prefix: String,
                               level: Int) {
    // do not process in parallel for if level >= 3 because no sense - parents processed in parallel already
    dir.processChildren(level < 3) { file ->
      if (robotData.isSkipped(file)) {
        return@processChildren
      }

      if (Files.isDirectory(file)) {
        val childRobotData = robotData.fork(file, rootDir)
        val childPrefix = "$prefix/${file.fileName}"
        processDirectory(file, rootDir, sourceRoot, childRobotData, childPrefix, level + 1)

        if (childRobotData != robotData) {
          processPhantomIcons(file, sourceRoot, childRobotData, childPrefix)
        }
      }
      else if (isImage(file, iconsOnly)) {
        icons.computeIfAbsent(ImageType.getBasicName(file.fileName.toString(), prefix)) { ImageInfo(it, sourceRoot, phantom = false) }
          .addImage(file, robotData.getImageFlags(file))
      }
    }
  }

  private fun processPhantomIcons(root: Path, sourceRoot: JpsModuleSourceRoot, robotData: IconRobotsData, prefix: String) {
    for (icon in robotData.getOwnDeprecatedIcons()) {
      val id = ImageType.getBasicName(icon.first, prefix)
      if (icons.containsKey(id)) {
        continue
      }

      val iconFile = root.resolve(icon.first.removePrefix("/").removePrefix(File.separator))
      val paths = ImageInfo(id, sourceRoot, true)
      paths.addImage(iconFile, icon.second)

      if (phantomIcons.containsKey(id)) {
        throw Exception("Duplicated phantom icon found: $id\n$root/${ROBOTS_FILE_NAME}")
      }

      phantomIcons.put(id, paths)
    }
  }

  private fun upToProjectHome(dir: Path): IconRobotsData {
    if (dir == projectHome) {
      return IconRobotsData(null, ignoreSkipTag, usedIconsRobots)
    }

    val parent = dir.parent ?: return IconRobotsData(null, ignoreSkipTag, usedIconsRobots)
    return upToProjectHome(parent).fork(parent, projectHome)
  }

  private fun downToRoot(root: Path, file: Path, isDirectory: Boolean, common: Path?, robotData: IconRobotsData, level: Int): Path? {
    if (robotData.isSkipped(file)) {
      return common
    }
    else if (isDirectory) {
      if (level == 1) {
        val name = file.fileName.toString()
        if (isBlacklistedTopDirectory(name)) {
          return common
        }
      }

      val childRobotData = robotData.fork(file, root)
      var childCommon = common
      Files.newDirectoryStream(file).use { stream ->
        for (it in stream) {
          childCommon = downToRoot(root, it, Files.isDirectory(it), childCommon, childRobotData, level + 1)
        }
      }
      return childCommon
    }
    else if (isImage(file, iconsOnly)) {
      if (common == null) {
        return file.parent
      }
      else {
        var ancestor: Path? = common
        while (ancestor != null && !file.startsWith(ancestor)) {
          ancestor = ancestor.parent
        }
        return ancestor
      }
    }
    else {
      return common
    }
  }
}

private data class DeprecatedEntry(val matcher: Pattern, val data: DeprecationData)
private data class OwnDeprecatedIcon(val relativeFile: String, val data: DeprecationData)

internal class IconRobotsData(private val parent: IconRobotsData? = null,
                              private val ignoreSkipTag: Boolean,
                              private val usedIconsRobots: MutableSet<Path>?) {
  private val skip = ArrayList<Pattern>()
  private val used = ArrayList<Pattern>()
  private val deprecated = ArrayList<DeprecatedEntry>()
  private val skipSync = ArrayList<Pattern>()
  private val forceSync = ArrayList<Pattern>()

  private val ownDeprecatedIcons = ArrayList<OwnDeprecatedIcon>()

  fun getImageFlags(file: Path): ImageFlags {
    val isUsed = matches(file, used)
    val deprecationData = findDeprecatedData(file)
    val flags = ImageFlags(isUsed, deprecationData)
    val parentFlags = parent?.getImageFlags(file) ?: return flags
    return mergeImageFlags(flags, parentFlags, file)
  }

  fun getImageSyncFlags(file: Path) = ImageSyncFlags(skipSync = matches(file, skipSync), forceSync = matches(file, forceSync))

  fun getOwnDeprecatedIcons(): List<Pair<String, ImageFlags>> {
    return ownDeprecatedIcons.map { Pair(it.relativeFile, ImageFlags(used = false, deprecation = it.data)) }
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

    usedIconsRobots?.add(robots)

    val answer = IconRobotsData(this, ignoreSkipTag, usedIconsRobots)
    parse(robots,
          RobotFileHandler("skip:") { value -> answer.skip.add(compilePattern(dir, root, value)) },
          RobotFileHandler("used:") { value -> answer.used.add(compilePattern(dir, root, value)) },
          RobotFileHandler("deprecated:") { value ->
            val comment = value.substringAfter(";", "").trim().takeIf { it.isNotEmpty() }
            val valueWithoutComment = value.substringBefore(";")
            val pattern = valueWithoutComment.substringBefore("->").trim()
            val replacementString = valueWithoutComment.substringAfter("->", "").trim().takeIf { it.isNotEmpty() }
            val replacement = replacementString?.substringAfter('@')?.trim()
            val replacementContextClass = replacementString?.substringBefore('@', "")?.trim()?.takeIf { it.isNotEmpty() }

            val deprecatedData = DeprecationData(comment, replacement, replacementContextClass,
                                                 replacementReference = computeReplacementReference(comment))
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
    // allow only same class fields (IDEA-218345)
    return comment?.substringAfter("use {@link #", "")?.substringBefore('}')?.trim()?.takeIf { it.isNotEmpty() }
  }

  private fun parse(robots: Path, vararg handlers: RobotFileHandler) {
    Files.lines(robots).forEach { line ->
      if (line.isBlank()) return@forEach
      for (h in handlers) {
        if (line.startsWith(h.start)) {
          h.handler(line.substring(h.start.length))
          return@forEach
        }
      }
      throw Exception("Can't parse $robots. Line: $line")
    }
  }

  private fun compilePattern(dir: Path, root: Path, value: String): Pattern {
    var pattern = value.trim()
    if (pattern.startsWith('/')) {
      pattern = """$root$pattern"""
    }
    else {
      pattern = "$dir/$pattern"
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
        throw RuntimeException("cannot reset matcher $matcher with a new input $basicPath: $e")
      }
    }
  }

  private fun getBasicPath(file: Path): String {
    val path = file.toString().replace(File.separatorChar, '/')
    val lastDotIndex = path.lastIndexOf('.')
    assert(lastDotIndex != 0)
    if (lastDotIndex < 0) {
      return path
    }

    val basicPathWithoutExtension = ImageType.stripSuffix(path.substring(0, lastDotIndex))
    val extension = path.substring(lastDotIndex + 1)
    return "$basicPathWithoutExtension.$extension"
  }
}

private data class RobotFileHandler(val start: String, val handler: (String) -> Unit)

private fun mergeImageFlags(flags1: ImageFlags, flags2: ImageFlags, comment: Path): ImageFlags {
  return ImageFlags(used = flags1.used || flags2.used,
                    deprecation = mergeDeprecations(flags1.deprecation, flags2.deprecation, comment))
}

private fun mergeDeprecations(data1: DeprecationData?,
                              data2: DeprecationData?,
                              comment: Path): DeprecationData? {
  if (data1 == null) {
    return data2
  }
  if (data2 == null || data1 == data2) {
    return data1
  }

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
    val iterator = iterator ?: return false
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

internal fun isBlacklistedTopDirectory(name: String): Boolean {
  return name == "META-INF" || name == "intentionDescriptions"  || name == "inspectionDescriptions" || name == "fileTemplates"
}