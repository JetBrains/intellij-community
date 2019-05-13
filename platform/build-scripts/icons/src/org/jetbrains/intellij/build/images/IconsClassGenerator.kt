// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.LineSeparator
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.diff.Diff
import org.jetbrains.jps.model.JpsSimpleElement
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

data class ModifiedClass(val module: JpsModule, val file: Path, val result: CharSequence)

class IconsClassGenerator(private val projectHome: File, val util: JpsModule, private val writeChangesToDisk: Boolean = true) {
  private val processedClasses = AtomicInteger()
  private val processedIcons = AtomicInteger()
  private val processedPhantom = AtomicInteger()
  private val modifiedClasses = ContainerUtil.createConcurrentList<ModifiedClass>()
  private val obsoleteClasses = ContainerUtil.createConcurrentList<Path>()

  fun processModule(module: JpsModule) {
    val customLoad: Boolean
    val packageName: String
    val className: String
    val outFile: Path
    if ("intellij.platform.icons" == module.name) {
      customLoad = false
      packageName = "com.intellij.icons"
      className = "AllIcons"

      val dir = util.getSourceRoots(JavaSourceRootType.SOURCE).first().file.absolutePath + "/com/intellij/icons"
      outFile = Paths.get(dir, "AllIcons.java")
    }
    else if ("intellij.android.artwork" == module.name) {
      // backward compatibility - AndroidIcons class should be not modified
      packageName = "icons"
      customLoad = true
      className = "AndroidArtworkIcons"

      val dir = module.getSourceRoots(JavaSourceRootType.SOURCE).first().file.absolutePath
      outFile = Paths.get(dir, "icons", "AndroidArtworkIcons.java")
    }
    else {
      customLoad = true
      packageName = "icons"

      val firstRoot = module.getSourceRoots(JavaSourceRootType.SOURCE).firstOrNull() ?: return

      val firstRootDir = firstRoot.file.toPath().resolve("icons")
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
      val targetRoot = (generatedRoot ?: firstRoot).file.toPath().resolve("icons")

      if (generatedRoot != null && oldClassName != null) {
        val oldFile = firstRootDir.resolve("$oldClassName.java")
        println("deleting $oldFile from source root which isn't marked as 'generated'")
        Files.delete(oldFile)
      }
      if (oldClassName == null) {
        try {
          oldClassName = findIconClass(targetRoot)
        }
        catch (ignored: NoSuchFileException) {
        }
      }

      className = oldClassName ?: directoryName(module) + "Icons"
      outFile = targetRoot.resolve("$className.java")
    }

    val oldText = try {
      Files.readAllBytes(outFile).toString(StandardCharsets.UTF_8)
    }
    catch (ignored: NoSuchFileException) {
      null
    }
    val newText = generate(module, className, packageName, customLoad, getCopyrightComment(oldText))

    val oldLines = oldText?.lines() ?: emptyList()
    val newLines = newText?.lines() ?: emptyList()

    if (newLines.isNotEmpty()) {
      processedClasses.incrementAndGet()

      if (oldLines != newLines) {
        if (writeChangesToDisk) {
          val separator = getSeparators(oldText)
          Files.createDirectories(outFile.parent)
          Files.write(outFile, newLines.joinToString(separator = separator.separatorString).toByteArray())
          println("Updated icons class: ${outFile.fileName}")
        }
        else {
          val sb = StringBuilder()
          var ch = Diff.buildChanges(oldLines.toTypedArray(), newLines.toTypedArray())
          while (ch != null) {
            val deleted = oldLines.subList(ch.line0, ch.line0 + ch.deleted)
            val inserted = newLines.subList(ch.line1, ch.line1 + ch.inserted)

            if (sb.isNotEmpty()) sb.append("=".repeat(20)).append("\n")
            deleted.forEach { sb.append("-").append(it).append("\n") }
            inserted.forEach { sb.append("+").append(it).append("\n") }

            ch = ch.link
          }

          modifiedClasses.add(ModifiedClass(module, outFile, sb))
        }
      }
    }
    else {
      if (Files.exists(outFile)) {
        obsoleteClasses.add(outFile)
      }
    }
  }

  fun printStats() {
    println()
    println("Generated classes: ${processedClasses.get()}. Processed icons: ${processedIcons.get()}. Phantom icons: ${processedPhantom.get()}")
    if (obsoleteClasses.isNotEmpty()) {
      println("\nObsolete classes:")
      println(obsoleteClasses.joinToString("\n"))
      println("\nObsolete class it is class for icons that cannot be found anymore. Possible reasons:")
      println("1. Icons not located under resources root.\n   Solution - move icons to resources root or fix existing root type (must be \"resources\")")
      println("2. Icons were removed but not class.\n   Solution - remove class.")
      println("3. Icons located under resources root named \"compatibilityResources\". \"compatibilityResources\" for icons that not used externally as icon class fields, " +
              "but maybe referenced directly by path.\n   Solution - remove class or move icons to another resources root")
    }
  }

