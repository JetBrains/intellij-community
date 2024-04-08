// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation

import com.intellij.codeInsight.documentation.render.CachingDataReader
import com.intellij.diagnostic.VMOptions
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.platform.diagnostic.telemetry.helpers.Milliseconds
import com.intellij.platform.util.coroutines.namedChildScope
import com.intellij.util.MemorySizeAware
import com.intellij.util.io.URLUtil
import com.intellij.util.ui.html.image.*
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import org.jetbrains.annotations.TestOnly
import java.awt.image.BufferedImage
import java.io.IOException
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.net.URL
import java.net.URLConnection
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.max

@Service(Service.Level.APP)
class CachingAdaptiveImageManagerService(val coroutineScope: CoroutineScope) : AdaptiveImagesManager, Disposable {
  companion object {
    @JvmStatic
    fun getInstance(): CachingAdaptiveImageManagerService = ApplicationManager.getApplication().getService(CachingAdaptiveImageManagerService::class.java)
  }

  private val initialTimeNs = System.nanoTime()

  private val delegate: CachingAdaptiveImageManager = CachingAdaptiveImageManager(
    coroutineScope = coroutineScope,
    contentLoadContext = Dispatchers.IO,
    rasterizationContext = Dispatchers.Default,
    contentLoader = ::getImageSourceData,
    svgRasterizer = ::rasterizeSVGImage,
    sourceResolver = ::adaptiveImageOriginToSource,
    timeProvider = { Milliseconds((System.nanoTime() - initialTimeNs) / 1_000_000) },
    maxSize = determineCacheSize("doc.render.image.cache.size")
  )

  init {
    Disposer.register(this, delegate)
    ApplicationManager.getApplication().messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: MutableList<out VFileEvent>) = delegate.processVfsEvents(events)
    })
  }

  override fun createRenderer(rendererScope: CoroutineScope, eventListener: (AdaptiveImageRendererEvent) -> Unit) =
    delegate.createRenderer(rendererScope, eventListener)


  override fun createRenderer(eventListener: (AdaptiveImageRendererEvent) -> Unit) =
    delegate.createRenderer(eventListener)

  override fun dispose() = Unit
}

internal fun determineCacheSize(sizeRegistryKey: String): Long {
  var memorySizeMb = 750 // default value, if something goes wrong
  try {
    memorySizeMb = VMOptions.readOption(VMOptions.MemoryKind.HEAP, true)
  }
  catch (e: Throwable) {
    logger<CachingAdaptiveImageManagerService>().error("Failed to get Xmx", e)
  }

  var cacheSize = 20_000_00L // minimum value
  try {
    cacheSize = max(cacheSize, (memorySizeMb.toLong() * 1024 * 1024 * Registry.get(sizeRegistryKey).asDouble()).toLong())
  }
  catch (e: Throwable) {
    logger<CachingAdaptiveImageManagerService>().error("Error calculating cache size limit", e)
  }

  return cacheSize
}

data class VfsAdaptiveImageSource(val virtualFile: VirtualFile) : AdaptiveImageSource {
  override fun toString() = "VfsAdaptiveImageSource(${virtualFile})"
}

data class UrlAdaptiveImageSource(val url: String) : AdaptiveImageSource {
  override fun toString() = "UrlAdaptiveImageSource(${url})"
}

internal fun getUrlData(url: URL): DataWithMimeType {
  val connection = url.openConnection()
  val bytes = connection.getInputStream().use { it.readBytes() }
  return DataWithMimeType(bytes, connection.contentType)
}

internal fun getUrlDataCaching(urlStr: String): DataWithMimeType {
  val url = URL(urlStr)
  val contentType = URLConnection.getFileNameMap().getContentTypeFor(url.path)
                    ?: return getUrlData(url)

  val bytes = CachingDataReader.getInstance().getInputStream(url)?.use { it.readBytes() }
              ?: throw IOException("Unable to fetch url $urlStr")
  return DataWithMimeType(bytes, contentType)
}

internal fun getVfsData(virtualFile: VirtualFile): DataWithMimeType {
  val contentType = URLConnection.getFileNameMap().getContentTypeFor(virtualFile.name)
                    ?: throw IllegalArgumentException("Failed to get file mime type for $virtualFile")
  val bytes = virtualFile.contentsToByteArray()
  return DataWithMimeType(bytes, contentType)
}

