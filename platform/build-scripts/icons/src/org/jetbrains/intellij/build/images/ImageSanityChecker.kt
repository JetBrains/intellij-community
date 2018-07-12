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

import com.intellij.util.containers.MultiMap
import org.jetbrains.intellij.build.images.ImageExtension.*
import org.jetbrains.intellij.build.images.ImageSanityCheckerBase.Severity.*
import org.jetbrains.intellij.build.images.ImageType.*
import org.jetbrains.jps.model.module.JpsModule
import java.awt.Dimension
import java.io.File
import java.util.*

abstract class ImageSanityCheckerBase(val projectHome: File, val ignoreSkipTag: Boolean) {
  private val STUB_PNG_MD5 = "5a87124746c39b00aad480e92672eca0" // /actions/stub.svg - 16x16

  private val IMAGES_WITH_BOTH_SVG_AND_PNG = MultiMap.createSet<String, String>().apply {
    //putValue("intellij.platform.icons", "general/settings")
    //putValue("intellij.platform.icons", "actions/cross")
  }

  fun check(module: JpsModule) {
    val allImages = ImageCollector(projectHome, false, ignoreSkipTag).collect(module)

    val (images, broken) = allImages.partition { it.file != null }
    log(Severity.ERROR, "image without base version", module, broken)

    checkHaveRetinaVersion(images, module)
    checkHaveCompleteIconSet(images, module)
    checkHaveValidSize(images, module)
    checkAreNotAmbiguous(images, module)
    checkSvgFallbackVersionsAreStubIcons(images, module)
    checkOverridingFallbackVersionsAreStubIcons(images, module)
    checkIconsWithBothSvgAndPng(images, module)
  }

  private fun checkHaveRetinaVersion(images: List<ImagePaths>, module: JpsModule) {
    process(images, Severity.INFO, "image without retina version", module) { image ->
      return@process image.getFiles(RETINA, RETINA_DARCULA).isNotEmpty()
    }
  }

  private fun checkHaveCompleteIconSet(images: List<ImagePaths>, module: JpsModule) {
    process(images, WARNING, "image without complete set of additional icons", module) { image ->
      return@process image.files.groupBy { ImageExtension.fromFile(it) }.all { (_, files) ->
        val types = files.map { ImageType.fromFile(it) }
        val hasBasic = types.contains(BASIC)
        val hasRetina = types.contains(RETINA)
        val hasDarcula = types.contains(DARCULA)
        val hasRetinaDarcula = types.contains(RETINA_DARCULA)

        if (!hasBasic)
          false
        else if (hasRetinaDarcula) {
          hasRetina && hasDarcula
        }
        else {
          !hasRetina || !hasDarcula
        }
      }
    }
  }

  private fun checkHaveValidSize(images: List<ImagePaths>, module: JpsModule) {
    process(images, WARNING, "icon with suspicious size", module) { image ->
      if (!isIcon(image.file!!)) return@process true

      return@process image.files.groupBy { ImageExtension.fromFile(it) }.all { (_, files) ->
        val basicSizes = files.filter { ImageType.fromFile(it) in setOf(BASIC, DARCULA) }.mapNotNull { imageSize(it) }.toSet()
        val retinaSizes = files.filter { ImageType.fromFile(it) in setOf(RETINA, RETINA_DARCULA) }.mapNotNull { imageSize(it) }.toSet()

        if (basicSizes.size > 1) return@all false
        if (retinaSizes.size > 1) return@all false
        if (basicSizes.size == 1 && retinaSizes.size == 1) {
          val sizeBasic = basicSizes.single()
          val sizeRetina = retinaSizes.single()
          val sizeBasicTwice = Dimension(sizeBasic.width * 2, sizeBasic.height * 2)
          return@all sizeBasicTwice == sizeRetina
        }
        return@all true
      }
    }
  }

  private fun checkAreNotAmbiguous(images: List<ImagePaths>, module: JpsModule) {
    process(images, WARNING, "image with ambiguous definition (has both '.png' and '.gif' versions)", module) { image ->
      val extensions = image.files.map { ImageExtension.fromFile(it) }
      return@process GIF !in extensions || PNG !in extensions
    }
  }

  private fun checkSvgFallbackVersionsAreStubIcons(images: List<ImagePaths>, module: JpsModule) {
    val imagesWithSvgAndPng = IMAGES_WITH_BOTH_SVG_AND_PNG[module.name]
    val filteredImages = images.filter { !imagesWithSvgAndPng.contains(it.id) }

    process(filteredImages, WARNING, "SVG icons should use stub.png as fallback", module) { image ->
      if (image.files.none { ImageExtension.fromFile(it) == SVG }) return@process true

      val legacyFiles = image.files.filter { ImageExtension.fromFile(it) != SVG }
      return@process isStubFallbackVersion(legacyFiles)
    }
  }

  private fun checkOverridingFallbackVersionsAreStubIcons(images: List<ImagePaths>, module: JpsModule) {
    process(images, WARNING, "Overridden icons should be replaced with stub.png as fallback", module) { image ->
      if (image.deprecation?.replacement == null) return@process true

      return@process isStubFallbackVersion(image.files)
    }
  }

  private fun checkIconsWithBothSvgAndPng(images: List<ImagePaths>, module: JpsModule) {
    val imagesWithSvgAndPng = IMAGES_WITH_BOTH_SVG_AND_PNG[module.name]
    val filteredImages = images.filter { imagesWithSvgAndPng.contains(it.id) }

    if (filteredImages.size != imagesWithSvgAndPng.size) {
      val notFoundImagesIds = imagesWithSvgAndPng.toMutableSet().apply {
        filteredImages.forEach { this.remove(it.id) }
      }
      log(WARNING, "This icon should have both SVG and PNG versions, but was not found\n" +
                   "see ImageSanityCheckerBase.IMAGES_WITH_BOTH_SVG_AND_PNG", module,
          notFoundImagesIds.map { ImagePaths(it, module.sourceRoots.first()) })
    }

    process(filteredImages, WARNING, "This icon should have both SVG and PNG versions\n" +
                                     "see ImageSanityCheckerBase.IMAGES_WITH_BOTH_SVG_AND_PNG", module) { image ->
      val svgFiles = image.files.filter { ImageExtension.fromFile(it) == SVG }
      val pngFiles = image.files.filter { ImageExtension.fromFile(it) == PNG }

      return@process svgFiles.isNotEmpty() && !isStubFallbackVersion(pngFiles)
    }
  }

  private fun isStubFallbackVersion(files: List<File>): Boolean {
    if (files.isEmpty()) return true
    if (files.size > 1) return false

    val file = files.single()
    if (ImageType.fromFile(file) != BASIC) return false

    val md5 = md5(file)
    return md5 == STUB_PNG_MD5
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
