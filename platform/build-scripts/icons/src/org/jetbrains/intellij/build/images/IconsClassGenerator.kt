// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

class IconsClassGenerator(private val projectHome: File, val util: JpsModule, private val writeChangesToDisk: Boolean = true) {
  private var processedClasses = 0
  private var processedIcons = 0
  private var processedPhantom = 0
  private var modifiedClasses = ArrayList<Triple<JpsModule, File, String>>()

  fun processModule(module: JpsModule) {
    val customLoad: Boolean
    val packageName: String
    val className: String
    val outFile: File
    if ("intellij.platform.icons" == module.name) {
      customLoad = false
      packageName = "com.intellij.icons"
      className = "AllIcons"

      val dir = util.getSourceRoots(JavaSourceRootType.SOURCE).first().file.absolutePath + "/com/intellij/icons"
      outFile = File(dir, "AllIcons.java")
    }
    else {
      customLoad = true
      packageName = "icons"

      val firstRoot = module.getSourceRoots(JavaSourceRootType.SOURCE).firstOrNull()
      if (firstRoot == null) return

      val generatedRoot = module.getSourceRoots(JavaSourceRootType.SOURCE).find { it.properties.isForGeneratedSources }
      val targetRoot = File((generatedRoot ?: firstRoot).file, "icons")

      val firstRootDir = File(firstRoot.file, "icons")
      if (firstRootDir.isDirectory && firstRootDir.list().isEmpty()) {
        //this is added to remove unneeded empty directories created by previous version of this script
        println("deleting empty directory ${firstRootDir.absolutePath}")
        firstRootDir.delete()
      }

      var oldClassName = findIconClass(firstRootDir)
      if (generatedRoot != null && oldClassName != null) {
        val oldFile = File(firstRootDir, "${oldClassName}.java")
        println("deleting $oldFile from source root which isn't marked as 'generated'")
        oldFile.delete()
      }
      if (oldClassName == null) {
        oldClassName = findIconClass(targetRoot)
      }

      className = oldClassName ?: directoryName(module) + "Icons"
      outFile = File(targetRoot, "${className}.java")
    }

    val oldText = if (outFile.exists()) outFile.readText() else null
    val copyrightComment = getCopyrightComment(oldText)
    val separator = getSeparators(oldText)

    val newText = generate(module, className, packageName, customLoad, copyrightComment)

    val oldLines = oldText?.lines() ?: emptyList()
    val newLines = newText?.lines() ?: emptyList()

    if (newLines.isNotEmpty()) {
      processedClasses++

      if (oldLines != newLines) {
        if (writeChangesToDisk) {
          outFile.parentFile.mkdirs()
          outFile.writeText(newLines.joinToString(separator = separator.separatorString))
          println("Updated icons class: ${outFile.name}")
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

          modifiedClasses.add(Triple(module, outFile, sb.toString()))
        }
      }
    }
  }

  fun printStats() {
    println()
    println("Generated classes: $processedClasses. Processed icons: $processedIcons. Phantom icons: $processedPhantom")
  }

  fun getModifiedClasses(): List<Triple<JpsModule, File, String>> = modifiedClasses

  private fun findIconClass(dir: File): String? {
    var className: String? = null
    dir.children.forEach {
      if (it.name.endsWith("Icons.java")) {
        className = it.name.substring(0, it.name.length - ".java".length)
      }
    }
    return className
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

    append(answer, "public class $className {", 0)
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

    val sortedKeys = (nodeMap.keys + leafMap.keys).sortedWith(NAME_COMPARATOR)
    for (key in sortedKeys) {
      val group = nodeMap[key]
      val image = leafMap[key]
      if (group != null) {
        val inners = StringBuilder()
        processIcons(group, inners, customLoad, depth + 1)

        if (inners.isNotEmpty()) {
          append(answer, "", level)
          append(answer, "public static class " + className(key) + " {", level)
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

    processedIcons++
    if (image.phantom) processedPhantom++

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

    val size = if (Files.exists(imageFile)) imageSize(imageFile) else null
    val comment: String
    when {
      size != null -> comment = " // ${size.width}x${size.height}"
      image.phantom -> comment = ""
      else -> error("Can't get icon size: $imageFile")
    }

    val method = if (customLoad) "load" else "IconLoader.getIcon"
    val relativePath = rootPrefix + FileUtilRt.toSystemIndependentName(sourceRootFile.relativize(imageFile).toString())
    append(answer,
           "public static final Icon $iconName = $method(\"$relativePath\");$comment",
           level)
  }

  private fun append(answer: StringBuilder, text: String, level: Int) {
    if (text.isNotBlank()) answer.append("  ".repeat(level))
    answer.append(text).append("\n")
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
