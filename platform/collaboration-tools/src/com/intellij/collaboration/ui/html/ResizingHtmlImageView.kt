// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.html

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.util.containers.ComparatorUtil.min
import com.intellij.util.ui.JBImageToolkit
import com.intellij.util.ui.StartupUiUtil
import kotlinx.coroutines.*
import java.awt.*
import java.awt.image.BufferedImage
import java.awt.image.ImageObserver
import java.net.URL
import java.util.*
import javax.swing.Icon
import javax.swing.event.DocumentEvent
import javax.swing.text.*
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLDocument
import kotlin.properties.Delegates.observable

private val loadingIcon: Icon
  get() = AllIcons.Process.Step_passive

private val notLoadedIcon: Icon
  get() = AllIcons.FileTypes.Image

/**
 * Custom image view to be used with [com.intellij.util.ui.ExtendableHTMLViewFactory]
 *
 * Images are automatically resized to fit the panel width
 *
 * Image loading can be customized via [AsyncHtmlImageLoader.KEY] property in a document
 */
//TODO: handle HTML props - h/vspace, h/valign
//TODO: handle CSS props - max-width/height to control the resize behavior
//TODO: handle hidpi (ensureHiDpi does not work with ToolkitImage/ImageObserver)
internal class ResizingHtmlImageView(element: Element) : View(element) {

  private var _loader: ImageLoader? = null
    set(value) {
      field?.cancel()
      field = value
    }
  private val loader: ImageLoader
    get() = _loader ?: run {
      createImageLoader().also {
        _loader = it
      }
    }

  private var cachedContainer: Container? = null
  private var lastPaintedRectangle: Rectangle? = null

  override fun getPreferredSpan(axis: Int): Float =
    when (val state = loader.state) {
      ImageLoader.State.NotLoaded -> notLoadedIcon.getSpan(axis)
      is ImageLoader.State.Loading -> state.dimension?.getSpan(axis) ?: loadingIcon.getSpan(axis)
      is ImageLoader.State.Loaded -> state.dimension.getSpan(axis)
    }.toFloat()

  /**
   * If the image was loaded we ALWAYS "break" this view to scale the image properly - [breakView]
   */
  override fun getBreakWeight(axis: Int, pos: Float, len: Float): Int {
    return if (axis == X_AXIS && loader.state is ImageLoader.State.Loaded) ForcedBreakWeight
    else BadBreakWeight
  }

  override fun breakView(axis: Int, offset: Int, pos: Float, len: Float): View {
    val state = loader.state
    if (axis == X_AXIS && state is ImageLoader.State.Loaded) {
      preferenceChanged(null, false, true)
      val imageWidth = state.dimension.width.toFloat()
      val imageHeight = state.dimension.height.toFloat()

      val width = min(len, imageWidth).coerceAtLeast(1f)

      val scale = (width / imageWidth).coerceAtMost(1f)
      val height = (imageHeight * scale).coerceAtLeast(1f)

      return SizedImageView(state.image, width, height)
    }
    else {
      return this
    }
  }

  override fun getAlignment(axis: Int): Float = 0f

  override fun paint(g: Graphics, allocation: Shape) {
    val rect = if (allocation is Rectangle) allocation else allocation.bounds
    val currentLoader = loader
    currentLoader.loadImage()
    when (val state = currentLoader.state) {
      ImageLoader.State.NotLoaded -> notLoadedIcon.paintIcon(null, g, rect.x, rect.y)
      is ImageLoader.State.Loading -> loadingIcon.paintIcon(null, g, rect.x, rect.y)
      is ImageLoader.State.Loaded -> StartupUiUtil.drawImage(g, state.image, rect, null)
    }
  }

  override fun changedUpdate(e: DocumentEvent?, a: Shape?, f: ViewFactory?) {
    super.changedUpdate(e, a, f)
    _loader = null
    preferenceChanged(null, true, true)
  }

  override fun setParent(parent: View?) {
    super.setParent(parent)
    cachedContainer = parent?.container
  }

  override fun modelToView(pos: Int, a: Shape, b: Position.Bias?): Shape {
    if (pos !in startOffset..endOffset) {
      throw BadLocationException(element.toString(), pos)
    }

    val x = a.bounds.x + if (pos == endOffset) a.bounds.width else 0
    return Rectangle(x, a.bounds.y, 0, a.bounds.height)
  }

  override fun viewToModel(x: Float, y: Float, a: Shape, bias: Array<Position.Bias>): Int {
    val right = a.bounds.x + a.bounds.width
    return if (x >= right) {
      bias[0] = Position.Bias.Backward
      endOffset
    }
    else {
      bias[0] = Position.Bias.Forward
      startOffset
    }
  }

  private fun createImageLoader(): ImageLoader {
    val src = element.attributes.getAttribute(HTML.Attribute.SRC) as? String
    val base = (document as HTMLDocument).base
    val asyncLoader = document.getProperty(AsyncHtmlImageLoader.KEY) as? AsyncHtmlImageLoader

    return ImageLoader(base, src, asyncLoader) { old, new ->
      if (old != new) {
        preferenceChanged(null, true, true)
      }
      requestRepaint()
    }
  }

  private fun requestRepaint() {
    val rect = lastPaintedRectangle
    if (rect == null) {
      cachedContainer?.repaint(100)
    }
    else {
      cachedContainer?.repaint(rect.x, rect.y, rect.width, rect.height)
    }
  }

