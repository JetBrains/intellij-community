package com.intellij.execution.multilaunch.design

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.RowsDnDSupport
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBViewport
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.EditableModel
import com.intellij.util.ui.JBUI
import com.intellij.execution.multilaunch.MultiLaunchConfiguration
import com.intellij.execution.multilaunch.execution.executables.Executable
import com.intellij.execution.multilaunch.state.toSnapshot
import com.intellij.execution.options.LifetimedSettingsEditor
import com.jetbrains.rd.util.lifetime.Lifetime
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.event.ItemEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable

@ApiStatus.Internal
class MultiLaunchConfigurationEditor(private val project: Project, private val configuration: MultiLaunchConfiguration) : LifetimedSettingsEditor<MultiLaunchConfiguration>() {
  private val runManagerListenerConnection: MessageBusConnection
  private var viewModel = MultiLaunchConfigurationViewModel(project, configuration)

  init {
    runManagerListenerConnection = project.messageBus.connect().apply {
      subscribe(RunManagerListener.TOPIC, ConfigurationChangedListener())
    }
  }

  override fun resetEditorFrom(multiLaunchConfiguration: MultiLaunchConfiguration) {
    viewModel.apply {
      reset()
      multiLaunchConfiguration.parameters.rows.forEach { contextSnapshot ->
        val row = ExecutableRowFactory.create(project, multiLaunchConfiguration, contextSnapshot)
        addRow(row)
      }
      activateAllToolWindows = multiLaunchConfiguration.parameters.activateToolWindows
    }
  }

  override fun applyEditorTo(multiLaunchConfiguration: MultiLaunchConfiguration) {
    multiLaunchConfiguration.parameters.rows.apply {
      clear()
      viewModel.rows.forEach {
        it ?: return@forEach
        add(it.toSnapshot())
      }
    }
    multiLaunchConfiguration.parameters.activateToolWindows = viewModel.activateAllToolWindows
  }

  override fun createEditor(lifetime: Lifetime): JComponent {
    return JPanel(MigLayout("fill")).apply {
      val comment = JLabel(ExecutionBundle.message("run.configurations.multilaunch.tasks.to.launch")).apply {
        foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
      }
      add(comment, "wrap")
      val table = createTable(lifetime)
      val pane = wrapWithScrollPane(table)
      add(pane, "grow, wrap")
      val activateAllToolWindows = createActivateToolWindowsCheckbox()
      add(activateAllToolWindows, "split 2")
      add(createActivateToolWindowsHelp(), "gapleft 5, wrap")
    }
  }

  private fun wrapWithScrollPane(component: JComponent): JBScrollPane {
    val scrollPane = object : JBScrollPane(component) {
      override fun getPreferredSize(): Dimension {
        val preferredSize = super.getPreferredSize()
        if (!isPreferredSizeSet) {
          setPreferredSize(Dimension(0, preferredSize.height))
        }
        return preferredSize
      }
    }

    scrollPane.border = IdeBorderFactory.createBorder(SideBorder.ALL)
    scrollPane.viewportBorder = JBUI.Borders.empty()
    return scrollPane
  }

  private fun createTable(lifetime: Lifetime): ExecutablesTable {
    return ExecutablesTable(project, viewModel, lifetime).apply {
      val renderer = object : ColoredTableCellRenderer() {
        override fun customizeCellRenderer(table: JTable,
                                           value: Any?,
                                           selected: Boolean,
                                           hasFocus: Boolean,
                                           row: Int,
                                           column: Int) {
          if (value !is Executable) return
          icon = value.icon
          append(value.name)
        }
      }
      setDefaultRenderer(Object::class.java, renderer)
      putClientProperty(JBViewport.FORCE_VISIBLE_ROW_COUNT_KEY, true)
      RowsDnDSupport.install(this, model as EditableModel)
    }
  }

  private fun createActivateToolWindowsCheckbox(): JBCheckBox {
    return JBCheckBox(ExecutionBundle.message("run.configurations.multilaunch.options.activate.tool.windows"), configuration.parameters.activateToolWindows).apply {
      addItemListener {
        when (it.stateChange) {
          ItemEvent.SELECTED -> viewModel.activateAllToolWindows = true
          ItemEvent.DESELECTED -> viewModel.activateAllToolWindows = false
        }
      }
    }
  }

  private fun createActivateToolWindowsHelp(): ContextHelpLabel {
    return HtmlChunk.div()
      .child(HtmlChunk.p().addText(ExecutionBundle.message("run.configurations.multilaunch.options.activate.tool.windows.description1")))
      .child(HtmlChunk.p().addText(ExecutionBundle.message("run.configurations.multilaunch.options.activate.tool.windows.description2")))
      .child(HtmlChunk.p().addText(ExecutionBundle.message("run.configurations.multilaunch.options.activate.tool.windows.description3")))
      .toString()
      .let(ContextHelpLabel::create)
  }

  override fun disposeEditor() {
    Disposer.dispose(runManagerListenerConnection)
  }

  inner class ConfigurationChangedListener : RunManagerListener {
    override fun runConfigurationAdded(settings: RunnerAndConfigurationSettings) { handleChanged(settings) }
    override fun runConfigurationChanged(settings: RunnerAndConfigurationSettings) { handleChanged(settings) }
    override fun runConfigurationRemoved(settings: RunnerAndConfigurationSettings) { handleChanged(settings) }

    private fun handleChanged(settings: RunnerAndConfigurationSettings) {
      if (settings.configuration == configuration) return
      resetEditorFrom(configuration)
    }
  }
}

