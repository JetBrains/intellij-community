// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")
package org.jetbrains.intellij.build.images

import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.svg.SvgTranscoder
import com.intellij.ui.svg.createSvgDocument
import com.intellij.util.LineSeparator
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.diff.Diff
import com.intellij.util.io.directoryStreamIfExists
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.lang.Murmur3_32Hash
import com.intellij.util.xml.dom.readXmlAsModel
import org.jetbrains.jps.model.JpsSimpleElement
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.*
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import javax.xml.stream.XMLStreamException

internal data class ModifiedClass(val module: JpsModule, val file: Path, val result: CharSequence)

// legacy ordering
private val NAME_COMPARATOR: Comparator<String> = compareBy { it.lowercase(Locale.ENGLISH) + '.' }

internal data class IconClassInfo(val customLoad: Boolean,
                                  val packageName: String,
                                  val className: String,
                                  val outFile: Path,
                                  val images: Collection<ImageInfo>)

internal open class IconsClassGenerator(private val projectHome: Path,
                                        val modules: List<JpsModule>,
                                        private val writeChangesToDisk: Boolean = true) {
  companion object {
    private val deprecatedIconFieldNameMap = CollectionFactory.createCharSequenceMap<String>(true)

    init {
      deprecatedIconFieldNameMap.put("RwAccess", "Rw_access")
      deprecatedIconFieldNameMap.put("MenuOpen", "Menu_open")
      deprecatedIconFieldNameMap.put("MenuCut", "Menu_cut")
      deprecatedIconFieldNameMap.put("MenuPaste", "Menu_paste")
      @Suppress("SpellCheckingInspection")
      deprecatedIconFieldNameMap.put("MenuSaveall", "Menu_saveall")
      deprecatedIconFieldNameMap.put("PhpIcon", "Php_icon")
      deprecatedIconFieldNameMap.put("Emulator02", "Emulator2")
    }
  }

  private val processedClasses = AtomicInteger()
  private val processedIcons = AtomicInteger()
  private val processedPhantom = AtomicInteger()
  private val modifiedClasses = ContainerUtil.createConcurrentList<ModifiedClass>()
  private val obsoleteClasses = ContainerUtil.createConcurrentList<Path>()

  private val util: JpsModule by lazy {
    modules.find { it.name == "intellij.platform.util" } ?: error("Can't load module 'util'")
  }

  internal open fun getIconClassInfo(module: JpsModule, moduleConfig: IntellijIconClassGeneratorModuleConfig?): List<IconClassInfo> {
    val packageName: String
    val className: CharSequence
    val outFile: Path
    when (module.name) {
      "intellij.platform.icons" -> {
        packageName = "com.intellij.icons"
        className = "AllIcons"

        val dir = util.getSourceRoots(JavaSourceRootType.SOURCE).first().file.absolutePath + "/com/intellij/icons"
        outFile = Path.of(dir, "$className.java")
      }
      "intellij.android.artwork" -> {
        packageName = "icons"

        val sourceRoot = module.getSourceRoots(JavaSourceRootType.SOURCE).single().file.absolutePath
        val resourceRoot = module.getSourceRoots(JavaResourceRootType.RESOURCE).single()
        // avoid merge conflicts - do not transform StudioIcons to nested class of AndroidIcons
        var imageCollector = ImageCollector(projectHome, moduleConfig = moduleConfig)
        val imagesA = imageCollector.collectSubDir(resourceRoot, "icons", includePhantom = true)
        imageCollector.printUsedIconRobots()

        imageCollector = ImageCollector(projectHome, moduleConfig = moduleConfig)
        val imagesS = imageCollector.collectSubDir(resourceRoot, "studio/icons", includePhantom = true)
        imageCollector.printUsedIconRobots()

        return listOf(
          IconClassInfo(true, packageName, "AndroidIcons", Path.of(sourceRoot, "icons", "AndroidIcons.java"), imagesA),
          IconClassInfo(true, packageName, "StudioIcons", Path.of(sourceRoot, "icons", "StudioIcons.java"), imagesS),
        )
      }
      else -> {
        packageName = moduleConfig?.packageName ?: getPluginPackageIfPossible(module) ?: "icons"

        val firstRoot = module.getSourceRoots(JavaSourceRootType.SOURCE).firstOrNull() ?: return emptyList()

        val firstRootDir = Path.of(JpsPathUtil.urlToPath(firstRoot.url)).resolve("icons")
        var oldClassName: String?
        // this is added to remove unneeded empty directories created by previous version of this script
        if (Files.isDirectory(firstRootDir)) {
          try {
            Files.delete(firstRootDir)
            println("deleting empty directory $firstRootDir")
          }
          catch (ignore: DirectoryNotEmptyException) {
          }

          oldClassName = findIconClass(firstRootDir)
        }
        else {
          oldClassName = null
        }

        val generatedRoot = module.getSourceRoots(JavaSourceRootType.SOURCE).find { it.properties.isForGeneratedSources }
        val targetRoot = (generatedRoot ?: firstRoot).file.toPath().resolve(packageName.replace('.', File.separatorChar))

        if (generatedRoot != null && oldClassName != null && firstRoot != generatedRoot) {
          val oldFile = firstRootDir.resolve("$oldClassName.java")
          println("deleting $oldFile from source root which isn't marked as 'generated'")
          Files.delete(oldFile)
        }

        if (moduleConfig?.className == null) {
          if (oldClassName == null) {
            try {
              oldClassName = findIconClass(targetRoot)
            }
            catch (ignored: NoSuchFileException) {
            }
          }

          className = oldClassName ?: directoryName(module).let {
            if (it.endsWith("Icons")) it else "${it}Icons"
          }
        }
        else {
          className = moduleConfig.className
        }
        outFile = targetRoot.resolve("$className.java")
      }
    }

    val imageCollector = ImageCollector(projectHome, moduleConfig = moduleConfig)
    val images = imageCollector.collect(module, includePhantom = true)
    imageCollector.printUsedIconRobots()
    return listOf(IconClassInfo(true, packageName, className.toString(), outFile, images))
  }

  fun processModule(module: JpsModule, moduleConfig: IntellijIconClassGeneratorModuleConfig?) {
    val classCode = StringBuilder()
    for (iconsClassInfo in getIconClassInfo(module, moduleConfig)) {
      val outFile = iconsClassInfo.outFile
      val oldText = try {
        Files.readString(outFile)
      }
      catch (ignored: NoSuchFileException) {
        null
      }

      classCode.setLength(0)
      val newText = writeClass(getCopyrightComment(oldText), iconsClassInfo, classCode)
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
      println("\nObsolete class it is class for icons that cannot be found anymore. Possible reasons:")
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

  private fun getCopyrightComment(text: String?): String {
    if (text == null) {
      return ""
    }
    val i = text.indexOf("package ")
    if (i == -1) {
      return ""
    }
    val comment = text.substring(0, i)
    return if (comment.trim().endsWith("*/") || comment.trim().startsWith("//")) comment else ""
  }

  private fun getSeparators(text: String?): LineSeparator {
    return StringUtil.detectSeparators(text ?: return LineSeparator.LF) ?: LineSeparator.LF
  }

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

    // IconsGeneratedSourcesFilter depends on following comment, if you are going to change the text
    // please do correspond changes in IconsGeneratedSourcesFilter as well
    result.append("/**\n")
    result.append(" * NOTE THIS FILE IS AUTO-GENERATED\n")
    result.append(" * DO NOT EDIT IT BY HAND, run \"Generate icon classes\" configuration instead\n")
    result.append(" */\n")

    result.append("public")
    // backward compatibility
    if (info.className != "AllIcons") {
      result.append(" final")
    }
    result.append(" class ").append(info.className).append(" {\n")
    if (info.customLoad) {
      append(result, "private static @NotNull Icon load(@NotNull String path, int cacheKey, int flags) {", 1)
      append(result, "return $iconLoaderCode.loadRasterizedIcon(path, ${info.className}.class.getClassLoader(), cacheKey, flags);", 2)
      append(result, "}", 1)

      val customExternalLoad = images.any { it.deprecation?.replacementContextClazz != null }
      if (customExternalLoad) {
        result.append('\n')
        append(result, "private static @NotNull Icon load(@NotNull String path, @NotNull Class<?> clazz) {", 1)
        append(result, "return $iconLoaderCode.getIcon(path, clazz);", 2)
        append(result, "}", 1)
      }
    }

    val inners = StringBuilder()
    processIcons(images, inners, info.customLoad, 0)
    if (inners.isEmpty()) {
      return null
    }

    result.append(inners)
    append(result, "}", 0)
    return result
  }

  private fun processIcons(images: Collection<ImageInfo>, result: StringBuilder, customLoad: Boolean, depth: Int) {
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
        leafMap.put(imageId, imageInfo)
      }
    }

    fun getWeight(key: String): Int {
      val image = leafMap.get(key) ?: return 0
      return if (image.deprecated) 1 else 0
    }

    val sortedKeys = ArrayList<String>(nodeMap.size + leafMap.size)
    sortedKeys.addAll(nodeMap.keys)
    sortedKeys.addAll(leafMap.keys)
    sortedKeys.sortWith(NAME_COMPARATOR)
    sortedKeys.sortWith(Comparator { o1, o2 -> getWeight(o1) - getWeight(o2) })

    var innerClassWasBefore = false
    for (key in sortedKeys) {
      val group = nodeMap.get(key)
      if (group != null) {
        val oldLength = result.length
        val className = className(key)
        if (isInlineClass(className)) {
          processIcons(group, result, customLoad, depth + 1)
        }
        else {
          // if first in block, do not add yet another extra newline
          if (result.length < 2 || result.get(result.length - 1) != '\n' || result.get(result.length - 2) != '{') {
            result.append('\n')
          }
          append(result, "public static final class $className {", level)
          val lengthBeforeBody = result.length
          processIcons(group, result, customLoad, depth + 1)
          if (lengthBeforeBody == result.length) {
            result.setLength(oldLength)
          }
          else {
            append(result, "}", level)
            innerClassWasBefore = true
          }
        }
      }

      val image = leafMap.get(key)
      if (image != null) {
        if (innerClassWasBefore) {
          innerClassWasBefore = false
          result.append('\n')
        }
        appendImage(image, result, level, customLoad)
      }
    }
  }

  protected open fun isInlineClass(name: CharSequence) = false

  private fun appendImage(image: ImageInfo, result: StringBuilder, level: Int, customLoad: Boolean) {
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
        if (result.get(result.length - 1) != '\n' || result.get(result.length - 2) != '\n') {
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

    val sourceRoot = image.sourceRoot
    var rootPrefix = "/"
    if (sourceRoot.rootType == JavaSourceRootType.SOURCE) {
      @Suppress("UNCHECKED_CAST")
      val packagePrefix = (sourceRoot.properties as JpsSimpleElement<JavaSourceRootProperties>).data.packagePrefix
      if (packagePrefix.isNotEmpty()) {
        rootPrefix += packagePrefix.replace('.', '/') + "/"
      }
    }

    // backward compatibility - use streaming camel case for StudioIcons
    val iconName = generateIconFieldName(file)
    val deprecation = image.deprecation

    if (deprecation?.replacementContextClazz != null) {
      val method = if (customLoad) "load" else "$iconLoaderCode.getIcon"
      append(result, "public static final @NotNull Icon $iconName = " +
                     "$method(\"${deprecation.replacement}\", ${deprecation.replacementContextClazz}.class);", level)
      return
    }
    else if (deprecation?.replacementReference != null) {
      append(result, "public static final @NotNull Icon $iconName = ${deprecation.replacementReference};", level)
      return
    }

    val rootDir = Path.of(JpsPathUtil.urlToPath(sourceRoot.url))
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
      val loadedImage: BufferedImage
      if (file.toString().endsWith(".svg")) {
        // don't mask any exception for svg file
        val data = loadAndNormalizeSvgFile(imageFile).toByteArray()
        loadedImage = SvgTranscoder.createImage(1f, createSvgDocument(null, data), null)
        key = getImageKey(data, file.fileName.toString())
      }
      else {
        loadedImage = Files.newInputStream(file).buffered().use { ImageIO.read(it) }
        key = 0
      }

      javaDoc = "/** ${loadedImage.width}x${loadedImage.height} */ "
    }
    catch (e: NoSuchFileException) {
      if (!image.phantom) {
        throw e
      }

      javaDoc = ""
      key = 0
    }

    val method = if (customLoad) "load" else "$iconLoaderCode.getIcon"
    val relativePath = rootPrefix + rootDir.relativize(imageFile).systemIndependentPath
    assert(relativePath.startsWith("/"))
    append(result, "${javaDoc}public static final @NotNull Icon $iconName = " +
                   "$method(\"${relativePath.removePrefix("/")}\", $key, ${image.getFlags()});", level)

    val oldName = deprecatedIconFieldNameMap.get(iconName)
    if (oldName != null) {
      append(result, "${javaDoc}public static final @Deprecated @NotNull Icon $oldName = $iconName;", level)
    }
  }

  protected fun append(result: StringBuilder, text: String, level: Int) {
    if (text.isNotBlank()) {
      for (i in 0 until level) {
        result.append(' ').append(' ')
      }
    }
    result.append(text).append('\n')
  }
}

