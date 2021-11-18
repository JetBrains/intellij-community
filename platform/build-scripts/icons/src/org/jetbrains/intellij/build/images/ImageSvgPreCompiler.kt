// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images

import com.intellij.openapi.util.text.Formats
import com.intellij.ui.svg.ImageValue
import com.intellij.ui.svg.SvgCacheManager
import com.intellij.ui.svg.SvgTranscoder
import com.intellij.ui.svg.createSvgDocument
import com.intellij.util.ImageLoader
import com.intellij.util.io.DigestUtil
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import org.apache.batik.transcoder.TranscoderException
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.mvstore.MVMap
import org.jetbrains.mvstore.MVStore
import org.jetbrains.mvstore.type.IntDataType
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.exitProcess

private class FileInfo(val file: Path) {
  companion object {
    fun digest(file: Path) = digest(loadAndNormalizeSvgFile(file).toByteArray())

    fun digest(fileNormalizedData: ByteArray): ByteArray = DigestUtil.sha512().digest(fileNormalizedData)
  }

  val checksum: ByteArray by lazy(LazyThreadSafetyMode.PUBLICATION) { digest(file) }
}

/**
 * Works together with [SvgCacheManager] to generate pre-cached icons
 */
internal class ImageSvgPreCompiler(private val compilationOutputRoot: Path? = null) {
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
  //private val productIconScales = (scales + scales.map { it * 2 }).toSortedSet().toFloatArray()

  //private val productIconPrefixes = mutableListOf<String>()

  private val totalFiles = AtomicLong(0)

  private val collisionGuard = ConcurrentHashMap<Int, FileInfo>()

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