fun getImageSourceData(src: AdaptiveImageSource): DataWithMimeType {
  return when (src) {
    is DataUrlAdaptiveImageSource -> DataWithMimeType(src.dataUrl.data, src.dataUrl.contentType)
    is VfsAdaptiveImageSource -> getVfsData(src.virtualFile)
    is UrlAdaptiveImageSource -> getUrlDataCaching(src.url)
    else -> throw IllegalArgumentException("Unsupported AdaptiveImageSource: $src")
  }
}

fun adaptiveImageOriginToSource(origin: AdaptiveImageOrigin): AdaptiveImageSource? {
  return when (origin) {
    is AdaptiveImageOrigin.DataUrl -> DataUrlAdaptiveImageSource(origin.dataUrl)
    is AdaptiveImageOrigin.Url -> {
      val vfsUrl = VfsUtilCore.convertFromUrl(URL(origin.url))
      val protocolSeparator = vfsUrl.indexOf(URLUtil.SCHEME_SEPARATOR).takeIf { it >= 0 } ?: return null
      val protocol = vfsUrl.substring(0, protocolSeparator)
      when (protocol) {
        URLUtil.JAR_PROTOCOL, URLUtil.FILE_PROTOCOL -> {
          val virtualFile = VirtualFileManager.getInstance().findFileByUrl(vfsUrl)
          virtualFile?.let { VfsAdaptiveImageSource(it) }
        }
        else -> UrlAdaptiveImageSource(origin.url)
      }

    }
    else -> throw IllegalArgumentException("Unsupported adaptive image origin '$origin'")
  }
}


/**
 * Manages [UnloadableAdaptiveImage] and [UnloadableRasterizedImage] instances
 * All public methods should be called from the EDT thread / EDT dispatcher
 */
