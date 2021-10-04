// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.navigationToolbar.experimental

import com.intellij.application.options.RegistryManager
import com.intellij.ide.ActivityTracker
import com.intellij.ide.ui.ToolbarSettings
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.ide.ui.experimental.toolbar.RunWidgetAvailabilityManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ActionToolbar.NOWRAP_LAYOUT_POLICY
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.wm.IdeRootPaneNorthExtension
import com.intellij.util.messages.Topic
import com.intellij.util.ui.JBSwingUtilities
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Graphics
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

interface NewToolbarPaneListener {
  companion object {
    val TOPIC: Topic<NewToolbarPaneListener> = Topic(NewToolbarPaneListener::class.java, Topic.BroadcastDirection.NONE, true)
  }

  fun stateChanged()
}

class NewToolbarRootPaneExtension(val myProject: Project) : IdeRootPaneNorthExtension(), Disposable {
  companion object {
    private const val NEW_TOOLBAR_KEY = "NEW_TOOLBAR_KEY"

    private val logger = logger<NewToolbarRootPaneExtension>()
  }

  private val runWidgetAvailabilityManager = RunWidgetAvailabilityManager.getInstance(myProject)
  private val runWidgetListener = object : RunWidgetAvailabilityManager.RunWidgetAvailabilityListener {
    override fun availabilityChanged(value: Boolean) {
      repaint()
    }
  }

  private val myPanelWrapper = JPanel(BorderLayout())
  private val myPanel: JPanel = object : JPanel(NewToolbarBorderLayout()) {
    init {
      isOpaque = true
      border = BorderFactory.createEmptyBorder(0, JBUI.scale(4), 0, JBUI.scale(4))
    }

    override fun getComponentGraphics(graphics: Graphics?): Graphics {
      return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics))
    }
  }

  init {
    Disposer.register(myProject, this)
    RegistryManager.getInstance().get("ide.new.navbar")
      .addListener(object : RegistryValueListener {
        override fun afterValueChanged(value: RegistryValue) {
          uiSettingsChanged(UISettings.instance)
        }
      }, this)

    myProject.messageBus
      .connect(this)
      .subscribe(NewToolbarPaneListener.TOPIC, object : NewToolbarPaneListener {
        override fun stateChanged() {
          repaint()
        }
      })

    runWidgetAvailabilityManager.addListener(runWidgetListener)
  }

  private fun addGroupComponent(
    action: AnAction,
    layoutConstrains: String,
  ) {
    val toolbar = ActionManager.getInstance().createActionToolbar(
      ActionPlaces.RUN_TOOLBAR,
      (action as? ActionGroup) ?: DefaultActionGroup(action),
      true,
    ) as ActionToolbarImpl
    toolbar.targetComponent = myPanel
    toolbar.layoutPolicy = NOWRAP_LAYOUT_POLICY
    myPanel.add(toolbar, layoutConstrains)
  }

  override fun getKey() = NEW_TOOLBAR_KEY

  override fun revalidate() {
    ActivityTracker.getInstance().inc()
  }

  override fun getComponent(): JComponent = myPanelWrapper

  override fun uiSettingsChanged(settings: UISettings) {
    logger.info("Show old main toolbar: ${settings.showMainToolbar}; show old navigation bar: ${settings.showNavigationBar}")
    myPanelWrapper.removeAll()
    myPanel.removeAll()

    val toolbarSettings = ToolbarSettings.Instance
    val isEnabled = toolbarSettings.isEnabled
    myPanelWrapper.isEnabled = isEnabled
    myPanelWrapper.isVisible = toolbarSettings.isVisible && !settings.presentationMode

    if (isEnabled) {
      myPanelWrapper.add(myPanel, BorderLayout.CENTER)

      val newToolbarActions = CustomActionsSchema.getInstance()
        .getCorrectedAction(if(runWidgetAvailabilityManager.isAvailable()) "NewToolbarActions" else "NewToolbarActionsWithoutRight") as ActionGroup

      val listChildren = newToolbarActions.getChildren(null)
      addGroupComponent(listChildren[2], BorderLayout.EAST)
      addGroupComponent(listChildren[1], BorderLayout.CENTER)
      addGroupComponent(listChildren[0], BorderLayout.WEST)
    }

    repaint()
  }

  private fun repaint() {
    myPanel.revalidate()
    myPanel.repaint()
    revalidate()
  }

  override fun copy() = NewToolbarRootPaneExtension(myProject)

  override fun dispose() {
    runWidgetAvailabilityManager.removeListener(runWidgetListener)
  }
}