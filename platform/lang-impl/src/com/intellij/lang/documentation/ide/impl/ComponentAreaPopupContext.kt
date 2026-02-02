// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.impl

import com.intellij.codeInsight.hints.presentation.translateNew
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.IdeEventQueue.Companion.getInstance
import com.intellij.lang.documentation.ide.ui.PopupUpdateEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupRelativePosition
import com.intellij.openapi.ui.popup.PopupShowOptionsBuilder
import com.intellij.openapi.ui.popup.PopupShowOptionsImpl
import com.intellij.ui.MouseMovementTracker
import com.intellij.ui.ScreenUtil
import com.intellij.ui.WidthBasedLayout
import com.intellij.ui.awt.AnchoredPoint
import com.intellij.ui.popup.AbstractPopup
import com.intellij.ui.util.height
import com.intellij.ui.util.width
import com.intellij.util.Alarm
import com.intellij.util.asSafely
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.yield
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.lang.ref.WeakReference
import javax.swing.SwingUtilities
import kotlin.math.max
import kotlin.math.min

internal class ComponentAreaPopupContext(
  project: Project,
  component: Component,
  private val areaWithinComponent: Rectangle,
  private val onDocumentationSessionDone: Runnable?,
  private val minHeight: Int,
  private val delay: Int,
) : DefaultPopupContext(project, null) {

  private val myComponentReference = WeakReference(component)
  private var boundsHandler: DocSessionPopupBoundsHandler? = null

  private var mouseMovementTracker = MouseMovementTracker()

  val session: DocumentationManager.DocumentationOnHoverSession = object : DocumentationManager.DocumentationOnHoverSession {
    override fun mouseOutsideOfSourceArea() {
      boundsHandler?.mouseOutsideOfSourceArea()
    }

    override fun mouseWithinSourceArea() {
      boundsHandler?.mouseWithinSourceArea()
    }

    override fun tryFinishImmediately(): Boolean {
      boundsHandler?.let { return it.tryFinishImmediately() }
      onDocumentationSessionDone?.run()
      return true
    }

  }

  override fun boundsHandler(): PopupBoundsHandler =
    DocSessionPopupBoundsHandler().also { boundsHandler = it }

  inner class DocSessionPopupBoundsHandler : PopupBoundsHandler {

    private var mouseMoved = false
    private var mouseInDocPopup = false
    private var hideRequested = false
    private var isMovingTowardsPopup = false
    private var clickedInside: Boolean = false
    private var popup: AbstractPopup? = null
    private var alarm: Alarm? = null
    private var defaultPopupHeight: Int? = null

    override fun showPopup(popup: AbstractPopup) {
      if (hideRequested) {
        popup.cancel()
        return
      }
      alarm = Alarm(popup)
      this.popup = popup
      val position = calculatePosition(myComponentReference.get() ?: return, popup)

      alarm!!.addRequest(Runnable {
        val component = myComponentReference.get() ?: return@Runnable
        if (hideRequested
            || !popup.canShow()
            || !component.isShowing
            || SwingUtilities.getWindowAncestor(component)
              ?.let { window ->
                val windowLocation = window.locationOnScreen
                val relativeMousePosition = MouseInfo.getPointerInfo().location.translateNew(-windowLocation.x, -windowLocation.y)
                val componentUnderMouse = window.findComponentAt(relativeMousePosition)
                return@let window.isFocused && componentUnderMouse == component
              } != true
        ) {
          popup.cancel()
          return@Runnable
        }
        popup.setRequestFocus(false)
        popup.show(position)
        relocatePopupIfNeeded(popup)
        val window = SwingUtilities.getWindowAncestor(popup.content)
        getInstance().addDispatcher(object : IdeEventQueue.NonLockedEventDispatcher {

          override fun dispatch(e: AWTEvent): Boolean {
            if (e.source == window) {
              when (e.id) {
                MouseEvent.MOUSE_EXITED -> if (!clickedInside) {
                  mouseLeftPopup()
                }
                MouseEvent.MOUSE_ENTERED -> if (!clickedInside) {
                  mouseEnteredPopup()
                }
                MouseEvent.MOUSE_CLICKED -> clickedInside = true
              }
            }
            else if (e.id == MouseEvent.MOUSE_CLICKED && clickedInside) {
              if (SwingUtilities.getWindowAncestor(e.source.asSafely<Component>()) != window) {
                mouseInDocPopup = false
                clickedInside = false
                alarm?.cancelAllRequests()
                alarm = null
                popup.cancel()
              }
            }
            if (e.id == MouseEvent.MOUSE_MOVED && e is MouseEvent) {
              mouseMoved = true
              val screenBounds = popup.consumedScreenBounds
              isMovingTowardsPopup = mouseMovementTracker.isMovingTowards(e, screenBounds)
            }
            return false
          }
        }, popup)
      }, delay)
    }

    fun mouseOutsideOfSourceArea() {
      if (popup?.isVisible == false) {
        hideRequested = true
        alarm?.cancelAllRequests()
        popup?.cancel()
      }
      else if (!mouseInDocPopup && mouseMoved) {
        hideRequested = true
        scheduleHide()
      }
    }

    fun mouseWithinSourceArea() {
      hideRequested = false
    }

    fun tryFinishImmediately(): Boolean {
      if (clickedInside) return false
      if (isMovingTowardsPopup) return false
      hideRequested = true
      alarm?.cancelAllRequests()
      if (popup != null) {
        popup?.cancel()
      }
      else {
        onDocumentationSessionDone?.run()
      }
      return true
    }

    private fun mouseEnteredPopup() {
      if (!mouseMoved) return
      mouseInDocPopup = true
    }

    private fun mouseLeftPopup() {
      if (!mouseMoved) return
      mouseInDocPopup = false
      clickedInside = false
      hideRequested = true
      scheduleHide()
    }

    private fun scheduleHide() {
      alarm?.let {
        if (it.isEmpty) {
          it.addRequest(Runnable {
            if (isMovingTowardsPopup) {
              scheduleHide()
            }
            else if (hideRequested && !mouseInDocPopup) {
              popup?.cancel()
            }
          }, 50)
        }
      }
    }

    private fun calculatePosition(component: Component, popup: AbstractPopup): PopupShowOptionsBuilder {
      val bounds = component.bounds
      return PopupShowOptionsBuilder()
               .withComponentPoint(AnchoredPoint(
                 AnchoredPoint.Anchor.TOP_LEFT,
                 component,
                 Point(bounds.x + areaWithinComponent.x, bounds.y + areaWithinComponent.y + areaWithinComponent.height),
               ))
               .withRelativePosition(PopupRelativePosition.BOTTOM)
               .withDefaultPopupAnchor(AnchoredPoint.Anchor.TOP_LEFT)
               .withMinimumHeight(minHeight)
               .withDefaultPopupComponentUnscaledGap(4)
               .takeIf { isWithinScreen(it.build(), popup) }
             ?: PopupShowOptionsBuilder()
               .withComponentPoint(AnchoredPoint(
                 AnchoredPoint.Anchor.TOP_LEFT,
                 component,
                 Point(bounds.x + areaWithinComponent.x, bounds.y + areaWithinComponent.y),
               ))
               .withRelativePosition(PopupRelativePosition.TOP)
               .withDefaultPopupAnchor(AnchoredPoint.Anchor.BOTTOM_LEFT)
               .withMinimumHeight(minHeight)
               .withDefaultPopupComponentUnscaledGap(4)
    }


    override suspend fun updatePopup(popup: AbstractPopup, resized: Boolean, popupUpdateEvent: PopupUpdateEvent) {
      if (myComponentReference.get()?.isShowing != true) {
        popup.cancel()
        return
      }
      if (!resized) {
        resizePopup(popup, popupUpdateEvent)
        yield()
      }
      adjustPopupHeight(popup, popupUpdateEvent)
      relocatePopupIfNeeded(popup)
    }

    private fun adjustPopupHeight(
      popup: AbstractPopup,
      popupUpdateEvent: PopupUpdateEvent,
    ) {
      val width = popup.width - JBUI.scale(1) -
                  generateSequence(popup.component as Container) { it.parent }.sumOf { it.insets.width }
      val hMax = WidthBasedLayout.getPreferredHeight(popup.component.getComponent(0), width) +
                 generateSequence(popup.component as Container) { it.parent }.sumOf { it.insets.height } +
                 // add some margin, we are not able to calculate from other sources.
                 JBUI.scale(1)
      val currentHeight = popup.height
      if (currentHeight > hMax) {
        // Shrink popup
        if (defaultPopupHeight == null) {
          defaultPopupHeight = currentHeight
        }
        popup.size = Dimension(width, hMax)
      }
      else if (popupUpdateEvent is PopupUpdateEvent.ContentChanged
               && popupUpdateEvent.updateKind == PopupUpdateEvent.ContentUpdateKind.DocumentationPageOpened
               && hMax > currentHeight
               && defaultPopupHeight != null) {
        // restore popup height after showing a notification
        popup.size = Dimension(width, min(hMax, defaultPopupHeight!!))
      }
    }

    private fun relocatePopupIfNeeded(popup: AbstractPopup) {
      var popupLocation = popup.locationOnScreen
      val screen = ScreenUtil.getScreenRectangle(popupLocation)
      val popupSize = popup.size
      if (popupLocation.y + popupSize.height > screen.y + screen.height) {
        popup.setLocation(Point(popupLocation.x, screen.y + screen.height - popupSize.height))
        popupLocation = popup.locationOnScreen
      }

      val componentLocation = myComponentReference.get()?.locationOnScreen ?: return
      val componentActiveArea = Rectangle(areaWithinComponent).also {
        it.x += componentLocation.x
        it.y += componentLocation.y
      }
      val popupStartOffset = popupLocation.y
      val popupEndOffset = popupLocation.y + popupSize.height
      val componentActiveAreaStartOffset = componentActiveArea.y
      val componentActiveAreaEndOffset = componentActiveArea.y + componentActiveArea.height

      // if popup area and component active area intersect
      if (max(popupStartOffset, componentActiveAreaStartOffset) < min(popupEndOffset, componentActiveAreaEndOffset)) {
        // reposition popup above the component
        popupLocation.y = componentActiveArea.y - popupSize.height - 4
        popup.setLocation(popupLocation)
      }
    }

    private fun isWithinScreen(position: PopupShowOptionsImpl, popup: AbstractPopup): Boolean {
      val screen = ScreenUtil.getScreenRectangle(position.screenX, position.screenY)
      val targetBounds = Rectangle(Point(position.screenX, position.screenY), popup.content.getPreferredSize())
      if (targetBounds.height < 200) {
        targetBounds.height = 200
      }
      targetBounds.height += position.popupComponentUnscaledGap
      return screen.contains(targetBounds)
    }
  }
}