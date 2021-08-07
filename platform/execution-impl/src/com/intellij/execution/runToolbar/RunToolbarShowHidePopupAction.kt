// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.IdeFrame
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.AbstractPopup
import com.intellij.ui.popup.ComponentPopupBuilderImpl
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.PositionTracker
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.*
import java.beans.PropertyChangeEvent
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.event.AncestorEvent
import javax.swing.event.AncestorListener


class RunToolbarShowHidePopupAction : AnAction(), CustomComponentAction, DumbAware {
/*  companion object {
    private val PROP_ACTIVE_PROCESS_COLOR = Key<Color>("ACTIVE_PROCESS_COLOR")
  }*/

  override fun actionPerformed(e: AnActionEvent) {

  }

  override fun update(e: AnActionEvent) {
    e.project?.let {
      val slotManager = RunToolbarSlotManager.getInstance(it)

/*      slotManager.getMainOrFirstActiveProcess()?.let{
        e.presentation.putClientProperty(PROP_ACTIVE_PROCESS_COLOR, it.pillColor)
      }*/

      val isOpened = e.dataContext.getData(RunToolbarMainWidgetComponent.RUN_TOOLBAR_MAIN_WIDGET_COMPONENT_KEY)?.isOpened
                     ?: false
      e.presentation.icon = when {
        isOpened -> {
          e.presentation.text = ActionsBundle.message("action.RunToolbarShowHidePopupAction.hide.text")
          AllIcons.Toolbar.Collapse
        }
        slotManager.slotsCount() == 0 -> {
          e.presentation.text = ActionsBundle.message("action.RunToolbarShowHidePopupAction.text")
          AllIcons.Toolbar.AddSlot
        }
        else -> {
          e.presentation.text = ActionsBundle.message("action.RunToolbarShowHidePopupAction.show.text")
          AllIcons.Toolbar.Expand
        }
      }
    }
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return ExtraSlotsActionButton(this, presentation, place)
  }

  private class ExtraSlotsActionButton(action: AnAction,
                               presentation: Presentation,
                               place: String) : ActionButton(action, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
    companion object {
      private var counter: MutableMap<Project, Int> = mutableMapOf()
    }

    private var popup: JBPopup? = null
    private var pane: RunToolbarExtraSlotPane? = null
    private var project: Project? = null

    private val t: Int = JBUI.scale(4)
    fun updateIconImmediately(manager: RunToolbarSlotManager, mainWidget: RunToolbarMainWidgetComponent) {
      myIcon = when {
        mainWidget.isOpened -> AllIcons.Toolbar.Collapse
        manager.slotsCount() == 0 -> AllIcons.Toolbar.AddSlot
        else -> AllIcons.Toolbar.Expand
      }
    }

    private var canClose = false
    private var pressedOnMainWidget = false
    private var pressedOnButton = false

    override fun addNotify() {
      super.addNotify()

      (SwingUtilities.getWindowAncestor(this) as? IdeFrame)?.project?.let {
        project = it
        val value = counter.getOrDefault(it, 0) + 1
        counter[it] = value
        if (value == 1) {
          RunToolbarSlotManager.getInstance(it).active = true
        }
        pane = RunToolbarExtraSlotPane(it) { cancel() }
      }

      mousePosition?.let {
        val bounds = this.bounds
        bounds.location = Point(0, 0)

        if (bounds.contains(it)) {
          myRollover = true
          repaint()
        }
      }
    }

    override fun removeNotify() {
      project?.let { project ->
        counter[project]?.let {
          val value = maxOf(it - 1, 0)
          counter[project] = value
          if (value == 0) {
            RunToolbarSlotManager.getInstance(project).active = false
          }
        }
      }
      super.removeNotify()
    }

/*    protected override fun presentationPropertyChanged(e: PropertyChangeEvent) {
      super.presentationPropertyChanged(e)

      presentation.getClientProperty(PROP_ACTIVE_PROCESS_COLOR)?.let {
        putClientProperty("JButton.backgroundColor", it)
        background = it
      }

    }*/

    private fun cancel() {
      pressedOnMainWidget = false
      pressedOnButton = false
      canClose = true
      popup?.cancel()
    }

    override fun actionPerformed(event: AnActionEvent) {
      event.project?.let { project ->
        val manager = RunToolbarSlotManager.getInstance(project)
        event.dataContext.getData(RunToolbarMainWidgetComponent.RUN_TOOLBAR_MAIN_WIDGET_COMPONENT_KEY)?.let { mainWidgetComponent ->
          if (mainWidgetComponent.isOpened) {
            cancel()
            return
          }

          mainWidgetComponent.isOpened = true
          val slotPane = (if (pane?.project == project) pane else null) ?: RunToolbarExtraSlotPane(project) { cancel() }
          pane = slotPane

          if (manager.slotsCount() == 0) {
            manager.addNewSlot()
          }

          val tracker = object : PositionTracker<AbstractPopup>(this) {
            override fun recalculateLocation(b: AbstractPopup): RelativePoint {
              val location = bounds.location
              location.y += height + t
              return RelativePoint(component, location)
            }
          }

          val button = this

          val builder = object : ComponentPopupBuilderImpl(slotPane.getView(), mainWidgetComponent) {
            override fun createPopup(): JBPopup {
              return createPopup(Supplier<AbstractPopup?> {
                object : AbstractPopup() {

                  override fun cancel(e: InputEvent?) {
                    e?.let {
                      if (it is MouseEvent) {
                        val bounds = button.bounds
                        bounds.location = Point(0, 0)
                        if (bounds.contains(RelativePoint(it).getPoint(button))) {
                          pressedOnButton = true
                        }
                        else {
                          val mainBounds = mainWidgetComponent.bounds
                          mainBounds.location = Point(0, 0)
                          pressedOnMainWidget = mainBounds.contains(RelativePoint(it).getPoint(mainWidgetComponent))
                        }
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
              (canClose || popup?.isFocused == false) && (!pressedOnMainWidget && !pressedOnButton)
            }
            .setMayBeParent(true)
            .setShowBorder(true)
            .createPopup()

          val recalculateLocation = if (popup is AbstractPopup) tracker.recalculateLocation(popup)
          else RelativePoint(this, this.bounds.location)
          popup.show(recalculateLocation)

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
              if (popup is AbstractPopup) {
                popup.setLocation(tracker.recalculateLocation(popup))
              }
            }
          }

          val awtEventListener = AWTEventListener {
            if (it.id == MouseEvent.MOUSE_RELEASED) {
              pressedOnMainWidget = false
            }
            else if (it is WindowEvent) {
              if (it.window is Dialog) {
                cancel()
              }
            }
          }

          Toolkit.getDefaultToolkit().addAWTEventListener(
            awtEventListener, AWTEvent.MOUSE_EVENT_MASK or AWTEvent.WINDOW_EVENT_MASK)


          UIUtil.getRootPane(this)?.layeredPane?.let {
            it.addComponentListener(adapterListener)
            Disposer.register(popup) {
              it.removeComponentListener(adapterListener)
            }
          }

          Disposer.register(popup) {
            pressedOnMainWidget = false
            pressedOnButton = false
            mainWidgetComponent.isOpened = false
            updateIconImmediately(manager, mainWidgetComponent)
            mainWidgetComponent.removeAncestorListener(ancestorListener)
            Toolkit.getDefaultToolkit().removeAWTEventListener(awtEventListener)
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