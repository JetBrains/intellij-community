// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.navigationToolbar.experimental

import com.intellij.ide.ui.ToolbarSettings
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.ide.ui.experimental.toolbar.RunWidgetAvailabilityManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SimpleModificationTracker
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

@Service
class NewToolbarRootPaneManager(private val project: Project) : SimpleModificationTracker(),
                                                                Disposable {

  private val runWidgetAvailabilityManager = RunWidgetAvailabilityManager.getInstance(project)
  private val runWidgetListener = RunWidgetAvailabilityManager.RunWidgetAvailabilityListener {
    NewToolbarRootPaneExtension.getInstance(project)?.revalidate()
  }

  init {
    Disposer.register(project, this)

    project.messageBus
      .connect(this)
      .subscribe(NewToolbarPaneListener.TOPIC, NewToolbarPaneListener {
        NewToolbarRootPaneExtension.getInstance(project)?.revalidate()
      })

    runWidgetAvailabilityManager.addListener(runWidgetListener)
  }

  override fun dispose() {
    runWidgetAvailabilityManager.removeListener(runWidgetListener)
  }

  fun doLayout(component: JComponent) {
    incModificationCount()

    component.removeAll()
    if (component.isEnabled && component.isVisible) {
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
        toolbar.targetComponent = component
        toolbar.layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY

        component.add(toolbar as JComponent, layoutConstrains)
      }
    }
  }
}

class NewToolbarRootPaneExtension(private val project: Project) : IdeRootPaneNorthExtension() {

  companion object {

    private const val NEW_TOOLBAR_KEY = "NEW_TOOLBAR_KEY"

    private val logger = logger<NewToolbarRootPaneExtension>()

    fun getInstance(project: Project): IdeRootPaneNorthExtension? {
      return EP_NAME.getExtensionsIfPointIsRegistered(project)
        .find { it is NewToolbarRootPaneExtension }
    }
  }

  private val panel: JPanel = object : JPanel(NewToolbarBorderLayout()) {

    init {
      isOpaque = true
      border = BorderFactory.createEmptyBorder(0, JBUI.scale(4), 0, JBUI.scale(4))
    }

    override fun getComponentGraphics(graphics: Graphics?): Graphics {
      return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics))
    }
  }

  override fun getKey() = NEW_TOOLBAR_KEY

  override fun getComponent(): JComponent {
    revalidate()
    return panel
  }

  override fun uiSettingsChanged(settings: UISettings) {
    logger.info("Show old main toolbar: ${settings.showMainToolbar}; show old navigation bar: ${settings.showNavigationBar}")

    val toolbarSettings = ToolbarSettings.Instance
    panel.isEnabled = toolbarSettings.isEnabled
    panel.isVisible = toolbarSettings.isVisible && !settings.presentationMode

    panel.revalidate()
    panel.repaint()
  }

  override fun copy() = NewToolbarRootPaneExtension(project)

  override fun revalidate() {
    if (project.isDisposed) {
      logger.warn("Project '$project' disposal has already been initiated.")
      return
    }

    project.service<NewToolbarRootPaneManager>().doLayout(panel)
    project.service<StatusBarWidgetsManager>().updateAllWidgets()
  }
}