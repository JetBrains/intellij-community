// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.images

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.icons.ImageDescriptor
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.jps.model.JpsSimpleElement
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleSourceRoot
import org.jetbrains.jps.util.JpsPathUtil
import java.io.File
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.listDirectoryEntries

internal const val ROBOTS_FILE_NAME = "icon-robots.txt"

private val EMPTY_IMAGE_FLAGS = ImageFlags(used = false, deprecation = null)

internal class ImageInfo(
  @JvmField val id: String,
  @JvmField val sourceRoot: JpsModuleSourceRoot,
  @JvmField val phantom: Boolean
) {
  private var flags = EMPTY_IMAGE_FLAGS
  private val images = ArrayList<Path>()

  val files: List<Path>
    get() = images

  fun trimPrefix(prefix: String): ImageInfo {
    val copy = ImageInfo(id = id.removePrefix(prefix), sourceRoot = sourceRoot, phantom = phantom)
    copy.images.addAll(images)
    copy.flags = flags
    return copy
  }

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
        path.contains("_stroke.") -> {
          result = result or ImageDescriptor.HAS_STROKE
        }
      }
    }
    return result
  }

  val basicFile: Path?
    get() = getFiles(ImageType.BASIC).minByOrNull { ImageExtension.fromFile(it)!! }

  val presentablePath: Path
    get() = basicFile ?: files.first()

  val used: Boolean
    get() = flags.used
  val deprecated: Boolean
    get() = flags.deprecation != null
  val deprecation: DeprecationData?
    get() = flags.deprecation

  val scheduledForRemoval: Boolean by lazy {
    flags.deprecation?.comment?.contains("to be removed") == true
  }

  val sourceCodeParameterName: String by lazy {
    var rootPrefix = "/"
    if (sourceRoot.rootType == JavaSourceRootType.SOURCE) {
      @Suppress("UNCHECKED_CAST")
      val packagePrefix = (sourceRoot.properties as JpsSimpleElement<JavaSourceRootProperties>).data.packagePrefix
      if (packagePrefix.isNotEmpty()) {
        rootPrefix += packagePrefix.replace('.', '/') + "/"
      }
    }

    val rootDir = Path.of(JpsPathUtil.urlToPath(sourceRoot.url))
    val deprecation = this.deprecation
    val imageFile: Path
    if (deprecation?.replacement == null) {
      imageFile = basicFile!!
    }
    else {
      imageFile = rootDir.resolve(deprecation.replacement.removePrefix("/").removePrefix(File.separator))
      assert(isIcon(imageFile)) {
        "Invalid deprecation replacement '${deprecation.replacement}': $imageFile is not an icon"
      }
    }

    val relativePath = rootPrefix + rootDir.relativize(imageFile).invariantSeparatorsPathString
    assert(relativePath.startsWith("/"))
    relativePath.removePrefix("/")
  }
}

internal data class ImageFlags(@JvmField val used: Boolean, @JvmField val deprecation: DeprecationData?)

internal data class ImageSyncFlags(@JvmField val skipSync: Boolean, @JvmField val forceSync: Boolean)

internal data class DeprecationData(
  @JvmField val comment: String?,
  @JvmField val replacement: String?,
  @JvmField val replacementContextClazz: String?,
  @JvmField val replacementReference: String?
)

