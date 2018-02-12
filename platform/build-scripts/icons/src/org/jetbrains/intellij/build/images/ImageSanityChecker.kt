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

import org.jetbrains.intellij.build.images.ImageSanityCheckerBase.Severity.*
import org.jetbrains.intellij.build.images.ImageType.*
import org.jetbrains.jps.model.module.JpsModule
import java.awt.Dimension
import java.io.File
import java.util.*

abstract class ImageSanityCheckerBase(val projectHome: File, val ignoreSkipTag: Boolean) {
  fun check(module: JpsModule) {
    val allImages = ImageCollector(projectHome, false, ignoreSkipTag).collect(module)

    val (images, broken) = allImages.partition { it.file != null }
    log(Severity.ERROR, "image without base version", module, broken)

    checkHaveRetinaVersion(images, module)
    checkHaveCompleteIconSet(images, module)
    checkHaveValidSize(images, module)
    checkAreNotAmbiguous(images, module)
  }

  private fun checkHaveRetinaVersion(images: List<ImagePaths>, module: JpsModule) {
    process(images, Severity.INFO, "image without retina version", module) { image ->
      val hasRetina = RETINA in image.files
      val hasRetinaDarcula = RETINA_DARCULA in image.files
      return@process hasRetina || hasRetinaDarcula
    }
  }

  private fun checkHaveCompleteIconSet(images: List<ImagePaths>, module: JpsModule) {
    process(images, WARNING, "image without complete set of additional icons", module) { image ->
      val hasRetina = RETINA in image.files
      val hasDarcula = DARCULA in image.files
      val hasRetinaDarcula = RETINA_DARCULA in image.files

      if (hasRetinaDarcula) {
        return@process hasRetina && hasDarcula
      }
      else {
        return@process !hasRetina || !hasDarcula
      }
    }
  }

  private fun checkHaveValidSize(images: List<ImagePaths>, module: JpsModule) {
    process(images, WARNING, "icon with suspicious size", module) { image ->
      if (!isIcon(image.file!!)) return@process true
      val sizes = image.files.mapValues { imageSize(it.value) }

      val sizeBasic = sizes[BASIC]!!
      val sizeRetina = sizes[RETINA]
      val sizeDarcula = sizes[DARCULA]
      val sizeRetinaDarcula = sizes[RETINA_DARCULA]

      val sizeBasicTwice = Dimension(sizeBasic.width * 2, sizeBasic.height * 2)
      return@process (sizeDarcula == null || sizeBasic == sizeDarcula) &&
                     (sizeRetina == null || sizeRetinaDarcula == null || sizeRetina == sizeRetinaDarcula) &&
                     (sizeRetina == null || sizeBasicTwice == sizeRetina)
    }
  }

  private fun checkAreNotAmbiguous(images: List<ImagePaths>, module: JpsModule) {
    process(images, WARNING, "image with ambiguous definition (ex: has both '.png' and '.gif' versions)", module) { image ->
      return@process !image.ambiguous
    }
  }

  private fun process(images: List<ImagePaths>, severity: Severity, message: String, module: JpsModule,
                      processor: (ImagePaths) -> Boolean) {
    val result = ArrayList<ImagePaths>()
    images.forEach {
      if (!processor(it)) result.add(it)
    }
    log(severity, message, module, result)
  }

  internal abstract fun log(severity: Severity, message: String, module: JpsModule, images: Collection<ImagePaths>)

  enum class Severity { INFO, WARNING, ERROR }
}

class ImageSanityChecker(projectHome: File) : ImageSanityCheckerBase(projectHome, false) {
  private val infos: StringBuilder = StringBuilder()
  private val warnings: StringBuilder = StringBuilder()

  fun printInfo() {
    if (infos.isNotEmpty()) {
      println()
      println(infos)
    }
  }

  fun printWarnings() {
    if (warnings.isNotEmpty()) {
      println()
      println(warnings)
    }
    else {
      println()
      println("No warnings found")
    }
  }

  override fun log(severity: Severity, message: String, module: JpsModule, images: Collection<ImagePaths>) {
    val logger = when (severity) {
      ERROR -> warnings
      WARNING -> warnings
      INFO -> infos
    }
    val prefix = when (severity) {
      ERROR -> "ERROR:"
      WARNING -> "WARNING:"
      INFO -> "INFO:"
    }

    if (images.isEmpty()) return
    logger.append("$prefix $message found in module '${module.name}'\n")
    images.sortedBy { it.id }.forEach {
      logger.append("    ${it.id} - ${it.presentablePath.path}\n")
    }
    logger.append("\n")
  }
}
