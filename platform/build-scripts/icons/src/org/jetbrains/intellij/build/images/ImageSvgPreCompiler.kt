// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")
@file:OptIn(ExperimentalCoroutinesApi::class)

package org.jetbrains.intellij.build.images

import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.Formats
import com.intellij.ui.svg.SvgCacheManager
import com.intellij.ui.svg.SvgTranscoder
import com.intellij.ui.svg.createSvgDocument
import com.intellij.util.io.DigestUtil
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import kotlinx.coroutines.*
import org.jetbrains.ikv.builder.IkvWriter
import org.jetbrains.ikv.builder.sizeUnawareIkvWriter
import org.jetbrains.intellij.build.io.ByteBufferAllocator
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

private class FileInfo(val file: Path) {
  companion object {
    fun digest(file: Path) = digest(loadAndNormalizeSvgFile(file).toByteArray())

    fun digest(fileNormalizedData: ByteArray): ByteArray = DigestUtil.sha512().digest(fileNormalizedData)
  }

  val checksum: ByteArray by lazy(LazyThreadSafetyMode.PUBLICATION) { digest(file) }
}

private val scales = floatArrayOf(
  1f,
  1.25f, /*Windows*/
  1.5f, /*Windows*/
  2.0f,
)

/**
 * Works together with [SvgCacheManager] to generate pre-cached icons
 */
internal class ImageSvgPreCompiler(private val compilationOutputRoot: Path? = null) {
  /// the 4.0 scale is used on retina macOS for product icon, adds a few more scales for few icons
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

