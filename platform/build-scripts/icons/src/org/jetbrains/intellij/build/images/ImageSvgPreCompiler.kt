// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ImageLoader
import com.intellij.util.SVGLoader
import com.intellij.util.SVGLoaderCacheIO
import com.intellij.util.SVGLoaderPrebuilt
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.exitProcess

/**
 * Works together with [SVGLoaderCache] to generate pre-cached icons
 */
class ImageSvgPreCompiler {
  /// the expected scales of images that we have
  /// the macOS touch bar uses 2.5x scale
  /// the application icon (which one?) is 4x on macOS
  private val scales = doubleArrayOf(1.0,
                                     1.25, /*Windows*/
                                     1.5, /*Windows*/
                                     2.0,
                                     2.5 /*macOS touchBar*/
  )

  /// the 4.0 scale is used on retina macOS for product icon, adds few more scales for few icons
  private val productIconScales = (scales + scales.map { it * 2 }).toSortedSet().toDoubleArray()

  private val productIconPrefixes = mutableListOf<String>()

  private val generatedSize = AtomicLong(0)
  private val totalFiles = AtomicLong(0)

  private data class Request(val from: Path, val to: Path)

  fun addProductIconPrefix(path: String) {
    productIconPrefixes += path.trim().replace('\\', '/').trim('/').removeSuffix(".svg")
  }

  fun preCompileIcons(modules: List<JpsModule>) {
    val allRoots: List<Request> = modules
      .mapNotNull { module ->
        val path = JpsJavaExtensionService.getInstance().getOutputDirectory(module, false)?.toPath() ?: return@mapNotNull null
        Request(from = path, to = path)
      }

    preCompileIconsImpl(allRoots)
  }

  private fun preCompileIconsImpl(requests: List<Request>) {
    requests.stream().parallel().forEach { req ->
      val rootDir = req.from
      if (!Files.isDirectory(rootDir)) {
        return@forEach
      }

      Files.walk(rootDir).parallel()
        .filter { file ->
          file.fileName.toString().endsWith(".svg") && Files.isRegularFile(file)
        }
        .unordered()
        .distinct()
        .forEach { fromFile ->
          val relativePath = rootDir.relativize(fromFile)
          val toFile = req.to.resolve(relativePath)

          val scales = when {
            productIconPrefixes.any { relativePath.toFile().path.startsWith(it) } -> {
              println("INFO Generating Product Icon scales for $relativePath")
              productIconScales
            }
            else -> scales
          }
          //TODO: use output directory
          preCompile(fromFile, toFile, scales)
        }
    }
  }

  fun printStats() {
    println()
    println("SVG generation: ${StringUtil.formatFileSize(generatedSize.get())} bytes in total in ${totalFiles.get()} files(s)")
  }

  private fun preCompile(svgFile: Path, targetFileBase: Path, scales: DoubleArray) {
    totalFiles.incrementAndGet()

    val data = Files.readAllBytes(svgFile)
    if (contains(data, dataPattern)) {
      println("WARN: image $svgFile uses data urls and will be skipped")
      return
    }

    val dir = targetFileBase.parent
    val fileName = targetFileBase.fileName.toString()
    Files.createDirectories(dir)
    for (scale in scales) {
      val dim = ImageLoader.Dimension2DDouble(0.0, 0.0)
      val image = SVGLoader.loadWithoutCache(svgFile.toUri().toURL(), data.inputStream(), scale, dim)

      val targetFile = dir.resolve(fileName + SVGLoaderPrebuilt.getPreBuiltImageURLSuffix(scale))
      SVGLoaderCacheIO.writeImageFile(targetFile, image, dim, false)

      val length = Files.size(targetFile)
      require(length > 0) { "File ${targetFile} is empty!" }
      generatedSize.addAndGet(length)
    }
  }

  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      try {
        mainImpl(args)
      }
      catch (t: Throwable) {
        System.err.println("Unexpected crash: ${t.message}")
        t.printStackTrace(System.err)
        exitProcess(1)
      }
    }

    private fun mainImpl(args: Array<String>) {
      println("Pre-building SVG images...")
      if (args.isEmpty()) {
        println("Usage: <tool> tasks_file product_icons*")
        println("")
        println("tasks_file: list of paths, every path on a new line: {<input dir>\\n<output dir>\\n}+")
        println("")
        exitProcess(1)
      }

      System.setProperty("java.awt.headless", "true")

      val argsFile = args.firstOrNull() ?: error("only one parameter is supported")
      val requests = File(argsFile).readLines().chunked(2).map { block ->
        if (block.size != 2) error("Invalid args format. Two paths were expected but was: $block")

        val (from, to) = block
        val fromFile = Paths.get(from)
        val toFile = Paths.get(to)

        Request(from = fromFile, to = toFile)
      }.toList()

      val productIcons = args.drop(1).toSortedSet()
      println("Expecting product icons: $productIcons")

      val compiler = ImageSvgPreCompiler()
      productIcons.forEach(compiler::addProductIconPrefix)
      compiler.preCompileIconsImpl(requests)
      compiler.printStats()
    }
  }
}

private val dataPattern = "data:image".toByteArray()

private fun contains(data: ByteArray, what: ByteArray): Boolean {
  outer@
  for (i in 0 until data.size - what.size + 1) {
    for (j in what.indices) {
      if (data[i + j] != what[j]) {
        continue@outer
      }
    }
    return true
  }
  return false
}