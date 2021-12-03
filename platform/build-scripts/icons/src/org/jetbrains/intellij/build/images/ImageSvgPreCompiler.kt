// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.images

import com.intellij.openapi.util.text.Formats
import com.intellij.ui.svg.ImageValue
import com.intellij.ui.svg.SvgCacheManager
import com.intellij.ui.svg.SvgTranscoder
import com.intellij.ui.svg.createSvgDocument
import com.intellij.util.io.DigestUtil
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import org.apache.batik.transcoder.TranscoderException
import org.jetbrains.ikv.builder.IkvWriter
import org.jetbrains.ikv.builder.sizeUnawareIkvWriter
import org.jetbrains.intellij.build.io.ByteBufferAllocator
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.mvstore.DataUtil
import org.jetbrains.mvstore.MVMap
import org.jetbrains.mvstore.type.IntDataType
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.atomic.AtomicInteger
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
  /// the 4.0 scale is used on retina macOS for product icon, adds few more scales for few icons
  //private val productIconScales = (scales + scales.map { it * 2 }).toSortedSet().toFloatArray()

  //private val productIconPrefixes = mutableListOf<String>()

  private val totalFiles = AtomicInteger(0)
  private val totalSize = AtomicInteger(0)

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
    fun optimize(dbDir: Path, compilationOutputRoot: Path, dirs: List<Path>): List<Path> {
      return ImageSvgPreCompiler(compilationOutputRoot).compileIcons(dbDir, dirs)
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

      val dbDir = args.getOrNull(0) ?: error("only one parameter is supported")
      val argsFile = args.getOrNull(1) ?: error("only one parameter is supported")
      val dirs = Files.readAllLines(Path.of(argsFile)).map { Path.of(it) }

      val productIcons = args.drop(2).toSortedSet()
      println("Expecting product icons: $productIcons")

      val compiler = ImageSvgPreCompiler()
      // todo
      //productIcons.forEach(compiler::addProductIconPrefix)
      compiler.compileIcons(Path.of(dbDir), dirs)
    }
  }

  fun preCompileIcons(modules: List<JpsModule>, dbFile: Path) {
    val javaExtensionService = JpsJavaExtensionService.getInstance()
    compileIcons(dbFile, modules.mapNotNull { javaExtensionService.getOutputDirectory(it, false)?.toPath() })
  }

  @Suppress("PropertyName")
  private class Stores(classifier: String, dbDir: Path) {
    val s1 = StoreContainer(1f, classifier, dbDir)
    val s1_25 = StoreContainer(1.25f, classifier, dbDir)
    val s1_5 = StoreContainer(1.5f, classifier, dbDir)
    val s2 = StoreContainer(2f, classifier, dbDir)
    val s2_5 = StoreContainer(2.5f, classifier, dbDir)

    fun close(list: MutableList<Path>) {
      s1.close()
      s1_25.close()
      s1_5.close()
      s2.close()
      s2_5.close()

      s1.file?.let { list.add(it) }
      s1_25.file?.let { list.add(it) }
      s1_5.file?.let { list.add(it) }
      s2.file?.let { list.add(it) }
      s2_5.file?.let { list.add(it) }
    }
  }

  private class StoreContainer(private val scale: Float, private val classifier: String, private val dbDir: Path) {
    @Volatile
    private var store: IkvWriter? = null
    var file: Path? = null
      private set

    fun getOrCreate() = store ?: getSynchronized()

    @Synchronized
    private fun getSynchronized(): IkvWriter {
      var store = store
      if (store == null) {
        val file = dbDir.resolve("icons-v1-$scale$classifier.db")
        this.file = file
        store = sizeUnawareIkvWriter(file)
        this.store = store
      }
      return store
    }

    fun close() {
      store?.close()
    }
  }

  fun compileIcons(dbDir: Path, dirs: List<Path>): List<Path> {
    Files.createDirectories(dbDir)

    val lightStores = Stores("", dbDir)
    val darkStores = Stores("-d", dbDir)
    val resultFiles = ArrayList<Path>()
    try {
      val mapBuilder = MVMap.Builder<Int, ImageValue>()
      mapBuilder.keyType(IntDataType.INSTANCE)
      mapBuilder.valueType(ImageValue.ImageValueSerializer())

      val getMapByScale: (scale: Float, isDark: Boolean) -> IkvWriter = { scale, isDark ->
        val list = if (isDark) darkStores else lightStores
        when (scale) {
          1f -> list.s1.getOrCreate()
          1.25f -> list.s1_25.getOrCreate()
          1.5f -> list.s1_5.getOrCreate()
          2f -> list.s2.getOrCreate()
          2.5f -> list.s2_5.getOrCreate()
          else -> throw UnsupportedOperationException("Scale $scale is not supported")
        }
      }

      val rootRobotData = IconRobotsData(parent = null, ignoreSkipTag = false, usedIconsRobots = null)
      val result = ForkJoinTask.invokeAll(dirs.map { dir ->
        ForkJoinTask.adapt(Callable {
          val result = mutableListOf<IconData>()
          processDir(dir, dir, 1, rootRobotData, result)
          result
        })
      }).flatMap { it.rawResult }

      val array: Array<IconData?> = result.toTypedArray()
      array.sortBy { it!!.variants.first() }

      /// the expected scales of images that we have
      /// the macOS touch bar uses 2.5x scale
      /// the application icon (which one?) is 4x on macOS
      val scales = floatArrayOf(
        1f,
        1.25f, /*Windows*/
        1.5f, /*Windows*/
        2.0f,
        2.5f /*macOS touchBar*/
      )

      val collisionGuard = ConcurrentHashMap<Int, FileInfo>()
      for ((index, icon) in array.withIndex()) {
        // key is the same for all variants
        // check collision only here - after sorting (so, we produce stable results as we skip same icon every time)
        if (checkCollision(icon!!.imageKey, icon.variants[0], icon.light1xData, collisionGuard)) {
          array[index] = null
        }
      }

      ForkJoinTask.invokeAll(scales.map { scale ->
        ForkJoinTask.adapt {
          ByteBufferAllocator().use { bufferAllocator ->
            for (icon in array) {
              processImage(icon = icon ?: continue, getMapByScale = getMapByScale, scale = scale, bufferAllocator = bufferAllocator)
            }
          }
        }
      })

      //println("${Formats.formatFileSize(totalSize.get().toLong())} (${totalSize.get()}, iconCount=${totalFiles.get()}, resultSize=${result.size})")
    }
    catch (e: Throwable) {
      try {
        lightStores.close(resultFiles)
        darkStores.close(resultFiles)
      }
      catch (e1: Throwable) {
        e.addSuppressed(e)
      }
      throw e
    }

    val span = GlobalOpenTelemetry.getTracer("build-script")
      .spanBuilder("close rasterized SVG database")
      .setAttribute("path", dbDir.toString())
      .setAttribute(AttributeKey.longKey("iconCount"), totalFiles.get().toLong())
      .startSpan()
    try {
      lightStores.close(resultFiles)
      darkStores.close(resultFiles)
      span.setAttribute(AttributeKey.stringKey("fileSize"), Formats.formatFileSize(totalSize.get().toLong()))
    }
    finally {
      span.end()
    }

    resultFiles.sort()
    return resultFiles
  }

  private class IconData(val light1xData: ByteArray, val variants: List<Path>, val imageKey: Int)

  private fun processDir(dir: Path,
                         rootDir: Path,
                         level: Int,
                         rootRobotData: IconRobotsData,
                         result: MutableCollection<IconData>) {
    val stream = try {
      Files.newDirectoryStream(dir)
    }
    catch (e: NotDirectoryException) {
      return
    }
    catch (e: NoSuchFileException) {
      return
    }

    val idToVariants = stream.use {
      var idToVariants: MutableMap<String, MutableList<Path>>? = null
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
          idToVariants.computeIfAbsent(getImageCommonName(fileName)) { mutableListOf() }.add(file)
        }
        else if (!fileName.endsWith(".class")) {
          processDir(file, rootDir, level + 1, rootRobotData.fork(file, rootDir), result)
        }
      }
      idToVariants ?: return
    }

    val keys = idToVariants.keys.toTypedArray()
    keys.sort()
    for (commonName in keys) {
      val variants = idToVariants.get(commonName)!!
      variants.sort()

      val light1x = variants[0]
      val light1xPath = light1x.toString()
      if (light1xPath.endsWith("@2x.svg") || light1xPath.endsWith("_dark.svg")) {
        throw IllegalStateException("$light1x doesn't have 1x light icon")
      }

      val light1xData = loadAndNormalizeSvgFile(light1x)
      if (light1xData.contains("data:image")) {
        Span.current().addEvent("image $light1x uses data urls and will be skipped")
        continue
      }

      val light1xBytes = light1xData.toByteArray()
      val imageKey = getImageKey(light1xBytes, light1x.fileName.toString())
      totalFiles.addAndGet(variants.size)
      try {
        result.add(IconData(light1xBytes, variants, imageKey))
      }
      catch (e: TranscoderException) {
        throw RuntimeException("Cannot process $commonName (variants=$variants)", e)
      }
    }
  }

  private fun processImage(icon: IconData,
                           getMapByScale: (scale: Float, isDark: Boolean) -> IkvWriter,
                           scale: Float,
                           bufferAllocator: ByteBufferAllocator) {
    //println("$id: ${variants.joinToString { rootDir.relativize(it).toString() }}")

    val variants = icon.variants
    // key is the same for all variants
    val imageKey = icon.imageKey

    val light2x = variants.find { it.toString().endsWith("@2x.svg") }
    var document = if (scale >= 2 && light2x != null) {
      createSvgDocument(null, Files.newInputStream(light2x))
    }
    else {
      createSvgDocument(null, icon.light1xData)
    }
    addEntry(getMapByScale(scale, false), SvgTranscoder.createImage(scale, document, null), imageKey, totalSize, bufferAllocator)

    val dark2x = variants.find { it.toString().endsWith("@2x_dark.svg") }
    val dark1x = variants.find { it !== dark2x && it.toString().endsWith("_dark.svg") } ?: return
    document = createSvgDocument(null, Files.newInputStream(if (scale >= 2 && dark2x != null) dark2x else dark1x))
    val image = SvgTranscoder.createImage(scale, document, null)
    addEntry(getMapByScale(scale, true), image, imageKey, totalSize, bufferAllocator)
  }

  private fun checkCollision(imageKey: Int, file: Path, fileNormalizedData: ByteArray,
                             collisionGuard: ConcurrentHashMap<Int, FileInfo>): Boolean {
    val duplicate = collisionGuard.putIfAbsent(imageKey, FileInfo(file))
    if (duplicate == null) {
      return false
    }

    if (duplicate.checksum.contentEquals(FileInfo.digest(fileNormalizedData))) {
      assert(duplicate.file !== file)
      Span.current().addEvent("${getRelativeToCompilationOutPath(duplicate.file)} duplicates ${getRelativeToCompilationOutPath(file)}")
      //println("${getRelativeToCompilationOutPath(duplicate.file)} duplicates ${getRelativeToCompilationOutPath(file)}")
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

private fun addEntry(map: IkvWriter, image: BufferedImage, imageKey: Int, totalSize: AtomicInteger, bufferAllocator: ByteBufferAllocator) {
  val w = image.width
  val h = image.height

  assert(image.type == BufferedImage.TYPE_INT_ARGB)
  assert(!image.colorModel.isAlphaPremultiplied)

  val data = (image.raster.dataBuffer as DataBufferInt).data
  val buffer = bufferAllocator.allocate(DataUtil.VAR_INT_MAX_SIZE * 2 + data.size * Int.SIZE_BYTES  + 1)
  if (w == h) {
    if (w < 254) {
      buffer.put(w.toByte())
    }
    else {
      buffer.put(255.toByte())
      writeVar(buffer, w)
    }
  }
  else {
    buffer.put(254.toByte())
    writeVar(buffer, w)
    writeVar(buffer, h)
  }

  buffer.asIntBuffer().put(data)
  buffer.position(buffer.position() + (data.size * Int.SIZE_BYTES))
  buffer.flip()
  totalSize.addAndGet(buffer.remaining())
  map.write(imageKey, buffer)
}

private fun writeVar(buf: ByteBuffer, value: Int) {
  if (value ushr 7 == 0) {
    buf.put(value.toByte())
  }
  else if (value ushr 14 == 0) {
    buf.put((value and 127 or 128).toByte())
    buf.put((value ushr 7).toByte())
  }
  else if (value ushr 21 == 0) {
    buf.put((value and 127 or 128).toByte())
    buf.put((value ushr 7 or 128).toByte())
    buf.put((value ushr 14).toByte())
  }
  else if (value ushr 28 == 0) {
    buf.put((value and 127 or 128).toByte())
    buf.put((value ushr 7 or 128).toByte())
    buf.put((value ushr 14 or 128).toByte())
    buf.put((value ushr 21).toByte())
  }
  else {
    buf.put((value and 127 or 128).toByte())
    buf.put((value ushr 7 or 128).toByte())
    buf.put((value ushr 14 or 128).toByte())
    buf.put((value ushr 21 or 128).toByte())
    buf.put((value ushr 28).toByte())
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