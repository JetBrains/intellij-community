// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images

import com.intellij.openapi.util.text.Formats
import com.intellij.ui.svg.ImageValue
import com.intellij.ui.svg.MyTranscoder
import com.intellij.ui.svg.SaxSvgDocumentFactory
import com.intellij.ui.svg.SvgCacheManager
import com.intellij.util.ImageLoader
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.mvstore.MVMap
import org.jetbrains.mvstore.MVStore
import org.jetbrains.mvstore.type.LongDataType
import org.xml.sax.InputSource
import java.nio.file.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.exitProcess

/**
 * Works together with [SvgCacheManager] to generate pre-cached icons
 */
internal class ImageSvgPreCompiler {
  /// the expected scales of images that we have
  /// the macOS touch bar uses 2.5x scale
  /// the application icon (which one?) is 4x on macOS
  private val scales = floatArrayOf(1f,
                                     1.25f, /*Windows*/
                                     1.5f, /*Windows*/
                                     2.0f,
                                     2.5f /*macOS touchBar*/
  )

  /// the 4.0 scale is used on retina macOS for product icon, adds few more scales for few icons
  private val productIconScales = (scales + scales.map { it * 2 }).toSortedSet().toFloatArray()

  private val productIconPrefixes = mutableListOf<String>()

  private val totalFiles = AtomicLong(0)

  fun preCompileIcons(modules: List<JpsModule>, dbFile: Path) {
    val javaExtensionService = JpsJavaExtensionService.getInstance()
    compileIcons(dbFile, modules.mapNotNull { javaExtensionService.getOutputDirectory(it, false)?.toPath() })
  }

  fun compileIcons(dbFile: Path, dirs: List<Path>) {
    val storeBuilder = MVStore.Builder()
      .autoCommitBufferSize(128_1024)
      .backgroundExceptionHandler { _, e: Throwable -> throw e }
      .compressionLevel(2)
    val store = storeBuilder.truncateAndOpen(dbFile)
    try {
      val scaleToMap = ConcurrentHashMap<Float, MVMap<Long, ImageValue>>(2, 0.75f, 2)

      val mapBuilder = MVMap.Builder<Long, ImageValue>()
      mapBuilder.keyType(LongDataType.INSTANCE)
      mapBuilder.valueType(ImageValue.ImageValueSerializer())

      val getMapByScale: (scale: Float, isDark: Boolean) -> MutableMap<Long, ImageValue> = { k, isDark ->
        SvgCacheManager.getMap(k, isDark, scaleToMap, store, mapBuilder)
      }

      val rootRobotData = IconRobotsData(parent = null, ignoreSkipTag = false, usedIconsRobots = null)
      dirs.stream().parallel().forEach { dir ->
        processDir(dir, dir, 1, rootRobotData, getMapByScale)
      }
    }
    finally {
      println("Saving rasterized SVG database (${totalFiles.get()} icons)...")
      store.close()
      println("Saved rasterized SVG database (size=${Formats.formatFileSize(Files.size(dbFile))}, path=$dbFile)")
    }
  }

  private fun processDir(dir: Path,
                         rootDir: Path,
                         level: Int,
                         rootRobotData: IconRobotsData,
                         getMapByScale: (scale: Float, isDark: Boolean) -> MutableMap<Long, ImageValue>) {
    val stream = try {
      Files.newDirectoryStream(dir)
    }
    catch (e: NotDirectoryException) {
      return
    }
    catch (e: NoSuchFileException) {
      return
    }

    var svgFiles: MutableList<Path>? = null

    stream.use {
      val robotData = rootRobotData.fork(dir, dir)
      for (file in stream) {
        if (level == 1) {
          if (isBlacklistedTopDirectory(file.fileName.toString())) {
            continue
          }
        }

        if (robotData.isSkipped(file)) {
          continue
        }

        if (file.toString().endsWith(".svg")) {
          if (svgFiles == null) {
            svgFiles = ArrayList()
          }
          svgFiles!!.add(file)
        }
        else {
          processDir(file, rootDir, level + 1, rootRobotData.fork(file, rootDir), getMapByScale)
        }
      }
    }

    val list = svgFiles ?: return
    list.sort()
    processSvgFiles(list, rootDir, getMapByScale)
  }

