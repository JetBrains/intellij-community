/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build.images

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.jps.model.JpsSimpleElement
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil
import java.io.File
import java.util.*

class IconsClassGenerator(val projectHome: File, val util: JpsModule, val writeChangesToDisk: Boolean = true) {
  private var processedClasses = 0
  private var processedIcons = 0
  private var modifiedClasses = ArrayList<Pair<JpsModule, File>>()

  fun processModule(module: JpsModule) {
    val customLoad: Boolean
    val packageName: String
    val className: String
    val outFile: File
    if ("icons" == module.name) {
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

    val copyrightComment = getCopyrightComment(outFile)
    val text = generate(module, className, packageName, customLoad, copyrightComment)
    if (text != null) {
      processedClasses++

      if (!outFile.exists() || outFile.readText().lines() != text.lines()) {
        modifiedClasses.add(Pair(module, outFile))

        if (writeChangesToDisk) {
          outFile.parentFile.mkdirs()
          outFile.writeText(text)
          println("Updated icons class: ${outFile.name}")
        }
      }
    }
  }

  fun printStats() {
    println()
    println("Generated classes: $processedClasses. Processed icons: $processedIcons")
  }

  fun getModifiedClasses() = modifiedClasses

  private fun findIconClass(dir: File): String? {
    var className: String? = null
    dir.children.forEach {
      if (it.name.endsWith("Icons.java")) {
        className = it.name.substring(0, it.name.length - ".java".length)
      }
    }
    return className
  }

  private fun getCopyrightComment(file: File): String {
    if (!file.isFile) return ""
    val text = file.readText()
    val i = text.indexOf("package ")
    if (i == -1) return ""
    val comment = text.substring(0, i)
    return if (comment.trim().endsWith("*/") || comment.trim().startsWith("//")) comment else ""
  }

  private fun generate(module: JpsModule, className: String, packageName: String, customLoad: Boolean, copyrightComment: String): String? {
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
    }

    val imageCollector = ImageCollector(projectHome, true)
    val images = imageCollector.collect(module)
    imageCollector.printUsedIconRobots()

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
    val leafMap = ContainerUtil.newMapFromValues(leafs.iterator(), { getImageId(it, depth) })

    val sortedKeys = (nodeMap.keys + leafMap.keys).sortedWith(NAME_COMPARATOR)
    sortedKeys.forEach { key ->
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
        val file = image.file
        if (file != null) {
          val name = file.name
          val used = image.used
          val deprecated = image.deprecated

          if (isIcon(file)) {
            processedIcons++

            if (used || deprecated) {
              append(answer, "", level)
              append(answer, "@SuppressWarnings(\"unused\")", level)
            }
            if (deprecated) {
              append(answer, "@Deprecated", level)
            }

            val sourceRoot = image.sourceRoot
            var root_prefix: String = ""
            if (sourceRoot.rootType == JavaSourceRootType.SOURCE) {
              @Suppress("UNCHECKED_CAST")
              val packagePrefix = (sourceRoot.properties as JpsSimpleElement<JavaSourceRootProperties>).data.packagePrefix
              if (!packagePrefix.isEmpty()) root_prefix = "/" + packagePrefix.replace('.', '/')
            }

            val size = imageSize(file) ?: error("Can't get icon size: $file")
            val method = if (customLoad) "load" else "IconLoader.getIcon"
            val relativePath = root_prefix + "/" + FileUtil.getRelativePath(sourceRoot.file, file)!!.replace('\\', '/')
            append(answer,
                   "public static final Icon ${iconName(name)} = $method(\"$relativePath\"); // ${size.width}x${size.height}",
                   level)
          }
        }
      }
    }
  }

  private fun append(answer: StringBuilder, text: String, level: Int) {
    answer.append("  ".repeat(level))
    answer.append(text).append("\n")
  }

  private fun getImageId(image: ImagePaths, depth: Int): String {
    val path = StringUtil.trimStart(image.id, "/").split("/")
    if (path.size < depth) throw IllegalArgumentException("Can't get image ID - ${image.id}, $depth")
    return path.drop(depth).joinToString("/")
  }

  private fun directoryName(module: JpsModule): String {
    return directoryNameFromConfig(module) ?: className(module.name)
  }

  private fun directoryNameFromConfig(module: JpsModule): String? {
    val rootUrl = getFirstContentRootUrl(module) ?: return null
    val rootDir = File(JpsPathUtil.urlToPath(rootUrl))
    if (!rootDir.isDirectory) return null

    val file = File(rootDir, "icon-robots.txt")
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
    name.split("-", "_").forEach {
      answer.append(capitalize(it))
    }
    return toJavaIdentifier(answer.toString())
  }

  private fun iconName(name: String): String {
    val id = capitalize(name.substring(0, name.lastIndexOf('.')))
    return toJavaIdentifier(id)
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
