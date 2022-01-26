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
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Conditions
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.wm.IdeRootPaneNorthExtension
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.EdtExecutorService
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
import java.util.function.Consumer
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
class NewToolbarRootPaneManager(private val project: Project) : SimpleModificationTracker(), Disposable {
  companion object {
    private val logger = logger<NewToolbarRootPaneManager>()
    fun getInstance(project: Project): NewToolbarRootPaneManager = project.service()
  }

  init {
    RunWidgetAvailabilityManager.getInstance(project).addListener(this) {
      logger.info("New toolbar: run widget availability changed $it")
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
        .thenAcceptAsync(Consumer {
          applyTo(it, panel, extension.layout)
        it.forEach { it2 ->
            if (it2.key == null) {
              val comp = extension.layout.getLayoutComponent(it2.value)
              if (comp != null) {
                panel.remove(comp)
              }
            }
          }
          },
          EdtExecutorService.getInstance())
        .exceptionally {
          thisLogger().error(it)
          null
        }
    }
  }

  /**
   * Null key in the result map means that we need to clear the old panel that corresponded to the null group
   */
  @RequiresBackgroundThread
  private fun correctedToolbarActions(): Map<ActionGroup?, String> {
    val toolbarGroup = CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_EXPERIMENTAL_TOOLBAR) as? ActionGroup
                       ?: return emptyMap()
    val children = toolbarGroup.getChildren(null)
    val leftGroup = children.firstOrNull { it.templateText.equals(ActionsBundle.message("group.LeftToolbarSideGroup.text")) }
    val rightGroup = children.firstOrNull { it.templateText.equals(ActionsBundle.message("group.RightToolbarSideGroup.text")) }
    val restGroup = DefaultActionGroup(children.filter { it != leftGroup && it != rightGroup })
    val map = mutableMapOf<ActionGroup?, String>()
    map[leftGroup as? ActionGroup] = BorderLayout.WEST
    map[rightGroup as? ActionGroup] = BorderLayout.EAST
    map[restGroup] = BorderLayout.CENTER

    return map
  }

  @RequiresEdt
  private fun applyTo(
    actions: Map<ActionGroup?, String>,
    component: JComponent,
    layout: BorderLayout
  ) {
    val actionManager = ActionManager.getInstance()

    actions.mapKeys { (actionGroup, _) ->
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
    }.forEach { (toolbar, layoutConstraints) ->
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

  inner class CustomizeToolbarAction : AnAction(ActionsBundle.message("action.CustomizeToolbarAction.text")) {
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
    if (project.isDisposed) {
      logger.warn("New toolbar: Project '$project' disposal has already been initiated.")
      return
    }

    NewToolbarRootPaneManager.getInstance(project).startUpdateActionGroups(this)
  }
}