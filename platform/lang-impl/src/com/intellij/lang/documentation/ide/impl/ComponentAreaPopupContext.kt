// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.impl

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.IdeEventQueue.Companion.getInstance
import com.intellij.lang.documentation.ide.ui.PopupUpdateEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupRelativePosition
import com.intellij.openapi.ui.popup.PopupShowOptionsBuilder
import com.intellij.openapi.ui.popup.PopupShowOptionsImpl
import com.intellij.ui.MouseMovementTracker
import com.intellij.ui.ScreenUtil
import com.intellij.ui.awt.AnchoredPoint
import com.intellij.ui.popup.AbstractPopup
import com.intellij.util.Alarm
import com.intellij.util.asSafely
import kotlinx.coroutines.yield
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.lang.ref.WeakReference
import javax.swing.SwingUtilities

internal class ComponentAreaPopupContext(
  project: Project,
  component: Component,
  private val areaWithinComponent: Rectangle,
  private val onDocumentationSessionDone: Runnable?,
  private val minHeight: Int,
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

    override fun showPopup(popup: AbstractPopup) {
      if (hideRequested) {
        popup.cancel()
        return
      }
      alarm = Alarm(popup)
      this.popup = popup
      val position = calculatePosition(myComponentReference.get() ?: return, popup)

      alarm!!.addRequest(Runnable {
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
        // Add some delay, which usually is related to reference resolve and other stuff,
        // so that the popup does not appear immediately, even if the delay is zero.
      }, 150 + CodeInsightSettings.getInstance().JAVADOC_INFO_DELAY)
    }

    fun mouseOutsideOfSourceArea() {
      if (!mouseInDocPopup && mouseMoved) {
        hideRequested = true
        if (popup?.isVisible == false) {
          alarm?.cancelAllRequests()
          popup?.cancel()
        }
        else {
          scheduleHide()
        }
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

    private fun calculatePosition(component: Component, popup: AbstractPopup) =
      PopupShowOptionsBuilder()
        .withComponentPoint(AnchoredPoint(
          AnchoredPoint.Anchor.TOP_LEFT,
          component,
          Point(areaWithinComponent.x, areaWithinComponent.y + areaWithinComponent.height),
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
          Point(areaWithinComponent.x, areaWithinComponent.y),
        ))
        .withRelativePosition(PopupRelativePosition.TOP)
        .withDefaultPopupAnchor(AnchoredPoint.Anchor.BOTTOM_LEFT)
        .withMinimumHeight(minHeight)
        .withDefaultPopupComponentUnscaledGap(4)


    override suspend fun updatePopup(popup: AbstractPopup, resized: Boolean, popupUpdateEvent: PopupUpdateEvent) {
      if (!resized) {
        resizePopup(popup, popupUpdateEvent)
        yield()
      }
      relocatePopupIfNeeded(popup)
    }

    private fun relocatePopupIfNeeded(popup: AbstractPopup) {
      val componentLocation = myComponentReference.get()?.locationOnScreen ?: return
      val componentActiveArea = Rectangle(areaWithinComponent).also {
        it.x += componentLocation.x
        it.y += componentLocation.y
      }

      val popupLocation = popup.locationOnScreen
      if (popupLocation.y < componentActiveArea.y + componentActiveArea.height) {
        // reposition popup above the component
        val bounds = popup.content.bounds
        if (bounds.height < componentActiveArea.y) {
          popupLocation.y = componentActiveArea.y - bounds.height - 4
          popup.setLocation(popupLocation)
        }
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