// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar.rider

import com.intellij.ide.ui.ToolbarSettings
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.ide.ui.customization.CustomizationUtil
import com.intellij.ide.ui.experimental.toolbar.RunWidgetAvailabilityManager
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionGroupWrapper
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionWrapper
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.ToolbarActionsUpdatedListener
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.wm.IdeRootPaneNorthExtension
import com.intellij.openapi.wm.ex.ProjectFrameActionExclusionService
import com.intellij.util.cancelOnDispose
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import com.intellij.util.ui.JBSwingUtilities
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Graphics
import java.util.concurrent.CompletableFuture
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

@FunctionalInterface
fun interface RiderMainToolbarStateListener {
  companion object {
    @Topic.ProjectLevel
    val TOPIC: Topic<RiderMainToolbarStateListener> = Topic(
      RiderMainToolbarStateListener::class.java,
      Topic.BroadcastDirection.NONE,
      true,
    )
  }

  /**
   * This event gets emitted when the experimental toolbar wants things dependent on its state to refresh their visibility.
   */
  fun refreshVisibility()
}

/**
 * This is the Rider main toolbar (available in other IDEs under a registry key), used only in the classic UI.
 */
open class RiderMainToolbarRootPaneManager(private val project: Project) : SimpleModificationTracker() {
  companion object {
    fun getInstance(project: Project): RiderMainToolbarRootPaneManager = project.service()
  }

  fun startUpdateActionGroups(panel: JPanel) {
    incModificationCount()

    if (!panel.isEnabled || !panel.isVisible || !ToolbarSettings.getInstance().isAvailable) {
      return
    }

    CompletableFuture.supplyAsync({ correctedToolbarActions(panel) }, AppExecutorUtil.getAppExecutorService())
      .thenAcceptAsync(
        { placeToActionGroup ->
          val layout = panel.layout as BorderLayout
          applyTo(placeToActionGroup, panel, layout)
          for ((place, actionGroup) in placeToActionGroup) {
            if (actionGroup == null) {
              layout.getLayoutComponent(place)?.let {
                panel.remove(it)
              }
            }
          }
        },
        {
          ApplicationManager.getApplication().invokeLater(it, project.disposed)
        }
      )
      .exceptionally {
        logger<RiderMainToolbarRootPaneManager>().error(it)
        null
      }
    getToolbarGroup()?.let {
      CustomizationUtil.installToolbarCustomizationHandler(it, mainGroupName(), panel, ActionPlaces.MAIN_TOOLBAR)
    }
  }

  open fun isLeftSideAction(action: AnAction): Boolean =
    action.templateText.equals(ActionsBundle.message("group.LeftToolbarSideGroup.text"))

  open fun isRightSideAction(action: AnAction): Boolean =
    action.templateText.equals(ActionsBundle.message("group.RightToolbarSideGroup.text"))

  @RequiresBackgroundThread
  private fun correctedToolbarActions(panel: JPanel): Map<String, ActionGroup?> {
    val toolbarGroup = getToolbarGroup() ?: return emptyMap()
    val excludedActionIds = service<ProjectFrameActionExclusionService>().getExcludedActionIds(panel.projectFrameTypeId(), ActionPlaces.MAIN_TOOLBAR)

    val leftGroup = sideGroup(toolbarGroup, excludedActionIds, ::isLeftSideAction)
    val rightGroup = sideGroup(toolbarGroup, excludedActionIds, ::isRightSideAction)

    val restGroup = object : ActionGroup(), DumbAware {
      override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        return filteredToolbarActions(toolbarGroup, e, excludedActionIds)
          .filter { !isLeftSideAction(it) && !isRightSideAction(it) }
          .toTypedArray()
      }
    }

    val map = mutableMapOf<String, ActionGroup?>()
    map[BorderLayout.WEST] = leftGroup
    map[BorderLayout.EAST] = rightGroup
    map[BorderLayout.CENTER] = restGroup

    return map
  }

  private fun sideGroup(
    toolbarGroup: ActionGroup,
    excludedActionIds: Set<String>,
    selector: (AnAction) -> Boolean,
  ): ActionGroup {
    return object : ActionGroup(), DumbAware {
      override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val toolbarActions = filteredToolbarActions(toolbarGroup, e, excludedActionIds)
        return toolbarActions.firstOrNull(selector)?.let { action -> arrayOf(action) } ?: emptyArray()
      }
    }
  }

  private fun getToolbarGroup(): ActionGroup? {
    val mainGroupName = mainGroupName()
    return CustomActionsSchema.getInstance().getCorrectedAction(mainGroupName) as? ActionGroup
  }

  open fun mainGroupName(): String {
      return IdeActions.GROUP_EXPERIMENTAL_TOOLBAR
  }

  private class MyActionToolbarImpl(place: String,
                                    actionGroup: ActionGroup,
                                    horizontal: Boolean, decorateButtons: Boolean,
                                    popupActionGroup: ActionGroup?,
                                    popupActionId: String?) : ActionToolbarImpl(place, actionGroup, horizontal, decorateButtons, false) {
    init {
      installPopupHandler(true, popupActionGroup, popupActionId)
    }

    override fun addNotify() {
      super.addNotify()
      updateActionsImmediately()
    }
  }

  @RequiresEdt
  private fun applyTo(actions: Map<String, ActionGroup?>, component: JComponent, layout: BorderLayout) {
    actions.mapValues { (_, actionGroup) ->
      if (actionGroup != null) {
        MyActionToolbarImpl(ActionPlaces.MAIN_TOOLBAR, actionGroup, true, false, getToolbarGroup(), mainGroupName())
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
        toolbar.layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY
        component.add(toolbar.component, layoutConstraints)
      }
    }
    component.revalidate()
    component.repaint()
  }
}

