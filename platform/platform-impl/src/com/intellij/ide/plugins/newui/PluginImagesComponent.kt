// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalAtomicApi::class)

package com.intellij.ide.plugins.newui

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.marketplace.MarketplaceRequests.Companion.readOrUpdateFile
import com.intellij.idea.AppMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.UI
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.io.IOUtil
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil.drawImage
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsDevice
import java.awt.Image
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import java.io.IOException
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * @author Alexander Lobas
 */
@ApiStatus.Internal
class PluginImagesComponent : JPanel {
  private val screenshots = MutableStateFlow(Screenshots())

  private val myHandCursor: Cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
  private var myParent: JComponent? = null
  private val myShowFullContent: Boolean
  private var myImages: List<ReferencedImage> = emptyList()
  private var myCurrentImage = 0
  private var myHovered = false
  private var myFullScreenPopup: JBPopup? = null

  private data class Screenshots(
    val urls: List<String>,
    val externalPluginIdForScreenShots: String?,
  ) {
    constructor() : this(
      urls = emptyList(),
      externalPluginIdForScreenShots = null,
    )
  }

  constructor() {
    myShowFullContent = false

    val listener: MouseAdapter = object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        handleClick(e)
      }

      override fun mouseMoved(e: MouseEvent) {
        handleMMove(e)
      }

      override fun mouseEntered(e: MouseEvent?) {
        myHovered = true
        repaint()
      }

