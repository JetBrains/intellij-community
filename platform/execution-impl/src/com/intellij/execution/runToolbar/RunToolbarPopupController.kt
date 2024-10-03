// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Disposer
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.AbstractPopup
import com.intellij.ui.popup.ComponentPopupBuilderImpl
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.PositionTracker
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.event.*
import java.util.function.Supplier
import javax.swing.SwingUtilities
import javax.swing.event.AncestorEvent
import javax.swing.event.AncestorListener

@ApiStatus.Internal
class RunToolbarPopupController(val project: Project,
                                val mainWidgetComponent: RunToolbarMainWidgetComponent) : PopupControllerComponentListener, Disposable {
  private var popup: JBPopup? = null
  private var pane: RunToolbarExtraSlotPane = RunToolbarExtraSlotPane(project, { mainWidgetComponent.width })

  private val t: Int = JBUI.scale(4)

  private val popupControllerComponents = mutableListOf<Component>()

  private var componentPressed = false
  private var canClose = false

  init {
    Disposer.register(project, this)
  }

  internal fun updateControllerComponents(components: MutableList<Component>) {
    getPopupControllers().forEach { it.removeListener(this) }

    popupControllerComponents.clear()
    popupControllerComponents.addAll(components)

    getPopupControllers().forEach { it.addListener(this) }
  }

  private fun getPopupControllers(): MutableList<PopupControllerComponent> {
    return popupControllerComponents.filter { it is PopupControllerComponent  }.map { (it as PopupControllerComponent).getController() }.toMutableList()
  }

  private fun show() {
    if (mainWidgetComponent.isOpened) {
      cancel()
      return
    }

    mainWidgetComponent.isOpened = true
    val slotPane = (if (pane.project == project) pane else null) ?:
                   run {
                     pane.clear()
                     RunToolbarExtraSlotPane(project, { mainWidgetComponent.width })
                   }
    pane = slotPane

    fun getTrackerRelativePoint(): RelativePoint {
      return RelativePoint(mainWidgetComponent, Point(0, mainWidgetComponent.height + t))
    }

    val tracker = object : PositionTracker<AbstractPopup>(mainWidgetComponent) {
      override fun recalculateLocation(b: AbstractPopup): RelativePoint {
        return getTrackerRelativePoint()
      }
    }

    val builder = object : ComponentPopupBuilderImpl(slotPane.getView(), mainWidgetComponent) {
      override fun createPopup(): JBPopup {
        return createPopup(Supplier<AbstractPopup?> {
          object : AbstractPopup() {

            override fun cancel(e: InputEvent?) {
              e?.let {
                if (it is MouseEvent) {
                  checkBounds(it)
                }
              }
              super.cancel(e)
            }
          }
        })
      }
    }

    val popup = builder
      .setCancelOnClickOutside(true)
      .setCancelCallback {
        (canClose || popup?.isFocused == false) && !componentPressed
      }
      .setMayBeParent(true)
      .setShowBorder(true)
      .createPopup()

    fun updatePopupLocation() {
      if (popup is AbstractPopup) {
        popup.setLocation(tracker.recalculateLocation(popup))

        popup.popupWindow?.let {
          if (it.isShowing) {
            it.pack()
          }
        }
      }
    }

    SwingUtilities.invokeLater {
      updatePopupLocation()
    }

    popup.show(if (popup is AbstractPopup)
                 tracker.recalculateLocation(popup)
               else
                 getTrackerRelativePoint()
    )

    updatePopupLocation()

    val ancestorListener = object : AncestorListener {
      override fun ancestorAdded(event: AncestorEvent?) {

      }

      override fun ancestorRemoved(event: AncestorEvent?) {
        cancel()
      }

      override fun ancestorMoved(event: AncestorEvent?) {
      }
    }

    mainWidgetComponent.addAncestorListener(ancestorListener)

    val adapterListener = object : ComponentAdapter() {
      override fun componentMoved(e: ComponentEvent?) {
        updateLocation()
      }

      override fun componentResized(e: ComponentEvent?) {
        updateLocation()
      }

      private fun updateLocation() {
        updatePopupLocation()
      }
    }

    mainWidgetComponent.addComponentListener(adapterListener)

    val awtEventListener = AWTEventListener {
      if (it.id == MouseEvent.MOUSE_RELEASED) {
        componentPressed = false
      }
      else if (it is WindowEvent) {
        if (it.window is Dialog) {
          cancel()
        }
      }
    }

    Toolkit.getDefaultToolkit().addAWTEventListener(
      awtEventListener, AWTEvent.MOUSE_EVENT_MASK or AWTEvent.WINDOW_EVENT_MASK)


    UIUtil.getRootPane(mainWidgetComponent)?.layeredPane?.let {
      it.addComponentListener(adapterListener)
      Disposer.register(popup) {
        it.removeComponentListener(adapterListener)
      }
    }

    Disposer.register(popup) {
      componentPressed = false
      mainWidgetComponent.isOpened = false
      getPopupControllers().forEach { it.updateIconImmediately(mainWidgetComponent.isOpened) }
      mainWidgetComponent.removeAncestorListener(ancestorListener)
      mainWidgetComponent.removeComponentListener(adapterListener)
      Toolkit.getDefaultToolkit().removeAWTEventListener(awtEventListener)
      canClose = false
      pane.clear()
      this.popup = null
    }
    getPopupControllers().forEach { it.updateIconImmediately(mainWidgetComponent.isOpened) }

    this.popup = popup
  }

  private fun checkBounds(e: MouseEvent) {
    val bounds = mainWidgetComponent.bounds
    bounds.location = Point(0, 0)
    if (bounds.contains(RelativePoint(e).getPoint(mainWidgetComponent))) {
      componentPressed = true
    }
  }

  internal fun cancel() {
    componentPressed = false
    canClose = true
    pane.clear()
    popup?.cancel()
  }

  override fun dispose() {
    cancel()
    getPopupControllers().forEach { it.removeListener(this) }
  }

  override fun actionPerformedHandler() {
    show()
  }
}

@ApiStatus.Internal
interface PopupControllerComponent {
  fun addListener(listener: PopupControllerComponentListener)
  fun removeListener(listener: PopupControllerComponentListener)
  fun updateIconImmediately(isOpened: Boolean)

  fun getController(): PopupControllerComponent {
    return this
  }
}

@ApiStatus.Internal
interface PopupControllerComponentListener {
  fun actionPerformedHandler()
}