  private fun processSvgFiles(list: List<Path>,
                              rootDir: Path,
                              getMapByScale: (scale: Float, isDark: Boolean) -> MutableMap<Long, ImageValue>) {
    val svgDocumentFactory = SaxSvgDocumentFactory()
    var i = 0
    while (i < list.size) {
      var file = list[i]
      val relativePath = rootDir.relativize(file)
      val scales = when {
        productIconPrefixes.any { relativePath.toString().startsWith(it) } -> {
          println("INFO Generating Product Icon scales for $relativePath")
          productIconScales
        }
        else -> scales
      }

      var x2: Path? = null
      val nextIndex = i + 1
      if (nextIndex < list.size && list[nextIndex].toString().endsWith("@2x.svg")) {
        x2 = list[nextIndex]
        i++
      }
      else if (file.toString().endsWith("@2x_dark.svg")) {
        // @2x_dark.svg > _dark.svg, so use prev
        x2 = file
        if (nextIndex == list.size || list[nextIndex].fileName.toString() != x2.fileName.toString().replace("@2x", "")) {
          throw IllegalStateException("No regular icon for 2x: $file")
        }

        file = list[nextIndex]
        i++
      }

      preCompile(file, x2, svgDocumentFactory, scales, getMapByScale)

      i++
    }
  }

  private fun preCompile(svgFile: Path,
                         x2: Path?,
                         svgDocumentFactory: SaxSvgDocumentFactory,
                         scales: FloatArray,
                         getMapByScale: (scale: Float, isDark: Boolean) -> MutableMap<Long, ImageValue>) {
    val data = loadAndNormalizeSvgFile(svgFile)
    totalFiles.incrementAndGet()

    if (data.contains("data:image")) {
      println("WARN: image $svgFile uses data urls and will be skipped")
      return
    }

    // key is the same for all variants
    val imageKey = getImageKey(data.toByteArray())
    val dimension = ImageLoader.Dimension2DDouble(0.0, 0.0)
    for (scale in scales) {
      dimension.setSize(0.0, 0.0)

      val svgPath = svgFile.toString()
      val inputSource = if (scale >= 2 && x2 != null) InputSource(Files.readAllBytes(x2).inputStream()) else InputSource(data.reader())
      inputSource.systemId = svgPath
      val document = svgDocumentFactory.createDocument(svgPath, inputSource)
      val image = MyTranscoder.createImage(scale, document, dimension)

      val map = getMapByScale(scale, svgPath.endsWith("_dark.svg"))
      val newValue = SvgCacheManager.writeImage(image, dimension)
      val oldValue = map.putIfAbsent(imageKey, newValue)
      if (oldValue != null) {
        if (oldValue == newValue) {
          // duplicated images - not yet clear should be forbid it or not
          return
        }
        throw IllegalStateException("Hash collision for key $svgFile")
      }
    }
  }

  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      try {
        mainImpl(args)
      }
      catch (e: Throwable) {
        System.err.println("Unexpected crash: ${e.message}")
        e.printStackTrace(System.err)
        exitProcess(1)
      }

      exitProcess(0)
    }

    private fun mainImpl(args: Array<String>) {
      println("Pre-building SVG images...")
      if (args.isEmpty()) {
        println("Usage: <tool> dbFile tasks_file product_icons*")
        println("")
        println("tasks_file: list of paths, every path on a new line: {<input dir>\\n}+")
        println("")
        exitProcess(1)
      }

      System.setProperty("java.awt.headless", "true")

      val dbFile = args.getOrNull(0) ?: error("only one parameter is supported")
      val argsFile = args.getOrNull(1) ?: error("only one parameter is supported")
      val dirs = Files.readAllLines(Paths.get(argsFile)).map { Paths.get(it) }

      val productIcons = args.drop(2).toSortedSet()
      println("Expecting product icons: $productIcons")

      val compiler = ImageSvgPreCompiler()
      // todo
      //productIcons.forEach(compiler::addProductIconPrefix)
      compiler.compileIcons(Paths.get(dbFile), dirs)
    }
  }
}