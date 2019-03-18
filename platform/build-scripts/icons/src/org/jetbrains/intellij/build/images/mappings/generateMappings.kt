// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.mappings

import com.intellij.openapi.application.PathManager
import org.jetbrains.intellij.build.images.IconsClassGenerator
import org.jetbrains.intellij.build.images.ImageCollector
import org.jetbrains.intellij.build.images.sync.isValidIcon
import org.jetbrains.intellij.build.images.sync.protectStdErr
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import java.io.File
import java.util.*
import kotlin.streams.toList

fun main() {
  val homePath = PathManager.getHomePath()
  val home = File(homePath)
  val project = JpsSerializationManager.getInstance().loadModel(homePath, null).project
  val util = project.modules.find {
    it.name == "intellij.platform.util"
  } ?: throw error("Can't load module 'util'")
  val generator = IconsClassGenerator(home, util)
  val mappings = protectStdErr {
    project.modules.parallelStream().map { module ->
      val iconsClassInfo = generator.getIconsClassInfo(module) ?: return@map null
      val imageCollector = ImageCollector(home.toPath(), iconsOnly = true, className = iconsClassInfo.className)
      val images = imageCollector.collect(module, includePhantom = true)
      if (images.isNotEmpty()) {
        val icons = images.asSequence()
          .filter { it.file != null && isValidIcon(it.file!!) }
          .map { it.sourceRoot.file }.toSet()
        return@map when {
          icons.isEmpty() -> null
          icons.size > 1 -> error("${iconsClassInfo.className}: ${icons.joinToString()}")
          else -> """
            |{
            |   "product": "intellij",
            |   "set": "${iconsClassInfo.className}",
            |   "src": "../IntelliJIcons/idea/${icons.first().toRelativeString(home)}",
            |   "category": "icons"
            |}
          """.trimMargin().prependIndent("     ")
        }

      }
      else null
    }.filter(Objects::nonNull).toList().joinToString(separator = ",\n")
  }
  println("""
    |{
    |  "mappings": [
    |$mappings
    |  ]
    |}
  """.trimMargin())
}