class CachingAdaptiveImageManager(
  val coroutineScope: CoroutineScope,
  val contentLoadContext: CoroutineContext = EmptyCoroutineContext,
  val rasterizationContext: CoroutineContext = EmptyCoroutineContext,
  val contentLoader: suspend (AdaptiveImageSource) -> DataWithMimeType,
  val svgRasterizer: (SVGRasterizationConfig) -> RasterizedVectorImage,
  val sourceResolver: (AdaptiveImageOrigin) -> AdaptiveImageSource?,
  val unloadListener: ((Unloadable<*,*>) -> Unit)? = null,
  val timeProvider: () -> Milliseconds,
  val maxSize: Long,
) : AdaptiveImagesManager, MemorySizeAware, Disposable {

  private val supervisor: CompletableJob = SupervisorJob(coroutineScope.coroutineContext.job)

  internal class RasterizationIntention(
    val deferred: CompletableDeferred<UnloadableRasterizedImage>,
    var numWaiting: Int = 0
  )

  private val myLoadedImagesCache = UnloadableCache<AdaptiveImageSource, LoadedAdaptiveImage, UnloadableAdaptiveImage>()
  private val myRasterizedImagesCache = UnloadableCache<SVGRasterizationConfig, RasterizedVectorImage, UnloadableRasterizedImage>()

  private val myImagesBeingLoaded = HashMap<AdaptiveImageSource, Deferred<UnloadableAdaptiveImage>>()
  private val myImagesBeingRasterized = HashMap<SVGRasterizationConfig, Deferred<UnloadableRasterizedImage>>()
  private val myRasterizationIntentions = HashMap<SVGRasterizationConfig, RasterizationIntention>()

  private val myRendererRefQueue = ReferenceQueue<AdaptiveImageRendererImpl>()
  private val myVirtualFileToRenderersMap = HashMap<VirtualFile, MutableList<RendererRef>>()

  suspend fun loadAdaptiveImage(src: AdaptiveImageSource): UnloadableAdaptiveImage {
    processRendererRefQueue()
    myLoadedImagesCache.get(src)?.also { return it }

    val deferred = myImagesBeingLoaded.computeIfAbsent(src) {
      coroutineScope.async(contentLoadContext + supervisor + CoroutineName("loading $src")) {
        UnloadableAdaptiveImage(src, loadAdaptiveImage(contentLoader(src), src.toString()))
      }
    }

    val newValue = try {
      deferred.await()
    }
    finally {
      myImagesBeingLoaded.remove(src)
    }

    myLoadedImagesCache.register(newValue)
    enforceMaxSizeLimit()
    return newValue
  }

  internal inline fun <T> withRasterizationIntention(config: SVGRasterizationConfig, block: (Deferred<UnloadableRasterizedImage>) -> T): T {
    val intention = myRasterizationIntentions.computeIfAbsent(config) { RasterizationIntention(CompletableDeferred()) }
    intention.numWaiting++

    try {
      return block(intention.deferred)
    }
    finally {
      intention.numWaiting--
      if (intention.numWaiting <= 0) {
        myRasterizationIntentions.remove(config)
      }
    }
  }

  internal suspend fun rasterizeSVGImage(rasterizationConfig: SVGRasterizationConfig): UnloadableRasterizedImage {
    processRendererRefQueue()

    myRasterizedImagesCache.get(rasterizationConfig)?.also { return it }

    val deferred = myImagesBeingRasterized.computeIfAbsent(rasterizationConfig) {
      val name = "rasterizing ${it.svgImage.src} ${it.logicalWidth}x${it.logicalHeight}@${it.scale}"
      coroutineScope.async(rasterizationContext + supervisor + CoroutineName(name)) {
        UnloadableRasterizedImage(it, svgRasterizer(it))
      }
    }

    val newValue = try {
      deferred.await()
    }
    finally {
      myImagesBeingRasterized.remove(rasterizationConfig)
    }

    myRasterizationIntentions.remove(rasterizationConfig)?.deferred?.complete(newValue)

    myRasterizedImagesCache.register(newValue)
    enforceMaxSizeLimit()
    return newValue
  }

  internal fun tryGetRasterizedSVGFromCache(rasterizationConfig: SVGRasterizationConfig) : UnloadableRasterizedImage? =
    myRasterizedImagesCache.get(rasterizationConfig)

  internal fun processVfsEvents(events: MutableList<out VFileEvent>) {
    processRendererRefQueue()
    for (event in events) {
      val virtualFile = event.file ?: continue
      val image = myLoadedImagesCache.get(VfsAdaptiveImageSource(virtualFile))
      val imageWasLoaded = image?.value != null
      image?.unload()
      myVirtualFileToRenderersMap[virtualFile]?.let { ArrayList(it) }?.forEach { it.get()?.invalidate(imageWasLoaded) }
    }
  }

  private fun enforceMaxSizeLimit() {
    while (memorySize > maxSize) {
      val loadedItem = myLoadedImagesCache.getLRUValue()
      val rasterizedItem = myRasterizedImagesCache.getLRUValue()

      val itemToUnload = if (loadedItem != null && rasterizedItem != null) {
        if (rasterizedItem.lastUsedNs < loadedItem.lastUsedNs) rasterizedItem else loadedItem
      }
      else loadedItem ?: rasterizedItem

      if (itemToUnload == null) {
        thisLogger().warn("no items to unload, but size $memorySize is still over max $maxSize")
        return
      }

      itemToUnload.unload()
      unloadListener?.also { it(itemToUnload) }
    }
  }

  private fun processRendererRefQueue() {
    val filesToClean = HashSet<VirtualFile>()
    while (true) {
      val ref = (myRendererRefQueue.poll() as RendererRef?) ?: break
      filesToClean.addAll(ref.dependencies)
    }

    for (f in filesToClean) {
      val list = myVirtualFileToRenderersMap[f] ?: continue
      list.removeIf { it.get() == null }
      if (list.isEmpty()) {
        myVirtualFileToRenderersMap.remove(f)
      }
    }
  }

  internal fun updateRendererDependencies(rendererRef: RendererRef, newImage: UnloadableAdaptiveImage?) {
    val oldDeps = rendererRef.dependencies
    val src = newImage?.src
    rendererRef.dependencies = if (src is VfsAdaptiveImageSource) listOf(src.virtualFile) else listOf()

    for (f in oldDeps) {
      myVirtualFileToRenderersMap[f]?.removeIf { it == rendererRef }
    }

    for (f in rendererRef.dependencies) {
      myVirtualFileToRenderersMap.computeIfAbsent(f) { ArrayList() }.add(rendererRef)
    }
  }

  override fun getMemorySize() = myLoadedImagesCache.memorySize + myRasterizedImagesCache.memorySize

  override fun createRenderer(rendererScope: CoroutineScope, eventListener: (AdaptiveImageRendererEvent) -> Unit): AdaptiveImageRenderer {
    processRendererRefQueue()

    val renderer = AdaptiveImageRendererImpl(rendererScope, this, eventListener, timeProvider)
    renderer.rendererRef = RendererRef(renderer, myRendererRefQueue)
    return renderer
  }

  override fun createRenderer(eventListener: (AdaptiveImageRendererEvent) -> Unit) =
    createRenderer(GlobalScope.namedChildScope("AdaptiveImageRenderer", Dispatchers.EDT), eventListener)


  override fun dispose() {
    if (!supervisor.isCancelled) {
      supervisor.cancel()
    }
  }

  @TestOnly
  fun getImagesBeingLoadedCount() = myImagesBeingLoaded.size

  @TestOnly
  fun getImagesBeingRasterizedCount() = myImagesBeingRasterized.size

  @TestOnly
  fun testProcessVfsEvents(events: MutableList<out VFileEvent>) = processVfsEvents(events)

  @TestOnly
  fun conditionalUnloadImages(predicate: (UnloadableAdaptiveImage) -> Boolean) = myLoadedImagesCache.conditionalUnload(predicate)

  @TestOnly
  fun getCachedLoadedImages() = myLoadedImagesCache.values

  @TestOnly
  fun getCachedRasterizedImages() = myRasterizedImagesCache.values

  @TestOnly
  fun getVirtualFileToRenderersMappingCount(): Int {
    processRendererRefQueue()
    return myVirtualFileToRenderersMap.size
  }
}