internal fun filteredToolbarActions(
  toolbarGroup: ActionGroup,
  event: AnActionEvent?,
  excludedActionIds: Set<String>,
  actionManager: ActionManager = ActionManager.getInstance(),
): Array<AnAction> {
  if (excludedActionIds.isEmpty()) {
    return toolbarGroup.getChildren(event)
  }

  return toolbarGroup.getChildren(event)
    .filterNot { action -> action.topLevelActionId(actionManager)?.let(excludedActionIds::contains) == true }
    .toTypedArray()
}

private fun AnAction.topLevelActionId(actionManager: ActionManager): String? {
  var current = this
  while (true) {
    current = when (current) {
      is AnActionWrapper -> current.delegate
      is ActionGroupWrapper -> current.delegate
      else -> return actionManager.getId(current)
    }
  }
}

private fun JComponent.projectFrameTypeId(): String? {
  return rootPane?.getClientProperty(PROJECT_FRAME_TYPE_ID_CLIENT_PROPERTY) as? String
}

private const val PROJECT_FRAME_TYPE_ID_CLIENT_PROPERTY = "projectFrameTypeId"

internal class NewToolbarRootPaneExtension : IdeRootPaneNorthExtension {
  companion object {
    private val LOG = logger<NewToolbarRootPaneExtension>()
  }

  override val key: String
    get() = "NEW_TOOLBAR_KEY"

  override fun createComponent(project: Project, isDocked: Boolean): JPanel? {
    if (isDocked) {
      return null
    }

    val panel = MyPanel(project)
    RiderMainToolbarRootPaneManager.getInstance(project).startUpdateActionGroups(panel)
    return panel
  }

  private class MyPanel(private val project: Project) : JPanel(RiderMainToolbarBorderLayout()), UISettingsListener {
    private var disposable: Disposable? = null

    init {
      isOpaque = true
      border = BorderFactory.createEmptyBorder(0, JBUI.scale(4), 0, JBUI.scale(4))

      val toolbarSettings = ToolbarSettings.getInstance()
      isEnabled = toolbarSettings.isAvailable
      isVisible = toolbarSettings.isVisible && !UISettings.getInstance().presentationMode

      if (isEnabled) {
        listenIfNeeded()
      }
    }

    override fun addNotify() {
      super.addNotify()
      if (isEnabled && isVisible) {
        RiderMainToolbarRootPaneManager.getInstance(project).startUpdateActionGroups(this)
      }
    }

    private fun listenIfNeeded() {
      val disposable = Disposer.newDisposable()
      this.disposable = disposable

      (project as ComponentManagerEx).getCoroutineScope().launch {
        RunWidgetAvailabilityManager.getInstance(project).availabilityChanged.collectLatest {
          withContext(Dispatchers.EDT) {
            LOG.info("New toolbar: run widget availability changed $it")
            RiderMainToolbarRootPaneManager.getInstance(project).startUpdateActionGroups(this@MyPanel)
          }
        }
      }.cancelOnDispose(disposable)

      ApplicationManager.getApplication().messageBus.connect(disposable)
        .subscribe(ToolbarActionsUpdatedListener.TOPIC, ToolbarActionsUpdatedListener {
          project.getCoroutineScope().launch(Dispatchers.EDT) {
            revalidate()
            doLayout()
          }
        })
    }

    override fun removeNotify() {
      disposable?.let {
        disposable = null
        Disposer.dispose(it)
      }
      super.removeNotify()
    }

    override fun getComponentGraphics(graphics: Graphics?): Graphics {
      return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics))
    }

    override fun uiSettingsChanged(uiSettings: UISettings) {
      val toolbarSettings = ToolbarSettings.getInstance()
      val isAvailable = toolbarSettings.isAvailable
      val isVisible = toolbarSettings.isVisible && !uiSettings.presentationMode

      if (isEnabled == isAvailable && this.isVisible == isVisible) {
        return
      }

      LOG.info("Show old main toolbar: ${uiSettings.showMainToolbar}; show old navigation bar: ${uiSettings.showNavigationBar}")
      LOG.info("Show new main toolbar: ${ToolbarSettings.getInstance().isVisible}")

      isEnabled = isAvailable
      this.isVisible = isVisible
      project.messageBus.syncPublisher(RiderMainToolbarStateListener.TOPIC).refreshVisibility()

      revalidate()
      repaint()

      RiderMainToolbarRootPaneManager.getInstance(project).startUpdateActionGroups(this)
    }
  }
}