  fun getModifiedClasses(): List<ModifiedClass> = modifiedClasses

  private fun findIconClass(dir: Path): String? {
    if (!dir.toFile().exists()) return null
    Files.newDirectoryStream(dir).use { stream ->
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
    if (text == null) return ""
    val i = text.indexOf("package ")
    if (i == -1) return ""
    val comment = text.substring(0, i)
    return if (comment.trim().endsWith("*/") || comment.trim().startsWith("//")) comment else ""
  }

  private fun getSeparators(text: String?): LineSeparator {
    if (text == null) return LineSeparator.LF
    return StringUtil.detectSeparators(text) ?: LineSeparator.LF
  }

  private fun generate(module: JpsModule, className: String, packageName: String, customLoad: Boolean, copyrightComment: String): String? {
    val imageCollector = ImageCollector(projectHome.toPath(), iconsOnly = true, className = className)

    val images = imageCollector.collect(module, includePhantom = true)
    if (images.isEmpty()) {
      return null
    }

    imageCollector.printUsedIconRobots()

    val answer = StringBuilder()
    answer.append(copyrightComment)
    append(answer, "package $packageName;\n", 0)
    append(answer, "import com.intellij.openapi.util.IconLoader;", 0)
    append(answer, "", 0)
    append(answer, "import javax.swing.*;", 0)
    append(answer, "", 0)

    // IconsGeneratedSourcesFilter depends on following comment, if you going to change the text
    // please do corresponding changes in IconsGeneratedSourcesFilter as well
    append(answer, "/**", 0)
    append(answer, " * NOTE THIS FILE IS AUTO-GENERATED", 0)
    append(answer, " * DO NOT EDIT IT BY HAND, run \"Generate icon classes\" configuration instead", 0)
    append(answer, " */", 0)


    answer.append("public")
    // backward compatibility
    if (className != "AllIcons") {
      answer.append(" final")
    }
    answer.append(" class ").append(className).append(" {\n")
    if (customLoad) {
      append(answer, "private static Icon load(String path) {", 1)
      append(answer, "return IconLoader.getIcon(path, ${className}.class);", 2)
      append(answer, "}", 1)
      append(answer, "", 0)

      val customExternalLoad = images.any { it.deprecation?.replacementContextClazz != null }
      if (customExternalLoad) {
        append(answer, "private static Icon load(String path, Class<?> clazz) {", 1)
        append(answer, "return IconLoader.getIcon(path, clazz);", 2)
        append(answer, "}", 1)
        append(answer, "", 0)
      }
    }

    val inners = StringBuilder()
    processIcons(images, inners, customLoad, 0)
    if (inners.isEmpty()) return null

    answer.append(inners)
    append(answer, "}", 0)
    return answer.toString()
  }

  private fun processIcons(images: List<ImagePaths>, answer: StringBuilder, customLoad: Boolean, depth: Int) {
    val level = depth + 1

    val (nodes, leafs) = images.partition { getImageId(it, depth).contains('/') }
    val nodeMap = nodes.groupBy { getImageId(it, depth).substringBefore('/') }
    val leafMap = ContainerUtil.newMapFromValues(leafs.iterator()) { getImageId(it, depth) }

    fun getWeight(key: String): Int {
      val image = leafMap[key]
      if (image == null) {
        return 0
      }
      return if (image.deprecated) 1 else 0
    }

    val sortedKeys = (nodeMap.keys + leafMap.keys)
      .sortedWith(NAME_COMPARATOR)
      .sortedWith(kotlin.Comparator(function = { o1, o2 ->
        getWeight(o1) - getWeight(o2)
      }))

    for (key in sortedKeys) {
      val group = nodeMap[key]
      val image = leafMap[key]
      if (group != null) {
        val inners = StringBuilder()
        processIcons(group, inners, customLoad, depth + 1)

        if (inners.isNotEmpty()) {
          append(answer, "", level)
          append(answer, "public final static class " + className(key) + " {", level)
          append(answer, inners.toString(), 0)
          append(answer, "}", level)
        }
      }

      if (image != null) {
        appendImage(image, answer, level, customLoad)
      }
    }
  }

  private fun appendImage(image: ImagePaths,
                          answer: StringBuilder,
                          level: Int,
                          customLoad: Boolean) {
    val file = image.file ?: return
    if (!image.phantom && !isIcon(file)) {
      return
    }

    processedIcons.incrementAndGet()
    if (image.phantom) {
      processedPhantom.incrementAndGet()
    }

    if (image.used || image.deprecated) {
      val deprecationComment = image.deprecation?.comment
      append(answer, "", level)
      if (deprecationComment != null) {
        append(answer, "/** @deprecated $deprecationComment */", level)
      }
      append(answer, "@SuppressWarnings(\"unused\")", level)
    }
    if (image.deprecated) {
      append(answer, "@Deprecated", level)
    }

    val sourceRoot = image.sourceRoot
    var rootPrefix = "/"
    if (sourceRoot.rootType == JavaSourceRootType.SOURCE) {
      @Suppress("UNCHECKED_CAST")
      val packagePrefix = (sourceRoot.properties as JpsSimpleElement<JavaSourceRootProperties>).data.packagePrefix
      if (!packagePrefix.isEmpty()) {
        rootPrefix += packagePrefix.replace('.', '/') + "/"
      }
    }

    val iconName = iconName(file)
    val deprecation = image.deprecation

    if (deprecation?.replacementContextClazz != null) {
      val method = if (customLoad) "load" else "IconLoader.getIcon"
      append(answer,
             "public static final Icon $iconName = $method(\"${deprecation.replacement}\", ${deprecation.replacementContextClazz}.class);",
             level)
      return
    }
    else if (deprecation?.replacementReference != null) {
      append(answer, "public static final Icon $iconName = ${deprecation.replacementReference};", level)
      return
    }

    val sourceRootFile = Paths.get(JpsPathUtil.urlToPath(sourceRoot.url))
    val imageFile: Path
    if (deprecation?.replacement == null) {
      imageFile = file
    }
    else {
      imageFile = sourceRootFile.resolve(deprecation.replacement.removePrefix("/").removePrefix(File.separator))
      assert(isIcon(imageFile)) { "Overriding icon should be valid: $iconName - $imageFile" }
    }

    val size = if (imageFile.toFile().exists()) imageSize(imageFile) else null
    if (size != null) {
      append(answer, "/**", level)
      append(answer, " * ${size.width}x${size.height}", level)
      append(answer, " */", level)
    }
    else if (!image.phantom) error("Can't get icon size: $imageFile")
    val method = if (customLoad) "load" else "IconLoader.getIcon"
    val relativePath = rootPrefix + FileUtilRt.toSystemIndependentName(sourceRootFile.relativize(imageFile).toString())
    append(answer, "public static final Icon $iconName = $method(\"$relativePath\");", level)
  }

  private fun append(answer: StringBuilder, text: String, level: Int) {
    if (text.isNotBlank()) {
      for (i in 0 until level) {
        answer.append("  ")
      }
    }
    answer.append(text).append('\n')
  }

  private fun getImageId(image: ImagePaths, depth: Int): String {
    val path = image.id.removePrefix("/").split("/")
    if (path.size < depth) {
      throw IllegalArgumentException("Can't get image ID - ${image.id}, $depth")
    }
    return path.drop(depth).joinToString("/")
  }

  private fun directoryName(module: JpsModule): String {
    return directoryNameFromConfig(module) ?: className(module.name)
  }

  private fun directoryNameFromConfig(module: JpsModule): String? {
    val rootUrl = getFirstContentRootUrl(module) ?: return null
    val rootDir = File(JpsPathUtil.urlToPath(rootUrl))
    if (!rootDir.isDirectory) return null

    val file = File(rootDir, ROBOTS_FILE_NAME)
    if (!file.exists()) return null

    val prefix = "name:"
    var moduleName: String? = null
    file.forEachLine {
      if (it.startsWith(prefix)) {
        val name = it.substring(prefix.length).trim()
        if (name.isNotEmpty()) moduleName = name
      }
    }
    return moduleName
  }

  private fun getFirstContentRootUrl(module: JpsModule): String? {
    return module.contentRootsList.urls.firstOrNull()
  }

  private fun className(name: String): String {
    val answer = StringBuilder()
    name.removePrefix("intellij.").split("-", "_", ".").forEach {
      answer.append(capitalize(it))
    }
    return toJavaIdentifier(answer.toString())
  }

  private fun iconName(file: Path): String {
    val name = capitalize(file.fileName.toString().substringBeforeLast('.'))
    return toJavaIdentifier(name)
  }

  private fun toJavaIdentifier(id: String): String {
    val sb = StringBuilder()
    id.forEach {
      if (Character.isJavaIdentifierPart(it)) {
        sb.append(it)
      }
      else {
        sb.append('_')
      }
    }

    if (Character.isJavaIdentifierStart(sb.first())) {
      return sb.toString()
    }
    else {
      return "_" + sb.toString()
    }
  }

  private fun capitalize(name: String): String {
    if (name.length == 2) return name.toUpperCase()
    return name.capitalize()
  }

  // legacy ordering
  private val NAME_COMPARATOR: Comparator<String> = compareBy { it.toLowerCase() + "." }
}