internal class RendererRef(r: AdaptiveImageRendererImpl, refQueue: ReferenceQueue<AdaptiveImageRendererImpl>)
  : WeakReference<AdaptiveImageRendererImpl>(r, refQueue)
{
  var dependencies: List<VirtualFile> = emptyList()
}

const val RENDER_THROTTLE_MS = 100L

@OptIn(ExperimentalCoroutinesApi::class)
internal class AdaptiveImageRendererImpl(
  private val coroutineScope: CoroutineScope,
  private val adaptiveImageManager: CachingAdaptiveImageManager,
  private val eventListener: (AdaptiveImageRendererEvent) -> Unit,
  val timeProvider: () -> Milliseconds,
) : AdaptiveImageRenderer {
  internal lateinit var rendererRef: RendererRef

  private data class ConfigSnapshot(val src: AdaptiveImageSource?, val width: Float, val height: Float, val scale: Float)
  private class NonBlockingRenderResult(val image: BufferedImage?, val shouldReRender: Boolean)

  private var myAdaptiveImage: UnloadableAdaptiveImage? = null
    set(value) {
      adaptiveImageManager.updateRendererDependencies(rendererRef, value)
      field = value
    }
  private var myRasterizedImage: UnloadableRasterizedImage? = null
  private var mySrc: AdaptiveImageSource? = null
  private var myRenderLogicalWidth: Float = 0f
  private var myRenderLogicalHeight: Float = 0f
  private var myScale: Float = 0f
  private var myRenderingJob: Job? = null
  private var myLastSvgRasterizationTimeMs: Long = -1
  private var myIsError: Boolean = false

  override fun setRenderConfig(width: Float, height: Float, scale: Float) {
    myRenderLogicalWidth = width
    myRenderLogicalHeight = height
    myScale = scale
    myIsError = false
  }

  override fun setOrigin(origin: AdaptiveImageOrigin?) {
    val src = origin?.let { adaptiveImageManager.sourceResolver(it) }
    val oldSrc = mySrc
    if (src == oldSrc) return
    mySrc = src
    myAdaptiveImage = null
    myRasterizedImage = null
    myRenderingJob?.cancel()
    myRenderingJob = null
    myIsError = false

    if (oldSrc != null) {
      eventListener(AdaptiveImageRendererEvent.Unloaded())
    }
  }

  override fun getRenderedImage(): BufferedImage? {
    val nonBlockingResult = tryRenderNonBlocking()
    if (nonBlockingResult.shouldReRender && myRenderingJob == null) {
      coroutineScope.launch(CoroutineName("AdaptiveImageRenderer $mySrc"), start = CoroutineStart.UNDISPATCHED) {
        myRenderingJob = coroutineContext.job
        try {
          rendererMain()
        }
        finally {
          myRenderingJob = null
        }
      }

      val retry = tryRenderNonBlocking()
      return retry.image
    }

    return nonBlockingResult.image
  }

  override fun invalidate(reload: Boolean) {
    myAdaptiveImage = null
    myRasterizedImage = null
    if (reload) {
      getRenderedImage()
    }
  }

  private fun tryRenderNonBlocking(): NonBlockingRenderResult {
    if (mySrc == null) return NonBlockingRenderResult(null, false)

    val adaptiveImage = myAdaptiveImage
    val renderedImage = myRasterizedImage
    val loadedImage = adaptiveImage?.value
    val rasterizedImage = renderedImage?.value

    if (rasterizedImage != null) {
      val cfg = renderedImage.src
      val shouldReRender = cfg.logicalWidth != myRenderLogicalWidth
                           || cfg.logicalHeight != myRenderLogicalHeight
                           || cfg.scale != myScale
                           || cfg.svgImage != loadedImage
      return NonBlockingRenderResult(rasterizedImage.image, shouldReRender)
    }

    return when (loadedImage) {
      is LoadedRasterImage -> NonBlockingRenderResult(loadedImage.image, false)
      is LoadedSVGImage -> NonBlockingRenderResult(null, true)
      else -> NonBlockingRenderResult(null, true)
    }
  }

  private suspend fun rendererMain() {
    while(true) {
      val config = createConfigSnapshot()
      if (config.src == null) return

      val adaptiveImage = myAdaptiveImage
      val loadedImage = adaptiveImage?.value

      if (adaptiveImage == null || adaptiveImage.src != config.src || loadedImage == null) {
        val newAdaptiveImage = try {
          adaptiveImageManager.loadAdaptiveImage(config.src)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Throwable) {
          thisLogger().info("Failed to load image", e)
          myAdaptiveImage = null
          myRasterizedImage = null
          myIsError = true
          eventListener(AdaptiveImageRendererEvent.Error())
          return
        }

        val newLoadedImage = newAdaptiveImage.value ?: continue
        myAdaptiveImage = newAdaptiveImage
        myRasterizedImage = null
        myLastSvgRasterizationTimeMs = -1
        eventListener(AdaptiveImageRendererEvent.Loaded(newLoadedImage.dimensions, newLoadedImage is LoadedSVGImage))

        // if loaded image is SVG we check rasterization cache but do not start rasterization at this moment
        // because there is a high chance that host will recalculate view dimensions when handling 'Loaded' event
        if (newLoadedImage is LoadedSVGImage) {
          val freshConfig = createConfigSnapshot()
          if (freshConfig.src == config.src) {
            val rasterizationConfig = SVGRasterizationConfig(newLoadedImage, freshConfig.width, freshConfig.height, freshConfig.scale)
            adaptiveImageManager.tryGetRasterizedSVGFromCache(rasterizationConfig)?.also {
              myRasterizedImage = it
              eventListener(AdaptiveImageRendererEvent.Rasterized(newLoadedImage.dimensions, true))
            }
          }
        }
        return
      }

      if (loadedImage !is LoadedSVGImage) {
        return //nothing to do, it is already rasterized
      }

      val rasterizationConfig = SVGRasterizationConfig(loadedImage, config.width, config.height, config.scale)
      var rasterizedImage: UnloadableRasterizedImage? = null

      val currentTimeMs = timeProvider().value
      val elapsedSinceLastRender = currentTimeMs - myLastSvgRasterizationTimeMs
      if (RENDER_THROTTLE_MS > elapsedSinceLastRender && myLastSvgRasterizationTimeMs >= 0) {
        // wait for throttle delay and also watch if someone else completes rasterization of the same image
        rasterizedImage = adaptiveImageManager.withRasterizationIntention(rasterizationConfig) { intention ->
          select {
            onTimeout(RENDER_THROTTLE_MS - elapsedSinceLastRender) { null }
            intention.onAwait { it }
          }
        } ?: continue
      }

      myLastSvgRasterizationTimeMs = currentTimeMs

      if (rasterizedImage == null) {
        rasterizedImage = try {
          adaptiveImageManager.rasterizeSVGImage(rasterizationConfig)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Throwable) {
          thisLogger().info("Failed to rasterize image", e)
          myAdaptiveImage = null
          myRasterizedImage = null
          myIsError = true
          eventListener(AdaptiveImageRendererEvent.Error())
          return
        }
      }

      myRasterizedImage = rasterizedImage
      eventListener(AdaptiveImageRendererEvent.Rasterized(loadedImage.dimensions, true))

      val freshConfig = createConfigSnapshot()
      if (freshConfig == config) {
        return
      }
    } //~while (true)
  }

  @TestOnly
  override fun hasError() = myIsError

  @TestOnly
  override fun resetError() {
    myIsError = false
  }

  private fun createConfigSnapshot() = ConfigSnapshot(mySrc, myRenderLogicalWidth, myRenderLogicalHeight, myScale)
}
