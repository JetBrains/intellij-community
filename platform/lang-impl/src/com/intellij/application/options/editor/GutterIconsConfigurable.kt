// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor

import com.intellij.codeInsight.daemon.GutterIconDescriptor
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.daemon.LineMarkerProviders
import com.intellij.codeInsight.daemon.LineMarkerSettings
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.CheckBoxList
import com.intellij.ui.ListSpeedSearch
import com.intellij.ui.SeparatorWithText
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.speedSearch.SpeedSearchSupply
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Point
import javax.swing.BorderFactory
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants

/**
 * @author Dmitry Avdeev
 */
@ApiStatus.Internal
class GutterIconsConfigurable : BoundSearchableConfigurable(
  IdeBundle.message("configurable.GutterIconsConfigurable.display.name"),
  "reference.settings.editor.gutter.icons",
  ID,
), Configurable.NoScroll {
  private var list: CheckBoxList<GutterIconDescriptor>? = null
  private var currentDescriptors: List<GutterIconDescriptor> = emptyList()

  @get:TestOnly
  val descriptors: List<GutterIconDescriptor>
    get() = currentDescriptors

  override fun createPanel(): DialogPanel {
    val pluginDescriptors = HashMap<GutterIconDescriptor, PluginDescriptor>()
    val descriptors = collectDescriptors(pluginDescriptors)
    val list = createList(findFirstDescriptors(descriptors, pluginDescriptors))
    list.setItems(descriptors) { it.name }
    currentDescriptors = descriptors
    this.list = list

    lateinit var showGutterIcons: Cell<JBCheckBox>
    return panel {
      row {
        showGutterIcons = checkBox(ApplicationBundle.message("checkbox.show.gutter.icons"))
      }
      row {
        scrollCell(list)
          .align(Align.FILL)
          .enabledIf(showGutterIcons.selected)
      }.resizableRow()

      onIsModified {
        val settings = LineMarkerSettings.getSettings()
        descriptors.any { list.isItemSelected(it) != settings.isEnabled(it) } ||
        showGutterIcons.component.isSelected != EditorSettingsExternalizable.getInstance().areGutterIconsShown()
      }
      onReset {
        val settings = LineMarkerSettings.getSettings()
        for (descriptor in descriptors) {
          list.setItemSelected(descriptor, settings.isEnabled(descriptor))
        }
        // setItemSelected changes only the renderer check boxes, so the list must repaint to show the new state
        list.repaint()
        showGutterIcons.component.isSelected = EditorSettingsExternalizable.getInstance().areGutterIconsShown()
      }
      onApply {
        val editorSettings = EditorSettingsExternalizable.getInstance()
        val gutterIconsShown = showGutterIcons.component.isSelected
        if (gutterIconsShown != editorSettings.areGutterIconsShown()) {
          editorSettings.setGutterIconsShown(gutterIconsShown)
          reinitAllEditors()
        }
        val settings = LineMarkerSettings.getSettings()
        for (descriptor in descriptors) {
          settings.setEnabled(descriptor, list.isItemSelected(descriptor))
        }
        for (project in ProjectManager.getInstance().openProjects) {
          DaemonCodeAnalyzerEx.getInstanceEx(project).restart("GutterIconsConfigurable.apply")
        }
        ApplicationManager.getApplication().messageBus.syncPublisher(EditorOptionsListener.GUTTER_ICONS_CONFIGURABLE_TOPIC)
          .changesApplied()
      }
    }
  }

  override fun enableSearch(option: String?): Runnable = Runnable {
    val list = list ?: return@Runnable
    if (option == null) return@Runnable
    SpeedSearchSupply.getSupply(list, true)?.findAndSelectElement(option)
  }

  companion object {
    @NonNls
    const val ID: String = "editor.preferences.gutterIcons"
  }

  class ShowSettingsAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
    override fun actionPerformed(e: AnActionEvent) {
      // in split mode, the client node of this page has another class, so the search uses the id
      ShowSettingsUtilImpl.showSettingsDialog(e.project, ID, null)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  }
}

