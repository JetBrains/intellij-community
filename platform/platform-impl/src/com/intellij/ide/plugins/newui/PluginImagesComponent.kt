// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.marketplace.MarketplaceRequests.Companion.readOrUpdateFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.IOUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil.drawImage
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
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * @author Alexander Lobas
 */
@ApiStatus.Internal
class PluginImagesComponent : JPanel {
  private val myHandCursor: Cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

  private val myLock = Any()
  private var myParent: JComponent? = null
  private val myShowFullContent: Boolean
  private var myImages: List<Image>? = null
  private var myCurrentImage = 0
  private var myHovered = false
  private var myLoadingState: Any? = null
  private var myShowState: Any? = null
  private var myFullScreenPopup: JBPopup? = null

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
  }

  private constructor(images: List<Image>, currentImage: Int) {
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

  fun setParent(parent: JComponent) {
    myParent = parent
  }

  fun show(model: PluginUiModel) {
    val state: Any?

    synchronized(myLock) {
      myImages = null
      myLoadingState = Any()
      state = myLoadingState
      myShowState = null
    }

    loadImages(model, state!!)
    fullRepaint()
  }

  private fun handleImages(state: Any, images: MutableList<Image>?) {
    synchronized(myLock) {
      if (myLoadingState !== state) {
        return
      }
      myShowState = state
      myLoadingState = null
      myImages = images
      myCurrentImage = 0
    }

    if (myFullScreenPopup != null) {
      myFullScreenPopup!!.cancel()
      myFullScreenPopup = null
    }

    fullRepaint()
  }

  private fun fullRepaint() {
    val parent = getParent()
    if (parent != null) {
      parent.doLayout()
      parent.revalidate()
      parent.repaint()
    }
  }

  private fun loadImages(model: PluginUiModel, state: Any) {
    if (!model.isFromMarketplace || model.externalPluginIdForScreenShots == null ||
        ApplicationManager.getApplication().isHeadlessEnvironment()
    ) {
      handleImages(state, null)
      return
    }

    val screenShots: List<String>? = model.screenShots
    if (ContainerUtil.isEmpty(screenShots)) {
      handleImages(state, null)
      return
    }

    ProcessIOExecutorService.INSTANCE.execute(Runnable execute@{
      val images: MutableList<Image> = ArrayList()
      val parentDir = File(PathManager.getPluginTempPath(), "imageCache/" + model.externalPluginIdForScreenShots)

      for (screenShot in screenShots!!) {
        try {
          val name = StringUtil.substringAfterLast(screenShot, "/")!!
          val imageFile = File(parentDir, name)
          if (ApplicationManager.getApplication().isDisposed()) {
            return@execute
          }
          readOrUpdateFile<Any?>(imageFile.toPath(), screenShot, null, "") { stream: InputStream? ->
            IOUtil.closeSafe(Logger.getInstance(PluginImagesComponent::class.java), stream)
            Any()
          }
          var image = Toolkit.getDefaultToolkit().getImage(imageFile.absolutePath)
          if (image == null || image.getWidth(null) <= 0 || image.getHeight(null) <= 0) {
            FileInputStream(imageFile).use { stream ->
              image = ImageIO.read(stream)
            }
          }
          if (image == null || image.getWidth(null) <= 0 || image.getHeight(null) <= 0) {
            Logger.getInstance(PluginImagesComponent::class.java)
              .info("=== Plugin: " + model.pluginId + " screenshot: " + name + " not loaded ===")
          }
          else {
            images.add(image)
            if (images.size >= 10) break
          }
        }
        catch (e: IOException) {
          // IO errors such as image decoding problems are expected and must not be treated as IDE errors
          Logger.getInstance(PluginImagesComponent::class.java).warn(e)
        }
      }
      ApplicationManager.getApplication().invokeLater(Runnable { handleImages(state, images) }, ModalityState.stateForComponent(this))
    })
  }

  override fun getPreferredSize(): Dimension {
    var width = 0
    var height = 0

    val parent = getParent()
    if (parent != null) {
      val isImages: Boolean
      synchronized(myLock) {
        isImages = !ContainerUtil.isEmpty(myImages)
      }
      if (isImages) {
        width = this.fullWidth
        height = (width / 1.58).toInt() + JBUI.scale(28)
      }
    }

    return Dimension(width, height)
  }

  private val fullWidth: Int
    get() {
      val parent = getParent()
      if (parent != null && myParent != null) {
        return myParent!!.getWidth() - parent.insets.left
      }
      return getWidth()
    }

  private fun handleMMove(e: MouseEvent) {
    val state = handleEvent(e, true)
    val newCursor = if (state == None) null else myHandCursor

    if (getCursor() !== newCursor) {
      setCursor(newCursor)
    }
  }

  private fun handleClick(e: MouseEvent) {
    if (e.getButton() != MouseEvent.BUTTON1) {
      return
    }

    val state = handleEvent(e, false)
    if (state == None) {
      return
    }

    if (state >= 0) {
      synchronized(myLock) {
        if (myCurrentImage != state) {
          myCurrentImage = state
          repaint()
        }
      }
    }
    else if (state == FullScreen) {
      handleFullScreen()
    }
    else {
      showNextImage(state == PrevImage)
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
      return PrevImage
    }
    if (Rectangle(rightActionX, actionY, actionSize, actionSize).contains(mouseX, mouseY)) {
      return NextImage
    }
    if (Rectangle(x + offset, y, width - offset2, height - offset).contains(mouseX, mouseY)) {
      if (cutFullScreen && !Rectangle(x + offset2, y, width - 2 * offset2, height - offset).contains(mouseX, mouseY)) {
        return None
      }
      return FullScreen
    }

    val count: Int
    synchronized(myLock) {
      if (ContainerUtil.isEmpty(myImages)) {
        return None
      }
      count = myImages!!.size
    }

    if (count < 2) {
      return None
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

    return None
  }

  private fun handleFullScreen() {
    if (myFullScreenPopup != null) {
      myFullScreenPopup!!.cancel()
    }
    if (myShowFullContent) {
      return
    }

    val images: List<Image>?
    val current: Int
    val showState: Any?
    synchronized(myLock) {
      if (ContainerUtil.isEmpty(myImages)) {
        return
      }
      images = myImages
      current = myCurrentImage
      showState = myShowState
    }

    val component = PluginImagesComponent(images!!, current)
    val panel: JPanel = Wrapper(component)
    panel.preferredSize = graphicsConfiguration.bounds.size

    myFullScreenPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, component).createPopup()
    component.myFullScreenPopup = myFullScreenPopup
    component.myShowState = showState

    myFullScreenPopup!!.addListener(object : JBPopupListener {
      override fun beforeShown(event: LightweightWindowEvent) {
        val window = SwingUtilities.getWindowAncestor(event.asPopup().getContent())
        if (window.graphicsConfiguration.device
            .isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT)
        ) {
          window.setBackground(Gray.TRANSPARENT)
          window.opacity = 0.95f
        }
      }

      override fun onClosed(event: LightweightWindowEvent) {
        myFullScreenPopup = null
        synchronized(myLock) {
          if (myShowState === component.myShowState && component.myCurrentImage != myCurrentImage) {
            myCurrentImage = component.myCurrentImage
          }
        }
        repaint()
      }
    })

    myFullScreenPopup!!.showInScreenCoordinates(this, Point())
  }

  private fun showNextImage(left: Boolean) {
    synchronized(myLock) {
      if (myImages == null) {
        return
      }
      val count = myImages!!.size
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
    val count: Int
    val current: Int
    val image: Image

    synchronized(myLock) {
      if (myImages == null) {
        return
      }
      count = myImages!!.size
      if (count == 0) {
        return
      }
      current = myCurrentImage
      image = myImages!![current]
    }

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
    val imageWidth = image.getWidth(this)
    val imageHeight = image.getHeight(this)
    val paintWidth = width - 2 * offset
    val paintHeight = height - offset

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

    private const val None = -1
    private const val NextImage = -2
    private const val PrevImage = -3
    private const val FullScreen = -4
  }
}