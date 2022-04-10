// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar.experimental

import com.intellij.ide.ui.ToolbarSettings
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.ide.ui.customization.CustomizationUtil
import com.intellij.ide.ui.experimental.toolbar.RunWidgetAvailabilityManager
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.wm.IdeRootPaneNorthExtension
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import com.intellij.util.ui.JBSwingUtilities
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Graphics
import java.util.concurrent.CompletableFuture
import javax.swing.*

@FunctionalInterface
fun interface ExperimentalToolbarStateListener {
  companion object {
    @JvmField
    @Topic.ProjectLevel
    val TOPIC: Topic<ExperimentalToolbarStateListener> = Topic(
      ExperimentalToolbarStateListener::class.java,
      Topic.BroadcastDirection.NONE,
      true,
    )
  }

  /**
   * This event gets emitted when the experimental toolbar wants things dependent on its state to refresh their visibility.
   */
  fun refreshVisibility()
}

@Service
internal class NewToolbarRootPaneManager(private val project: Project) : SimpleModificationTracker(), Disposable {
  companion object {
    private val LOG = logger<NewToolbarRootPaneManager>()

    fun getInstance(project: Project): NewToolbarRootPaneManager = project.service()
  }

  init {
    RunWidgetAvailabilityManager.getInstance(project).addListener(this) {
      LOG.info("New toolbar: run widget availability changed $it")
      IdeRootPaneNorthExtension.EP_NAME.findExtension(NewToolbarRootPaneExtension::class.java, project)?.let { extension ->
        startUpdateActionGroups(extension)
      }
    }
  }

  override fun dispose() {
  }

  internal fun startUpdateActionGroups(extension: NewToolbarRootPaneExtension) {
    incModificationCount()

    val panel = extension.panel
    if (panel.isEnabled && panel.isVisible && ToolbarSettings.getInstance().isEnabled) {
      CompletableFuture.supplyAsync(::correctedToolbarActions, AppExecutorUtil.getAppExecutorService())
        .thenAcceptAsync({ placeToActionGroup ->
                           applyTo(placeToActionGroup, panel, extension.layout)
                           for ((place, actionGroup) in placeToActionGroup) {
                             if (actionGroup == null) {
                               val comp = extension.layout.getLayoutComponent(place)
                               if (comp != null) {
                                 panel.remove(comp)
                               }
                             }
                           }
                         }
        ) {
          ApplicationManager.getApplication().invokeLater(it, project.disposed)
        }
        .exceptionally {
          LOG.error(it)
          null
        }
      getToolbarGroup()?.let {
        CustomizationUtil.installToolbarCustomizationHandler(it, mainGroupName(), panel, ActionPlaces.MAIN_TOOLBAR)
      }
    }
  }

  @RequiresBackgroundThread
  private fun correctedToolbarActions(): Map<String, ActionGroup?> {
    val toolbarGroup = getToolbarGroup() ?: return emptyMap()

    val children = toolbarGroup.getChildren(null)

    val leftGroup = children.firstOrNull { it.templateText.equals(ActionsBundle.message("group.LeftToolbarSideGroup.text")) }
    val rightGroup = children.firstOrNull { it.templateText.equals(ActionsBundle.message("group.RightToolbarSideGroup.text")) }
    val restGroup = DefaultActionGroup(children.filter { it != leftGroup && it != rightGroup })

    val map = mutableMapOf<String, ActionGroup?>()
    map[BorderLayout.WEST] = leftGroup as? ActionGroup
    map[BorderLayout.EAST] = rightGroup as? ActionGroup
    map[BorderLayout.CENTER] = restGroup

    return map
  }

  private fun getToolbarGroup(): ActionGroup? {
    val mainGroupName = mainGroupName()
    return CustomActionsSchema.getInstance().getCorrectedAction(mainGroupName) as? ActionGroup
  }

  private fun mainGroupName() = if (RunWidgetAvailabilityManager.getInstance(project).isAvailable()) {
    IdeActions.GROUP_EXPERIMENTAL_TOOLBAR
  }
  else {
    IdeActions.GROUP_EXPERIMENTAL_TOOLBAR_WITHOUT_RIGHT_PART
  }

  private class MyActionToolbarImpl(place: String,
                                    actionGroup: ActionGroup,
                                    horizontal: Boolean, decorateButtons: Boolean,
                                    popupActionGroup: ActionGroup?,
                                    popupActionId: String?) : ActionToolbarImpl(place, actionGroup, horizontal, decorateButtons,
                                                                                popupActionGroup, popupActionId) {

    override fun addNotify() {
      super.addNotify()
      updateActionsImmediately()
    }
  }

  @RequiresEdt
  private fun applyTo(
    actions: Map<String, ActionGroup?>,
    component: JComponent,
    layout: BorderLayout
  ) {

    actions.mapValues { (_, actionGroup) ->
      if (actionGroup != null) {
        val toolbar = MyActionToolbarImpl(ActionPlaces.MAIN_TOOLBAR, actionGroup, true, false, getToolbarGroup(),
                                          mainGroupName())
        ApplicationManager.getApplication().messageBus.syncPublisher(ActionManagerListener.TOPIC).toolbarCreated(ActionPlaces.MAIN_TOOLBAR,
                                                                                                                 actionGroup, true, toolbar)
        toolbar
      }
      else {
        null
      }
    }.forEach { (layoutConstraints, toolbar) ->
      // We need to replace old component having the same constraints with the new one.
      if (toolbar != null) {

        layout.getLayoutComponent(component, layoutConstraints)?.let {
          component.remove(it)
        }
        toolbar.targetComponent = null
        toolbar.layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
        component.add(toolbar.component, layoutConstraints)
      }
    }
    component.revalidate()
    component.repaint()
  }
}

internal class NewToolbarRootPaneExtension(private val project: Project) : IdeRootPaneNorthExtension() {
  companion object {
    private const val NEW_TOOLBAR_KEY = "NEW_TOOLBAR_KEY"

    private val logger = logger<NewToolbarRootPaneExtension>()
  }

  internal val layout = NewToolbarBorderLayout()
  internal val panel: JPanel = object : JPanel(layout) {
    init {
      isOpaque = true
      border = BorderFactory.createEmptyBorder(0, JBUI.scale(4), 0, JBUI.scale(4))
      //show ui customization option
    }

    override fun getComponentGraphics(graphics: Graphics?): Graphics {
      return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics))
    }
  }

  override fun getKey() = NEW_TOOLBAR_KEY

  override fun getComponent(): JPanel {
    NewToolbarRootPaneManager.getInstance(project).startUpdateActionGroups(this)
    return panel
  }

  override fun uiSettingsChanged(settings: UISettings) {
    logger.info("Show old main toolbar: ${settings.showMainToolbar}; show old navigation bar: ${settings.showNavigationBar}")
    logger.info("Show new main toolbar: ${ToolbarSettings.getInstance().isVisible}")

    val toolbarSettings = ToolbarSettings.getInstance()
    panel.isEnabled = toolbarSettings.isEnabled
    panel.isVisible = toolbarSettings.isVisible && !settings.presentationMode
    project.messageBus.syncPublisher(ExperimentalToolbarStateListener.TOPIC).refreshVisibility()

    panel.revalidate()
    panel.repaint()

    NewToolbarRootPaneManager.getInstance(project).startUpdateActionGroups(this)
  }

  override fun copy() = NewToolbarRootPaneExtension(project)

  override fun revalidate() {
    NewToolbarRootPaneManager.getInstance(project).startUpdateActionGroups(this)
  }
}