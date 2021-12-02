// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.navigationToolbar.experimental

import com.intellij.ide.ui.ToolbarSettings
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.ide.ui.experimental.toolbar.RunWidgetAvailabilityManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.wm.IdeRootPaneNorthExtension
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import com.intellij.util.ui.JBSwingUtilities
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Graphics
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
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
  private val logger = logger<NewToolbarRootPaneManager>()

  private val runWidgetAvailabilityManager = RunWidgetAvailabilityManager.getInstance(project)
  private val runWidgetListener = RunWidgetAvailabilityManager.RunWidgetAvailabilityListener {
    logger.info("New toolbar: run widget availability changed $it")
    doLayoutAndRepaint()
  }

  init {
    Disposer.register(project, this)

    val connection = project.messageBus
      .connect(this)

    connection.subscribe(UISettingsListener.TOPIC, UISettingsListener {
      doLayoutAndRepaint()
    })

    runWidgetAvailabilityManager.addListener(runWidgetListener)
  }

  override fun dispose() {
    runWidgetAvailabilityManager.removeListener(runWidgetListener)
  }

  internal fun doLayout(component: JComponent) {
    incModificationCount()

    if (component.isEnabled && component.isVisible) {
      CompletableFuture.supplyAsync(
        ::correctedToolbarActions,
        AppExecutorUtil.getAppExecutorService(),
      ).thenAcceptAsync(
        Consumer {
          component.removeAll()
          applyTo(it, component)
        },
        EdtExecutorService.getInstance(),
      ).exceptionally {
        thisLogger().error(it)
        null
      }
    }
  }

  @RequiresBackgroundThread
  private fun correctedToolbarActions(): Map<ActionGroup, String> {
    val actionsSchema = CustomActionsSchema.getInstance()

    return mapOf(
      (if (runWidgetAvailabilityManager.isAvailable()) "RightToolbarSideGroup" else "RightToolbarSideGroupNoRunWidget") to BorderLayout.EAST,
      "CenterToolbarSideGroup" to BorderLayout.CENTER,
      "LeftToolbarSideGroup" to BorderLayout.WEST,
    ).mapKeys { (actionId, _) ->
      val action = actionsSchema.getCorrectedAction(actionId)
      action as? ActionGroup ?: throw IllegalArgumentException("Action group '$actionId' not found; actual action: $action")
    }
  }

  @RequiresEdt
  private fun applyTo(
    actions: Map<ActionGroup, String>,
    component: JComponent,
  ) {
    val actionManager = ActionManager.getInstance()

    actions.mapKeys { (actionGroup, _) ->
      actionManager.createActionToolbar(
        ActionPlaces.MAIN_TOOLBAR,
        actionGroup,
        true,
      )
    }.forEach { (toolbar, layoutConstraints) ->
      toolbar.targetComponent = component
      toolbar.layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
      component.add(toolbar as JComponent, layoutConstraints)
    }
  }

  private fun doLayoutAndRepaint() {

    NewToolbarRootPaneExtension.getInstance(project)?.let {
      doLayout(it.component)
      it.repaint()
    }
  }
}

class NewToolbarRootPaneExtension(private val project: Project) : IdeRootPaneNorthExtension() {

  companion object {

    private const val NEW_TOOLBAR_KEY = "NEW_TOOLBAR_KEY"

    private val logger = logger<NewToolbarRootPaneExtension>()

    fun getInstance(project: Project): NewToolbarRootPaneExtension? {
      return EP_NAME.getExtensionsIfPointIsRegistered(project)
        .asSequence()
        .filterIsInstance<NewToolbarRootPaneExtension>()
        .firstOrNull()
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

  override fun getComponent(): JPanel {
    project.service<NewToolbarRootPaneManager>().doLayout(panel)
    repaint()
    return panel
  }

  override fun uiSettingsChanged(settings: UISettings) {
    logger.info("Show old main toolbar: ${settings.showMainToolbar}; show old navigation bar: ${settings.showNavigationBar}")
    logger.info("Show new main toolbar: ${ToolbarSettings.Instance.isVisible}")

    val toolbarSettings = ToolbarSettings.Instance
    panel.isEnabled = toolbarSettings.isEnabled
    panel.isVisible = toolbarSettings.isVisible && !settings.presentationMode
    project.service<StatusBarWidgetsManager>().updateAllWidgets()

    repaint()
  }

  override fun copy() = NewToolbarRootPaneExtension(project)

  override fun revalidate() {
    if (project.isDisposed) {
      logger.warn("New toolbar: Project '$project' disposal has already been initiated.")
      return
    }

    if (ToolbarSettings.Instance.isEnabled) {
      val group = CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_EXPERIMENTAL_TOOLBAR) as? CenterToolbarGroup
                  ?: return
      val toolBar = ActionManagerEx.getInstanceEx().createActionToolbar(ActionPlaces.MAIN_TOOLBAR, group, true)
      toolBar.targetComponent = null
      toolBar.layoutPolicy = ActionToolbar.WRAP_LAYOUT_POLICY
      panel.add(toolBar as JComponent, BorderLayout.CENTER)
    }
  }

  fun repaint() {
    panel.revalidate()
    panel.repaint()
  }
}