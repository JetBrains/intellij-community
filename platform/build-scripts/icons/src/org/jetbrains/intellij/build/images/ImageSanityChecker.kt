// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.images

import com.intellij.util.containers.ContainerUtil
import org.jetbrains.intellij.build.images.ImageExtension.*
import org.jetbrains.intellij.build.images.ImageType.*
import org.jetbrains.jps.model.module.JpsModule
import java.awt.Dimension
import java.nio.file.Path

abstract class ImageSanityCheckerBase(private val projectHome: Path, private val ignoreSkipTag: Boolean) {
  fun check(module: JpsModule, moduleConfig: IntellijIconClassGeneratorModuleConfig?) {
    val allImages = ImageCollector(projectHome = projectHome, iconsOnly = false, ignoreSkipTag = ignoreSkipTag,
                                   moduleConfig = moduleConfig).collect(module)

    val (images, broken) = allImages.partition { it.basicFile != null }
    log(Severity.ERROR, "image without base version", module, broken)

    checkHaveRetinaVersion(images, module)
    checkHaveCompleteIconSet(images, module)
    checkHaveValidSize(images, module)
    checkAreNotAmbiguous(images, module)
    checkNoDeprecatedIcons(images, module)
    checkNoSvgFallbackVersions(images, module)
    checkNoOverridingFallbackVersions(images, module)
  }

  private fun checkHaveRetinaVersion(images: List<ImageInfo>, module: JpsModule) {
    process(images, Severity.INFO, "image without retina version", module) { image ->
      return@process image.getFiles(RETINA, RETINA_DARCULA).isNotEmpty()
    }
  }

  private fun checkHaveCompleteIconSet(images: List<ImageInfo>, module: JpsModule) {
    process(images, Severity.WARNING, "image without complete set of additional icons", module) { image ->
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

  private val excludedFromSizeCheck = setOf(
    "/expui/run/rerun",
    "/expui/toolbar/unknown@20x20",
    "/expui/welcome/open",
    "/resharper/SolutionAnalysis/StatusBarIndicatorBackground"
  )

  private fun checkHaveValidSize(images: List<ImageInfo>, module: JpsModule) {
    process(images.filter { !excludedFromSizeCheck.contains(it.id) }, Severity.WARNING, "icon with suspicious size", module) { image ->
      if (!isIcon(image.basicFile!!)) {
        return@process true
      }

      image.files
        .groupBy { ImageExtension.fromFile(it) }
        .all { (ext, files) ->
          val basicSizes = files.filter { ImageType.fromFile(it) in setOf(BASIC, DARCULA, STROKE) }.mapNotNull { imageSize(it) }.toSet()
          val retinaSizes = files.filter { ImageType.fromFile(it) in setOf(RETINA, RETINA_DARCULA) }.mapNotNull { imageSize(it) }.toSet()

          if (basicSizes.size > 1 || retinaSizes.size > 1) {
            return@all false
          }
          if (basicSizes.size == 1 && retinaSizes.size == 1) {
            val sizeBasic = basicSizes.single()
            val sizeRetina = retinaSizes.single()
            if (ext == SVG) {
              return@all sizeBasic == sizeRetina
            }
            else {
              val sizeBasicTwice = Dimension(sizeBasic.width * 2, sizeBasic.height * 2)
              return@all sizeBasicTwice == sizeRetina
            }
          }
          return@all true
        }
    }
  }

  private fun checkAreNotAmbiguous(images: List<ImageInfo>, module: JpsModule) {
    process(images, Severity.WARNING, "image with ambiguous definition (has both '.png' and '.gif' versions)", module) { image ->
      val extensions = image.files.map { ImageExtension.fromFile(it) }
      GIF !in extensions || PNG !in extensions
    }
  }

  private fun checkNoDeprecatedIcons(images: List<ImageInfo>, module: JpsModule) {
    process(images, Severity.WARNING, "deprecated icons must be moved to /compatibilityResources", module) { image ->
      image.deprecation == null || image.files.isEmpty()
    }
  }

  private fun checkNoSvgFallbackVersions(images: List<ImageInfo>, module: JpsModule) {
    process(images, Severity.WARNING, "SVG icons should not use PNG icons as fallback", module) { image ->
      if (image.files.none { ImageExtension.fromFile(it) == SVG }) {
        return@process true
      }

      image.files.none { ImageExtension.fromFile(it) != SVG }
    }
  }

  private fun checkNoOverridingFallbackVersions(images: List<ImageInfo>, module: JpsModule) {
    process(images, Severity.WARNING, "Overridden icons should not use PNG icons as fallback", module) { image ->
      if (image.deprecation?.replacement == null) {
        true
      }
      else {
        image.files.isEmpty()
      }
    }
  }


  private inline fun process(images: List<ImageInfo>,
                             severity: Severity,
                             message: String,
                             module: JpsModule,
                             processor: (ImageInfo) -> Boolean) {
    val result = ArrayList<ImageInfo>()
    for (image in images) {
      if (!processor(image)) {
        result.add(image)
      }
    }
    log(severity = severity, message = message, module = module, images = result)
  }

  internal abstract fun log(severity: Severity, message: String, module: JpsModule, images: Collection<ImageInfo>)

  enum class Severity { INFO, WARNING, ERROR }
}

class ImageSanityChecker(projectHome: Path) : ImageSanityCheckerBase(projectHome, false) {
  // used with parallelStream()
  private val infos = ContainerUtil.createConcurrentList<String>()
  private val warnings = ContainerUtil.createConcurrentList<String>()

  fun printWarnings() {
    //if (infos.isNotEmpty()) {
    //  println()
    //  println(infos.joinToString("\n"))
    //}
    if (warnings.isNotEmpty()) {
      println()
      println(warnings.joinToString("\n"))
    }
    else {
      println()
      println("No warnings found")
    }
  }

  override fun log(severity: Severity, message: String, module: JpsModule, images: Collection<ImageInfo>) {
    val logger = when (severity) {
      Severity.ERROR -> warnings
      Severity.WARNING -> warnings
      Severity.INFO -> infos
    }
    val prefix = when (severity) {
      Severity.ERROR -> "ERROR:"
      Severity.WARNING -> "WARNING:"
      Severity.INFO -> "INFO:"
    }

    if (images.isEmpty()) return

    val lines = mutableListOf<String>()
    lines += "$prefix $message found in module '${module.name}'"
    images.sortedBy { it.id }.forEach {
      lines += "    ${it.id} - ${it.presentablePath}"
    }
    lines += ""

    logger.addAll(lines)
  }
}
