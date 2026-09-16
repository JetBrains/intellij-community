// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ComponentUtil
import com.intellij.util.ui.EdtInvocationManager
import com.intellij.util.ui.MacScrollbarPreferences
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Graphics2D
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.JScrollBar
import javax.swing.JScrollPane

internal open class MacScrollBarUI : DefaultScrollBarUI {
  constructor(thickness: Int, thicknessMax: Int, thicknessMin: Int) : super(
    thickness = thickness,
    thicknessMax = thicknessMax,
    thicknessMin = thicknessMin,
  )

  constructor() : super(thickness = 14, thicknessMax = 14, thicknessMin = 11)

  companion object {
    private val CURRENT_STYLE = object : MacScrollbarNative<MacScrollbarStyle>() {
      override fun run() {
        if (!SystemInfoRt.isMac) {
          return
        }

        val oldStyle = invoke()
        if (!Registry.`is`("ide.mac.disableMacScrollbars", false)) {
          super.run()
        }

        val newStyle = invoke()
        if (newStyle != oldStyle) {
          val list = ArrayList<MacScrollBarUI>()
          processReferences(toAdd = null, toRemove = null, list = list)
          for (ui in list) {
            ui.updateStyle(newStyle)
          }
        }
      }

      override fun invoke(): MacScrollbarStyle {
        val style = MacScrollbarPreferences.getPreferredStyle()
        val value = if (1 == style.toInt()) MacScrollbarStyle.Overlay else MacScrollbarStyle.Legacy
        Logger.getInstance(MacScrollBarUI::class.java).debug("scroll bar style ", value, " from ", style)
        return value
      }

      override fun toString(): String = "scroll bar style"

      override fun initialize() {
        MacScrollbarPreferences.observeStyleChanges(this)
      }
    }

    /**
     * The movement listener that is intended to do not hide shown thumb while mouse is moving.
     */
    private val MOVEMENT_LISTENER = AtomicReference<AWTEventListener?>(object : AWTEventListener {
      override fun eventDispatched(event: AWTEvent?) {
        if (event != null && MouseEvent.MOUSE_MOVED == event.id) {
          val source = event.source
          if (source is Component) {
            val pane = ComponentUtil.getParentOfType(JScrollPane::class.java, source)
            if (pane != null) {
              pauseThumbAnimation(pane.horizontalScrollBar)
              pauseThumbAnimation(pane.verticalScrollBar)
            }
          }
        }
      }

      /**
       * Pauses animation of the thumb if it is shown.
       *
       * @param bar the scroll bar with custom UI
       */
      private fun pauseThumbAnimation(bar: JScrollBar?) {
        val ui = bar?.ui
        if (ui is MacScrollBarUI) {
          val animationBehavior = ui.installedState!!.animationBehavior
          if (0 < animationBehavior.thumbFrame) {
            animationBehavior.onThumbMove()
          }
        }
      }
    })
  }

  override fun createBaseAnimationBehavior(state: DefaultScrollbarUiInstalledState): ScrollBarAnimationBehavior {
    if (!SystemInfoRt.isMac) {
      return super.createBaseAnimationBehavior(state)
    }
    return MacScrollBarAnimationBehavior(
      state.coroutineScope,
      scrollBarComputable = { state.scrollBar },
      trackAnimator = state.track.animator,
      thumbAnimator = state.thumb.animator,
    )
  }

  override fun isAbsolutePositioning(event: MouseEvent): Boolean {
    if (!SystemInfoRt.isMac) {
      return super.isAbsolutePositioning(event)
    }
    return Behavior.JumpToSpot == Behavior.CURRENT_BEHAVIOR()
  }

  override fun isTrackClickable(): Boolean {
    if (!SystemInfoRt.isMac) {
      return super.isTrackClickable()
    }
    val state = installedState ?: return false
    return isOpaque(state.scrollBar) || (state.animationBehavior.trackFrame > 0 && state.animationBehavior.thumbFrame > 0)
  }

  override val isTrackExpandable: Boolean
    get() {
      if (!SystemInfoRt.isMac) {
        return super.isTrackExpandable
      }
      return !isOpaque(installedState!!.scrollBar)
    }

