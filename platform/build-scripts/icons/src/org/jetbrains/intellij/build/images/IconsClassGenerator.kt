// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.images

import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.icons.loadRasterImage
import com.intellij.ui.svg.getSvgDocumentSize
import com.intellij.util.LineSeparator
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.diff.Diff
import com.intellij.util.io.directoryStreamIfExists
import com.intellij.util.xml.dom.readXmlAsModel
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import org.jetbrains.intellij.build.images.sync.dotnet.DotnetIconClasses
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot
import org.jetbrains.jps.util.JpsPathUtil
import java.io.File
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import javax.xml.stream.XMLStreamException
import kotlin.io.path.exists

@JvmRecord
internal data class ModifiedClass(
  @JvmField val module: JpsModule,
  @JvmField val file: Path,
  @JvmField val result: CharSequence
)

@JvmRecord
internal data class IconClassInfo(
  @JvmField val packageName: String,
  @JvmField val className: String,
  @JvmField val outFile: Path,
  @JvmField val images: Collection<ImageInfo>,
  @JvmField val mappings: Map<String, String>? = null,
  @JvmField val isInternal: Boolean = false,
)

internal open class IconsClassGenerator(
  private val projectHome: Path,
  @JvmField val modules: List<JpsModule>,
  private val writeChangesToDisk: Boolean = true
) {
  private companion object {
    private const val ICON_MANAGER_CODE = "IconManager.getInstance()"

    // legacy ordering
    private val NAME_COMPARATOR: Comparator<String> = compareBy { it.lowercase(Locale.ENGLISH) + '.' }

    private val deprecatedIconFieldNameMap = CollectionFactory.createCharSequenceMap<String>(true).apply {
      this["RwAccess"] = "Rw_access"
      this["MenuOpen"] = "Menu_open"
      this["MenuCut"] = "Menu_cut"
      this["MenuPaste"] = "Menu_paste"
      @Suppress("SpellCheckingInspection")
      this["MenuSaveall"] = "Menu_saveall"
      this["PhpIcon"] = "Php_icon"
      this["Emulator02"] = "Emulator2"
    }

    private val commentRegExp = Regex("(?s)<!--.*?-->")
  }

  private val processedClasses = AtomicInteger()
  private val processedIcons = AtomicInteger()
  private val processedPhantom = AtomicInteger()
  private val modifiedClasses = CopyOnWriteArrayList<ModifiedClass>()
  private val obsoleteClasses = CopyOnWriteArrayList<Path>()

  private val openSourceRoot: Path? by lazy {
    val communityFolderMarkerFile = "intellij.idea.community.main.iml"
    if (projectHome.resolve(communityFolderMarkerFile).exists()) return@lazy projectHome
    /* we also have open source plugins under 'contrib' directory, but the license is specified for each plugin separately, so it won't be
       safe to use the generic Apache 2.0 license for all of them */
    val communityRoot = projectHome.resolve("community")
    if (communityRoot.resolve(communityFolderMarkerFile).exists()) return@lazy communityRoot
    return@lazy null
  }

  private val utilUi: JpsModule by lazy {
    modules.find { it.name == "intellij.platform.util.ui" } ?: error("Can't load module 'util'")
  }

  internal open fun getIconClassInfo(module: JpsModule, moduleConfig: IntellijIconClassGeneratorModuleConfig?): List<IconClassInfo> {
    when (module.name) {
      "intellij.platform.icons" -> {
        val packageName = "com.intellij.icons"
        val className = "AllIcons"

        val dir = utilUi.getSourceRoots(JavaSourceRootType.SOURCE).first().path.toAbsolutePath().resolve("com/intellij/icons")
        val outFile = dir.resolve("$className.java")

        val imageCollector = ImageCollector(projectHome = projectHome, moduleConfig = moduleConfig)
        val images = imageCollector.collect(module = module, includePhantom = true)
        imageCollector.printUsedIconRobots()

        val (allImages, mappings) = imageCollector.mergeImages(images, module)
        return listOf(IconClassInfo(packageName = packageName, className = className, outFile = outFile, images = allImages, mappings = mappings))
      }
      "intellij.android.artwork" -> {
        val packageName = "icons"

        val sourceRoot = module.getSourceRoots(JavaSourceRootType.SOURCE).single().file.absolutePath
        val resourceRoot = module.getSourceRoots(JavaResourceRootType.RESOURCE).single()
        // avoid a merge conflict - do not transform StudioIcons to a nested class of AndroidIcons
        var imageCollector = ImageCollector(projectHome, moduleConfig = moduleConfig)
        val imagesA = imageCollector.collectSubDir(resourceRoot, "icons", includePhantom = true)
        imageCollector.printUsedIconRobots()

        imageCollector = ImageCollector(projectHome, moduleConfig = moduleConfig)
        val imagesS = imageCollector.collectSubDir(resourceRoot, "studio/icons", includePhantom = true)
        imageCollector.printUsedIconRobots()
        imageCollector = ImageCollector(projectHome, moduleConfig = moduleConfig)
        val imagesI = imageCollector.collectSubDir(resourceRoot, "studio/illustrations", includePhantom = true)
        imageCollector.printUsedIconRobots()

        val (studioImages, studioMappings) = imageCollector.mergeImages(imagesS, module)

        return listOf(
          IconClassInfo(packageName, "AndroidIcons", Path.of(sourceRoot, "icons/AndroidIcons.java"), imagesA),
          IconClassInfo(packageName, "StudioIcons", Path.of(sourceRoot, "icons/StudioIcons.java"), studioImages, studioMappings),
          IconClassInfo(packageName, "StudioIllustrations", Path.of(sourceRoot, "icons/StudioIllustrations.java"), imagesI),
        )
      }
      else -> {
        val imageCollector = ImageCollector(projectHome, moduleConfig = moduleConfig)
        val images = imageCollector.collect(module, includePhantom = true)
        imageCollector.printUsedIconRobots()

        val sourceRoots = module.getSourceRoots(JavaSourceRootType.SOURCE).sortedByDescending { it.properties.isForGeneratedSources }
        val sourceRoot = sourceRoots.firstOrNull() ?: return emptyList()

        val possiblePackageNames = listOfNotNull(
          moduleConfig?.packageName,
          getPluginPackageIfPossible(module),
          "com.${module.name}.icons",
          "com.intellij.${module.name.removePrefix("intellij.platform.")}",
          "icons",
        )
        val existingIconsClass = findExistingIconsClass(sourceRoots, possiblePackageNames)
        val packageName = existingIconsClass?.packageName ?: possiblePackageNames.first()
        val targetRoot = findPackageDirectory(sourceRoot, packageName)

        if (existingIconsClass != null && !existingIconsClass.sourceRoot.properties.isForGeneratedSources
            && sourceRoot.properties.isForGeneratedSources && images.isNotEmpty()) {
          val oldFile = existingIconsClass.filePath
          println("deleting $oldFile from source root which isn't marked as 'generated', it'll be recreated under the proper root")
          Files.delete(oldFile)
        }

        val className = moduleConfig?.className
                        ?: existingIconsClass?.className
                        ?: "${directoryName(module).removeSuffix("Icons")}Icons"
        val outFile = targetRoot.resolve("$className.java")
        val (allImages, mappings) = imageCollector.mergeImages(images, module)
        val info = IconClassInfo(packageName = packageName, className = className, outFile = outFile, images = allImages, mappings = mappings, isInternal = className.contains("Impl"))
        return transformIconClassInfo(info, module)
      }
    }
  }

  private fun transformIconClassInfo(info: IconClassInfo, module: JpsModule): List<IconClassInfo> =
    when (module.name) {
      "intellij.rider.icons" -> DotnetIconClasses.transformIconClassInfo(info)
      else -> listOf(info)
    }

  @JvmRecord
  private data class ExistingIconsClass(
    val sourceRoot: JpsTypedModuleSourceRoot<JavaSourceRootProperties>,
    val filePath: Path,
    val packageName: String,
    val className: String
  )

  private fun findExistingIconsClass(
    sourceRoots: List<JpsTypedModuleSourceRoot<JavaSourceRootProperties>>,
    possiblePackageNames: List<String>
  ): ExistingIconsClass? {
    for (sourceRoot in sourceRoots) {
      for (packageName in possiblePackageNames) {
        val directory = findPackageDirectory(sourceRoot, packageName)
        val className = findIconClass(directory)
        if (className != null) {
          return ExistingIconsClass(sourceRoot, directory.resolve("$className.java"), packageName, className)
        }
      }
    }
    return null
  }

  private fun findPackageDirectory(root: JpsTypedModuleSourceRoot<JavaSourceRootProperties>, packageName: String): Path {
    val packagePrefix = root.properties.packagePrefix
    if (packagePrefix == packageName) return root.path
    val relativePackage = packageName.removePrefix("$packagePrefix.")
    return root.path.resolve(relativePackage.replace('.', File.separatorChar))
  }

  fun processModule(module: JpsModule, moduleConfig: IntellijIconClassGeneratorModuleConfig?) {
    val classCode = StringBuilder()
    for (iconClassInfo in getIconClassInfo(module, moduleConfig)) {
      val outFile = iconClassInfo.outFile
      val oldText = try {
        Files.readString(outFile)
      }
      catch (_: NoSuchFileException) {
        null
      }

      classCode.setLength(0)
      val newText = writeClass(copyrightComment = getCopyrightComment(oldText, module), info = iconClassInfo, result = classCode)
      if (newText.isNullOrEmpty()) {
        if (Files.exists(outFile)) {
          obsoleteClasses.add(outFile)
        }
        continue
      }

      processedClasses.incrementAndGet()

      val newLines = newText.lines()
      val oldLines = oldText?.lines() ?: emptyList()
      if (oldLines == newLines) {
        continue
      }

      if (writeChangesToDisk) {
        val separator = getSeparators(oldText)
        Files.createDirectories(outFile.parent)
        Files.writeString(outFile, newLines.joinToString(separator = separator.separatorString))
        println("Updated class: ${outFile.fileName}")
      }
      else {
        val diff = Diff.linesDiff(oldLines.toTypedArray(), newLines.toTypedArray()) ?: ""
        modifiedClasses.add(ModifiedClass(module, outFile, diff))
      }
    }
  }

  fun printStats() {
    println(
      "\nGenerated classes: ${processedClasses.get()}. " +
      "Processed icons: ${processedIcons.get()}. " +
      "Phantom icons: ${processedPhantom.get()}"
    )
    if (obsoleteClasses.isNotEmpty()) {
      println("\nObsolete classes:")
      println(obsoleteClasses.joinToString("\n"))
      println("\nObsolete class is an icon class that cannot be found anymore. Possible reasons:")
      println(
        "1. Icons not located under resources root." +
        "\n   Solution - move icons to resources root or fix existing root type (must be \"resources\")"
      )
      println("2. Icons were removed but not class.\n   Solution - remove class.")
      println(
        "3. Icons located under resources root named \"compatibilityResources\". \"compatibilityResources\" for icons that not used externally as icon class fields, " +
        "but maybe referenced directly by path.\n   Solution - remove class or move icons to another resources root"
      )
    }
  }

  fun getModifiedClasses(): List<ModifiedClass> = modifiedClasses

  private fun findIconClass(dir: Path): String? {
    dir.directoryStreamIfExists { stream ->
      for (it in stream) {
        val name = it.fileName.toString()
        if (name.endsWith("Icons.java")) {
          return name.substring(0, name.length - ".java".length)
        }
      }
    }
    return null
  }

  private fun getCopyrightComment(text: String?, module: JpsModule): String {
    if (text == null) {
      if (openSourceRoot == null || module.contentRootsList.urls.any { !Path.of(JpsPathUtil.urlToOsPath(it)).startsWith(openSourceRoot!!) }) {
        return ""
      }

      return "// Copyright 2000-${LocalDate.now().year} JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.\n"
    }
    val i = text.indexOf("package ")
    if (i == -1) {
      return ""
    }
    val comment = text.substring(0, i)
    return if (comment.startsWith("//") || comment.trimEnd().endsWith("*/")) comment else ""
  }

  private fun getSeparators(text: String?): LineSeparator =
    text?.let { StringUtil.detectSeparators(text) } ?: LineSeparator.LF

  private fun writeClass(copyrightComment: String, info: IconClassInfo, result: StringBuilder): CharSequence? {
    val images = info.images
    if (images.isEmpty()) {
      return null
    }

    result.append(copyrightComment)
    append(result, "package ${info.packageName};\n", 0)
    append(result, "import com.intellij.ui.IconManager;", 0)
    append(result, "import org.jetbrains.annotations.NotNull;", 0)
    result.append('\n')
    append(result, "import javax.swing.*;", 0)
    result.append('\n')
    if (images.any(ImageInfo::scheduledForRemoval)) {
      append(result, "import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;", 0)
      result.append('\n')
    }

    // `IconsGeneratedSourcesFilter` depends on the following comment;
    // if you are going to change it, please do correspond changes in `IconsGeneratedSourcesFilter` and generated files as well.
    result.append("/**\n")
    result.append(" * NOTE THIS FILE IS AUTO-GENERATED\n")
    result.append(" * DO NOT EDIT IT BY HAND, run \"Generate icon classes\" configuration instead\n")
    result.append(" */\n")
    if (info.isInternal) {
      result.append("@org.jetbrains.annotations.ApiStatus.Internal\n")
    }

    result.append("public")
    // backward compatibility
    if (info.className != "AllIcons") {
      result.append(" final")
    }
    result.append(" class ").append(info.className).append(" {\n")

    if (info.mappings.isNullOrEmpty() || info.images.find { !info.mappings.containsKey(it.sourceCodeParameterName) } != null) {
      append(result, "private static @NotNull Icon load(@NotNull String path, int cacheKey, int flags) {", 1)
      append(result, "return $ICON_MANAGER_CODE.loadRasterizedIcon(path, ${info.className}.class.getClassLoader(), cacheKey, flags);", 2)
      append(result, "}", 1)
    }

    if (!info.mappings.isNullOrEmpty() && info.images.find { info.mappings.containsKey(it.sourceCodeParameterName) } != null) {
      append(result, "private static @NotNull Icon load(@NotNull String expUIPath, @NotNull String path, int cacheKey, int flags) {", 1)
      append(result, "return $ICON_MANAGER_CODE.loadRasterizedIcon(path, expUIPath, ${info.className}.class.getClassLoader(), cacheKey, flags);", 2)
      append(result, "}", 1)
    }

    val customExternalLoad = images.any { it.deprecation?.replacementContextClazz != null }
    if (customExternalLoad) {
      result.append('\n')
      append(result, "private static @NotNull Icon load(@NotNull String path, @NotNull Class<?> clazz) {", 1)
      append(result, "return $ICON_MANAGER_CODE.getIcon(path, clazz);", 2)
      append(result, "}", 1)
    }

    val inners = StringBuilder()
    processIcons(images, info.mappings, inners, depth = 0)
    if (inners.isEmpty()) {
      return null
    }

    result.append(inners)
    append(result, "}", 0)
    return result
  }

  private fun processIcons(images: Collection<ImageInfo>, mappings: Map<String, String>?, result: StringBuilder, depth: Int) {
    val level = depth + 1

    val nodeMap = HashMap<String, MutableList<ImageInfo>>(images.size / 2)
    val leafMap = HashMap<String, ImageInfo>(images.size)
    for (imageInfo in images) {
      val imageId = getImageId(imageInfo, depth)
      val index = imageId.indexOf('/')
      if (index >= 0) {
        nodeMap.computeIfAbsent(imageId.substring(0, index)) { mutableListOf() }.add(imageInfo)
      }
      else {
        leafMap[imageId] = imageInfo
      }
    }

    fun getWeight(key: String): Int {
      val image = leafMap[key] ?: return 0
      return if (image.deprecated) 1 else 0
    }

    val sortedKeys = ArrayList<String>(nodeMap.size + leafMap.size)
    sortedKeys.addAll(nodeMap.keys)
    sortedKeys.addAll(leafMap.keys)
    sortedKeys.sortWith(NAME_COMPARATOR)
    sortedKeys.sortWith(Comparator { o1, o2 -> getWeight(o1) - getWeight(o2) })

    var innerClassWasBefore = false
    val hasher = IconHasher(sortedKeys.size)
    for (key in sortedKeys) {
      val group = nodeMap[key]
      if (group != null) {
        val oldLength = result.length
        val className = className(key)
        if (isInlineClass(className)) {
          processIcons(group, mappings, result, depth + 1)
        }
        else {
          // if first in block, do not add yet another extra newline
          if (result.length < 2 || result[result.length - 1] != '\n' || result[result.length - 2] != '{') {
            result.append('\n')
          }
          append(result, "public static final class $className {", level)
          val lengthBeforeBody = result.length
          processIcons(group, mappings, result, depth + 1)
          if (lengthBeforeBody == result.length) {
            result.setLength(oldLength)
          }
          else {
            append(result, "}", level)
            innerClassWasBefore = true
          }
        }
      }

      val image = leafMap[key]
      if (image != null) {
        if (innerClassWasBefore) {
          innerClassWasBefore = false
          result.append('\n')
        }
        appendImage(image, mappings, result, level, hasher)
      }
    }
  }

  protected open fun isInlineClass(name: CharSequence): Boolean =
    DotnetIconClasses.isInlineClass(name)

  private fun appendImage(image: ImageInfo, mappings: Map<String, String>?, result: StringBuilder, level: Int, hasher: IconHasher) {
    val file = image.basicFile ?: return
    if (!image.phantom && !isIcon(file)) {
      return
    }

    processedIcons.incrementAndGet()
    if (image.phantom) {
      processedPhantom.incrementAndGet()
    }

    if (image.used || image.deprecated) {
      val deprecationComment = image.deprecation?.comment
      if (deprecationComment != null) {
        // if first in block, do not add yet another extra newline
        if (result[result.length - 1] != '\n' || result[result.length - 2] != '\n') {
          result.append('\n')
        }
        append(result, "/** @deprecated $deprecationComment */", level)
      }
      append(result, "@SuppressWarnings(\"unused\")", level)
    }
    if (image.deprecated) {
      append(result, "@Deprecated", level)
    }
    if (image.scheduledForRemoval) {
      append(result, "@ScheduledForRemoval", level)
    }

    // backward compatibility - use a streaming camel case for StudioIcons
    val iconName = generateIconFieldName(file)
    val deprecation = image.deprecation

    if (deprecation?.replacementContextClazz != null) {
      append(result, "public static final @NotNull Icon $iconName = " +
                     "load(\"${deprecation.replacement}\", ${deprecation.replacementContextClazz}.class);", level)
      return
    }
    else if (deprecation?.replacementReference != null) {
      append(result, "public static final @NotNull Icon $iconName = ${deprecation.replacementReference};", level)
      return
    }

    val rootDir = Path.of(JpsPathUtil.urlToPath(image.sourceRoot.url))
    val imageFile: Path
    if (deprecation?.replacement == null) {
      imageFile = file
    }
    else {
      imageFile = rootDir.resolve(deprecation.replacement.removePrefix("/").removePrefix(File.separator))
      assert(isIcon(imageFile)) {
        "Invalid deprecation replacement '${deprecation.replacement}': $imageFile is not an icon"
      }
    }

    var javaDoc: String
    var key: Int
    try {
      if (file.toString().endsWith(".svg")) {
        // don't mask any exception for svg file
        val data = loadAndNormalizeSvgFile(imageFile).toByteArray()
        val size = getSvgDocumentSize(data = data)
        key = hasher.hash(data, file.fileName.toString())
        javaDoc = "/** ${size.width.toInt()}x${size.height.toInt()} */ "
      }
      else {
        val loadedImage = Files.newInputStream(file).use { loadRasterImage(it) }
        key = 0
        javaDoc = "/** ${loadedImage.width}x${loadedImage.height} */ "
      }
    }
    catch (e: NoSuchFileException) {
      if (!image.phantom) {
        throw e
      }

      javaDoc = ""
      key = 0
    }

    val imagePathCodeParameter = image.sourceCodeParameterName
    append(result, "${javaDoc}public static final @NotNull Icon $iconName = " +
                   "load(${appendExpUIPath(imagePathCodeParameter, mappings)}\"$imagePathCodeParameter\", $key, ${image.getFlags()});", level)

    val oldName = deprecatedIconFieldNameMap[iconName]
    if (oldName != null) {
      append(result, "${javaDoc}public static final @Deprecated @NotNull Icon $oldName = $iconName;", level)
    }
  }

  private fun appendExpUIPath(imagePathCodeParameter: String, mappings: Map<String, String>?): String {
    if (mappings != null) {
      val expUIPath = mappings[imagePathCodeParameter]
      if (expUIPath != null) {
        return "\"$expUIPath\", "
      }
    }
    return ""
  }

  private fun append(result: StringBuilder, text: String, level: Int) {
    if (text.isNotBlank()) {
      repeat(level) {
        result.append(' ').append(' ')
      }
    }
    result.append(text).append('\n')
  }

  private fun generateIconFieldName(file: Path): CharSequence {
    val imageFileName = file.fileName.toString()
    when {
      file.startsWith("$androidIcons/icons") -> {
        return toCamelCaseJavaIdentifier(imageFileName, imageFileName.lastIndexOf('.'))
      }
      file.startsWith("$androidIcons") -> {
        return toStreamingSnakeCaseJavaIdentifier(imageFileName, imageFileName.lastIndexOf('.'))
      }
      else -> {
        val id = if ((imageFileName.length - 4) == 2) {
          imageFileName.uppercase(Locale.ENGLISH)
        }
        else {
          imageFileName.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString()
          }
        }
        return toJavaIdentifier(id = id, endIndex = imageFileName.lastIndexOf('.'))
      }
    }
  }

  private fun getImageId(image: ImageInfo, depth: Int): String {
    val path = image.id.removePrefix("/").split('/')
    if (path.size < depth) {
      throw IllegalArgumentException("Can't get image id - ${image.id}, $depth")
    }
    return path.drop(depth).joinToString("/")
  }

  private fun directoryName(module: JpsModule): CharSequence =
    directoryNameFromConfig(module) ?: className(module.name)

  private fun directoryNameFromConfig(module: JpsModule): String? {
    val rootUrl = module.contentRootsList.urls.firstOrNull() ?: return null
    val rootDir = Paths.get(JpsPathUtil.urlToPath(rootUrl))
    val file = rootDir.resolve(ROBOTS_FILE_NAME)
    val prefix = "name:"
    try {
      Files.lines(file).use { lines ->
        for (line in lines) {
          if (line.startsWith(prefix)) {
            val name = line.substring(prefix.length).trim()
            if (name.isNotEmpty()) {
              return name
            }
          }
        }
      }
    }
    catch (_: NoSuchFileException) { }
    return null
  }

  private fun className(name: String): CharSequence {
    val result = StringBuilder(name.length)
    name.removePrefix("intellij.vcs.").removePrefix("intellij.").split('-', '_', '.').forEach {
      result.append(capitalize(it))
    }
    return toJavaIdentifier(result, result.length)
  }

  private fun toJavaIdentifier(id: CharSequence, endIndex: Int): CharSequence {
    var sb: StringBuilder? = null
    var index = 0
    while (index < endIndex) {
      val c = id[index]
      if (if (index == 0) Character.isJavaIdentifierStart(c) else Character.isJavaIdentifierPart(c)) {
        sb?.append(c)
      }
      else {
        if (sb == null) {
          sb = StringBuilder(endIndex)
          sb.append(id, 0, index)
        }
        if (c == '-') {
          index++
          if (index == endIndex) {
            break
          }
          sb.append(id[index].uppercaseChar())
        }
        else {
          sb.append('_')
        }
      }

      index++
    }
    return sb ?: id.subSequence(0, endIndex)
  }

  private fun toStreamingSnakeCaseJavaIdentifier(id: String, endIndex: Int): CharSequence {
    val sb = StringBuilder(endIndex)
    var index = 0
    while (index < endIndex) {
      val c = id[index]
      if (if (index == 0) Character.isJavaIdentifierStart(c) else Character.isJavaIdentifierPart(c)) {
        sb.append(c.uppercaseChar())
      }
      else {
        sb.append('_')
      }
      index++
    }
    return sb
  }

  private fun toCamelCaseJavaIdentifier(id: String, endIndex: Int): CharSequence {
    val sb = StringBuilder(endIndex)
    var index = 0
    var upperCase = true
    while (index < endIndex) {
      val c = id[index]
      if (c == '_' || c == '-') {
        upperCase = true
      }
      else if (if (index == 0) Character.isJavaIdentifierStart(c) else Character.isJavaIdentifierPart(c)) {
        if (upperCase) {
          sb.append(c.uppercaseChar())
          upperCase = false
        }
        else {
          sb.append(c)
        }
      }
      else {
        sb.append('_')
      }
      index++
    }
    return sb
  }

  private fun capitalize(name: String): String =
    if (name.length == 2) name.uppercase(Locale.ENGLISH)
    else name.replaceFirstChar {
      if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    }

  // normalizing line separators to '\n' (required because a cache key is based on content)
  private fun loadAndNormalizeSvgFile(svgFile: Path): String {
    val builder = StringBuilder()
    Files.lines(svgFile).use { lines ->
      for (line in lines) {
        for (start in line.indices) {
          if (line[start].isWhitespace()) {
            continue
          }

          var end = line.length
          for (j in (line.length - 1) downTo (start + 1)) {
            if (!line[j].isWhitespace()) {
              end = j + 1
              break
            }
          }

          builder.append(line, start, end)
          // if tag is not closed, space must be added to ensure that code on the next line is separated from the previous line of code
          if (builder[end - 1] != '>') {
            builder.append(' ')
          }
          break
        }
      }
    }
    return commentRegExp.replace(builder, "")
  }

  private fun getPluginPackageIfPossible(module: JpsModule): String? {
    for (resourceRoot in module.getSourceRoots(JavaResourceRootType.RESOURCE)) {
      val root = Path.of(JpsPathUtil.urlToPath(resourceRoot.url))
      var pluginXml = root.resolve("META-INF/plugin.xml")
      if (!Files.exists(pluginXml)) {
        // ok, any xml file
        try {
          pluginXml = Files.newDirectoryStream(root).use { files -> files.find { it.toString().endsWith(".xml") } } ?: break
        }
        catch (e: NoSuchFileException) {
          println("Directory attempted to be used but did not exist ${e.message}")
        }
      }

      try {
        return readXmlAsModel(Files.newInputStream(pluginXml)).getAttributeValue("package")
      }
      catch (_: NoSuchFileException) { }
      catch (_: XMLStreamException) { /* ignore invalid XML */ }
    }
    return null
  }
}

private class IconHasher(expectedSize: Int) {
  private val hashStream = Hashing.komihash5_0().hashStream()
  private val uniqueGuard = IntOpenHashSet(expectedSize)

  // grid-layout.svg duplicates grid-view.svg, but grid-layout_dark.svg differs from grid-view_dark.svg
  // so, add filename to image id to support such a scenario
  fun hash(data: ByteArray, fileName: String): Int {
    val hash = hashStream.reset().putByteArray(data).putString(fileName).asInt
    check(uniqueGuard.add(hash)) { "uniqueGuard check failed: $fileName | $hash" }
    return hash
  }
}