  // image should be loaded by this point
  private inner class SizedImageView(private val image: Image, private val width: Float, private val height: Float) : View(element) {

    override fun getPreferredSpan(axis: Int): Float = if (axis == X_AXIS) width else height

    override fun paint(g: Graphics, allocation: Shape) {
      val rect = if (allocation is Rectangle) allocation else allocation.bounds
      StartupUiUtil.drawImage(g, image, rect, null)
      lastPaintedRectangle = rect
    }

    override fun getAlignment(axis: Int): Float =
      this@ResizingHtmlImageView.getAlignment(axis)

    override fun getToolTipText(x: Float, y: Float, allocation: Shape?): String? =
      this@ResizingHtmlImageView.getToolTipText(x, y, allocation)

    override fun modelToView(pos: Int, a: Shape, b: Position.Bias?): Shape =
      this@ResizingHtmlImageView.modelToView(pos, a, b)

    override fun viewToModel(x: Float, y: Float, a: Shape, biasReturn: Array<Position.Bias>): Int =
      this@ResizingHtmlImageView.viewToModel(x, y, a, biasReturn)
  }

  private fun Dimension.getSpan(axis: Int): Float = (if (axis == X_AXIS) width else height).toFloat()
  private fun Icon.getSpan(axis: Int): Float = (if (axis == X_AXIS) iconWidth else iconHeight).toFloat()
}

private class ImageLoader(
  private val baseUrl: URL?,
  private val src: String?,
  private val asyncLoader: AsyncHtmlImageLoader?,
  private val onStateChange: (old: State, new: State) -> Unit
) : ImageObserver {

  private var loadingJob: Job? = null
  private val dimension = Dimension(-1, -1)

  var state: State by observable(State.NotLoaded) { _, old, new ->
    onStateChange(old, new)
  }
    private set

  fun loadImage() {
    if (loadingJob == null) {
      loadingJob = requestImage()
    }
  }

  private fun requestImage(): Job {
    if (src == null) {
      return Job().apply { cancel() }
    }

    try {
      if (src.startsWith("data:image") && src.contains("base64")) {
        val result = Job()
        val base64Image = tryCreateBase64Image(src)
        if (base64Image != null) {
          JBImageToolkit.prepareImage(base64Image, -1, -1, this)
          result.complete()
        }
        else {
          result.cancel()
        }
        return result
      }

      if (asyncLoader != null) {
        return requestImageAsync(asyncLoader, baseUrl, src)
      }
      else {
        val url = baseUrl?.let { URL(it, src) } ?: URL(src)
        return JBImageToolkit.createImage(url).also {
          JBImageToolkit.prepareImage(it, -1, -1, this)
        }.let {
          Job().apply { complete() }
        }
      }
    }
    catch (e: Exception) {
      return Job().apply { completeExceptionally(e) }
    }
  }

  @OptIn(DelicateCoroutinesApi::class)
  private fun requestImageAsync(loader: AsyncHtmlImageLoader, baseUrl: URL?, src: String): Job =
    GlobalScope.launch(Dispatchers.Main + CoroutineName("HTML image requestor")) {
      state = State.Loading()
      val image = try {
        loader.load(baseUrl, src)!!
      }
      catch (e: Exception) {
        state = State.NotLoaded
        throw CancellationException("Image loading cancelled", e)
      }
      if (image is BufferedImage) {
        state = State.Loaded(image, Dimension(image.width, image.height))
      }
      else {
        JBImageToolkit.prepareImage(image, -1, -1, this@ImageLoader)
      }
    }

  override fun imageUpdate(img: Image, flags: Int, x: Int, y: Int, width: Int, height: Int): Boolean =
    invokeAndWaitIfNeeded {
      if (loadingJob?.isCancelled != false) false else doUpdate(img, flags, width, height)
    }

  private fun doUpdate(img: Image, flags: Int, width: Int, height: Int): Boolean {
    if (flags and (ImageObserver.ABORT or ImageObserver.ERROR) != 0) {
      state = State.NotLoaded
      return false
    }

    if (flags and ImageObserver.WIDTH != 0) {
      dimension.width = width
    }

    if (flags and ImageObserver.HEIGHT != 0) {
      dimension.height = height
    }

    if (flags and (ImageObserver.FRAMEBITS or ImageObserver.ALLBITS or ImageObserver.SOMEBITS) != 0) {
      val dim = Dimension(img.getWidth(null), img.getHeight(null))
      state = State.Loaded(img, dim)
    }
    else if (state !is State.Loaded) {
      state = State.Loading(dimension.takeIf { width >= 0 && height >= 0 })
    }

    return flags and ImageObserver.ALLBITS == 0
  }

  fun cancel() {
    loadingJob?.cancel()
  }

  sealed interface State {
    object NotLoaded : State
    class Loading(val dimension: Dimension? = null) : State
    class Loaded(val image: Image, val dimension: Dimension) : State
  }
}

// example: "data:image/png;base64,ENCODED_IMAGE_HERE"
private fun tryCreateBase64Image(src: String): Image? {
  val encodedImage = src.split(',').takeIf { it.size == 2 }?.get(1) ?: return null
  val decodedImage = Base64.getDecoder().decode(encodedImage)
  return JBImageToolkit.createImage(decodedImage)
}