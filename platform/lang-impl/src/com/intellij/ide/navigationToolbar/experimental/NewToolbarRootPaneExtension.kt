// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar.experimental

import com.intellij.ide.ui.ToolbarSettings
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.ide.ui.customization.CustomizationUtil
import com.intellij.ide.ui.experimental.toolbar.RunWidgetAvailabilityManager
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.ToolbarActionsUpdatedListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.wm.IdeRootPaneNorthExtension
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
fun interface ExperimentalToolbarStateListener {
  companion object {
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
private class NewToolbarRootPaneManager(private val project: Project) : SimpleModificationTracker() {
  companion object {
    fun getInstance(project: Project): NewToolbarRootPaneManager = project.service()
  }

  fun startUpdateActionGroups(panel: JPanel) {
    incModificationCount()

    if (!panel.isEnabled || !panel.isVisible || !ToolbarSettings.getInstance().isAvailable) {
      return
    }

    CompletableFuture.supplyAsync(::correctedToolbarActions, AppExecutorUtil.getAppExecutorService())
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
        logger<NewToolbarRootPaneManager>().error(it)
        null
      }
    getToolbarGroup()?.let {
      CustomizationUtil.installToolbarCustomizationHandler(it, mainGroupName(), panel, ActionPlaces.MAIN_TOOLBAR)
    }
  }

  @RequiresBackgroundThread
  private fun correctedToolbarActions(): Map<String, ActionGroup?> {
    val toolbarGroup = getToolbarGroup() ?: return emptyMap()

    val children = toolbarGroup.getChildren(null)

    val leftGroup = children.firstOrNull {
      it.templateText.equals(ActionsBundle.message("group.LeftToolbarSideGroup.text")) || it.templateText.equals(
        ActionsBundle.message("group.LeftToolbarSideGroupXamarin.text"))
    }
    val rightGroup = children.firstOrNull {
      it.templateText.equals(ActionsBundle.message("group.RightToolbarSideGroup.text")) || it.templateText.equals(
        ActionsBundle.message("group.RightToolbarSideGroupXamarin.text"))
    }
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

  private fun mainGroupName(): String {
    return if (RunWidgetAvailabilityManager.getInstance(project).isAvailable()) {
      IdeActions.GROUP_EXPERIMENTAL_TOOLBAR
    }
    else {
      IdeActions.GROUP_EXPERIMENTAL_TOOLBAR_XAMARIN
    }
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
  private fun applyTo(actions: Map<String, ActionGroup?>, component: JComponent, layout: BorderLayout) {
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

private class NewToolbarRootPaneExtension : IdeRootPaneNorthExtension {
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
    NewToolbarRootPaneManager.getInstance(project).startUpdateActionGroups(panel)
    return panel
  }

  private class MyPanel(private val project: Project) : JPanel(NewToolbarBorderLayout()), UISettingsListener {
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

    private fun listenIfNeeded() {
      val disposable = Disposer.newDisposable()
      this.disposable = disposable

      @Suppress("DEPRECATION")
      project.coroutineScope.launch {
        RunWidgetAvailabilityManager.getInstance(project).availabilityChanged.collectLatest {
          withContext(Dispatchers.EDT) {
            LOG.info("New toolbar: run widget availability changed $it")
            NewToolbarRootPaneManager.getInstance(project).startUpdateActionGroups(this@MyPanel)
          }
        }
      }.cancelOnDispose(disposable)

      ApplicationManager.getApplication().messageBus.connect(disposable)
        .subscribe(ToolbarActionsUpdatedListener.TOPIC, ToolbarActionsUpdatedListener {
          @Suppress("DEPRECATION")
          project.coroutineScope.launch(Dispatchers.EDT) {
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
      project.messageBus.syncPublisher(ExperimentalToolbarStateListener.TOPIC).refreshVisibility()

      revalidate()
      repaint()

      NewToolbarRootPaneManager.getInstance(project).startUpdateActionGroups(this)
    }
  }
}