// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui

import com.intellij.CommonBundle
import com.intellij.ide.RemoteDesktopService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLayeredPane
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.animation.FloatConsumer
import com.intellij.util.ui.*
import kotlinx.coroutines.*
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.*

open class LoadingDecorator @JvmOverloads constructor(
  content: JComponent?,
  parent: Disposable,
  private val startDelayMs: Int,
  useMinimumSize: Boolean = false,
  icon: AnimatedIcon = AsyncProcessIcon.createBig("Loading")
) {
  companion object {
    @JvmField
    val OVERLAY_BACKGROUND: Color = JBColor.namedColor("BigSpinner.background", JBColor.PanelBackground)
  }

  constructor(
    content: JComponent?,
    parent: Disposable,
    startDelayMs: Int,
    useMinimumSize: Boolean = false,
    icon: AsyncProcessIcon,
  ) : this(
    content = content,
    parent = parent,
    startDelayMs = startDelayMs,
    useMinimumSize = useMinimumSize,
    icon = icon as AnimatedIcon,
  )

  var overlayBackground: Color? = null

  private val pane: JLayeredPane = LoadingDecoratorLayeredPane(if (useMinimumSize) content else null)
  private val loadingLayer: LoadingLayer = LoadingLayer(icon)
  private val fadeOutAnimator: Animator?
  private var startRequestJob: Job? = null

  var loadingText: @Nls String?
    get() = loadingLayer.text.text
    set(loadingText) {
      loadingLayer.text.isVisible = !loadingText.isNullOrBlank()
      loadingLayer.text.text = loadingText
    }

  val isLoading: Boolean
    get() = loadingLayer.isLoading

  init {
    loadingText = CommonBundle.getLoadingTreeNodeText()
    fadeOutAnimator = if (GraphicsEnvironment.isHeadless() || ApplicationManager.getApplication()?.isUnitTestMode == true) {
      null
    }
    else {
      LoadingLayerAnimator(
        setAlpha = { loadingLayer.setAlpha(it) },
        end = {
          loadingLayer.setAlpha(0f)
          hideLoadingLayer()
          loadingLayer.setAlpha(-1f)
          pane.repaint()
        },
      )
    }

    pane.add(content, JLayeredPane.DEFAULT_LAYER, 0)
    Disposer.register(parent) {
      fadeOutAnimator?.dispose()
      Disposer.dispose(loadingLayer.progress)
      startRequestJob?.cancel()
    }
  }

  /**
   * Removes a loading layer to restore a blit-accelerated scrolling.
   */
  private fun hideLoadingLayer() {
    pane.remove(loadingLayer)
    loadingLayer.isVisible = false
  }

  /* Placing the invisible layer on top of JViewport suppresses blit-accelerated scrolling
     as JViewport.canUseWindowBlitter() doesn't take component's visibility into account.

     We need to add / remove the loading layer on demand to preserve the blit-based scrolling.

     Blit-acceleration copies as much of the rendered area as possible and then repaints only a newly exposed region.
     This helps to improve scrolling performance and to reduce CPU usage (especially if drawing is compute-intensive). */
  private fun addLoadingLayerOnDemand() {
    if (pane !== loadingLayer.parent) {
      pane.add(loadingLayer, JLayeredPane.DRAG_LAYER, 1)
    }
  }

  protected open fun customizeLoadingLayer(parent: JPanel, text: JLabel, icon: AnimatedIcon): NonOpaquePanel {
    parent.layout = GridBagLayout()
    text.font = StartupUiUtil.labelFont
    text.foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
    icon.border = if ((text.text ?: "").endsWith("...")) JBUI.Borders.emptyRight(8) else JBUI.Borders.empty()
    val result = NonOpaquePanel(VerticalLayout(6))
    result.border = JBUI.Borders.empty(10)
    result.add(icon)
    result.add(text)
    parent.add(result)
    return result
  }

  val component: JComponent
    get() = pane

  open fun startLoading(takeSnapshot: Boolean) {
    if (isLoading || startRequestJob != null) {
      return
    }

    if (startDelayMs > 0) {
      val scheduledTime = System.currentTimeMillis()
      startRequestJob = (ApplicationManager.getApplication() as ComponentManagerEx).getCoroutineScope().launch {
        delay((startDelayMs - (System.currentTimeMillis() - scheduledTime)).coerceAtLeast(0))
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          doStartLoading(takeSnapshot)
        }
      }
    }
    else {
      doStartLoading(takeSnapshot)
    }
  }

  protected fun doStartLoading(takeSnapshot: Boolean) {
    addLoadingLayerOnDemand()
    loadingLayer.setVisible(true, takeSnapshot)
  }

  open fun stopLoading() {
    startRequestJob?.let {
      startRequestJob = null
      it.cancel()
    }
    if (!isLoading) {
      return
    }
    loadingLayer.setVisible(visible = false, takeSnapshot = false)
    pane.repaint()
  }

  private inner class LoadingLayer(@JvmField val progress: AnimatedIcon) : JPanel() {
    val text = JLabel("", SwingConstants.CENTER)

    private var snapshot: BufferedImage? = null
    private var snapshotBg: Color? = null

    val isLoading: Boolean
      get() = _visible

    private var _visible = false

    private var currentAlpha = 0f
    private val textComponent: NonOpaquePanel

    init {
      isOpaque = false
      isVisible = false
      progress.isOpaque = false
      textComponent = customizeLoadingLayer(parent = this, text = text, icon = progress)
      progress.suspend()
    }

    fun setVisible(visible: Boolean, takeSnapshot: Boolean) {
      if (_visible == visible || (_visible && currentAlpha != -1f)) {
        return
      }

      _visible = visible
      fadeOutAnimator?.reset()
      if (_visible) {
        isVisible = true
        currentAlpha = -1f
        if (takeSnapshot && width > 0 && height > 0) {
          snapshot = ImageUtil.createImage(GraphicsUtil.safelyGetGraphics(this), width, height, BufferedImage.TYPE_INT_RGB)
          val g = snapshot!!.createGraphics()
          pane.paint(g)
          val opaque = UIUtil.findNearestOpaque(this)
          snapshotBg = if (opaque == null) JBColor.PanelBackground else opaque.background
          g.dispose()
        }
        progress.resume()
        fadeOutAnimator?.suspend()
      }
      else {
        disposeSnapshot()
        progress.suspend()
        fadeOutAnimator?.resume()
      }
    }

    private fun disposeSnapshot() {
      snapshot?.let {
        it.flush()
        snapshot = null
      }
    }

    override fun paintComponent(g: Graphics) {
      val snapshot = snapshot
      if (snapshot != null) {
        if (snapshot.width == width && snapshot.height == height) {
          g.drawImage(snapshot, 0, 0, width, height, null)
          @Suppress("UseJBColor")
          g.color = Color(200, 200, 200, 240)
          g.fillRect(0, 0, width, height)
          return
        }
        else {
          disposeSnapshot()
        }
      }
      val background = snapshotBg ?: overlayBackground
      if (background != null) {
        g.color = background
        g.fillRect(0, 0, width, height)
      }
    }

    fun setAlpha(alpha: Float) {
      currentAlpha = alpha
      paintImmediately(textComponent.bounds)
    }

    override fun paintChildren(g: Graphics) {
      if (currentAlpha != -1f) {
        (g as Graphics2D).composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, currentAlpha)
      }
      super.paintChildren(g)
    }
  }

  interface CursorAware
}

private class LoadingLayerAnimator(
  private val setAlpha: FloatConsumer,
  private val end: () -> Unit,
) : Animator(
  name = "Loading",
  totalFrames = 10,
  cycleDuration = if (RemoteDesktopService.isRemoteSession()) 2500 else 500,
  isRepeatable = false,
) {
  override fun paintNow(frame: Int, totalFrames: Int, cycle: Int) {
    setAlpha.accept(1f - (frame.toFloat() / totalFrames.toFloat()))
  }

  override fun paintCycleEnd() {
    // paint with zero alpha before hiding completely
    // if called from Animator constructor, maybe field not yet initialized
    setAlpha.accept(0f)
    end()
  }
}

private class LoadingDecoratorLayeredPane(private val content: JComponent?) : JBLayeredPane(), LoadingDecorator.CursorAware {
  init {
    isFullOverlayLayout = true
  }

  override fun getMinimumSize(): Dimension {
    return if (content != null && !isMinimumSizeSet) content.minimumSize else super.getMinimumSize()
  }

  override fun getPreferredSize(): Dimension {
    return if (content != null && !isPreferredSizeSet) content.preferredSize else super.getPreferredSize()
  }
}