private fun collectDescriptors(pluginDescriptors: MutableMap<GutterIconDescriptor, PluginDescriptor>): List<GutterIconDescriptor> {
  val pointsByPlugin = LineMarkerProviders.EP_NAME.extensionList
    .filter { point ->
      val instance = point.instance
      instance is LineMarkerProviderDescriptor && instance.name != null
    }
    .groupBy { it.pluginDescriptor }
  val descriptors = ArrayList<GutterIconDescriptor>()
  val ids = HashSet<String>()
  for ((pluginDescriptor, points) in pointsByPlugin) {
    for (point in points) {
      val instance = point.instance as GutterIconDescriptor
      if (instance.options.isNotEmpty()) {
        for (option in instance.options) {
          if (ids.add(option.id)) {
            descriptors.add(option)
            pluginDescriptors[option] = pluginDescriptor
          }
        }
      }
      else {
        if (ids.add(instance.id)) {
          descriptors.add(instance)
        }
        pluginDescriptors[instance] = pluginDescriptor
      }
    }
  }
  descriptors.sortWith { o1, o2 ->
    val byPlugin = StringUtil.naturalCompare(getPluginDisplayName(pluginDescriptors.getValue(o1)),
                                             getPluginDisplayName(pluginDescriptors.getValue(o2)))
    if (byPlugin != 0) byPlugin else StringUtil.naturalCompare(o1.name, o2.name)
  }
  return descriptors
}

private fun findFirstDescriptors(
  descriptors: List<GutterIconDescriptor>,
  pluginDescriptors: Map<GutterIconDescriptor, PluginDescriptor>,
): Map<GutterIconDescriptor, PluginDescriptor> {
  val firstDescriptors = HashMap<GutterIconDescriptor, PluginDescriptor>()
  var current: PluginId? = null
  for (descriptor in descriptors) {
    val pluginDescriptor = pluginDescriptors[descriptor] ?: continue
    val pluginId = pluginDescriptor.pluginId
    // You get different plugin descriptors for `GutterIconDescriptor`s from the same plugin
    // if they are located in different plugin modules.
    // But there is no reason to put them into separate UI groups especially taking into account
    // we sort `descriptors` only by plugin name, so compare plugin descriptors by their id
    if (pluginId != current) {
      firstDescriptors[descriptor] = pluginDescriptor
      current = pluginId
    }
  }
  return firstDescriptors
}

private fun createList(firstDescriptors: Map<GutterIconDescriptor, PluginDescriptor>): CheckBoxList<GutterIconDescriptor> {
  val list = object : CheckBoxList<GutterIconDescriptor>() {
    override fun adjustRendering(rootComponent: JComponent, checkBox: JCheckBox, index: Int, selected: Boolean, hasFocus: Boolean): JComponent {
      val panel = JPanel(BorderLayout())
      panel.border = BorderFactory.createEmptyBorder()
      val descriptor = getItemAt(index)
      val label = JLabel(descriptor?.icon ?: EmptyIcon.ICON_16)
      label.isOpaque = true
      label.preferredSize = Dimension(25, -1)
      label.horizontalAlignment = SwingConstants.CENTER
      panel.add(label, BorderLayout.WEST)
      panel.add(checkBox, BorderLayout.CENTER)
      panel.background = getBackground(false)
      label.background = getBackground(selected)
      if (!checkBox.isOpaque) {
        checkBox.isOpaque = true
      }
      checkBox.border = JBUI.Borders.empty()

      val pluginDescriptor = descriptor?.let { firstDescriptors[it] }
      if (pluginDescriptor != null) {
        val separator = SeparatorWithText()
        separator.caption = getPluginDisplayName(pluginDescriptor)
        panel.add(separator, BorderLayout.NORTH)
      }

      return panel
    }

    override fun findPointRelativeToCheckBox(x: Int, y: Int, checkBox: JCheckBox, index: Int): Point? {
      return findPointRelativeToCheckBoxWithAdjustedRendering(x, y, checkBox, index)
    }
  }
  list.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
  list.border = BorderFactory.createEmptyBorder()
  ListSpeedSearch.installOn(list) { it.text }
  return list
}

private fun getPluginDisplayName(pluginDescriptor: PluginDescriptor): @Nls String {
  return if (pluginDescriptor is IdeaPluginDescriptor && PluginManagerCore.CORE_ID == pluginDescriptor.pluginId) {
    IdeBundle.message("title.common")
  }
  else {
    pluginDescriptor.name
  }
}
