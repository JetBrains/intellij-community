/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.build.scripts

import com.intellij.build.scripts.ImageType.*
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.model.module.JpsModule
import java.awt.Dimension
import java.io.File
import java.util.*

class ImageSanityChecker(val projectHome: File) {
  private val infos: StringBuilder = StringBuilder()
  private val warnings: StringBuilder = StringBuilder()

  fun check(module: JpsModule) {
    val allImages = ImageCollector(projectHome, false).collect(module)

    val (images, broken) = allImages.partition { it.file != null }
    log(warnings, "ERROR: icons without base version found in module", module, broken)

    checkHaveRetinaVersion(images, module)
    checkHaveCompleteIconSet(images, module)
    checkHaveValidSize(images, module)
  }

  fun printInfo() {
    if (infos.isNotEmpty()) {
      println("")
      println(infos)
    }
  }

  fun printWarnings() {
    if (warnings.isNotEmpty()) {
      println("")
      println(warnings)
    }
  }

  private fun checkHaveRetinaVersion(images: List<ImagePaths>, module: JpsModule) {
    process(images, infos, "INFO: icons without retina version found in module", module) { image ->
      val hasRetina = image.files[RETINA] != null
      val hasRetinaDarcula = image.files[RETINA_DARCULA] != null
      return@process hasRetina || hasRetinaDarcula
    }
  }

  private fun checkHaveCompleteIconSet(images: List<ImagePaths>, module: JpsModule) {
    process(images, warnings, "WARNING: icons without complete set of additional icons found in module", module) { image ->
      val hasRetina = image.files[RETINA] != null
      val hasDarcula = image.files[DARCULA] != null
      val hasRetinaDarcula = image.files[RETINA_DARCULA] != null

      if (hasRetinaDarcula) {
        return@process hasRetina && hasDarcula
      }
      else {
        return@process !hasRetina || !hasDarcula
      }
    }
  }

  private fun checkHaveValidSize(images: List<ImagePaths>, module: JpsModule) {
    process(images, warnings, "WARNING: icons with suspicious size found in module", module) { image ->
      if (!isIcon(image.file!!)) return@process true
      if (FileUtil.normalize(image.file!!.path).contains("/tips/images/")) return@process true
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

  private fun process(images: List<ImagePaths>, logger: StringBuilder, message: String, module: JpsModule,
                      processor: (ImagePaths) -> Boolean) {
    val result = ArrayList<ImagePaths>()
    images.forEach {
      if (!processor(it)) result.add(it)
    }
    log(logger, message, module, result)
  }

  private fun log(logger: StringBuilder, message: String, module: JpsModule, images: Collection<ImagePaths>) {
    if (images.isEmpty()) return
    logger.append("$message '${module.name}'\n")
    images.sortedBy { it.id }.forEach {
      val path = it.file ?: it.files.values.first()
      logger.append("    ${it.id} - $path\n")
    }
    logger.append("\n")
  }
}