      override fun mouseExited(e: MouseEvent?) {
        myHovered = false
        repaint()
      }
    }
    addMouseListener(listener)
    addMouseMotionListener(listener)
    launchOnShow("PluginImagesComponent images") {
      screenshots.collectLatest { screenshots ->
        loadImages(screenshots).use { images ->
          withContext(Dispatchers.UI + CoroutineName("show")) {
            try {
              showImages(images)
              awaitCancellation()
            }
            finally {
              withContext(NonCancellable + CoroutineName("cleanup")) {
                clearImages() // ensure the images are no longer used before they're disposed
              }
            }
          }
        }
      }
    }
  }

  private constructor(images: List<ReferencedImage>, currentImage: Int) {
    myShowFullContent = true
    myHovered = true
    myImages = images
    myCurrentImage = currentImage
    setOpaque(false)

    val listener: MouseAdapter = object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        handleClick(e)
      }

      override fun mouseMoved(e: MouseEvent) {
        handleMMove(e)
      }
    }
    addMouseListener(listener)
    addMouseMotionListener(listener)
  }

  private suspend fun loadImages(screenshots: Screenshots): ReferencedImages {
    val images: MutableList<Image> = ArrayList()
    var success = false
    try {
      withContext(Dispatchers.IO + CoroutineName("load")) {
        var parentDir = PathManager.getSystemDir().resolve("plugins").resolve("imageCache")
        if (screenshots.externalPluginIdForScreenShots != null) {
          parentDir = parentDir.resolve(screenshots.externalPluginIdForScreenShots)
        }
        for (screenshotURL in screenshots.urls) {
          ensureActive()
          try {
            val name = StringUtil.substringAfterLast(screenshotURL, "/")
            if (name == null) {
              LOG.warn("Malformed screenshot URL: $screenshotURL")
              continue
            }
            val imageFile = parentDir.resolve(name)
            readOrUpdateFile(imageFile, screenshotURL, null, "") { stream ->
              IOUtil.closeSafe(LOG, stream)
            }
            images.add(Toolkit.getDefaultToolkit().createImage(imageFile.toAbsolutePath().toString()))
            if (images.size >= 10) break
          }
          catch (e: IOException) {
            // IO errors such as image decoding problems are expected and must not be treated as IDE errors
            LOG.warn("An exception occurred during loading of the screenshot $screenshotURL", e)
          }
        }
      }
      success = true // passing ownership to the caller now
      return ReferencedImages(images.map { ReferencedImage(it) })
    }
    finally {
      // This is not strictly needed unless the images are actually accessed here, which they're not, for now.
      // But it's so easy to add something like getWidth(null) inside the body.
      // And then, if a cancellation or exception happens, well, that image will load in background
      // and if it's an animated GIF, it'll keep animating in background forever, wasting CPU cycles.
      if (!success) {
        images.forEach { it.flush() }
      }
    }
  }

  private fun showImages(images: List<ReferencedImage>) {
    ThreadingAssertions.assertEventDispatchThread()
    myImages = images
    myCurrentImage = 0
    myFullScreenPopup?.cancel()
    myFullScreenPopup = null
    fullRepaint()
  }

  private fun clearImages() {
    showImages(emptyList())
  }

  fun setParent(parent: JComponent) {
    myParent = parent
  }

  fun show(model: PluginUiModel) {
    screenshots.value = getEffectiveScreenShots(model)
  }

  private fun getEffectiveScreenShots(model: PluginUiModel): Screenshots {
    if (
      !model.isFromMarketplace ||
      model.externalPluginIdForScreenShots == null ||
      ApplicationManager.getApplication().isHeadlessEnvironment()
    ) {
      return Screenshots()
    }

    val screenShots = model.screenShots
    if (screenShots.isNullOrEmpty()) {
      return Screenshots()
    }

    return Screenshots(screenShots, model.externalPluginIdForScreenShots)
  }

  private fun fullRepaint() {
    val parent = getParent()
    if (parent != null) {
      parent.doLayout()
      parent.revalidate()
      parent.repaint()
    }
  }

  override fun getPreferredSize(): Dimension {
    var width = 0
    var height = 0

    val parent = getParent()
    if (parent != null) {
      if (myImages.isNotEmpty()) {
        width = this.fullWidth
        height = (width / 1.58).toInt() + JBUI.scale(28)
      }
    }

    return Dimension(width, height)
  }

  private val fullWidth: Int
    get() {
      val parent = getParent()
      val myParent = myParent
      if (parent != null && myParent != null) {
        return myParent.getWidth() - parent.insets.left
      }
      return getWidth()
    }

  private fun handleMMove(e: MouseEvent) {
    val state = handleEvent(e, true)
    val newCursor = if (state == NONE) null else myHandCursor

    if (getCursor() !== newCursor) {
      setCursor(newCursor)
    }
  }

  private fun handleClick(e: MouseEvent) {
    if (e.getButton() != MouseEvent.BUTTON1) {
      return
    }

    val state = handleEvent(e, false)
    if (state == NONE) {
      return
    }

    if (state >= 0) {
      if (myCurrentImage != state) {
        myCurrentImage = state
        repaint()
      }
    }
    else if (state == FULL_SCREEN) {
      handleFullScreen()
    }
    else {
      showNextImage(state == PREV_IMAGE)
    }
  }

  private fun handleEvent(e: MouseEvent, cutFullScreen: Boolean): Int {
    val insets = getInsets()
    val x = insets.left
    val y = insets.top
    val width = this.fullWidth - insets.left - insets.right
    val height = getHeight() - insets.top - insets.bottom
    val offset = JBUI.scale(28)
    val offset2 = offset * 2
    val actionOffset = JBUI.scale(8)
    val actionSize = this.actionSize
    val leftActionX = x + actionOffset
    val rightActionX = x + width - actionOffset - actionSize
    val actionY = y + (height - offset - actionSize) / 2
    val mouseX = e.getX()
    val mouseY = e.getY()

    if (Rectangle(leftActionX, actionY, actionSize, actionSize).contains(mouseX, mouseY)) {
      return PREV_IMAGE
    }
    if (Rectangle(rightActionX, actionY, actionSize, actionSize).contains(mouseX, mouseY)) {
      return NEXT_IMAGE
    }
    if (Rectangle(x + offset, y, width - offset2, height - offset).contains(mouseX, mouseY)) {
      if (cutFullScreen && !Rectangle(x + offset2, y, width - 2 * offset2, height - offset).contains(mouseX, mouseY)) {
        return NONE
      }
      return FULL_SCREEN
    }

    if (myImages.isEmpty()) {
      return NONE
    }
    val count = myImages.size

    if (count < 2) {
      return NONE
    }

    val ovalSize = JBUI.scale(if (myShowFullContent) 8 else 6)
    val ovalGap = JBUI.scale(14)
    val ovalsWidth = count * ovalSize + (count - 1) * ovalGap
    val ovalX = x + (width - ovalsWidth) / 2
    val ovalY = insets.top + height - (offset + ovalSize) / 2
    val bounds = Rectangle(ovalX, ovalY, ovalSize, ovalSize)

    for (i in 0..<count) {
      if (bounds.contains(mouseX, mouseY)) {
        return i
      }
      bounds.x += ovalSize + ovalGap
    }

    return NONE
  }

  private fun handleFullScreen() {
    myFullScreenPopup?.cancel()
    if (myShowFullContent) {
      return
    }
    if (myImages.isEmpty()) {
      return
    }
    val images = myImages
    val current = myCurrentImage

    val component = PluginImagesComponent(images, current)
    val panel: JPanel = Wrapper(component)
    panel.preferredSize = graphicsConfiguration.bounds.size

    // Now the images will be used by both this and the full screen component.
    // It's possible that this component will dereference them soon (if another set is loading),
    // but reference counting will ensure that the image is disposed when it's no longer referenced.
    // This might even happen twice, but that's fine, as images can be reloaded and disposed again.
    images.forEach { it.ref() }
    val newFullScreenPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, component).createPopup()
    Disposer.register(newFullScreenPopup) {
      images.forEach { it.deref() }
    }
    this.myFullScreenPopup = newFullScreenPopup
    component.myFullScreenPopup = newFullScreenPopup

    newFullScreenPopup.addListener(object : JBPopupListener {
      override fun beforeShown(event: LightweightWindowEvent) {
        val window = SwingUtilities.getWindowAncestor(event.asPopup().getContent())
        if (window.graphicsConfiguration.device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT)) {
          window.setBackground(Gray.TRANSPARENT)
          window.opacity = 0.95f
        }
      }

      override fun onClosed(event: LightweightWindowEvent) {
        myFullScreenPopup = null
        if (component.myImages == myImages && component.myCurrentImage != myCurrentImage) {
          myCurrentImage = component.myCurrentImage
        }
        repaint()
      }
    })

    newFullScreenPopup.showInScreenCoordinates(this, Point())
  }

  private fun showNextImage(left: Boolean) {
    val count = myImages.size
    if (count < 2) {
      return
    }
    if (left) {
      if (myCurrentImage > 0) {
        myCurrentImage--
      }
      else {
        myCurrentImage = count - 1
      }
    }
    else if (myCurrentImage < count - 1) {
      myCurrentImage++
    }
    else {
      myCurrentImage = 0
    }
    repaint()
  }

  override fun paint(g: Graphics) {
    if (myShowFullContent) {
      val g2d = g.create() as Graphics2D

      try {
        g2d.background = Gray.get(158, 158)
        g2d.clearRect(0, 0, getWidth(), getHeight())
        g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.95f)

        paintComponent(g)
        return
      }
      finally {
        g2d.dispose()
      }
    }
    super.paint(g)
  }

  override fun paintComponent(g: Graphics) {
    val count: Int = myImages.size
    if (count == 0) {
      return
    }
    val current: Int = myCurrentImage
    val image = myImages[current].image

    val insets = getInsets()
    val x = insets.left
    val y = insets.top
    val width = this.fullWidth - insets.left - insets.right
    val height = getHeight() - insets.top - insets.bottom
    val offset = JBUI.scale(28)

    if (!myShowFullContent) {
      g.color = PluginManagerConfigurable.MAIN_BG_COLOR
      g.fillRect(x, y, width, height)
    }

    val imageX = insets.left + offset
    val imageY = insets.top
    val paintWidth = width - 2 * offset
    val paintHeight = height - offset
    paintImage(g, image, imageX, imageY, paintWidth, paintHeight)

    val g2 = g.create() as Graphics2D

    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)

      if (!myShowFullContent) {
        g2.color = PluginManagerConfigurable.SEARCH_FIELD_BORDER_COLOR
        g2.draw(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), (width - 1).toFloat(), (height - offset).toFloat(), 7f, 7f))
      }

      if (count < 2) {
        return
      }

      val ovalSize = JBUI.scale(if (myShowFullContent) 8 else 6)
      val ovalGap = JBUI.scale(14)
      val ovalsWidth = count * ovalSize + (count - 1) * ovalGap
      var ovalX = x + (width - ovalsWidth) / 2
      val ovalY = insets.top + height - (offset + ovalSize) / 2

      for (i in 0..<count) {
        if (i == current) {
          g2.color = CURRENT_IMAGE_FILL_COLOR
          g2.fillOval(ovalX, ovalY, ovalSize, ovalSize)
        }
        else {
          g2.color = JBUI.CurrentTheme.Button.buttonOutlineColorStart(false)
          g2.drawOval(ovalX, ovalY, ovalSize - 1, ovalSize - 1)
        }
        ovalX += ovalSize + ovalGap
      }

      if (myHovered) {
        paintAction(g2, x, y, width, height, offset, true)
        paintAction(g2, x, y, width, height, offset, false)
      }
    }
    finally {
      g2.dispose()
    }
  }

  private fun paintImage(
    g: Graphics,
    imageToPaint: Image,
    imageX: Int,
    imageY: Int,
    paintWidth: Int,
    paintHeight: Int,
  ) {
    // Important to call these on the original image,
    // as this ensures that the component will be notified when the image changes,
    // in the case of an animated GIF.
    val imageWidth = imageToPaint.getWidth(this)
    val imageHeight = imageToPaint.getHeight(this)

    if (imageWidth <= 0 || imageHeight <= 0) return  // Not loaded yet. Component.imageUpdate will handle repaint later.

    // Lux can't paint toolkit images, so we paint it to a buffer first.
    val image = if (AppMode.isRemoteDevHost()) ImageUtil.toBufferedImage(imageToPaint) else imageToPaint

    if (imageWidth <= paintWidth && imageHeight <= paintHeight) {
      drawImage(g, image, imageX + (paintWidth - imageWidth) / 2, imageY + (paintHeight - imageHeight) / 2, this)
    }
    else {
      val zoomedHeight = imageHeight * paintWidth / imageWidth
      val zoomedWidth = imageWidth * paintHeight / imageHeight

      if (zoomedWidth <= paintWidth) {
        drawImage(g, image, Rectangle(imageX + (paintWidth - zoomedWidth) / 2, imageY, zoomedWidth, paintHeight), this)
      }
      else if (zoomedHeight <= paintHeight) {
        drawImage(g, image, Rectangle(imageX, imageY + (paintHeight - zoomedHeight) / 2, paintWidth, zoomedHeight), this)
      }
      else {
        drawImage(g, image, Rectangle(imageX, imageY, paintWidth, paintHeight), this)
      }
    }
  }

  private fun paintAction(g2: Graphics2D, x: Int, y: Int, width: Int, height: Int, offset: Int, left: Boolean) {
    val actionOffset = JBUI.scale(8)
    val actionSize = this.actionSize

    var actionX = if (left) (x + actionOffset) else (x + width - actionOffset - actionSize)

    var actionY = y + (height - offset - actionSize) / 2

    g2.color = JBUI.CurrentTheme.Button.buttonColorStart()
    g2.fillOval(actionX, actionY, actionSize, actionSize)
    g2.color = JBUI.CurrentTheme.Button.buttonOutlineColorStart(false)
    g2.drawOval(actionX, actionY, actionSize, actionSize)

    val icon = if (left) AllIcons.Actions.ArrowCollapse else AllIcons.Actions.ArrowExpand
    val iconWidth = icon.iconWidth
    val iconHeight = icon.iconHeight

    actionX += (actionSize - iconWidth) / 2
    actionY += (actionSize - iconHeight) / 2
    icon.paintIcon(this, g2, actionX, actionY)
  }

  private val actionSize: Int
    get() = JBUI.scale(if (myShowFullContent) 48 else 28)

  companion object {
    private val CURRENT_IMAGE_FILL_COLOR: Color =
      JBColor.namedColor("Plugins.ScreenshotPagination.CurrentImage.fillColor", JBColor(0x6C707E, 0xCED0D6))

    private const val NONE = -1
    private const val NEXT_IMAGE = -2
    private const val PREV_IMAGE = -3
    private const val FULL_SCREEN = -4
  }
}

private class ReferencedImages(private val images: List<ReferencedImage>) {
  suspend fun use(block: suspend (List<ReferencedImage>) -> Unit) {
    images.forEach { it.ref() }
    try {
      block(images)
    }
    finally {
      images.forEach { it.deref() }
    }
  }
}

private class ReferencedImage(val image: Image) {
  private val refCount: AtomicInt = AtomicInt(0)

  fun ref() {
    refCount.incrementAndFetch()
  }

  fun deref() {
    if (refCount.decrementAndFetch() == 0) {
      image.flush()
    }
  }
}

private val LOG = logger<PluginImagesComponent>()
