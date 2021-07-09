// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.AbstractPopup
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.PositionTracker
import com.intellij.util.ui.UIUtil
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.event.AncestorEvent
import javax.swing.event.AncestorListener

class RunToolbarExtraSlotsAction : AnAction(), CustomComponentAction, DumbAware {

  override fun actionPerformed(e: AnActionEvent) {

  }

  override fun update(e: AnActionEvent) {
    e.project?.let {
      val slotManager = RunToolbarSlotManager.getInstance(it)
      val isOpened = e.dataContext.getData(RunToolbarMainWidgetComponent.RUN_TOOLBAR_MAIN_WIDGET_COMPONENT_KEY)?.isOpened
                     ?: false
      e.presentation.icon = when {
        isOpened -> AllIcons.Toolbar.Collapse
        slotManager.slotsCount() == 0 -> AllIcons.Toolbar.AddSlot
        else -> AllIcons.Toolbar.Expand
      }
    }
  }

  override fun createCustomComponent(presentation: Presentation, place: String, dataContext: DataContext): JComponent {
    return object : ActionButton(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {

      private var popup: JBPopup? = null
      private var slotsPane: RunToolbarExtraSlotPane? = null

      private val t: Int = JBUI.scale(5)
      fun updateIconImmediately(manager: RunToolbarSlotManager, mainWidget: RunToolbarMainWidgetComponent) {
        myIcon = when {
          mainWidget.isOpened -> AllIcons.Toolbar.Collapse
          manager.slotsCount() == 0 -> AllIcons.Toolbar.AddSlot
          else -> AllIcons.Toolbar.Expand
        }

      }

      private var canClose = false
      override fun actionPerformed(event: AnActionEvent) {
        event.project?.let { project ->
          val manager = RunToolbarSlotManager.getInstance(project)
          event.dataContext.getData(RunToolbarMainWidgetComponent.RUN_TOOLBAR_MAIN_WIDGET_COMPONENT_KEY)?.let { mainWidgetComponent ->
            if (mainWidgetComponent.isOpened) {
              canClose = true
              popup?.cancel()
              return
            }


            val pane = RunToolbarExtraSlotPane(project)
            if (manager.slotsCount() == 0) {
              manager.addNewSlot()
            }
            slotsPane = pane

            val tracker = object : PositionTracker<AbstractPopup>(this) {
              override fun recalculateLocation(b: AbstractPopup): RelativePoint {
                val location = bounds.location
                location.y += height + t
                return RelativePoint(component, location)
              }
            }
            val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(pane.getView(), mainWidgetComponent)
              .setCancelOnClickOutside(true)
              .setCancelCallback { canClose }
              .setShowBorder(true)
              .setCancelOnOtherWindowOpen(true)
              .createPopup()

            val recalculateLocation = if (popup is AbstractPopup) tracker.recalculateLocation(popup)
            else RelativePoint(this, this.bounds.location)
            popup.show(recalculateLocation)
            mainWidgetComponent.isOpened = true

            val ancestorListener = object : AncestorListener {
              override fun ancestorAdded(event: AncestorEvent?) {

              }

              override fun ancestorRemoved(event: AncestorEvent?) {
                canClose = true
                popup.cancel()
              }

              override fun ancestorMoved(event: AncestorEvent?) {
              }
            }

            mainWidgetComponent.addAncestorListener(ancestorListener)

            val listener = object : ComponentAdapter() {
              override fun componentMoved(e: ComponentEvent?) {
                updateLocation()
              }

              override fun componentResized(e: ComponentEvent?) {
                updateLocation()
              }

              private fun updateLocation() {
                if (popup is AbstractPopup)
                  popup.setLocation(tracker.recalculateLocation(popup))
              }
            }

            UIUtil.getRootPane(this)?.layeredPane?.let {
              it.addComponentListener(listener)
              Disposer.register(popup) {
                it.removeComponentListener(listener)
              }
            }

            Disposer.register(popup) {
              mainWidgetComponent.isOpened = false
              updateIconImmediately(manager, mainWidgetComponent)
              mainWidgetComponent.removeAncestorListener(ancestorListener)
              canClose = false
              this.popup = null

            }
            updateIconImmediately(manager, mainWidgetComponent)
            this.popup = popup
          }
        }
      }
    }
  }
}