  override fun paintTrack(g: Graphics2D, c: JComponent) {
    val animationBehavior = installedState!!.animationBehavior
    if (animationBehavior.trackFrame > 0 && animationBehavior.thumbFrame > 0 || isOpaque(c) || !SystemInfoRt.isMac) {
      super.paintTrack(g, c)
    }
  }

  override fun paintThumb(g: Graphics2D, c: JComponent, state: DefaultScrollbarUiInstalledState) {
    if (!SystemInfoRt.isMac) {
      super.paintThumb(g, c, state)
    }
    else if (isOpaque(c)) {
      paint(p = state.thumb, g = g, c = c, small = true)
    }
    else if (state.animationBehavior.thumbFrame > 0) {
      paint(p = state.thumb, g = g, c = c, small = false)
    }
  }

  override fun installUI(c: JComponent) {
    super.installUI(c)
    if (SystemInfoRt.isMac) {
      updateStyle(CURRENT_STYLE())
    }
    else {
      updateStyle(MacScrollbarStyle.Overlay)
    }
    processReferences(toAdd = this, toRemove = null, list = null)
    val listener = MOVEMENT_LISTENER.getAndSet(null)
    if (listener != null) {
      Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.MOUSE_MOTION_EVENT_MASK)
    }
  }

  override fun uninstallUI(c: JComponent) {
    processReferences(toAdd = null, toRemove = this, list = null)
    super.uninstallUI(c)
  }

  protected open fun updateStyle(style: MacScrollbarStyle?) {
    val state = installedState
    val scrollBar = state?.scrollBar ?: return
    scrollBar.isOpaque = style != MacScrollbarStyle.Overlay
    scrollBar.revalidate()
    scrollBar.repaint()
    state.animationBehavior.onThumbMove()
  }
}

internal enum class MacScrollbarStyle {
  Legacy, Overlay;
}

private enum class Behavior {
  NextPage, JumpToSpot;

  companion object {
    val CURRENT_BEHAVIOR = object : MacScrollbarNative<Behavior>() {
      override fun invoke(): Behavior {
        return if (MacScrollbarPreferences.isJumpToSpot()) JumpToSpot else NextPage
      }

      override fun toString(): String = "scroll bar behavior"

      override fun initialize() {
        MacScrollbarPreferences.observeBehaviorChanges(this)
      }
    }
  }
}

private abstract class MacScrollbarNative<T> : Runnable, () -> T? {
  private var value: T? = null

  init {
    callMac { initialize() }
    @Suppress("LeakingThis")
    EdtInvocationManager.invokeLaterIfNeeded(this)
  }

  abstract fun initialize()

  override fun invoke() = value

  override fun run() {
    value = callMac(this)
  }
}

private val UI = ArrayList<Reference<MacScrollBarUI>>()

/**
 * Processes references in the static list of references synchronously.
 * This method removes all cleared references and the reference specified to remove,
 * collects objects from other references into the specified list and
 * adds the reference specified to add.
 *
 * @param toAdd    the object to add to the static list of references (ignored if `null`)
 * @param toRemove the object to remove from the static list of references (ignored if `null`)
 * @param list     the list to collect all available objects (ignored if `null`)
 */
private fun processReferences(toAdd: MacScrollBarUI?, toRemove: MacScrollBarUI?, list: MutableList<MacScrollBarUI>?) {
  synchronized(UI) {
    val iterator = UI.iterator()
    while (iterator.hasNext()) {
      val reference = iterator.next()
      val ui = reference.get()
      if (ui == null || ui === toRemove) {
        iterator.remove()
      }
      else {
        list?.add(ui)
      }
    }

    if (toAdd != null) {
      UI.add(WeakReference(toAdd))
    }
  }
}

private fun <T : Any> callMac(producer: () -> T?): T? {
  if (!SystemInfoRt.isMac) {
    return null
  }

  try {
    return producer()
  }
  catch (e: Throwable) {
    logger<MacScrollBarUI>().warn(e)
  }
  return null
}