    @Suppress("RAW_RUN_BLOCKING")
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
      runBlocking {
        compiler.compileIcons(Path.of(dbDir), dirs)
      }
    }
  }

  suspend fun preCompileIcons(modules: List<JpsModule>, dbFile: Path) {
    val javaExtensionService = JpsJavaExtensionService.getInstance()
    compileIcons(dbFile, modules.mapNotNull { javaExtensionService.getOutputDirectory(it, false)?.toPath() })
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private suspend fun compileIcons(dbDir: Path, dirs: List<Path>): List<Path> {
    val rootRobotData = IconRobotsData(parent = null, ignoreSkipTag = false, usedIconRobots = null)
    val result: MutableList<IconData> = withContext(Dispatchers.IO) {
      dirs.map { dir ->
        async {
          val result = mutableListOf<IconData>()
          processDir(dir = dir, rootDir = dir, level = 1, rootRobotData = rootRobotData, result = result)
          result
        }
      }
    }.flatMapTo(mutableListOf()) { it.getCompleted() }
    result.sortBy { it.variants.first() }

    NioFiles.deleteRecursively(dbDir)
    Files.createDirectories(dbDir)

    val lightStores = Stores("", dbDir)
    val darkStores = Stores("-d", dbDir)
    val resultFiles = ArrayList<Path>()

    try {
      val collisionGuard = Int2ObjectOpenHashMap<FileInfo>()
      val time = measureTimeMillis {
        ByteBufferAllocator().use { bufferAllocator ->
          for (icon in result) {
            // the key is the same for all variants
            // check collision only here - after sorting (so, we produce stable results as we skip the same icon every time)
            if (checkCollision(imageKey = icon.imageKey,
                               file = icon.variants[0],
                               fileNormalizedData = icon.light1xData,
                               collisionGuard = collisionGuard)) {
              continue
            }

            processImage(icon = icon, lightStores = lightStores, darkStores = darkStores, bufferAllocator = bufferAllocator)
          }
        }
      }
      println("$time ms")

      println("${Formats.formatFileSize(totalSize.get().toLong())} (${totalSize.get()}, iconCount=${totalFiles.get()}, resultSize=${result.size})")
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
          processDir(dir = file, rootDir = rootDir, level = level + 1, rootRobotData = rootRobotData.fork(file, rootDir), result = result)
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
      val imageKey = getImageKey(fileData = light1xBytes, fileName = light1x.fileName.toString())
      totalFiles.addAndGet(variants.size)
      result.add(IconData(light1xData = light1xBytes, variants = variants, imageKey = imageKey))
    }
  }

  private fun processImage(icon: IconData, lightStores: Stores, darkStores: Stores, bufferAllocator: ByteBufferAllocator) {
    //println("$id: ${variants.joinToString { rootDir.relativize(it).toString() }}")

    val variants = icon.variants
    // the key is the same for all variants
    val imageKey = icon.imageKey
    //val light1x = SVGDOM(Data.makeFromBytes(inlineSvgStyles(icon.light1xData.decodeToString())))
    val light1x = createSvgDocument(data = icon.light1xData)
    val light2x = variants.firstOrNull { it.toString().endsWith("@2x.svg") }?.let { file ->
      //SVGDOM(Data.makeFromBytes(inlineSvgStyles(Files.readString(it))))
      Files.newInputStream(file).use { createSvgDocument(inputStream = it, uri = "@2x") }
    }

    val dark2xFile = variants.find { it.toString().endsWith("@2x_dark.svg") }
    //val dark1x = variants.find { it !== dark2xFile && it.toString().endsWith("_dark.svg") }?.let { createSvgDom(it) }
    val dark1x = variants.find { it !== dark2xFile && it.toString().endsWith("_dark.svg") }?.let { file ->
      Files.newInputStream(file).use(::createSvgDocument)
    }
    //val dark2x = dark2xFile?.let { createSvgDom(it) }
    val dark2x = dark2xFile?.let { file -> Files.newInputStream(file).use { createSvgDocument(inputStream = it, uri = "@2x") } }

    for (scale in scales) {
      addEntry(map = getMapByScale(list = lightStores, scale = scale),
               //bitmap = renderSvgUsingSkia(svg = if (scale >= 2 && light2x != null) light2x else light1x, scale = scale),
               bitmap = SvgTranscoder.createImage(scale = scale, document = if (scale >= 2 && light2x != null) light2x else light1x),
               imageKey = imageKey,
               totalSize = totalSize,
               bufferAllocator = bufferAllocator)

      addEntry(map = getMapByScale(list = darkStores, scale = scale),
               //bitmap = renderSvgUsingSkia(svg = if (scale >= 2 && dark2x != null) dark2x else (dark1x ?: continue), scale = scale),
               bitmap = SvgTranscoder.createImage(scale = scale, document = if (scale >= 2 && dark2x != null) dark2x else (dark1x ?: continue)),
               imageKey = imageKey,
               totalSize = totalSize,
               bufferAllocator = bufferAllocator)
    }
  }

  private fun checkCollision(imageKey: Int,
                             file: Path,
                             fileNormalizedData: ByteArray,
                             collisionGuard: Int2ObjectOpenHashMap<FileInfo>): Boolean {
    val duplicate = collisionGuard.putIfAbsent(imageKey, FileInfo(file)) ?: return false
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

private fun getMapByScale(list: Stores, scale: Float): IkvWriter {
  return when (scale) {
    1f -> list.s1.getOrCreate()
    1.25f -> list.s1_25.getOrCreate()
    1.5f -> list.s1_5.getOrCreate()
    2f -> list.s2.getOrCreate()
    2.5f -> list.s2_5.getOrCreate()
    else -> throw UnsupportedOperationException("Scale $scale is not supported")
  }
}

private class IconData(@JvmField val light1xData: ByteArray,
                       @JvmField val variants: List<Path>,
                       @JvmField val imageKey: Int)

@Suppress("DuplicatedCode")
private fun addEntry(map: IkvWriter, /*bitmap: Bitmap*/bitmap: BufferedImage, imageKey: Int, totalSize: AtomicInteger, bufferAllocator: ByteBufferAllocator) {
  val w = bitmap.width
  val h = bitmap.height

  map.write(map.entry(imageKey)) { channel, position ->
    var currentPosition = position

    val headerBuffer = writeHeader(bufferAllocator = bufferAllocator, w = w, h = h)
    headerBuffer.flip()
    currentPosition = writeData(currentPosition, channel, headerBuffer, totalSize)

    //val pixelNativePointer = bitmap.peekPixels()!!.addr
    //val pixelBuffer = BufferUtil.getByteBufferFromPointer(pixelNativePointer, bitmap.rowBytes * bitmap.height)
    //if (bitmap.colorInfo.colorType == ColorType.BGRA_8888) {
    //  currentPosition = writeData(currentPosition, channel, pixelBuffer, totalSize)
    //}
    //else {
    //  throw UnsupportedOperationException(bitmap.colorInfo.colorType.toString())
    //}

    assert(bitmap.type == BufferedImage.TYPE_INT_ARGB)
    assert(!bitmap.colorModel.isAlphaPremultiplied)

    val data = (bitmap.raster.dataBuffer as DataBufferInt).data
    val buffer = ByteBuffer.allocateDirect(data.size * Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    buffer.asIntBuffer().put(data)

    currentPosition = writeData(currentPosition, channel, buffer, totalSize)

    currentPosition
  }
}

private fun writeData(position: Long, channel: FileChannel, buffer: ByteBuffer, totalSize: AtomicInteger): Long {
  totalSize.addAndGet(buffer.remaining())

  var currentPosition = position
  do {
    currentPosition += channel.write(buffer, currentPosition)
  }
  while (buffer.hasRemaining())
  return currentPosition
}

private fun writeHeader(bufferAllocator: ByteBufferAllocator, w: Int, h: Int): ByteBuffer {
  val headerBuffer = bufferAllocator.allocate(Int.SIZE_BYTES + 5 * 2 * Int.SIZE_BYTES + 1)
  if (w == h) {
    if (w < 254) {
      headerBuffer.put(w.toByte())
    }
    else {
      headerBuffer.put(255.toByte())
      writeVar(headerBuffer, w)
    }
  }
  else {
    headerBuffer.put(254.toByte())
    writeVar(headerBuffer, w)
    writeVar(headerBuffer, h)
  }
  return headerBuffer
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
      val file = dbDir.resolve("icon-v4-$scale$classifier.db")
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
