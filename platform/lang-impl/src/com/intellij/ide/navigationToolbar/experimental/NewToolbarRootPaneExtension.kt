// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar.experimental

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.ToolbarSettings
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.ide.ui.customization.CustomizableActionsPanel
import com.intellij.ide.ui.experimental.toolbar.RunWidgetAvailabilityManager
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Conditions
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.wm.IdeRootPaneNorthExtension
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import com.intellij.util.ui.JBSwingUtilities
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Graphics
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.CompletableFuture
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

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
    }
  }

  @RequiresBackgroundThread
  private fun correctedToolbarActions(): Map<String, ActionGroup?> {
    val mainGroupName = if (RunWidgetAvailabilityManager.getInstance(project).isAvailable()) {
      IdeActions.GROUP_EXPERIMENTAL_TOOLBAR
    }
    else {
      IdeActions.GROUP_EXPERIMENTAL_TOOLBAR_WITHOUT_RIGHT_PART
    }
    val toolbarGroup = CustomActionsSchema.getInstance().getCorrectedAction(mainGroupName) as? ActionGroup
                       ?: return emptyMap()
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

  @RequiresEdt
  private fun applyTo(
    actions: Map<String, ActionGroup?>,
    component: JComponent,
    layout: BorderLayout
  ) {
    val actionManager = ActionManager.getInstance()

    actions.mapValues { (_, actionGroup) ->
      if (actionGroup != null) {
        actionManager.createActionToolbar(
          ActionPlaces.MAIN_TOOLBAR,
          actionGroup,
          true,
        )
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
      addMouseListener(customizeMouseListener())
    }

    override fun getComponentGraphics(graphics: Graphics?): Graphics {
      return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics))
    }
  }

  inner class CustomizeToolbarAction : DumbAwareAction(ActionsBundle.message("action.CustomizeToolbarAction.text")) {
    override fun actionPerformed(e: AnActionEvent) {
      object : DialogWrapper(project, true) {
        var customizeWidget = object : CustomizableActionsPanel() {
          override fun patchActionsTreeCorrespondingToSchema(root: DefaultMutableTreeNode?) {
            val actionGroup = CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_EXPERIMENTAL_TOOLBAR) as? ActionGroup
            fillTreeFromActions(root, actionGroup)
          }

          private fun fillTreeFromActions(root: DefaultMutableTreeNode?, actionGroup: ActionGroup?) {
            if (mySelectedSchema == null || actionGroup == null) return
            root?.removeAllChildren()
            root?.add(ActionsTreeUtil.createNode
            (ActionsTreeUtil.createCorrectedGroup(actionGroup, ActionsTreeUtil.getExperimentalToolbar(), mutableListOf(),
                                                  mySelectedSchema.actions)))

            (myActionsTree.model as DefaultTreeModel).reload()
          }

          override fun getRestoreGroup(): ActionGroup {
            return DefaultActionGroup(object : AnAction(IdeBundle.messagePointer("button.restore.last.state")) {
              override fun actionPerformed(e: AnActionEvent) {
                reset()
              }
            },
                                      object : AnAction(IdeBundle.messagePointer("button.restore.defaults")) {
                                        override fun actionPerformed(e: AnActionEvent) {
                                          val actionGroup = ActionManager.getInstance().getAction(
                                            IdeActions.GROUP_EXPERIMENTAL_TOOLBAR) as? ActionGroup
                                          fillTreeFromActions(myActionsTree.model.root as DefaultMutableTreeNode?, actionGroup)
                                          myActionsTree.setSelectionRow(0)
                                          apply()
                                        }
                                      }).apply {
              isPopup = true
              templatePresentation.icon = AllIcons.Actions.Rollback
            }
          }
        }

        init {
          super.init()
          setSize(600, 600)
        }

        override fun createCenterPanel(): JComponent {
          customizeWidget.reset()
          return customizeWidget.panel
        }

        override fun getOKAction(): Action {
          return object : AbstractAction(ActionsBundle.message("apply.toolbar.customization")) {
            override fun actionPerformed(e: ActionEvent?) {
              customizeWidget.apply()
              close(0)
            }
          }
        }
      }.show()
    }
  }

  private fun customizeMouseListener() = object : MouseAdapter() {
    override fun mouseClicked(e: MouseEvent?) {
      if (e?.component == null) return
      logger.trace("Customize toolbar mouse event. Component: " + e.component.javaClass + " click location: " + e.x + ", " + e.y +
      " toolbar bounds: " + this@NewToolbarRootPaneExtension.panel.bounds)

      val point = RelativePoint(e.component, Point(e.x, e.y))
      JBPopupFactory.getInstance().createActionGroupPopup(null, DefaultActionGroup(CustomizeToolbarAction()),
                                                          DataManager.getInstance().getDataContext(e.component),
                                                          false, true, false, null,
                                                          -1, Conditions.alwaysTrue()).show(point)
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