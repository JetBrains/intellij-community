// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar

import com.intellij.execution.*
import com.intellij.execution.actions.RunConfigurationsComboBoxAction
import com.intellij.execution.impl.EditConfigurationsDialog
import com.intellij.execution.runToolbar.components.ComboBoxArrowComponent
import com.intellij.execution.runToolbar.components.MouseListenerHelper
import com.intellij.execution.runToolbar.components.TrimmedMiddleLabel
import com.intellij.ide.DataManager
import com.intellij.ide.HelpTooltip
import com.intellij.ide.ui.UISettings.Companion.isIdeHelpTooltipEnabled
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl.DO_NOT_ADD_CUSTOMIZATION_HANDLER
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedCustomPanel
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Point
import java.beans.PropertyChangeEvent
import javax.swing.*

@ApiStatus.Internal
open class RunToolbarRunConfigurationsAction : RunConfigurationsComboBoxAction(), RTRunConfiguration {
  companion object {
    private val PROP_ACTIVE_TARGET = Key<ExecutionTarget?>("PROP_ACTIVE_TARGET")
    private val PROP_TARGETS = Key<List<ExecutionTarget>>("PROP_TARGETS")

    fun doRightClick(dataContext: DataContext) {
      ActionManager.getInstance().getAction("RunToolbarSlotContextMenuGroup")?.let {
        if (it is ActionGroup) {
          SwingUtilities.invokeLater {
            val popup = JBPopupFactory.getInstance().createActionGroupPopup(
              null, it, dataContext, false, false, false, null, 5, null)

            popup.showInBestPositionFor(dataContext)
          }
        }
      }
    }
  }

  override fun addTargetGroup(project: Project?, allActionsGroup: DefaultActionGroup?) {

  }

  open fun trace(e: AnActionEvent, add: String? = null) {

  }

  override fun getEditRunConfigurationAction(): AnAction? {
    return ActionManager.getInstance().getAction(RunToolbarEditConfigurationAction.ACTION_ID)
  }

  override fun createFinalAction(project: Project, configuration: RunnerAndConfigurationSettings): AnAction {
    return RunToolbarSelectConfigAction(project, configuration)
  }

  override fun getSelectedConfiguration(e: AnActionEvent): RunnerAndConfigurationSettings? {
    return e.configuration() ?: e.project?.let {
      val value = RunManager.getInstance(it).selectedConfiguration
      e.setConfiguration(value)
      value
    }
  }