private fun generateIconFieldName(file: Path): CharSequence {
  val imageFileName = file.fileName.toString()
  val path = file.toString()
  when {
    path.contains("android/artwork/resources/studio") -> {
      return toStreamingSnakeCaseJavaIdentifier(imageFileName, imageFileName.lastIndexOf('.'))
    }
    path.contains("android/artwork/resources/icons") -> {
      return toCamelCaseJavaIdentifier(imageFileName, imageFileName.lastIndexOf('.'))
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
  val path = image.id.removePrefix("/").split("/")
  if (path.size < depth) {
    throw IllegalArgumentException("Can't get image id - ${image.id}, $depth")
  }
  return path.drop(depth).joinToString("/")
}

private fun directoryName(module: JpsModule): CharSequence {
  return directoryNameFromConfig(module) ?: className(module.name)
}

private fun directoryNameFromConfig(module: JpsModule): String? {
  val rootUrl = getFirstContentRootUrl(module) ?: return null
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
  catch (ignore: NoSuchFileException) {
  }
  return null
}

private fun getFirstContentRootUrl(module: JpsModule) = module.contentRootsList.urls.firstOrNull()

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
    if (c == '_' || c == '-')  {
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

private fun capitalize(name: String): String {
  return if (name.length == 2) name.uppercase(Locale.ENGLISH) else name.replaceFirstChar {
    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
  }
}

private const val iconLoaderCode = "IconManager.getInstance()"

// grid-layout.svg duplicates grid-view.svg, but grid-layout_dark.svg differs from grid-view_dark.svg
// so, add filename to image id to support such scenario
internal fun getImageKey(fileData: ByteArray, fileName: String): Int {
  val h = Murmur3_32Hash.Murmur3_32Hasher(0)
  h.putBytes(fileData, 0, fileData.size)
  h.putString(fileName)
  return h.hash()
}

// remove line separators to unify line separators (\n vs \r\n), trim lines
// normalization is required because cache key is based on content
internal fun loadAndNormalizeSvgFile(svgFile: Path): String {
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
        // if tag is not closed, space must be added to ensure that code on next line is separated from previous line of code
        if (builder[end - 1] != '>') {
          builder.append(' ')
        }
        break
      }
    }
  }
  return builder.toString()
}

private fun getPluginPackageIfPossible(module: JpsModule): String? {
  for (resourceRoot in module.getSourceRoots(JavaResourceRootType.RESOURCE)) {
    val root = Path.of(JpsPathUtil.urlToPath(resourceRoot.url))
    var pluginXml = root.resolve("META-INF/plugin.xml")
    if (!Files.exists(pluginXml)) {
      // ok, any xml file
      pluginXml = Files.newDirectoryStream(root).use { files -> files.find { it.toString().endsWith(".xml") } } ?: break
    }

    try {
      return readXmlAsModel(Files.newInputStream(pluginXml)).getAttributeValue("package") ?: "icons"
    }
    catch (ignore: NoSuchFileException) {
    }
    catch (ignore: XMLStreamException) {
      // ignore invalid XML
    }
  }
  return null
}