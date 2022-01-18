// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.navigationToolbar.experimental

import com.intellij.ide.ActivityTracker
import com.intellij.ide.ui.ToolbarSettings
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.ide.ui.experimental.toolbar.RunWidgetAvailabilityManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeRootPaneNorthExtension
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.util.application
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.messages.Topic
import com.intellij.util.ui.JBSwingUtilities
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Graphics
import java.util.*
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

interface NewToolbarPaneListener {
  companion object {
    val TOPIC: Topic<NewToolbarPaneListener> = Topic(
      NewToolbarPaneListener::class.java,
      Topic.BroadcastDirection.NONE,
      true,
    )
  }

  fun stateChanged()
}

class NewToolbarRootPaneExtension(private val myProject: Project) : IdeRootPaneNorthExtension(), Disposable {
  companion object {
    private const val NEW_TOOLBAR_KEY = "NEW_TOOLBAR_KEY"

    private val logger = logger<NewToolbarRootPaneExtension>()
  }

  private val runWidgetAvailabilityManager = RunWidgetAvailabilityManager.getInstance(myProject)
  private val runWidgetListener = object : RunWidgetAvailabilityManager.RunWidgetAvailabilityListener {
    override fun availabilityChanged(value: Boolean) {
      reinitAndPaintAll()
    }
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
    Disposer.register(myProject, this)

    myProject.messageBus
      .connect(this)
      .subscribe(NewToolbarPaneListener.TOPIC, object : NewToolbarPaneListener {
        override fun stateChanged() {
          myPanel.doLayout()
          myPanel.repaint()
        }
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
        application.invokeLater {
          toolbar.component.revalidate()
          toolbar.component.repaint()
        }
        myPanel.add(toolbar as JComponent, layoutConstrains)
      }
    }

    myPanel.revalidate()
    myPanel.repaint()
  }

  override fun getComponent(): JComponent = myPanel

  override fun uiSettingsChanged(settings: UISettings) {
    logger.info("Show old main toolbar: ${settings.showMainToolbar}; show old navigation bar: ${settings.showNavigationBar}")
    logger.info("Show new main toolbar: ${ToolbarSettings.Instance.isVisible}")

    val toolbarSettings = ToolbarSettings.Instance
    myPanel.isEnabled = toolbarSettings.isEnabled
    myPanel.isVisible = myPanel.isEnabled && toolbarSettings.isVisible && !settings.presentationMode

    reinitAndPaintAll()
    updateStatusBar()
  }

  private fun updateStatusBar() {
    for (project in ProjectManager.getInstance().openProjects) {
      project.getService(StatusBarWidgetsManager::class.java).updateAllWidgets()
    }
  }

  override fun copy() = NewToolbarRootPaneExtension(myProject)

  override fun dispose() {
    runWidgetAvailabilityManager.removeListener(runWidgetListener)
  }

  /**
   * Here goes only the logic that updates the central panel when it gets customized from the UI
   */
  override fun revalidate() {
    if (ToolbarSettings.Instance.isEnabled && ToolbarSettings.Instance.isVisible) {
      val group = CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_EXPERIMENTAL_TOOLBAR) as? CenterToolbarGroup ?: return
      val toolBar = ActionManagerEx.getInstanceEx().createActionToolbar(ActionPlaces.MAIN_TOOLBAR, group, true)
      toolBar.targetComponent = null
      toolBar.layoutPolicy = ActionToolbar.WRAP_LAYOUT_POLICY
      myPanel.add(toolBar as JComponent, BorderLayout.CENTER)
    }


    try {
      myProject.getService(StatusBarWidgetsManager::class.java).updateAllWidgets()
    }catch (e: AlreadyDisposedException){
      //do nothing
    }catch (e: Throwable){
      //do nothing
    }
  }
}