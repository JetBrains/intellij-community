// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.navigationToolbar.experimental

import com.intellij.ide.ActivityTracker
import com.intellij.ide.ui.ToolbarSettings
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.ide.ui.experimental.toolbar.RunWidgetAvailabilityManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeRootPaneNorthExtension
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.util.messages.Topic
import com.intellij.util.ui.JBSwingUtilities
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Graphics
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

@FunctionalInterface
fun interface NewToolbarPaneListener {

  companion object {

    @JvmField
    val TOPIC: Topic<NewToolbarPaneListener> = Topic(
      NewToolbarPaneListener::class.java,
      Topic.BroadcastDirection.NONE,
      true,
    )
  }

  fun stateChanged()
}

class NewToolbarRootPaneExtension(private val project: Project) : IdeRootPaneNorthExtension(),
                                                                  Disposable {

  companion object {
    private const val NEW_TOOLBAR_KEY = "NEW_TOOLBAR_KEY"

    private val logger = logger<NewToolbarRootPaneExtension>()
  }

  private val runWidgetAvailabilityManager = RunWidgetAvailabilityManager.getInstance(project)
  private val runWidgetListener = RunWidgetAvailabilityManager.RunWidgetAvailabilityListener {
    reinitAndPaintAll()
  }

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
    Disposer.register(project, this)

    project.messageBus
      .connect(this)
      .subscribe(NewToolbarPaneListener.TOPIC, NewToolbarPaneListener {
        myPanel.doLayout()
        myPanel.repaint()
      })

    runWidgetAvailabilityManager.addListener(runWidgetListener)
  }

  override fun getKey() = NEW_TOOLBAR_KEY

  private fun reinitAndPaintAll() {
    ActivityTracker.getInstance().inc()

    myPanel.removeAll()
    if (myPanel.isVisible) {
      val actionsSchema = CustomActionsSchema.getInstance()
      for ((actionId, layoutConstrains) in mapOf(
        (if (runWidgetAvailabilityManager.isAvailable()) "RightToolbarSideGroup" else "RightToolbarSideGroupNoRunWidget") to BorderLayout.EAST,
        "CenterToolbarSideGroup" to BorderLayout.CENTER,
        "LeftToolbarSideGroup" to BorderLayout.WEST,
      )) {
        val action = actionsSchema.getCorrectedAction(actionId)
        val actionGroup = action as? ActionGroup
                          ?: throw IllegalArgumentException("Action group '$actionId' not found; actual action: $action")
        val toolbar = ActionManager.getInstance().createActionToolbar(
          ActionPlaces.MAIN_TOOLBAR,
          actionGroup,
          true,
        )
        toolbar.targetComponent = myPanel
        toolbar.layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY

        myPanel.add(toolbar as JComponent, layoutConstrains)
      }
    }

    revalidate()
  }

  override fun getComponent(): JComponent = myPanel

  override fun uiSettingsChanged(settings: UISettings) {
    logger.info("Show old main toolbar: ${settings.showMainToolbar}; show old navigation bar: ${settings.showNavigationBar}")

    val toolbarSettings = ToolbarSettings.Instance
    myPanel.isEnabled = toolbarSettings.isEnabled
    myPanel.isVisible = toolbarSettings.isVisible && !settings.presentationMode

    reinitAndPaintAll()
  }

  override fun copy() = NewToolbarRootPaneExtension(project)

  override fun dispose() {
    runWidgetAvailabilityManager.removeListener(runWidgetListener)
  }

  override fun revalidate() {
    myPanel.revalidate()
    myPanel.repaint()

    project.getService(StatusBarWidgetsManager::class.java).updateAllWidgets()
  }
}