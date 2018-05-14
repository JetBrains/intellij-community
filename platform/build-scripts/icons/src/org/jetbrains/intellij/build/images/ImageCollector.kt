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
import kotlin.collections.ArrayList

internal class ImagePaths(val id: String,
                          val sourceRoot: JpsModuleSourceRoot) {
  private var flags: ImageFlags = ImageFlags()
  private var images: MutableList<File> = ArrayList()

  fun addImage(file: File, fileFlags: ImageFlags) {
    images.add(file)
    flags = mergeImageFlags(flags, fileFlags, file.path)
  }


  val files: List<File> get() = images
  fun getFiles(vararg types: ImageType): List<File> = files.filter { ImageType.fromFile(it) in types }

  val file: File?
    get() = getFiles(ImageType.BASIC)
      .sortedBy { ImageExtension.fromFile(it) }
      .firstOrNull()

  val presentablePath: File get() = file ?: files.first() ?: File("<unknown>")


  val used: Boolean get() = flags.used
  val deprecated: Boolean get() = flags.deprecation != null
  val deprecationReplacement: String? get() = flags.deprecation?.replacement
  val deprecationComment: String? get() = flags.deprecation?.comment
}

class ImageFlags(val skipped: Boolean,
                 val used: Boolean,
                 val deprecation: DeprecationData?) {
  constructor() : this(false, false, null)
}

class DeprecationData(val comment: String?, val replacement: String?)


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
    val id = ImageType.getBasicName(file, prefix)

    val flags = robotData.getImageFlags(file)
    if (flags.skipped) return

    val iconPaths = result.computeIfAbsent(id, { ImagePaths(id, sourceRoot) })
    iconPaths.addImage(file, flags)
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
    private val skip: MutableList<Matcher> = ArrayList()
    private val used: MutableList<Matcher> = ArrayList()
    private val deprecated: MutableList<Pair<Matcher, DeprecationData>> = ArrayList()

    fun getImageFlags(file: File): ImageFlags {
      val isSkipped = !ignoreSkipTag && matches(file, skip)
      val isUsed = matches(file, used)
      val deprecationData = findDeprecatedData(file)
      val ourFlags = ImageFlags(isSkipped, isUsed, deprecationData)

      val parentFlags = parent?.getImageFlags(file) ?: ImageFlags()

      return mergeImageFlags(ourFlags, parentFlags, file.path)
    }

    fun isSkipped(file: File): Boolean = getImageFlags(file).skipped

    fun fork(dir: File, root: File): IconRobotsData {
      val robots = File(dir, ROBOTS_FILE_NAME)
      if (!robots.exists()) return this

      usedIconsRobots.add(robots)

      val answer = IconRobotsData(this)
      parse(robots,
            Pair("skip:", { value -> answer.skip += compilePattern(dir, root, value) }),
            Pair("used:", { value -> answer.used += compilePattern(dir, root, value) }),
            Pair("deprecated:", { value ->
              val comment = StringUtil.nullize(value.substringAfter(";", "").trim())
              val valueWithoutComment = value.substringBefore(";")
              val pattern = valueWithoutComment.substringBefore("->").trim()
              val replacement = StringUtil.nullize(valueWithoutComment.substringAfter("->", "").trim())

              answer.deprecated += Pair(compilePattern(dir, root, pattern), DeprecationData(comment, replacement))
            }),
            Pair("name:", { value -> }), // ignore directive for IconsClassGenerator
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

    private fun compilePattern(dir: File, root: File, value: String): Matcher {
      var pattern = value.trim()

      if (pattern.startsWith("/")) {
        pattern = root.absolutePath + pattern
      }
      else {
        pattern = dir.absolutePath + '/' + pattern
      }

      val regExp = FileUtil.convertAntToRegexp(pattern, false)
      try {
        return Pattern.compile(regExp).matcher("")
      }
      catch (e: Exception) {
        throw Exception("Cannot compile pattern: $pattern. Built on based in $dir/$ROBOTS_FILE_NAME")
      }
    }

    private fun findDeprecatedData(file: File): DeprecationData? {
      val basicPath = getBasicPath(file)
      return deprecated.find { it.first.reset(basicPath).matches() }?.second
    }

    private fun matches(file: File, matcher: List<Matcher>): Boolean {
      val basicPath = getBasicPath(file)
      return matcher.any { it.reset(basicPath).matches() }
    }

    private fun getBasicPath(file: File): String {
      val path = file.absolutePath.replace('\\', '/')

      val pathWithoutExtension = FileUtilRt.getNameWithoutExtension(path)
      val extension = FileUtilRt.getExtension(path)

      val basicPathWithoutExtension = ImageType.stripSuffix(pathWithoutExtension)
      val basicPath = basicPathWithoutExtension + if (extension.isNotEmpty()) "." + extension else ""
      return basicPath
    }
  }

  companion object {
    const val ROBOTS_FILE_NAME: String = "icon-robots.txt"
  }
}


private fun mergeImageFlags(flags1: ImageFlags,
                            flags2: ImageFlags,
                            comment: String): ImageFlags {
  return ImageFlags(flags1.skipped || flags2.skipped,
                    flags1.used || flags2.used,
                    mergeDeprecations(flags1.deprecation, flags2.deprecation, comment))
}

private fun mergeDeprecations(data1: DeprecationData?,
                              data2: DeprecationData?,
                              comment: String): DeprecationData? {
  if (data1 == null) return data2;
  if (data2 == null) return data1;

  return DeprecationData(mergeOptions(data1.comment, data2.comment,
                                      "Different deprecation comments found for same icon: $comment"),
                         mergeOptions(data1.replacement, data2.replacement,
                                     "Different deprecation replacements found for same icon: $comment"))
}

private fun <T> mergeOptions(val1: T?, val2: T?, comment: String): T? {
  if (val1 == null || val2 == null || val1 == val2) return val1 ?: val2
  throw AssertionError("$comment\n$val1\n$val2")
}