  override fun checkMainSlotVisibility(state: RunToolbarMainSlotState): Boolean {
    return false
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    if(!e.presentation.isVisible) return

    e.presentation.isVisible = !e.isActiveProcess()

    if (!RunToolbarProcess.isExperimentalUpdatingEnabled) {
      e.mainState()?.let {
        e.presentation.isVisible = e.presentation.isVisible && checkMainSlotVisibility(it)
      }
    }
    e.presentation.description = e.runToolbarData()?.let {
      RunToolbarData.prepareDescription(e.presentation.text,
                                        ActionsBundle.message("action.RunToolbarShowHidePopupAction.click.to.open.combo.text"))
    }

    var targetList = emptyList<ExecutionTarget>()
    e.project?.let {project ->
      val targetManager = ExecutionTargetManager.getInstance(project)
      e.configuration()?.configuration?.let { config->
        val name = Executor.shortenNameIfNeeded(config.name)
        e.presentation.setText(name, false)
        targetList = targetManager.getTargetsFor(config)
      }
    }


    e.presentation.putClientProperty(PROP_TARGETS, targetList)
    e.presentation.putClientProperty(PROP_ACTIVE_TARGET, e.executionTarget())

  }



  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return object : SegmentedCustomPanel(presentation) {
      init {
        layout = MigLayout("ins 0, gap 0, fill, hidemode 3", "[grow][pref!]")
        val configComponent = RunToolbarConfigComponent(presentation)
        add(configComponent, "grow")

        val targetComponent =
          object : JPanel(MigLayout("ins 0, fill", "[min!][grow]")){

            override fun getPreferredSize(): Dimension {
              val d = super.getPreferredSize()
              getProject()?.let {
                d.width = RunWidgetWidthHelper.getInstance(it).runTarget
              }
              return d
            }
          }.apply {
            add(JPanel(MigLayout("ins 0")).apply {
              val s = JBDimension(1, 24)

              preferredSize = s
              minimumSize = s
              maximumSize = s

              background = UIManager.getColor("Separator.separatorColor")
            }, "w min!")
            add(object : DraggablePane(){
              init {
                setListener(object : DragListener {
                  override fun dragStarted(locationOnScreen: Point) {
                  }

                  override fun dragged(locationOnScreen: Point, offset: Dimension) {
                  }

                  override fun dragStopped(locationOnScreen: Point, offset: Dimension) {
                  }
                })
              }
            }.apply {
              isOpaque = false
            }, "pos 0 0")
            add(RunToolbarTargetComponent(presentation), "grow")
            isOpaque = false
          }
        add(targetComponent, "w pref")
        background = JBColor.namedColor("ComboBoxButton.background", Gray.xDF)
      }

      override fun getPreferredSize(): Dimension {
        val d = super.getPreferredSize()
        getProject()?.let {
          d.width = RunWidgetWidthHelper.getInstance(it).runConfig
        }

        return d
      }
    }
  }

  private inner class RunToolbarTargetComponent(presentation: Presentation) : RunToolbarComboboxComponent(presentation) {
    override fun presentationChanged(event: PropertyChangeEvent) {
      parent.isVisible = presentation.getClientProperty(PROP_ACTIVE_TARGET)?.let {
        if (it !== DefaultExecutionTarget.INSTANCE && !it.isExternallyManaged) {
          updateView(it.displayName, true, it.icon, presentation.description)
          true
        }
        else false
      } ?: run {
        false
      }
    }


    override fun createPopup(onDispose: Runnable): JBPopup {
      val group = DefaultActionGroup()
      presentation.getClientProperty(PROP_TARGETS)?.forEach { target ->
        group.add(object : AnAction({ target.displayName }, target.icon) {
          override fun actionPerformed(e: AnActionEvent) {
            e.setExecutionTarget(target)
          }
        })
      }

      return createActionPopup(group, ActionToolbar.getDataContextFor(this), onDispose)
    }

    override fun getPreferredSize(): Dimension {
      val d = super.getPreferredSize()
      getProject()?.let {
        d.width = RunWidgetWidthHelper.getInstance(it).runTarget
      }
      return d
    }
  }

  private inner class RunToolbarConfigComponent(presentation: Presentation) : RunToolbarComboboxComponent(presentation) {

    override fun presentationChanged(event: PropertyChangeEvent) {
      updateView(presentation.text, presentation.isEnabled, presentation.icon, presentation.description)
    }

    override fun doRightClick() {
      doRightClick(ActionToolbar.getDataContextFor(this))
    }

    override fun createPopup(onDispose: Runnable): JBPopup {
      return createActionPopup(ActionToolbar.getDataContextFor(this), this, onDispose)
    }

    override fun doShiftClick() {
      val context = DataManager.getInstance().getDataContext(this)
      val project = CommonDataKeys.PROJECT.getData(context)
      if (project != null && !ActionUtil.isDumbMode(project)) {
        EditConfigurationsDialog(project).show()
        return
      }
    }

  }

  private abstract class RunToolbarComboboxComponent(presentation: Presentation) : SegmentedCustomPanel(presentation) {
    protected val setting = object : TrimmedMiddleLabel() {
      override fun getFont(): Font {
        return UIUtil.getToolbarFont()
      }

      override fun getForeground(): Color {
        return UIUtil.getLabelForeground()
      }
    }

    protected val arrow = ComboBoxArrowComponent().getView()

    init {
      MouseListenerHelper.addListener(this, { doClick() }, { doShiftClick() }, { doRightClick() })
      fill()
      putClientProperty(DO_NOT_ADD_CUSTOMIZATION_HANDLER, true)
      isOpaque = false
    }

    private fun fill() {
      layout = MigLayout("ins 0 0 0 3, novisualpadding, gap 0, fill, hidemode 3", "4[][min!]")

      add(setting, "ay center, growx, wmin 10")
      add(arrow, "w min!")

      setting.border = JBUI.Borders.empty()
    }

    protected fun updateView(@Nls text: String, enable: Boolean, icon: Icon? = null, @Nls toolTipText: String? = null) {
      setting.icon = icon
      setting.text = text
      setting.putClientProperty(DO_NOT_ADD_CUSTOMIZATION_HANDLER, true)


      setting.isEnabled = enable
      arrow.isVisible = enable

      setting.toolTipText = toolTipText
      arrow.toolTipText = toolTipText
    }


    protected open fun doRightClick() {}

    protected open fun doClick() {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown { showPopup() }
    }

    private fun showPopup() {
      val popup: JBPopup = createPopup {}

      if (isIdeHelpTooltipEnabled()) {
        HelpTooltip.setMasterPopup(this, popup)
      }
      popup.showUnderneathOf(this)
    }

    abstract fun createPopup(onDispose: Runnable): JBPopup

    protected open fun doShiftClick() {}
  }

  private class RunToolbarSelectConfigAction(
    val project: Project,
    val configuration: RunnerAndConfigurationSettings,
  ) : DumbAwareAction() {
    init {
      val name = Executor.shortenNameIfNeeded(configuration.name).takeIf { it.isNotEmpty() } ?: " "
      val presentation = templatePresentation
      presentation.setText(name, false)
      presentation.description = ExecutionBundle.message("select.0.1", configuration.type.configurationTypeDescription, name)
      updateIcon(presentation)
    }

    private fun updateIcon(presentation: Presentation) {
      setConfigurationIcon(presentation, configuration, project)
    }

    override fun actionPerformed(e: AnActionEvent) {
      e.project?.let {
        e.runToolbarData()?.clear()
        e.setConfiguration(configuration)
        e.id()?.let { id ->
          RunToolbarSlotManager.getInstance(it).configurationChanged(id, configuration)
        }
      }
    }

    override fun update(e: AnActionEvent) {
      super.update(e)
      updateIcon(e.presentation)

      updatePresentation(ExecutionTargetManager.getActiveTarget(project),
                         configuration,
                         project,
                         e.presentation,
                         e.place)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.EDT
  }

}

