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
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleSourceRoot
import java.io.File
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

internal class ImagePaths(val id: String, val sourceRoot: JpsModuleSourceRoot, val used: Boolean, val deprecated: Boolean) {
  var files: MutableMap<ImageType, File> = HashMap()
  var ambiguous: Boolean = false

  val file: File? get() = files[ImageType.BASIC]
  val presentablePath: File get() = file ?: files.values.first() ?: File("<unknown>")
}

internal class ImageCollector(val projectHome: File, val iconsOnly: Boolean = true, val ignoreSkipTag: Boolean = false) {
  private val result = HashMap <String, ImagePaths>()

  private val usedIconsRobots: MutableSet<File> = HashSet()

  fun collect(module: JpsModule): List<ImagePaths> {
    module.sourceRoots.forEach {
      processRoot(it)
    }
    return result.values.toList()
  }

  fun printUsedIconRobots() {
    usedIconsRobots.forEach {
      println("Found icon-robots: $it")
    }
  }

  private fun processRoot(sourceRoot: JpsModuleSourceRoot) {
    val root = sourceRoot.file
    if (!root.exists()) return
    if (!JavaModuleSourceRootTypes.PRODUCTION.contains(sourceRoot.rootType)) return

    val iconsRoot = downToRoot(root)
    if (iconsRoot == null) return

    val rootRobotData = upToProjectHome(root)
    if (rootRobotData.isSkipped(root)) return

    val robotData = rootRobotData.fork(iconsRoot, root)

    processDirectory(iconsRoot, sourceRoot, robotData, emptyList<String>())
  }

  private fun processDirectory(dir: File, sourceRoot: JpsModuleSourceRoot, robotData: IconRobotsData, prefix: List<String>) {
    dir.children.forEach { file ->
      if (robotData.isSkipped(file)) return@forEach
      if (file.isDirectory) {
        val root = sourceRoot.file
        val childRobotData = robotData.fork(file, root)
        val childPrefix = prefix + file.name
        processDirectory(file, sourceRoot, childRobotData, childPrefix)
      }
      else if (isImage(file, iconsOnly)) {
        processImageFile(file, sourceRoot, robotData, prefix)
      }
    }
  }

  private fun processImageFile(file: File, sourceRoot: JpsModuleSourceRoot, robotData: IconRobotsData, prefix: List<String>) {
    val nameWithoutExtension = FileUtil.getNameWithoutExtension(file.name)
    val type = ImageType.fromName(nameWithoutExtension)
    val id = type.getBasicName((prefix + nameWithoutExtension).joinToString("/"))

    val skipped = robotData.isSkipped(file)
    val used = robotData.isUsed(file)
    val deprecated = robotData.isDeprecated(file)
    if (skipped) return

    val iconPaths = result.computeIfAbsent(id, { ImagePaths(id, sourceRoot, used, deprecated) })
    if (type !in iconPaths.files) {
      iconPaths.files[type] = file
    }
    else {
      iconPaths.ambiguous = true
    }
  }

  private fun upToProjectHome(dir: File): IconRobotsData {
    if (FileUtil.filesEqual(dir, projectHome)) return IconRobotsData()
    val parent = dir.parentFile ?: return IconRobotsData()
    return upToProjectHome(parent).fork(parent, projectHome)
  }

  private fun downToRoot(dir: File): File? {
    val answer = downToRoot(dir, dir, null, IconRobotsData())
    return if (answer == null || answer.isDirectory) answer else answer.parentFile
  }

  private fun downToRoot(root: File, file: File, common: File?, robotData: IconRobotsData): File? {
    if (robotData.isSkipped(file)) return common

    if (file.isDirectory) {
      val childRobotData = robotData.fork(file, root)

      var childCommon = common
      file.children.forEach {
        childCommon = downToRoot(root, it, childCommon, childRobotData)
      }
      return childCommon
    }
    else if (isImage(file, iconsOnly)) {
      if (common == null) return file
      return FileUtil.findAncestor(common, file)
    }
    else {
      return common
    }
  }

  private inner class IconRobotsData(private val parent: IconRobotsData? = null) {
    private val skip: MutableSet<Matcher> = HashSet()
    private val used: MutableSet<Matcher> = HashSet()
    private val deprecated: MutableSet<Matcher> = HashSet()

    fun isSkipped(file: File): Boolean = !ignoreSkipTag && (matches(file, skip) || parent?.isSkipped(file) ?: false)
    fun isUsed(file: File): Boolean = matches(file, used) || parent?.isUsed(file) ?: false
    fun isDeprecated(file: File): Boolean = matches(file, deprecated) || parent?.isDeprecated(file) ?: false

    fun fork(dir: File, root: File): IconRobotsData {
      val robots = File(dir, "icon-robots.txt")
      if (!robots.exists()) return this

      usedIconsRobots.add(robots)

      val answer = IconRobotsData(this)
      parse(robots,
            Pair("skip:", { value -> compilePattern(answer.skip, dir, root, value) }),
            Pair("used:", { value -> compilePattern(answer.used, dir, root, value) }),
            Pair("deprecated:", { value -> compilePattern(answer.deprecated, dir, root, value) }),
            Pair("name:", { value -> }), // ignore
            Pair("#", { value -> }) // comment
      )
      return answer
    }

    private fun parse(robots: File, vararg handlers: Pair<String, (String) -> Unit>) {
      robots.forEachLine { line ->
        if (line.isBlank()) return@forEachLine
        for (h in handlers) {
          if (line.startsWith(h.first)) {
            h.second(StringUtil.trimStart(line, h.first))
            return@forEachLine
          }
        }
        throw Exception("Can't parse $robots. Line: $line")
      }
    }

    private fun compilePattern(set: MutableSet<Matcher>, dir: File, root: File, value: String) {
      var pattern = value.trim()

      if (pattern.startsWith("/")) {
        pattern = root.absolutePath + pattern
      }
      else {
        pattern = dir.absolutePath + '/' + pattern
      }

      val regExp = FileUtil.convertAntToRegexp(pattern, false)
      try {
        set.add(Pattern.compile(regExp).matcher(""))
      }
      catch (e: Exception) {
        throw Exception("Cannot compile pattern: $pattern. Built on based in $dir/icon-robots.txt")
      }
    }

    private fun matches(file: File, matcher: Set<Matcher>): Boolean {
      val path = file.absolutePath.replace('\\', '/')

      val pathWithoutExtension = FileUtilRt.getNameWithoutExtension(path)
      val extension = FileUtilRt.getExtension(path)

      val basicPathWithoutExtension = ImageType.stripSuffix(pathWithoutExtension)
      val basicPath = basicPathWithoutExtension + if (extension.isNotEmpty()) "." + extension else ""

      return matcher.any { it.reset(basicPath).matches() }
    }
  }
}