    @JvmStatic
    fun optimize(dbFile: Path, compilationOutputRoot: Path, dirs: List<Path>) {
      val compiler = ImageSvgPreCompiler(compilationOutputRoot)
      compiler.compileIcons(dbFile, dirs)
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
      val dirs = Files.readAllLines(Path.of(argsFile)).map { Path.of(it) }

      val productIcons = args.drop(2).toSortedSet()
      println("Expecting product icons: $productIcons")

      val compiler = ImageSvgPreCompiler()
      // todo
      //productIcons.forEach(compiler::addProductIconPrefix)
      compiler.compileIcons(Path.of(dbFile), dirs)
    }
  }

  fun preCompileIcons(modules: List<JpsModule>, dbFile: Path) {
    val javaExtensionService = JpsJavaExtensionService.getInstance()
    compileIcons(dbFile, modules.mapNotNull { javaExtensionService.getOutputDirectory(it, false)?.toPath() })
  }

  fun compileIcons(dbFile: Path, dirs: List<Path>) {
    val storeBuilder = MVStore.Builder()
      .autoCommitBufferSize(128_1024)
      .keysPerPage(257)
      .backgroundExceptionHandler { e, _ -> throw e }
      // fast lz4 - 14 MB, high compression - 9 MB, so, we use high even if it consumes a lot of CPU (anyway, performed in parallel)
      .compressionLevel(2)
    val store = storeBuilder.truncateAndOpen(dbFile)
    try {
      val mapBuilder = MVMap.Builder<Int, ImageValue>()
      mapBuilder.keyType(IntDataType.INSTANCE)
      mapBuilder.valueType(ImageValue.ImageValueSerializer())

      val scaleToMap = ConcurrentHashMap<Float, MVMap<Int, ImageValue>>(scales.size * 2, 0.75f, 2)
      val getMapByScale: (scale: Float, isDark: Boolean) -> MutableMap<Int, ImageValue> = { k, isDark ->
        SvgCacheManager.getMap(k, isDark, scaleToMap, store, mapBuilder)
      }

      val rootRobotData = IconRobotsData(parent = null, ignoreSkipTag = false, usedIconsRobots = null)
      dirs.parallelStream().forEach { dir ->
        processDir(dir, dir, 1, rootRobotData, getMapByScale)
      }
    }
    finally {
      val span = GlobalOpenTelemetry.getTracer("build-script")
        .spanBuilder("save rasterized SVG database")
        .setAttribute("path", dbFile.toString())
        .setAttribute(AttributeKey.longKey("iconCount"), totalFiles.get())
        .startSpan()
      try {
        store.close()
        span.setAttribute(AttributeKey.stringKey("fileSize"), Formats.formatFileSize(Files.size(dbFile)))
      }
      finally {
        span.end()
      }
    }
  }

  private fun processDir(dir: Path,
                         rootDir: Path,
                         level: Int,
                         rootRobotData: IconRobotsData,
                         getMapByScale: (scale: Float, isDark: Boolean) -> MutableMap<Int, ImageValue>) {
    val stream = try {
      Files.newDirectoryStream(dir)
    }
    catch (e: NotDirectoryException) {
      return
    }
    catch (e: NoSuchFileException) {
      return
    }

    var idToVariants: MutableMap<String, MutableList<Path>>? = null

    stream.use {
      val robotData = rootRobotData.fork(dir, dir)
      for (file in stream) {
        val fileName = file.fileName.toString()
        if (level == 1) {
          if (isBlacklistedTopDirectory(fileName)) {
            continue
          }
        }

        if (robotData.isSkipped(file)) {
          continue
        }

        if (fileName.endsWith(".svg")) {
          if (idToVariants == null) {
            idToVariants = HashMap()
          }
          idToVariants!!.computeIfAbsent(getImageCommonName(fileName)) { mutableListOf() }.add(file)
        }
        else if (!fileName.endsWith(".class")) {
          processDir(file, rootDir, level + 1, rootRobotData.fork(file, rootDir), getMapByScale)
        }
      }
    }

    val idToVariants1 = idToVariants ?: return
    val keys = idToVariants1.keys.toTypedArray()
    keys.sort()
    val dimension = ImageLoader.Dimension2DDouble(0.0, 0.0)
    for (commonName in keys) {
      val variants = idToVariants1.getValue(commonName)
      variants.sort()
      try {
        processImage(variants, getMapByScale, dimension)
      }
      catch (e: TranscoderException) {
        throw RuntimeException("Cannot process $commonName (variants=$variants)", e)
      }
    }
  }

  private fun processImage(variants: List<Path>,
                           getMapByScale: (scale: Float, isDark: Boolean) -> MutableMap<Int, ImageValue>,
                           // just to reuse
                           dimension: ImageLoader.Dimension2DDouble) {
    //println("$id: ${variants.joinToString { rootDir.relativize(it).toString() }}")

    val light1x = variants[0]
    val light1xPath = light1x.toString()
    if (light1xPath.endsWith("@2x.svg") || light1xPath.endsWith("_dark.svg")) {
      throw IllegalStateException("$light1x doesn't have 1x light icon")
    }

    val light1xData = loadAndNormalizeSvgFile(light1x)
    if (light1xData.contains("data:image")) {
      Span.current().addEvent("image $light1x uses data urls and will be skipped")
      return
    }

    totalFiles.addAndGet(variants.size.toLong())

    // key is the same for all variants
    val light1xBytes = light1xData.toByteArray()
    val imageKey = getImageKey(light1xBytes, light1x.fileName.toString())
    if (checkCollision(imageKey, light1x, light1xBytes)) {
      return
    }

    val light2x = variants.find { it.toString().endsWith("@2x.svg") }
    for (scale in scales) {
      val document = createSvgDocument(null, if (scale >= 2 && light2x != null) Files.newInputStream(light2x) else light1xData.byteInputStream())
      addEntry(getMapByScale(scale, false), SvgTranscoder.createImage(scale, document, dimension), dimension, light1x, imageKey)
    }

    val dark2x = variants.find { it.toString().endsWith("@2x_dark.svg") }
    val dark1x = variants.find { it !== dark2x && it.toString().endsWith("_dark.svg") } ?: return
    for (scale in scales) {
      val document = createSvgDocument(null, Files.newInputStream(if (scale >= 2 && dark2x != null) dark2x else dark1x))
      val image = SvgTranscoder.createImage(scale, document, dimension)
      addEntry(getMapByScale(scale, true), image, dimension, dark1x, imageKey)
    }
  }

  private fun checkCollision(imageKey: Int, file: Path, fileNormalizedData: ByteArray): Boolean {
    val duplicate = collisionGuard.putIfAbsent(imageKey, FileInfo(file))
    if (duplicate == null) {
      return false
    }

    if (duplicate.checksum.contentEquals(FileInfo.digest(fileNormalizedData))) {
      Span.current().addEvent("${getRelativeToCompilationOutPath(duplicate.file)} duplicates ${getRelativeToCompilationOutPath(file)}")
      // skip - do not add
      return true
    }

    throw IllegalStateException("Hash collision:\n  file1=${duplicate.file},\n  file2=${file},\n  imageKey=$imageKey")
  }

  private fun getRelativeToCompilationOutPath(file: Path): Path {
    if (compilationOutputRoot != null && file.startsWith(compilationOutputRoot)) {
      return compilationOutputRoot.relativize(file)
    }
    else {
      return file
    }
  }
}

private fun addEntry(map: MutableMap<Int, ImageValue>,
                     image: BufferedImage,
                     dimension: ImageLoader.Dimension2DDouble,
                     file: Path,
                     imageKey: Int) {
  val newValue = SvgCacheManager.writeImage(image, dimension)
  //println("put ${(map as MVMap).id} $file : $imageKey")
  val oldValue = map.putIfAbsent(imageKey, newValue)
  // duplicated images - not yet clear should be forbid it or not
  if (oldValue != null && oldValue != newValue) {
    throw IllegalStateException("Hash collision for key $file (imageKey=$imageKey)")
  }
}

private fun getImageCommonName(fileName: String): String {
  for (p in listOf("@2x.svg", "@2x_dark.svg", "_dark.svg", ".svg")) {
    if (fileName.endsWith(p)) {
      return fileName.substring(0, fileName.length - p.length)
    }
  }

  throw IllegalStateException("Not a SVG: $fileName")
}