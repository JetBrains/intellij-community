package com.intellij.execution.multilaunch.design.dialogs

import com.intellij.execution.ExecutionBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.execution.multilaunch.MultiLaunchConfiguration
import com.intellij.execution.multilaunch.execution.executables.Executable
import com.intellij.execution.multilaunch.design.components.IconCheckBoxList
import com.intellij.execution.multilaunch.execution.executables.impl.RunConfigurationExecutableManager
import com.intellij.util.ui.JBUI
import net.miginfocom.swing.MigLayout
import java.awt.Dimension
import javax.swing.JPanel

class AddMultipleConfigurationsDialog(
  private val project: Project,
  private val configuration: MultiLaunchConfiguration,
  private val existingExecutables: List<Executable?>
) : DialogWrapper(project) {
  private val selector = object : IconCheckBoxList<Executable>() {
    override fun getIcon(item: Executable?) = item?.icon
    override fun getText(item: Executable?) = item?.name
  }
  val selectedItems = mutableListOf<Executable>()

  init {
    title = ExecutionBundle.message("run.configurations.multilaunch.add.multiple.configurations.title")
    init()
  }

  override fun beforeShowCallback() {
    selector.clear()
    val executables = RunConfigurationExecutableManager.getInstance(project)
      .listExecutables(configuration)
      .filter { it !in existingExecutables }
    executables.forEach {
      selector.addItem(it, it.name, false)
    }
  }

  override fun createCenterPanel() = JPanel(MigLayout("ins 0, novisualpadding, fill, gap 0", "fill", "fill")).apply {
    val listPane = JBScrollPane(selector).apply {
      minimumSize = Dimension(JBUI.scale(350), JBUI.scale(200))
    }
    add(listPane, "wrap")
  }

  override fun doOKAction() {
    selectedItems.clear()
    for (i in 0 until selector.itemsCount) {
      val value = selector.getItemAt(i) ?: continue
      if (selector.isItemSelected(i)) {
        selectedItems.add(value)
      }
    }
    super.doOKAction()
  }
}