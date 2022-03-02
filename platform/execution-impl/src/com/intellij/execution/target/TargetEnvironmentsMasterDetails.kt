// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.CONTEXT_COMPONENT
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.MasterDetailsComponent
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsContexts.Tooltip
import com.intellij.ui.*
import com.intellij.ui.border.CustomLineBorder
import com.intellij.ui.layout.*
import com.intellij.util.PlatformIcons
import com.intellij.util.containers.toArray
import com.intellij.util.text.UniqueNameGenerator
import com.intellij.util.text.nullize
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Point
import javax.swing.*
import javax.swing.border.Border
import javax.swing.border.CompoundBorder
import javax.swing.tree.TreePath

class TargetEnvironmentsMasterDetails @JvmOverloads constructor(
  private val project: Project,
  private val initialSelectedName: String? = null,
  private val defaultLanguageRuntime: LanguageRuntimeType<*>?
) : MasterDetailsComponent() {

  private var _lastSelectedConfig: TargetEnvironmentConfiguration? = null
  internal val selectedConfig: TargetEnvironmentConfiguration?
    get() = myCurrentConfigurable?.editableObject as? TargetEnvironmentConfiguration ?: _lastSelectedConfig

  private val targetManager: TargetEnvironmentsManager get() = TargetEnvironmentsManager.getInstance(project)

  private lateinit var projectDefaultTargetComboBox: ComboBox<TargetEnvironmentConfiguration?>

  /**
   * The panel to set up "Project default target".
   */
  private val bottomPanel: DialogPanel = panel {
    row(ExecutionBundle.message("targets.details.project.default.target")) {
      cell {
        projectDefaultTargetComboBox = comboBox(
          model = DefaultComboBoxModel(),
          getter = { TargetEnvironmentsManager.getInstance(project).defaultTarget },
          setter = { value -> TargetEnvironmentsManager.getInstance(project).defaultTarget = value },
          renderer = SimpleListCellRenderer.create { label, value, _ ->
            if (value == null) {
              label.text = ExecutionBundle.message("local.machine")
              label.icon = AllIcons.Nodes.HomeFolder
            }
            else {
              label.text = value.displayName
              label.icon = value.getTargetType().icon
            }
          }
        ).component
        val helpButton = ContextHelpLabel.createWithLink(
          null,
          generateProjectDefaultHelpHtml(),
          ExecutionBundle.message("targets.details.project.default.target.help.documentation.link"),
          true
        ) {
          HelpManager.getInstance().invokeHelp("reference.run.targets")
        }.apply {
          horizontalTextPosition = SwingConstants.LEFT
        }
        helpButton()
      }
    }
  }.withTopLineBorder()

  init {
    // note that `MasterDetailsComponent` does not work without `initTree()`
    initTree()
    myTree.cellRenderer = TargetEnvironmentRenderer()
    myTree.emptyText.text = ExecutionBundle.message("targets.details.status.empty.text")
    myTree.emptyText.appendSecondaryText(ExecutionBundle.message("targets.details.status.text.add.new.target"),
                                         SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) {
      val size = myTree.emptyText.preferredSize
      val textY = myTree.height / if (myTree.emptyText.isShowAboveCenter) 3 else 2

      val visibleBounds = myTree.visibleRect
      val containerScreenPoint: Point = visibleBounds.location
      SwingUtilities.convertPointToScreen(containerScreenPoint, myTree)
      val targetPoint = Point(containerScreenPoint.x + (myTree.width - size.width) / 2, containerScreenPoint.y + textY + size.height)

      JBPopupFactory.getInstance().createActionGroupPopup(null, CreateNewTargetGroup(), object : DataContext {
        override fun getData(dataId: String): Any? {
          if (CONTEXT_COMPONENT.`is`(dataId)) {
            return myTree
          }
          return null
        }
      }, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false).showInScreenCoordinates(myTree, targetPoint)
    }
    val shortcutText = KeymapUtil.getFirstKeyboardShortcutText(CommonActionsPanel.getCommonShortcut(CommonActionsPanel.Buttons.ADD))
    myTree.emptyText.appendSecondaryText(" $shortcutText", StatusText.DEFAULT_ATTRIBUTES, null)
  }

  override fun getDisplayName(): String = ExecutionBundle.message("targets.details.configurable.name.remote.targets")

  override fun getEmptySelectionString(): String {
    return ExecutionBundle.message("targets.details.status.text.select.target.to.configure")
  }

  override fun createComponent(): JComponent {
    val panel = super.createComponent()

    myWholePanel.add(bottomPanel, BorderLayout.SOUTH)

    return panel
  }

  /**
   * Returns the model with the currently displayed list of targets and the list of targets itself.
   */
  private fun getProjectDefaultComboBoxModel() =
    (listOf(null) + getConfiguredTargets()).let { DefaultComboBoxModel(it.toTypedArray()) to it }

  override fun reset() {
    myRoot.removeAllChildren()

    bottomPanel.reset()

    allTargets().forEach { nextTarget -> addTargetNode(nextTarget) }

    super.reset()

    val (model, _) = getProjectDefaultComboBoxModel()
    projectDefaultTargetComboBox.model = model
    projectDefaultTargetComboBox.item = TargetEnvironmentsManager.getInstance(project).defaultTarget

    initialSelectedName?.let { selectNodeInTree(initialSelectedName) }
  }

  override fun isModified(): Boolean =
    allTargets().size != getConfiguredTargets().size ||
    deletedTargets().isNotEmpty() ||
    bottomPanel.isModified() ||
    super.isModified()

  override fun createActions(fromPopup: Boolean): List<AnAction> = mutableListOf(
    CreateNewTargetGroup(),
    MyDeleteAction(),
    DuplicateAction()
  )

  override fun processRemovedItems() {
    val deletedTargets = deletedTargets()
    deletedTargets.forEach { targetManager.targets.removeConfig(it) }
    super.processRemovedItems()
  }

  override fun wasObjectStored(editableObject: Any?): Boolean {
    return targetManager.targets.resolvedConfigs().contains(editableObject)
  }

  private fun deletedTargets(): Set<TargetEnvironmentConfiguration> = allTargets().toSet() - getConfiguredTargets()

  override fun apply() {
    super.apply()

    val addedConfigs = getConfiguredTargets() - targetManager.targets.resolvedConfigs()
    addedConfigs.forEach { targetManager.addTarget(it) }

    bottomPanel.apply()

    TREE_UPDATER.run()
  }

  override fun disposeUIResources() {
    _lastSelectedConfig = selectedObject as? TargetEnvironmentConfiguration
    super.disposeUIResources()
  }

  override fun removePaths(vararg paths: TreePath) {
    super.removePaths(*paths)

    updateProjectDefaultTargetComboBox()
  }

  private fun allTargets() = targetManager.targets.resolvedConfigs().filter { it.getTargetType().isSystemCompatible() }

  private fun addTargetNode(target: TargetEnvironmentConfiguration): MyNode {
    val configurable = TargetEnvironmentDetailsConfigurable(project, target, defaultLanguageRuntime, TREE_UPDATER)
    val node = TargetEnvironmentNode(target, configurable)
    addNode(node, myRoot)
    selectNodeInTree(node)
    return myRoot
  }

  private fun getConfiguredTargets(): List<TargetEnvironmentConfiguration> =
    myRoot.children().asSequence()
      .map { node -> (node as MyNode).configurable?.editableObject as? TargetEnvironmentConfiguration }
      .filterNotNull()
      .toList()

  private inner class CreateNewTargetAction<T : TargetEnvironmentConfiguration>(private val project: Project,
                                                                                private val type: TargetEnvironmentType<T>)
    : DumbAwareAction(ExecutionBundle.message("targets.details.action.new.target.of.type.text", type.displayName), null, type.icon) {

    override fun actionPerformed(e: AnActionEvent) {
      val newConfig: TargetEnvironmentConfiguration

      val wizard = TargetEnvironmentWizard.createWizard(project, type, defaultLanguageRuntime)
      if (wizard != null) {
        if (!wizard.showAndGet()) return

        newConfig = wizard.subject
      }
      else {
        newConfig = type.createDefaultConfig()
        type.initializeNewlyCreated(newConfig)
      }

      if (newConfig.displayName.isBlank()) {
        newConfig.displayName = UniqueNameGenerator.generateUniqueName(type.displayName) { curName ->
          getConfiguredTargets().none { it.displayName == curName }
        }
      }
      // there may be not yet stored names
      targetManager.ensureUniqueName(newConfig)
      val newNode = addTargetNode(newConfig)
      selectNodeInTree(newNode, true, true)
      updateProjectDefaultTargetComboBox()
    }
  }

  /**
   * Updates the list of items in "Project default target" combobox. Tries to preserve the selected item.
   */
  private fun updateProjectDefaultTargetComboBox() {
    val item = projectDefaultTargetComboBox.item
    val (model, items) = getProjectDefaultComboBoxModel()
    projectDefaultTargetComboBox.model = model
    if (item in items) {
      projectDefaultTargetComboBox.item = item
    }
    else {
      projectDefaultTargetComboBox.item = null
    }
  }

  private inner class CreateNewTargetGroup : ActionGroup(ExecutionBundle.message("targets.details.action.add.text"),
                                                         "", LayeredIcon.ADD_WITH_DROPDOWN),
                                             ActionGroupWithPreselection, DumbAware {
    init {
      registerCustomShortcutSet(CommonActionsPanel.getCommonShortcut(CommonActionsPanel.Buttons.ADD), myTree)
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
      return TargetEnvironmentType.getTargetTypesForRunConfigurations()
        .filter { it.isSystemCompatible() }
        .map { CreateNewTargetAction(project, it) }
        .toArray(AnAction.EMPTY_ARRAY)
    }

    override fun getActionGroup(): ActionGroup {
      return this
    }
  }

  private inner class DuplicateAction : DumbAwareAction(ExecutionBundle.message("targets.details.action.duplicate.text"),
                                                        ExecutionBundle.message("targets.details.action.duplicate.description"),
                                                        PlatformIcons.COPY_ICON) {
    init {
      registerCustomShortcutSet(CommonShortcuts.getDuplicate(), myTree)
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = getSelectedTarget() != null
    }

    override fun actionPerformed(e: AnActionEvent) {
      duplicateSelected()?.let { copy ->
        targetManager.addTarget(copy)
        val newNode = addTargetNode(copy)
        selectNodeInTree(newNode, true, true)
        updateProjectDefaultTargetComboBox()
      }
    }

    private fun duplicateSelected(): TargetEnvironmentConfiguration? =
      getSelectedTarget()?.let { it.getTargetType().duplicateConfig(it) }

    private fun getSelectedTarget() = selectedNode?.configurable?.editableObject as? TargetEnvironmentConfiguration
  }

  private class TargetEnvironmentNode(private val target: TargetEnvironmentConfiguration,
                                      configurable: TargetEnvironmentDetailsConfigurable) : MyNode(configurable) {

    override fun getDisplayName() = target.displayName

    val configuredLanguages: String
      get() = target.runtimes.resolvedConfigs()
        .map { it.getRuntimeType().displayName }
        .toSortedSet()
        .joinToString()

    fun computeIcon(expanded: Boolean): Icon? {
      val rawIcon = this.configurable?.getIcon(expanded) ?: return null
      val valid = try {
        target.validateConfiguration()
        true
      }
      catch (e: RuntimeConfigurationException) {
        false
      }
      return if (valid) rawIcon else LayeredIcon.create(rawIcon, AllIcons.RunConfigurations.InvalidConfigurationLayer)
    }
  }

  private class TargetEnvironmentRenderer : ColoredTreeCellRenderer() {

    override fun customizeCellRenderer(tree: JTree,
                                       value: Any?,
                                       selected: Boolean,
                                       expanded: Boolean,
                                       leaf: Boolean,
                                       row: Int,
                                       hasFocus: Boolean) {

      val node = value as? TargetEnvironmentNode ?: return
      font = UIUtil.getTreeFont()
      icon = node.computeIcon(expanded)

      append(node.displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES)

      node.configuredLanguages.nullize()?.let { languages ->
        append("  ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
        append(languages, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
    }
  }

  companion object {
    /**
     * @see com.intellij.openapi.ui.DialogWrapper.createSouthPanel
     */
    private fun <T : JComponent> T.withTopLineBorder(): T {
      val color = UIManager.getColor("DialogWrapper.southPanelDivider")
      val line: Border = CustomLineBorder(color ?: OnePixelDivider.BACKGROUND, 1, 0, 0, 0)
      border = CompoundBorder(line, JBUI.Borders.empty(16, 12))
      return this
    }

    @Tooltip
    private fun generateProjectDefaultHelpHtml(): String {
      val listOfAffectedRunConfigurations = StringBuilder().apply {
        append("<ul>")
        collectListOfTargetAwareRunConfigurations().forEach { configurationType ->
          append("<li>")
          append(configurationType)
          append("</li>")
        }
        append("</ul>")
      }.toString()
      return ExecutionBundle.message("targets.details.project.default.target.help.html", listOfAffectedRunConfigurations)
    }

    private fun collectListOfTargetAwareRunConfigurations(): List<String> {
      val defaultProject = ProjectManager.getInstance().defaultProject
      return ConfigurationType.CONFIGURATION_TYPE_EP.extensionList
        .mapNotNull { type -> type to type.configurationFactories.firstOrNull()?.createTemplateConfiguration(defaultProject) }
        .filter { (_, template) -> template is TargetEnvironmentAwareRunProfile && template.defaultLanguageRuntimeType != null }
        .map { (type, _) -> type.displayName }
        .sorted()
    }
  }
}