internal class ImageCollector(
  private val projectHome: Path,
  private val iconsOnly: Boolean = true,
  private val ignoreSkipTag: Boolean = false,
  private val moduleConfig: IntellijIconClassGeneratorModuleConfig?
) {
  // files processed in parallel, so, concurrent data structures must be used
  private val icons = ConcurrentHashMap<String, ImageInfo>()
  private val phantomIcons = ConcurrentHashMap<String, ImageInfo>()
  private val mergeRoots: MutableSet<String> = ContainerUtil.newConcurrentSet()
  private val mergeAdd = ConcurrentHashMap<String, List<String>>()
  private val usedIconRobots: MutableSet<Path> = ContainerUtil.newConcurrentSet()
  private var mappingFile: Path? = null

  fun collect(module: JpsModule, includePhantom: Boolean = false): Collection<ImageInfo> {
    for (sourceRoot in module.sourceRoots) {
      if (sourceRoot.rootType != JavaResourceRootType.RESOURCE) {
        continue
      }

      val rootDir = sourceRoot.path
      collectMappingFile(rootDir)

      val iconDirectory = moduleConfig?.iconDirectory
      if (iconDirectory != null) {
        val rootRobotData = upToProjectHome(rootDir)
        val iconRoot = rootDir.resolve(iconDirectory).normalize()
        processDirectory(dir = iconRoot, rootDir = rootDir, sourceRoot = sourceRoot, robotData = rootRobotData, prefix = "", level = 0)
        processPhantomIcons(root = iconRoot, sourceRoot = sourceRoot, robotData = rootRobotData, prefix = "")
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
        catch (_: NoSuchFileException) { }
      }
    }

    return if (includePhantom) icons.values + phantomIcons.values else icons.values
  }

  fun collectSubDir(sourceRoot: JpsModuleSourceRoot, name: String, includePhantom: Boolean = false): List<ImageInfo> {
    collectMappingFile(sourceRoot.path)
    processRoot(sourceRoot, sourceRoot.path.resolve(name))
    val result = icons.values.toMutableList()
    if (includePhantom) {
      result.addAll(phantomIcons.values)
    }
    return result
  }

  fun printUsedIconRobots() {
    if (usedIconRobots.isNotEmpty()) {
      println(usedIconRobots.joinToString(separator = "\n") { "Found icon-robots: $it" })
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
    catch (_: NoSuchFileException) {
      return
    }

    val iconRoot = downToRoot(
      root = rootDir,
      file = rootDir,
      isDirectory = attributes.isDirectory,
      common = null,
      robotData = IconRobotsData(null, ignoreSkipTag, usedIconRobots),
      level = 0
    ) ?: return
    val robotData = rootRobotData.fork(iconRoot, rootDir)

    processDirectory(dir = iconRoot, rootDir = rootDir, sourceRoot = sourceRoot, robotData = robotData, prefix = "", level = 0)
    processPhantomIcons(root = iconRoot, sourceRoot = sourceRoot, robotData = robotData, prefix = "")
  }

  private fun processDirectory(
    dir: Path,
    rootDir: Path,
    sourceRoot: JpsModuleSourceRoot,
    robotData: IconRobotsData,
    prefix: String,
    level: Int
  ) {
    // do not process in parallel for if level >= 3 because no sense - parents processed in parallel already
    processChildren(dir, isParallel = level < 3) { file ->
      if (robotData.isSkipped(file)) {
        return@processChildren
      }

      if (Files.isDirectory(file)) {
        val childRobotData = robotData.fork(file, rootDir)
        val childPrefix = "$prefix/${file.fileName}"
        if (childRobotData.mergeToRoot) {
          childRobotData.mergeToRoot = false
          mergeRoots.add(childPrefix)
          if (childRobotData.mergeAdd.isNotEmpty()) {
            mergeAdd[childPrefix] = childRobotData.mergeAdd.toList()
            childRobotData.mergeAdd.clear()
          }
        }
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
      return IconRobotsData(null, ignoreSkipTag, usedIconRobots)
    }

    val parent = dir.parent ?: return IconRobotsData(null, ignoreSkipTag, usedIconRobots)
    return upToProjectHome(parent).fork(parent, projectHome)
  }

  private fun downToRoot(root: Path, file: Path, isDirectory: Boolean, common: Path?, robotData: IconRobotsData, level: Int): Path? {
    if (robotData.isSkipped(file) || robotData.mergeToRoot) {
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

  private fun isBlacklistedTopDirectory(name: String): Boolean =
    name == "META-INF" || name == "intentionDescriptions" || name == "inspectionDescriptions" || name == "fileTemplates"

  private fun collectMappingFile(rootDir: Path) {
    if (mappingFile == null) {
      try {
        val mappings = rootDir.listDirectoryEntries("*Mapping*.json")
        if (mappings.size == 1) {
          mappings.isNotEmpty()
          mappingFile = mappings[0]
        }
      } catch (e: NoSuchFileException) {
        println("Tried to access directory: $rootDir for a mapping file, but the directory did not exist")
      }

    }
  }

  fun mergeImages(images: Collection<ImageInfo>, module: JpsModule): Pair<Collection<ImageInfo>, Map<String, String>> {
    val mappings = getMappings()

    if (mappings.isEmpty() && mergeRoots.isNotEmpty()) {
      println("*** Module: ${module.name} contains rule for merge new icons but icon mapping file not found ***")
    }
    else if (mappings.isNotEmpty() && mergeRoots.isEmpty()) {
      println("*** Module: ${module.name} contains icon mapping file but rule for merge new icons not found ***")
    }

    if (mergeRoots.isNotEmpty()) {
      val allImages = ArrayList<ImageInfo>()

      mainLoop@for (image in images) {
        val root = mergeRoots.find { image.id.startsWith("$it/") }
        if (root == null) {
          allImages.add(image)
        }
        else {
          val addElement = mergeAdd[root].orEmpty().find { image.sourceCodeParameterName == if (it.startsWith("/")) it.substring(1) else it }
          val addNew = addElement != null
          if (!addNew) {
            for (entry in mappings.entries) {
              if (image.sourceCodeParameterName == entry.value && images.find { it.sourceCodeParameterName == entry.key } != null) {
                continue@mainLoop
              }
            }
          }
          val equalOldName = image.id.substring(root.length)
          val equalOldImage = images.find { it.id == equalOldName }
          if (equalOldImage != null) {
            mappings[equalOldImage.sourceCodeParameterName] = image.sourceCodeParameterName
            if (!addNew) {
              continue@mainLoop
            }
          }

          if (addElement != null && addElement.startsWith("/")) {
            allImages.add(image)
          }
          else {
            allImages.add(image.trimPrefix(root))
          }
        }
      }

      return allImages to mappings
    }
    return images to mappings
  }

  private fun getMappings(): HashMap<String, String> {
    val mappings = HashMap<String, String>()
    val file = mappingFile

    if (file != null && Files.exists(file)) {
      val json = GsonBuilder().create().fromJson(Files.readString(file), JsonObject::class.java)

      for (key in json.keySet()) {
        val element = json.get(key)
        if (element.isJsonPrimitive) {
          mappings[element.asString] = key
        }
        else if (element.isJsonArray) {
          for (childElement in element.asJsonArray) {
            mappings[childElement.asString] = key
          }
        }
        else {
          parseMappings(element.asJsonObject, mappings, key)
        }
      }
    }
    return mappings
  }

  private fun parseMappings(json: JsonObject, mappings: HashMap<String, String>, path: String) {
    for (key in json.keySet()) {
      val subPath = "$path/$key"
      val element = json.get(key)

      if (element.isJsonObject) {
        parseMappings(element.asJsonObject, mappings, subPath)
      }
      else if (element.isJsonArray) {
        for (childElement in element.asJsonArray) {
          mappings[childElement.asString] = subPath
        }
      }
      else {
        mappings[element.asString] = subPath
      }
    }
  }
}

private data class DeprecatedEntry(val matcher: Pattern, val data: DeprecationData)
private data class OwnDeprecatedIcon(val relativeFile: String, val data: DeprecationData)

internal class IconRobotsData(
  private val parent: IconRobotsData? = null,
  private val ignoreSkipTag: Boolean,
  private val usedIconRobots: MutableSet<Path>?
) {
  private val skip = ArrayList<Pattern>()
  private val used = ArrayList<Pattern>()
  private val deprecated = ArrayList<DeprecatedEntry>()
  private val skipSync = ArrayList<Pattern>()
  private val forceSync = ArrayList<Pattern>()

  var mergeToRoot = false
  var mergeAdd = ArrayList<String>()

  private val ownDeprecatedIcons = ArrayList<OwnDeprecatedIcon>()

  fun getImageFlags(file: Path): ImageFlags {
    val isUsed = matches(file, used)
    val deprecationData = findDeprecatedData(file)
    val flags = ImageFlags(isUsed, deprecationData)
    val parentFlags = parent?.getImageFlags(file) ?: return flags
    return mergeImageFlags(flags, parentFlags, file)
  }

  fun getImageSyncFlags(file: Path): ImageSyncFlags =
    ImageSyncFlags(skipSync = matches(file, skipSync), forceSync = matches(file, forceSync))

  fun getOwnDeprecatedIcons(): List<Pair<String, ImageFlags>> =
    ownDeprecatedIcons.map { Pair(it.relativeFile, ImageFlags(used = false, deprecation = it.data)) }

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

    usedIconRobots?.add(robots)

    val answer = IconRobotsData(this, ignoreSkipTag, usedIconRobots)
    parse(robots,
          RobotFileHandler("skip:") { value -> answer.skip.add(compilePattern(dir, root, value)) },
          RobotFileHandler("used:") { value -> answer.used.add(compilePattern(dir, root, value)) },
          RobotFileHandler("mergeAdd:") { value -> answer.mergeAdd.add(value.trim()) },
          RobotFileHandler("merge") { answer.mergeToRoot = true },
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

  private fun computeReplacementReference(comment: String?): String? =
    // allow only the same class fields (IDEA-218345)
    comment?.substringAfter("use {@link #", "")?.substringBefore('}')?.trim()?.takeIf { it.isNotEmpty() }

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
    catch (_: Exception) {
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

private fun mergeImageFlags(flags1: ImageFlags, flags2: ImageFlags, comment: Path): ImageFlags =
  ImageFlags(used = flags1.used || flags2.used, deprecation = mergeDeprecations(flags1.deprecation, flags2.deprecation, comment))

private fun mergeDeprecations(data1: DeprecationData?, data2: DeprecationData?, comment: Path): DeprecationData? {
  if (data1 == null) {
    return data2
  }
  if (data2 == null || data1 == data2) {
    return data1
  }
  throw AssertionError("Different deprecation statements found for icon: $comment\n$data1\n$data2")
}

@Suppress("SSBasedInspection")
internal fun processChildren(path: Path, isParallel: Boolean = true, consumer: (Path) -> Unit) {
  path.listDirectoryEntries().stream()
    .apply { if (isParallel) parallel() }
    .forEach(consumer)
}
