/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.impl

import com.intellij.execution.*
import com.intellij.execution.configuration.ConfigurationFactoryEx
import com.intellij.execution.configurations.*
import com.intellij.execution.impl.RunConfigurable.Companion.collectNodesRecursively
import com.intellij.execution.impl.RunConfigurableNodeKind.*
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.options.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.LabeledComponent.create
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Trinity
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.IdeFocusManager.getGlobalInstance
import com.intellij.ui.*
import com.intellij.ui.RowsDnDSupport.RefinedDropSupport.Position.*
import com.intellij.ui.components.labels.ActionLink
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ArrayUtilRt
import com.intellij.util.IconUtil
import com.intellij.util.PlatformIcons
import com.intellij.util.containers.HashMap
import com.intellij.util.containers.nullize
import com.intellij.util.ui.*
import com.intellij.util.ui.tree.TreeUtil
import gnu.trove.THashSet
import gnu.trove.TObjectIntHashMap
import net.miginfocom.swing.MigLayout
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.KeyEvent
import java.util.*
import java.util.function.ToIntFunction
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.tree.*

private val DEFAULTS = object : Any() {
  override fun toString() = "Defaults"
}
private val INITIAL_VALUE_KEY = "initialValue"
private val LOG = logger<RunConfigurable>()

private fun getName(userObject: Any): String {
  return when {
    userObject is ConfigurationType -> userObject.displayName
    userObject === DEFAULTS -> "Defaults"
    userObject is ConfigurationFactory -> userObject.name
    //Folder objects are strings
    else -> if (userObject is SingleConfigurationConfigurable<*>) userObject.nameText else (userObject as? RunnerAndConfigurationSettingsImpl)?.name ?: userObject.toString()
  }
}

open class RunConfigurable @JvmOverloads constructor(private val myProject: Project, private var myRunDialog: RunDialogBase? = null) : BaseConfigurable(), Disposable {
  @Volatile private var isDisposed: Boolean = false
  val root = DefaultMutableTreeNode("Root")
  val treeModel = MyTreeModel(root)
  val tree = Tree(treeModel)
  private val rightPanel = JPanel(BorderLayout())
  private val splitter = JBSplitter("RunConfigurable.dividerProportion", 0.3f)
  private var wholePanel: JPanel? = null
  private var selectedConfigurable: Configurable? = null
  private val recentsLimit = JTextField("5", 2)
  private val confirmation = JCheckBox(ExecutionBundle.message("rerun.confirmation.checkbox"), true)
  private val additionalSettings = ArrayList<Pair<UnnamedConfigurable, JComponent>>()
  private val storedComponents = HashMap<ConfigurationFactory, Configurable>()
  private var toolbarDecorator: ToolbarDecorator? = null
  private var isFolderCreating: Boolean = false
  private val toolbarAddAction = MyToolbarAddAction()

  companion object {
    fun collectNodesRecursively(parentNode: DefaultMutableTreeNode,
                                nodes: MutableList<DefaultMutableTreeNode>,
                                vararg allowed: RunConfigurableNodeKind) {
      for (i in 0 until parentNode.childCount) {
        val child = parentNode.getChildAt(i) as DefaultMutableTreeNode
        if (ArrayUtilRt.find(allowed, getKind(child)) != -1) {
          nodes.add(child)
        }
        collectNodesRecursively(child, nodes, *allowed)
      }
    }

    fun getKind(node: DefaultMutableTreeNode?): RunConfigurableNodeKind {
      if (node == null) {
        return UNKNOWN
      }

      val userObject = node.userObject
      return when (userObject) {
        is SingleConfigurationConfigurable<*>, is RunnerAndConfigurationSettings -> {
          val settings = getSettings(node) ?: return UNKNOWN
          if (settings.isTemporary) TEMPORARY_CONFIGURATION else CONFIGURATION
        }
        is String -> FOLDER
        else -> if (userObject is ConfigurationType) CONFIGURATION_TYPE else UNKNOWN
      }
    }
  }

  override fun getDisplayName(): String = ExecutionBundle.message("run.configurable.display.name")

  private fun initTree() {
    tree.isRootVisible = false
    tree.showsRootHandles = true
    UIUtil.setLineStyleAngled(tree)
    TreeUtil.installActions(tree)
    TreeSpeedSearch(tree) { o ->
      val node = o.lastPathComponent as DefaultMutableTreeNode
      val userObject = node.userObject
      when (userObject) {
        is RunnerAndConfigurationSettingsImpl -> return@TreeSpeedSearch userObject.name
        is SingleConfigurationConfigurable<*> -> return@TreeSpeedSearch userObject.nameText
        else -> if (userObject is ConfigurationType) {
          return@TreeSpeedSearch userObject.displayName
        }
        else if (userObject is String) {
          return@TreeSpeedSearch userObject
        }
      }
      o.toString()
    }

    tree.cellRenderer = object : ColoredTreeCellRenderer() {
      override fun customizeCellRenderer(tree: JTree, value: Any, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int,
                                         hasFocus: Boolean) {
        if (value is DefaultMutableTreeNode) {
          val parent = value.parent as DefaultMutableTreeNode
          val userObject = value.userObject
          var shared: Boolean? = null
          val name = getName(userObject)
          if (userObject is ConfigurationType) {
            append(name, if (parent.isRoot) SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES else SimpleTextAttributes.REGULAR_ATTRIBUTES)
            icon = userObject.icon
          }
          else if (userObject === DEFAULTS) {
            append(name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
            icon = AllIcons.General.Settings
          }
          else if (userObject is String) {//Folders
            append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            icon = AllIcons.Nodes.Folder
          }
          else if (userObject is ConfigurationFactory) {
            append(name)
            icon = userObject.icon
          }
          else {
            var configuration: RunnerAndConfigurationSettings? = null
            if (userObject is SingleConfigurationConfigurable<*>) {
              val configurationSettings: RunnerAndConfigurationSettings = userObject.settings
              configuration = configurationSettings
              shared = userObject.isStoreProjectConfiguration
              icon = ProgramRunnerUtil.getConfigurationIcon(configurationSettings, !userObject.isValid)
            }
            else if (userObject is RunnerAndConfigurationSettingsImpl) {
              val settings = userObject as RunnerAndConfigurationSettings
              shared = settings.isShared
              icon = RunManagerEx.getInstanceEx(myProject).getConfigurationIcon(settings)
              configuration = settings
            }
            if (configuration != null) {
              append(name, if (configuration.isTemporary)
                SimpleTextAttributes.GRAY_ATTRIBUTES
              else
                SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }
          }
          if (shared != null) {
            val icon = icon
            val layeredIcon = LayeredIcon(icon, if (shared) AllIcons.Nodes.Shared else EmptyIcon.ICON_16)
            setIcon(layeredIcon)
            iconTextGap = 0
          }
          else {
            iconTextGap = 2
          }
        }
      }
    }
    val manager = runManager
    for (type in manager.configurationFactories) {
      val configurations = manager.getConfigurationSettingsList(type).nullize() ?: continue
      val typeNode = DefaultMutableTreeNode(type)
      root.add(typeNode)
      val folderMapping = HashMap<String, DefaultMutableTreeNode>()
      var folderCounter = 0
      for (configuration in configurations) {
        val folder = configuration.folderName
        if (folder != null) {
          var node: DefaultMutableTreeNode? = folderMapping[folder]
          if (node == null) {
            node = DefaultMutableTreeNode(folder)
            typeNode.insert(node, folderCounter)
            folderCounter++
            folderMapping.put(folder, node)
          }
          node.add(DefaultMutableTreeNode(configuration))
        }
        else {
          typeNode.add(DefaultMutableTreeNode(configuration))
        }
      }
    }

    // add defaults
    val defaults = DefaultMutableTreeNode(DEFAULTS)
    for (type in RunManagerImpl.getInstanceImpl(myProject).configurationFactoriesWithoutUnknown) {
      val configurationFactories = type.configurationFactories
      val typeNode = DefaultMutableTreeNode(type)
      defaults.add(typeNode)
      if (configurationFactories.size != 1) {
        for (factory in configurationFactories) {
          typeNode.add(DefaultMutableTreeNode(factory))
        }
      }
    }
    if (defaults.childCount > 0) {
      root.add(defaults)
    }

    tree.addTreeSelectionListener {
      val selectionPath = tree.selectionPath
      if (selectionPath != null) {
        val node = selectionPath.lastPathComponent as DefaultMutableTreeNode
        val userObject = getSafeUserObject(node)
        if (userObject is SingleConfigurationConfigurable<*>) {
          @Suppress("UNCHECKED_CAST")
          updateRightPanel(userObject as SingleConfigurationConfigurable<RunConfiguration>)
        }
        else if (userObject is String) {
          showFolderField(node, userObject)
        }
        else {
          if (userObject is ConfigurationType || userObject === DEFAULTS) {
            val parent = node.parent as DefaultMutableTreeNode
            if (parent.isRoot) {
              drawPressAddButtonMessage(if (userObject === DEFAULTS) null else userObject as ConfigurationType)
            }
            else {
              val factories = (userObject as ConfigurationType).configurationFactories
              if (factories.size == 1) {
                showTemplateConfigurable(factories[0])
              }
              else {
                drawPressAddButtonMessage(userObject)
              }
            }
          }
          else if (userObject is ConfigurationFactory) {
            showTemplateConfigurable(userObject)
          }
        }
      }
      updateDialog()
    }
    tree.registerKeyboardAction({ clickDefaultButton() }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED)
    sortTopLevelBranches()
    (tree.model as DefaultTreeModel).reload()
  }

  fun selectConfigurableOnShow(option: Boolean): RunConfigurable {
    if (!option) return this
    SwingUtilities.invokeLater {
      if (isDisposed) return@invokeLater

      tree.requestFocusInWindow()
      val settings = runManager.selectedConfiguration
      if (settings != null) {
        if (selectConfiguration(settings.configuration)) {
          return@invokeLater
        }
      }
      else {
        selectedConfigurable = null
      }
      //TreeUtil.selectInTree(defaults, true, myTree);
      drawPressAddButtonMessage(null)
    }
    return this
  }

  private fun selectConfiguration(configuration: RunConfiguration): Boolean {
    val enumeration = root.breadthFirstEnumeration()
    while (enumeration.hasMoreElements()) {
      val node = enumeration.nextElement() as DefaultMutableTreeNode
      var userObject = node.userObject
      if (userObject is SettingsEditorConfigurable<*>) {
        userObject = userObject.settings
      }
      if (userObject is RunnerAndConfigurationSettingsImpl) {
        val runnerAndConfigurationSettings = userObject as RunnerAndConfigurationSettings
        val configurationType = configuration.type
        if (Comparing.strEqual(runnerAndConfigurationSettings.configuration.type.id, configurationType.id) && Comparing.strEqual(
          runnerAndConfigurationSettings.configuration.name, configuration.name)) {
          TreeUtil.selectInTree(node, true, tree)
          return true
        }
      }
    }
    return false
  }

  private fun showTemplateConfigurable(factory: ConfigurationFactory) {
    var configurable: Configurable? = storedComponents[factory]
    if (configurable == null) {
      configurable = TemplateConfigurable(RunManagerImpl.getInstanceImpl(myProject).getConfigurationTemplate(factory))
      storedComponents.put(factory, configurable)
      configurable.reset()
    }
    updateRightPanel(configurable)
  }

  private fun showFolderField(node: DefaultMutableTreeNode, folderName: String) {
    rightPanel.removeAll()
    val p = JPanel(MigLayout("ins ${toolbarDecorator!!.actionsPanel.height} 5 0 0, flowx"))
    val textField = JTextField(folderName)
    textField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        node.userObject = textField.text
        treeModel.reload(node)
      }
    })
    textField.addActionListener { getGlobalInstance().doWhenFocusSettlesDown { getGlobalInstance().requestFocus(tree, true) } }
    p.add(JLabel("Folder name:"), "gapright 5")
    p.add(textField, "pushx, growx, wrap")
    p.add(JLabel(ExecutionBundle.message("run.configuration.rename.folder.disclaimer")), "gaptop 5, spanx 2")

    rightPanel.add(p)
    rightPanel.revalidate()
    rightPanel.repaint()
    if (isFolderCreating) {
      textField.selectAll()
      getGlobalInstance().doWhenFocusSettlesDown { getGlobalInstance().requestFocus(textField, true) }
    }
  }

  private fun getSafeUserObject(node: DefaultMutableTreeNode): Any {
    val userObject = node.userObject
    if (userObject is RunnerAndConfigurationSettingsImpl) {
      val configurationConfigurable = SingleConfigurationConfigurable.editSettings<RunConfiguration>(userObject as RunnerAndConfigurationSettings, null)
      installUpdateListeners(configurationConfigurable)
      node.userObject = configurationConfigurable
      return configurationConfigurable
    }
    return userObject
  }

  fun setRunDialog(runDialog: RunDialogBase) {
    myRunDialog = runDialog
  }

  fun updateRightPanel(configurable: Configurable) {
    rightPanel.removeAll()
    selectedConfigurable = configurable

    val configurableComponent = configurable.createComponent()
    rightPanel.add(BorderLayout.CENTER, configurableComponent)
    if (configurable is SingleConfigurationConfigurable<*>) {
      rightPanel.add(configurable.validationComponent, BorderLayout.SOUTH)
      ApplicationManager.getApplication().invokeLater { configurable.updateWarning() }
      if (configurableComponent != null) {
        val dataProvider = DataManager.getDataProvider(configurableComponent)
        if (dataProvider != null) {
          DataManager.registerDataProvider(rightPanel, dataProvider)
        }
      }
    }

    setupDialogBounds()
  }

  private fun sortTopLevelBranches() {
    val expandedPaths = TreeUtil.collectExpandedPaths(tree)
    TreeUtil.sortRecursively(root) { o1, o2 ->
      val userObject1 = o1.userObject
      val userObject2 = o2.userObject
      when {
        userObject1 is ConfigurationType && userObject2 is ConfigurationType -> (userObject1).displayName.compareTo(userObject2.displayName)
        userObject1 === DEFAULTS && userObject2 is ConfigurationType -> 1
        userObject2 === DEFAULTS && userObject1 is ConfigurationType -> - 1
        else -> 0
      }
    }
    TreeUtil.restoreExpandedPaths(tree, expandedPaths)
  }

  private fun update() {
    updateDialog()
    val selectionPath = tree.selectionPath
    if (selectionPath != null) {
      val node = selectionPath.lastPathComponent as DefaultMutableTreeNode
      treeModel.reload(node)
    }
  }

  private fun installUpdateListeners(info: SingleConfigurationConfigurable<RunConfiguration>) {
    val changed = booleanArrayOf(false)
    info.editor.addSettingsEditorListener { editor ->
      update()
      val configuration = info.configuration
      if (configuration is LocatableConfiguration) {
        if (configuration.isGeneratedName && !changed[0]) {
          try {
            val snapshot = editor.snapshot.configuration as LocatableConfiguration
            val generatedName = snapshot.suggestedName()
            if (generatedName != null && generatedName.isNotEmpty()) {
              info.nameText = generatedName
              changed[0] = false
            }
          }
          catch (ignore: ConfigurationException) {
          }
        }
      }
      setupDialogBounds()
    }

    info.addNameListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        changed[0] = true
        update()
      }
    })

    info.addSharedListener {
      changed[0] = true
      update()
    }
  }

  private fun drawPressAddButtonMessage(configurationType: ConfigurationType?) {
    val panel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
    panel.border = EmptyBorder(30, 0, 0, 0)
    panel.add(JLabel("Press the"))

    val addIcon = ActionLink("", IconUtil.getAddIcon(), toolbarAddAction)
    addIcon.border = EmptyBorder(0, 0, 0, 5)
    panel.add(addIcon)

    val configurationTypeDescription = if (configurationType != null)
      configurationType.configurationTypeDescription
    else
      ExecutionBundle.message("run.configuration.default.type.description")
    panel.add(JLabel(ExecutionBundle.message("empty.run.configuration.panel.text.label3", configurationTypeDescription)))
    val scrollPane = ScrollPaneFactory.createScrollPane(panel, true)

    rightPanel.removeAll()
    rightPanel.add(scrollPane, BorderLayout.CENTER)
    if (configurationType == null) {
      val settingsPanel = JPanel(GridBagLayout())
      val grid = GridBag().setDefaultAnchor(GridBagConstraints.NORTHWEST)

      for (each in additionalSettings) {
        settingsPanel.add(each.second, grid.nextLine().next())
      }
      settingsPanel.add(createSettingsPanel(), grid.nextLine().next())

      val wrapper = JPanel(BorderLayout())
      wrapper.add(settingsPanel, BorderLayout.WEST)
      wrapper.add(Box.createGlue(), BorderLayout.CENTER)

      rightPanel.add(wrapper, BorderLayout.SOUTH)
    }
    rightPanel.revalidate()
    rightPanel.repaint()
  }

  private fun createLeftPanel(): JPanel {
    initTree()
    val removeAction = MyRemoveAction()
    val moveUpAction = MyMoveAction(ExecutionBundle.message("move.up.action.name"), null, IconUtil.getMoveUpIcon(), -1)
    val moveDownAction = MyMoveAction(ExecutionBundle.message("move.down.action.name"), null, IconUtil.getMoveDownIcon(), 1)
    toolbarDecorator = ToolbarDecorator.createDecorator(tree).setAsUsualTopToolbar()
      .setAddAction(toolbarAddAction).setAddActionName(ExecutionBundle.message("add.new.run.configuration.action2.name"))
      .setRemoveAction(removeAction).setRemoveActionUpdater(removeAction)
      .setRemoveActionName(ExecutionBundle.message("remove.run.configuration.action.name"))
      .setMoveUpAction(moveUpAction).setMoveUpActionName(ExecutionBundle.message("move.up.action.name")).setMoveUpActionUpdater(
      moveUpAction)
      .setMoveDownAction(moveDownAction).setMoveDownActionName(ExecutionBundle.message("move.down.action.name")).setMoveDownActionUpdater(
      moveDownAction)
      .addExtraAction(AnActionButton.fromAction(MyCopyAction()))
      .addExtraAction(AnActionButton.fromAction(MySaveAction()))
      .addExtraAction(AnActionButton.fromAction(MyEditDefaultsAction()))
      .addExtraAction(AnActionButton.fromAction(MyCreateFolderAction()))
      .addExtraAction(AnActionButton.fromAction(MySortFolderAction()))
      .setMinimumSize(JBDimension(200, 200))
      .setButtonComparator(ExecutionBundle.message("add.new.run.configuration.action2.name"),
                           ExecutionBundle.message("remove.run.configuration.action.name"),
                           ExecutionBundle.message("copy.configuration.action.name"),
                           ExecutionBundle.message("action.name.save.configuration"),
                           ExecutionBundle.message("run.configuration.edit.default.configuration.settings.text"),
                           ExecutionBundle.message("move.up.action.name"),
                           ExecutionBundle.message("move.down.action.name"),
                           ExecutionBundle.message("run.configuration.create.folder.text")
      ).setForcedDnD()
    return toolbarDecorator!!.createPanel()
  }

  private fun createSettingsPanel(): JPanel {
    val bottomPanel = JPanel(GridBagLayout())
    val g = GridBag()

    bottomPanel.add(confirmation, g.nextLine().coverLine())
    bottomPanel.add(create(recentsLimit, ExecutionBundle.message("temporary.configurations.limit"), BorderLayout.WEST),
                    g.nextLine().insets(JBUI.insets(10, 0, 0, 0)).anchor(GridBagConstraints.WEST))

    recentsLimit.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        isModified = !Comparing.equal(recentsLimit.text, recentsLimit.getClientProperty(INITIAL_VALUE_KEY))
      }
    })
    confirmation.addChangeListener {
      isModified = !Comparing.equal(confirmation.isSelected, confirmation.getClientProperty(INITIAL_VALUE_KEY))
    }
    return bottomPanel
  }

  private val selectedConfigurationType: ConfigurationType?
    get() {
      val configurationTypeNode = selectedConfigurationTypeNode
      return if (configurationTypeNode != null) configurationTypeNode.userObject as ConfigurationType else null
    }

  override fun createComponent(): JComponent? {
    for (each in Extensions.getExtensions(RunConfigurationsSettings.EXTENSION_POINT, myProject)) {
      val configurable = each.createConfigurable()
      additionalSettings.add(Pair.create(configurable, configurable.createComponent()))
    }

    wholePanel = JPanel(BorderLayout())
    DataManager.registerDataProvider(wholePanel!!) { dataId ->
      if (RunConfigurationSelector.KEY.name == dataId)
        RunConfigurationSelector { configuration -> selectConfiguration(configuration) }
      else
        null
    }

    splitter.firstComponent = createLeftPanel()
    splitter.setHonorComponentsMinimumSize(true)
    splitter.secondComponent = rightPanel
    wholePanel!!.add(splitter, BorderLayout.CENTER)

    updateDialog()

    val d = wholePanel!!.preferredSize
    d.width = Math.max(d.width, 800)
    d.height = Math.max(d.height, 600)
    wholePanel!!.preferredSize = d

    return wholePanel
  }

  override fun reset() {
    val manager = runManager
    val config = manager.config
    recentsLimit.text = Integer.toString(config.recentsLimit)
    recentsLimit.putClientProperty(INITIAL_VALUE_KEY, recentsLimit.text)
    confirmation.isSelected = config.isRestartRequiresConfirmation
    confirmation.putClientProperty(INITIAL_VALUE_KEY, confirmation.isSelected)

    for (each in additionalSettings) {
      each.first.reset()
    }

    isModified = false
  }

  @Throws(ConfigurationException::class)
  override fun apply() {
    val manager = runManager
    manager.fireBeginUpdate()
    try {
      val settingsToOrder = TObjectIntHashMap<RunnerAndConfigurationSettings>()
      var order = 0
      val toDeleteSettings = THashSet(manager.allSettings)
      val selectedSettings = selectedSettings
      for (i in 0 until root.childCount) {
        val node = root.getChildAt(i) as DefaultMutableTreeNode
        val userObject = node.userObject
        if (userObject is ConfigurationType) {
          for (bean in applyByType(node, userObject, selectedSettings)) {
            settingsToOrder.put(bean.settings, order++)
            toDeleteSettings.remove(bean.settings)
          }
        }
      }
      manager.removeConfigurations(toDeleteSettings)

      val recentLimit = Math.max(RunManagerConfig.MIN_RECENT_LIMIT, StringUtil.parseInt(recentsLimit.text, 0))
      if (manager.config.recentsLimit != recentLimit) {
        manager.config.recentsLimit = recentLimit
        manager.checkRecentsLimit()
      }
      manager.config.isRestartRequiresConfirmation = confirmation.isSelected

      for (configurable in storedComponents.values) {
        if (configurable.isModified) {
          configurable.apply()
        }
      }

      additionalSettings.forEach { it.first.apply() }

      manager.setOrder(Comparator.comparingInt(ToIntFunction<RunnerAndConfigurationSettings> { settingsToOrder.get(it) }))
    }
    finally {
      manager.fireEndUpdate()
    }
    updateActiveConfigurationFromSelected()
    isModified = false
    tree.repaint()
  }

  fun updateActiveConfigurationFromSelected() {
    if (selectedConfigurable != null && selectedConfigurable is SingleConfigurationConfigurable<*>) {
      runManager.selectedConfiguration = (selectedConfigurable as SingleConfigurationConfigurable<*>).settings as RunnerAndConfigurationSettings
    }
  }

  @Throws(ConfigurationException::class)
  private fun applyByType(typeNode: DefaultMutableTreeNode,
                          type: ConfigurationType,
                          selectedSettings: RunnerAndConfigurationSettings?): List<RunConfigurationBean> {
    var indexToMove = -1

    val configurationBeans = ArrayList<RunConfigurationBean>()
    val names = THashSet<String>()
    val configurationNodes = ArrayList<DefaultMutableTreeNode>()
    collectNodesRecursively(typeNode, configurationNodes, CONFIGURATION, TEMPORARY_CONFIGURATION)
    for (node in configurationNodes) {
      val userObject = node.userObject
      var configurationBean: RunConfigurationBean? = null
      var settings: RunnerAndConfigurationSettings? = null
      if (userObject is SingleConfigurationConfigurable<*>) {
        settings = userObject.settings as RunnerAndConfigurationSettings
        applyConfiguration(typeNode, userObject)
        configurationBean = RunConfigurationBean(userObject)
      }
      else if (userObject is RunnerAndConfigurationSettingsImpl) {
        settings = userObject
        configurationBean = RunConfigurationBean(settings)
      }

      if (configurationBean != null) {
        val configurable = configurationBean.configurable
        val nameText = if (configurable != null) configurable.nameText else configurationBean.settings.name
        if (!names.add(nameText)) {
          TreeUtil.selectNode(tree, node)
          throw ConfigurationException(type.displayName + " with name \'" + nameText + "\' already exists")
        }
        configurationBeans.add(configurationBean)
        if (settings === selectedSettings) {
          indexToMove = configurationBeans.size - 1
        }
      }
    }
    val folderNodes = ArrayList<DefaultMutableTreeNode>()
    collectNodesRecursively(typeNode, folderNodes, FOLDER)
    names.clear()
    for (node in folderNodes) {
      val folderName = node.userObject as String
      if (folderName.isEmpty()) {
        TreeUtil.selectNode(tree, node)
        throw ConfigurationException("Folder name shouldn't be empty")
      }
      if (!names.add(folderName)) {
        TreeUtil.selectNode(tree, node)
        throw ConfigurationException("Folders name \'$folderName\' is duplicated")
      }
    }

    // try to apply all
    for (bean in configurationBeans) {
      applyConfiguration(typeNode, bean.configurable)
    }

    // just saved as 'stable' configuration shouldn't stay between temporary ones (here we order model to save)
    var shift = 0
    if (selectedSettings != null && selectedSettings.type === type) {
      shift = adjustOrder()
    }
    if (shift != 0 && indexToMove != -1) {
      configurationBeans.add(indexToMove - shift, configurationBeans.removeAt(indexToMove))
    }

    return configurationBeans
  }

  private fun getConfigurationTypeNode(type: ConfigurationType): DefaultMutableTreeNode? {
    for (i in 0 until root.childCount) {
      val node = root.getChildAt(i) as DefaultMutableTreeNode
      if (node.userObject === type) {
        return node
      }
    }
    return null
  }

  @Throws(ConfigurationException::class)
  private fun applyConfiguration(typeNode: DefaultMutableTreeNode, configurable: SingleConfigurationConfigurable<*>?) {
    try {
      if (configurable != null && configurable.isModified) {
        configurable.apply()
      }
    }
    catch (e: ConfigurationException) {
      for (i in 0 until typeNode.childCount) {
        val node = typeNode.getChildAt(i) as DefaultMutableTreeNode
        if (Comparing.equal(configurable, node.userObject)) {
          TreeUtil.selectNode(tree, node)
          break
        }
      }
      throw e
    }
  }

  override fun isModified(): Boolean {
    if (super.isModified()) {
      return true
    }

    val runManager = runManager
    val allSettings = runManager.allSettings
    var currentSettingCount = 0
    for (i in 0 until root.childCount) {
      val typeNode = root.getChildAt(i) as DefaultMutableTreeNode
      val `object` = typeNode.userObject as? ConfigurationType ?: continue

      val configurationNodes = ArrayList<DefaultMutableTreeNode>()
      collectNodesRecursively(typeNode, configurationNodes, CONFIGURATION, TEMPORARY_CONFIGURATION)
      if (countSettingsOfType(allSettings, `object`) != configurationNodes.size) {
        return true
      }

      for (configurationNode in configurationNodes) {
        val userObject = configurationNode.userObject
        val settings: RunnerAndConfigurationSettings
        if (userObject is SingleConfigurationConfigurable<*>) {
          if (userObject.isModified) {
            return true
          }
          settings = userObject.settings as RunnerAndConfigurationSettings
        }
        else if (userObject is RunnerAndConfigurationSettings) {
          settings = userObject
        }
        else {
          continue
        }

        val index = currentSettingCount++
        // we compare by instance, equals is not implemented and in any case object modification is checked by other logic
        if (allSettings.size <= index || allSettings[index] !== settings) {
          return true
        }
      }
    }
    if (allSettings.size != currentSettingCount) {
      return true
    }

    for (configurable in storedComponents.values) {
      if (configurable.isModified) return true
    }

    for (each in additionalSettings) {
      if (each.first.isModified) return true
    }

    return false
  }

  override fun disposeUIResources() {
    Disposer.dispose(this)
  }

  override fun dispose() {
    isDisposed = true
    storedComponents.values.forEach { it.disposeUIResources() }
    storedComponents.clear()

    additionalSettings.forEach { it.first.disposeUIResources() }

    TreeUtil.traverseDepth(root) { node ->
      if (node is DefaultMutableTreeNode) {
        val userObject = node.userObject
        (userObject as? SingleConfigurationConfigurable<*>)?.disposeUIResources()
      }
      true
    }
    rightPanel.removeAll()
    splitter.dispose()
  }

  private fun updateDialog() {
    val executor = (if (myRunDialog != null) myRunDialog!!.executor else null) ?: return
    val buffer = StringBuilder()
    buffer.append(executor.id)
    val configuration = selectedConfiguration
    if (configuration != null) {
      buffer.append(" - ")
      buffer.append(configuration.nameText)
    }
    myRunDialog!!.setOKActionEnabled(canRunConfiguration(configuration, executor))
    myRunDialog!!.setTitle(buffer.toString())
  }

  private fun setupDialogBounds() {
    SwingUtilities.invokeLater { UIUtil.setupEnclosingDialogBounds(wholePanel!!) }
  }

  private val selectedConfiguration: SingleConfigurationConfigurable<RunConfiguration>?
    get() {
      val selectionPath = tree.selectionPath
      if (selectionPath != null) {
        val treeNode = selectionPath.lastPathComponent as DefaultMutableTreeNode
        val userObject = treeNode.userObject
        if (userObject is SingleConfigurationConfigurable<*>) {
          @Suppress("UNCHECKED_CAST")
          return userObject as SingleConfigurationConfigurable<RunConfiguration>
        }
      }
      return null
    }

  open val runManager: RunManagerImpl
    get() = RunManagerImpl.getInstanceImpl(myProject)

  override fun getHelpTopic(): String? {
    val type = selectedConfigurationType ?: return "reference.dialogs.rundebug"
    return "reference.dialogs.rundebug.${type.id}"
  }

  private fun clickDefaultButton() {
    myRunDialog?.clickDefaultButton()
  }

  private val selectedConfigurationTypeNode: DefaultMutableTreeNode?
    get() {
      val selectionPath = tree.selectionPath
      var node: DefaultMutableTreeNode? = if (selectionPath != null) selectionPath.lastPathComponent as DefaultMutableTreeNode else null
      while (node != null) {
        val userObject = node.userObject
        if (userObject is ConfigurationType) {
          return node
        }
        node = node.parent as DefaultMutableTreeNode
      }
      return null
    }

  private fun getNode(row: Int) = tree.getPathForRow(row).lastPathComponent as DefaultMutableTreeNode

  fun getAvailableDropPosition(direction: Int): Trinity<Int, Int, RowsDnDSupport.RefinedDropSupport.Position>? {
    val rows = tree.selectionRows
    if (rows == null || rows.size != 1) {
      return null
    }

    val oldIndex = rows[0]
    var newIndex = oldIndex + direction

    if (!getKind(tree.getPathForRow(oldIndex).lastPathComponent as DefaultMutableTreeNode).supportsDnD()) {
      return null
    }

    while (newIndex > 0 && newIndex < tree.rowCount) {
      val targetPath = tree.getPathForRow(newIndex)
      val allowInto = getKind(targetPath.lastPathComponent as DefaultMutableTreeNode) == FOLDER && !tree.isExpanded(targetPath)
      val position = when {
        allowInto && treeModel.isDropInto(tree, oldIndex, newIndex) -> INTO
        direction > 0 -> BELOW
        else -> ABOVE
      }
      val oldNode = getNode(oldIndex)
      val newNode = getNode(newIndex)
      if (oldNode.parent !== newNode.parent && getKind(newNode) != FOLDER) {
        var copy = position
        if (position == BELOW) {
          copy = ABOVE
        }
        else if (position == ABOVE) {
          copy = BELOW
        }
        if (treeModel.canDrop(oldIndex, newIndex, copy)) {
          return Trinity.create(oldIndex, newIndex, copy)
        }
      }
      if (treeModel.canDrop(oldIndex, newIndex, position)) {
        return Trinity.create(oldIndex, newIndex, position)
      }

      when {
        position == BELOW && newIndex < tree.rowCount - 1 && treeModel.canDrop(oldIndex, newIndex + 1, ABOVE) -> return Trinity.create(oldIndex, newIndex + 1, ABOVE)
        position == ABOVE && newIndex > 1 && treeModel.canDrop(oldIndex, newIndex - 1, BELOW) -> return Trinity.create(oldIndex, newIndex - 1, BELOW)
        position == BELOW && treeModel.canDrop(oldIndex, newIndex, ABOVE) -> return Trinity.create(oldIndex, newIndex, ABOVE)
        position == ABOVE && treeModel.canDrop(oldIndex, newIndex, BELOW) -> return Trinity.create(oldIndex, newIndex, BELOW)
        else -> newIndex += direction
      }
    }
    return null
  }

  private fun createNewConfiguration(settings: RunnerAndConfigurationSettings,
                                     node: DefaultMutableTreeNode?,
                                     selectedNode: DefaultMutableTreeNode?): SingleConfigurationConfigurable<RunConfiguration> {
    val configurationConfigurable = SingleConfigurationConfigurable.editSettings<RunConfiguration>(settings, null)
    installUpdateListeners(configurationConfigurable)
    val nodeToAdd = DefaultMutableTreeNode(configurationConfigurable)
    treeModel.insertNodeInto(nodeToAdd, node!!, if (selectedNode != null) node.getIndex(selectedNode) + 1 else node.childCount)
    TreeUtil.selectNode(tree, nodeToAdd)
    return configurationConfigurable
  }

  fun createNewConfiguration(factory: ConfigurationFactory): SingleConfigurationConfigurable<RunConfiguration> {
    var node: DefaultMutableTreeNode
    var selectedNode: DefaultMutableTreeNode? = null
    val selectionPath = tree.selectionPath
    if (selectionPath != null) {
      selectedNode = selectionPath.lastPathComponent as DefaultMutableTreeNode
    }
    var typeNode = getConfigurationTypeNode(factory.type)
    if (typeNode == null) {
      typeNode = DefaultMutableTreeNode(factory.type)
      root.add(typeNode)
      sortTopLevelBranches()
      (tree.model as DefaultTreeModel).reload()
    }
    node = typeNode
    if (selectedNode != null && typeNode.isNodeDescendant(selectedNode)) {
      node = selectedNode
      if (getKind(node).isConfiguration) {
        node = node.parent as DefaultMutableTreeNode
      }
    }
    val settings = runManager.createConfiguration(createUniqueName(typeNode, null, CONFIGURATION, TEMPORARY_CONFIGURATION), factory)
    @Suppress("UNCHECKED_CAST")
    (factory as? ConfigurationFactoryEx<RunConfiguration>)?.onNewConfigurationCreated(settings.configuration)
    return createNewConfiguration(settings, node, selectedNode)
  }

  private inner class MyToolbarAddAction : AnAction(ExecutionBundle.message("add.new.run.configuration.action2.name"),
                                                    ExecutionBundle.message("add.new.run.configuration.action2.name"),
                                                    IconUtil.getAddIcon()), AnActionButtonRunnable {
    init {
      registerCustomShortcutSet(CommonShortcuts.INSERT, tree)
    }

    override fun actionPerformed(e: AnActionEvent) {
      showAddPopup(true)
    }

    override fun run(button: AnActionButton) {
      showAddPopup(true)
    }

    private fun showAddPopup(showApplicableTypesOnly: Boolean) {
      val allTypes = runManager.configurationFactoriesWithoutUnknown
      val configurationTypes: MutableList<ConfigurationType?> = getTypesToShow(showApplicableTypesOnly, allTypes).toMutableList()
      configurationTypes.sortWith(kotlin.Comparator { type1, type2 -> type1!!.displayName.compareTo(type2!!.displayName, ignoreCase = true) })
      val hiddenCount = allTypes.size - configurationTypes.size
      if (hiddenCount > 0) {
        configurationTypes.add(null)
      }

      val popup = NewRunConfigurationPopup.createAddPopup(configurationTypes, "$hiddenCount items more (irrelevant)...",
                                                          { factory -> createNewConfiguration(factory) }, selectedConfigurationType,
                                                          { showAddPopup(false) }, true)
      //new TreeSpeedSearch(myTree);
      popup.showUnderneathOf(toolbarDecorator!!.actionsPanel)
    }

    private fun getTypesToShow(showApplicableTypesOnly: Boolean, allTypes: List<ConfigurationType>): List<ConfigurationType> {
      if (showApplicableTypesOnly) {
        val applicableTypes = allTypes.filter { isApplicable(it) }
        if (applicableTypes.size < (allTypes.size - 3)) {
          return applicableTypes
        }
      }
      return allTypes
    }

    private fun isApplicable(type: ConfigurationType): Boolean {
      for (factory in type.configurationFactories) {
        if (factory.isApplicable(myProject)) {
          return true
        }
      }
      return false
    }
  }

  private inner class MyRemoveAction : AnAction(ExecutionBundle.message("remove.run.configuration.action.name"),
                                                ExecutionBundle.message("remove.run.configuration.action.name"),
                                                IconUtil.getRemoveIcon()), AnActionButtonRunnable, AnActionButtonUpdater {
    init {
      registerCustomShortcutSet(CommonShortcuts.getDelete(), tree)
    }

    override fun actionPerformed(e: AnActionEvent) {
      doRemove()
    }

    override fun run(button: AnActionButton) {
      doRemove()
    }

    private fun doRemove() {
      val selections = tree.selectionPaths
      tree.clearSelection()

      var nodeIndexToSelect = -1
      var parentToSelect: DefaultMutableTreeNode? = null

      val changedParents = HashSet<DefaultMutableTreeNode>()
      var wasRootChanged = false

      for (each in selections!!) {
        val node = each.lastPathComponent as DefaultMutableTreeNode
        val parent = node.parent as DefaultMutableTreeNode
        val kind = getKind(node)
        if (!kind.isConfiguration && kind != FOLDER)
          continue

        if (node.userObject is SingleConfigurationConfigurable<*>) {
          (node.userObject as SingleConfigurationConfigurable<*>).disposeUIResources()
        }

        nodeIndexToSelect = parent.getIndex(node)
        parentToSelect = parent
        treeModel.removeNodeFromParent(node)
        changedParents.add(parent)

        if (kind == FOLDER) {
          val children = ArrayList<DefaultMutableTreeNode>()
          for (i in 0 until node.childCount) {
            val child = node.getChildAt(i) as DefaultMutableTreeNode
            val userObject = getSafeUserObject(child)
            if (userObject is SingleConfigurationConfigurable<*>) {
              userObject.folderName = null
            }
            children.add(0, child)
          }
          var confIndex = 0
          for (i in 0 until parent.childCount) {
            if (getKind(parent.getChildAt(i) as DefaultMutableTreeNode).isConfiguration) {
              confIndex = i
              break
            }
          }
          for (child in children) {
            if (getKind(child) == CONFIGURATION)
              treeModel.insertNodeInto(child, parent, confIndex)
          }
          confIndex = parent.childCount
          for (i in 0 until parent.childCount) {
            if (getKind(parent.getChildAt(i) as DefaultMutableTreeNode) == TEMPORARY_CONFIGURATION) {
              confIndex = i
              break
            }
          }
          for (child in children) {
            if (getKind(child) == TEMPORARY_CONFIGURATION) {
              treeModel.insertNodeInto(child, parent, confIndex)
            }
          }
        }

        if (parent.childCount == 0 && parent.userObject is ConfigurationType) {
          changedParents.remove(parent)
          wasRootChanged = true

          nodeIndexToSelect = root.getIndex(parent)
          nodeIndexToSelect = Math.max(0, nodeIndexToSelect - 1)
          parentToSelect = root
          parent.removeFromParent()
        }
      }

      if (wasRootChanged) {
        (tree.model as DefaultTreeModel).reload()
      }
      else {
        for (each in changedParents) {
          treeModel.reload(each)
          tree.expandPath(TreePath(each))
        }
      }

      selectedConfigurable = null
      if (root.childCount == 0) {
        drawPressAddButtonMessage(null)
      }
      else {
        if (parentToSelect!!.childCount > 0) {
          val nodeToSelect = if (nodeIndexToSelect < parentToSelect.childCount) {
            parentToSelect.getChildAt(nodeIndexToSelect)
          }
          else {
            parentToSelect.getChildAt(nodeIndexToSelect - 1)
          }
          TreeUtil.selectInTree(nodeToSelect as DefaultMutableTreeNode, true, tree)
        }
      }
    }


    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = isEnabled(e)
    }

    override fun isEnabled(e: AnActionEvent): Boolean {
      var enabled = false
      val selections = tree.selectionPaths
      if (selections != null) {
        for (each in selections) {
          val kind = getKind(each.lastPathComponent as DefaultMutableTreeNode)
          if (kind.isConfiguration || kind == FOLDER) {
            enabled = true
            break
          }
        }
      }
      return enabled
    }
  }

  private inner class MyCopyAction : AnAction(ExecutionBundle.message("copy.configuration.action.name"),
                                              ExecutionBundle.message("copy.configuration.action.name"), PlatformIcons.COPY_ICON) {
    init {
      val action = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_DUPLICATE)
      registerCustomShortcutSet(action.shortcutSet, tree)
    }

    override fun actionPerformed(e: AnActionEvent) {
      val configuration = selectedConfiguration
      LOG.assertTrue(configuration != null)
      try {
        val typeNode = selectedConfigurationTypeNode!!
        val settings = configuration!!.snapshot
        val copyName = createUniqueName(typeNode, configuration.nameText, CONFIGURATION, TEMPORARY_CONFIGURATION)
        settings!!.name = copyName
        val factory = settings.factory
        @Suppress("UNCHECKED_CAST")
        (factory as? ConfigurationFactoryEx<RunConfiguration>)?.onConfigurationCopied(settings.configuration)
        val configurable = createNewConfiguration(settings, typeNode, selectedNode)
        IdeFocusManager.getInstance(myProject).requestFocus(configurable.nameTextField, true)
        configurable.nameTextField.selectionStart = 0
        configurable.nameTextField.selectionEnd = copyName.length
      }
      catch (e1: ConfigurationException) {
        Messages.showErrorDialog(toolbarDecorator!!.actionsPanel, e1.message, e1.title)
      }
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = selectedConfiguration?.configuration !is UnknownRunConfiguration
    }
  }

  private inner class MySaveAction : AnAction(ExecutionBundle.message("action.name.save.configuration"), null,
                                              AllIcons.Actions.Menu_saveall) {
    override fun actionPerformed(e: AnActionEvent) {
      val configurationConfigurable = selectedConfiguration
      LOG.assertTrue(configurationConfigurable != null)
      val originalConfiguration = configurationConfigurable!!.settings
      if (originalConfiguration.isTemporary) {
        //todo Don't make 'stable' real configurations here but keep the set 'they want to be stable' until global 'Apply' action
        runManager.makeStable(originalConfiguration)
        adjustOrder()
      }
      tree.repaint()
    }

    override fun update(e: AnActionEvent) {
      val configuration = selectedConfiguration
      val presentation = e.presentation
      val enabled: Boolean
      if (configuration == null) {
        enabled = false
      }
      else {
        val settings = configuration.settings
        enabled = settings != null && settings.isTemporary
      }
      presentation.isEnabledAndVisible = enabled
    }
  }

  /**
   * Just saved as 'stable' configuration shouldn't stay between temporary ones (here we order nodes in JTree only)
   * @return shift (positive) for move configuration "up" to other stable configurations. Zero means "there is nothing to change"
   */
  private fun adjustOrder(): Int {
    val selectionPath = tree.selectionPath ?: return 0
    val treeNode = selectionPath.lastPathComponent as DefaultMutableTreeNode
    val selectedSettings = getSettings(treeNode)
    if (selectedSettings == null || selectedSettings.isTemporary) {
      return 0
    }
    val parent = treeNode.parent as MutableTreeNode
    val initialPosition = parent.getIndex(treeNode)
    var position = initialPosition
    var node: DefaultMutableTreeNode? = treeNode.previousSibling
    while (node != null) {
      val settings = getSettings(node)
      if (settings != null && settings.isTemporary) {
        position--
      }
      else {
        break
      }
      node = node.previousSibling
    }
    for (i in 0 until initialPosition - position) {
      TreeUtil.moveSelectedRow(tree, -1)
    }
    return initialPosition - position
  }

  private inner class MyMoveAction(text: String, description: String?, icon: Icon, private val myDirection: Int) : AnAction(text,
                                                                                                                            description,
                                                                                                                            icon), AnActionButtonRunnable, AnActionButtonUpdater {

    override fun actionPerformed(e: AnActionEvent) {
      doMove()
    }

    private fun doMove() {
      getAvailableDropPosition(myDirection)?.let {
        treeModel.drop(it.first, it.second, it.third)
      }
    }

    override fun run(button: AnActionButton) {
      doMove()
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = isEnabled(e)
    }

    override fun isEnabled(e: AnActionEvent) = getAvailableDropPosition(myDirection) != null
  }

  private inner class MyEditDefaultsAction : AnAction(ExecutionBundle.message("run.configuration.edit.default.configuration.settings.text"),
                                                      ExecutionBundle.message(
                                                        "run.configuration.edit.default.configuration.settings.description"),
                                                      AllIcons.General.Settings) {
    override fun actionPerformed(e: AnActionEvent) {
      var defaults = TreeUtil.findNodeWithObject(DEFAULTS, tree.model, root) ?: return
      selectedConfigurationType?.let {
        defaults = TreeUtil.findNodeWithObject(it, tree.model, defaults) ?: return
      }
      val defaultsNode = defaults as DefaultMutableTreeNode? ?: return
      val path = TreeUtil.getPath(root, defaultsNode)
      tree.expandPath(path)
      TreeUtil.selectInTree(defaultsNode, true, tree)
      tree.scrollPathToVisible(path)
    }

    override fun update(e: AnActionEvent) {
      var isEnabled = TreeUtil.findNodeWithObject(DEFAULTS, tree.model, root) != null
      val path = tree.selectionPath
      if (path != null) {
        var o = path.lastPathComponent
        if (o is DefaultMutableTreeNode && o.userObject == DEFAULTS) {
          isEnabled = false
        }
        o = path.parentPath.lastPathComponent
        if (o is DefaultMutableTreeNode && o.userObject == DEFAULTS) {
          isEnabled = false
        }
      }
      e.presentation.isEnabled = isEnabled
    }
  }

  private inner class MyCreateFolderAction : AnAction(ExecutionBundle.message("run.configuration.create.folder.text"),
                                                      ExecutionBundle.message("run.configuration.create.folder.description"),
                                                      AllIcons.Nodes.Folder) {

    override fun actionPerformed(e: AnActionEvent) {
      val type = selectedConfigurationType ?: return
      val selectedNodes = selectedNodes
      val typeNode = getConfigurationTypeNode(type) ?: return
      val folderName = createUniqueName(typeNode, "New Folder", FOLDER)
      val folders = ArrayList<DefaultMutableTreeNode>()
      collectNodesRecursively(getConfigurationTypeNode(type)!!, folders, FOLDER)
      val folderNode = DefaultMutableTreeNode(folderName)
      treeModel.insertNodeInto(folderNode, typeNode, folders.size)
      isFolderCreating = true
      try {
        for (node in selectedNodes) {
          val folderRow = tree.getRowForPath(TreePath(folderNode.path))
          val rowForPath = tree.getRowForPath(TreePath(node.path))
          if (getKind(node).isConfiguration && treeModel.canDrop(rowForPath, folderRow, INTO)) {
            treeModel.drop(rowForPath, folderRow, INTO)
          }
        }
        tree.selectionPath = TreePath(folderNode.path)
      }
      finally {
        isFolderCreating = false
      }
    }

    override fun update(e: AnActionEvent) {
      var isEnabled = false
      var toMove = false
      val selectedNodes = selectedNodes
      var selectedType: ConfigurationType? = null
      for (node in selectedNodes) {
        val type = getType(node)
        if (selectedType == null) {
          selectedType = type
        }
        else {
          if (!Comparing.equal(type, selectedType)) {
            isEnabled = false
            break
          }
        }
        val kind = getKind(node)
        if (kind.isConfiguration || kind == CONFIGURATION_TYPE && node.parent === root || kind == FOLDER) {
          isEnabled = true
        }
        if (kind.isConfiguration) {
          toMove = true
        }
      }
      e.presentation.text = ExecutionBundle.message("run.configuration.create.folder.description${if (toMove) ".move" else ""}")
      e.presentation.isEnabled = isEnabled
    }
  }

  private inner class MySortFolderAction : AnAction(ExecutionBundle.message("run.configuration.sort.folder.text"),
                                                    ExecutionBundle.message("run.configuration.sort.folder.description"),
                                                    AllIcons.ObjectBrowser.Sorted), Comparator<DefaultMutableTreeNode> {

    override fun compare(node1: DefaultMutableTreeNode, node2: DefaultMutableTreeNode): Int {
      val kind1 = getKind(node1)
      val kind2 = getKind(node2)
      if (kind1 == FOLDER) {
        return if (kind2 == FOLDER) node1.parent.getIndex(node1) - node2.parent.getIndex(node2) else -1
      }
      if (kind2 == FOLDER) {
        return 1
      }
      val name1 = getName(node1.userObject)
      val name2 = getName(node2.userObject)
      return when (kind1) {
        TEMPORARY_CONFIGURATION -> if (kind2 == TEMPORARY_CONFIGURATION) name1.compareTo(name2) else 1
        else -> if (kind2 == TEMPORARY_CONFIGURATION) -1 else name1.compareTo(name2)
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
      val selectedNodes = selectedNodes
      val foldersToSort = ArrayList<DefaultMutableTreeNode>()
      for (node in selectedNodes) {
        val kind = getKind(node)
        if (kind == CONFIGURATION_TYPE || kind == FOLDER) {
          foldersToSort.add(node)
        }
      }
      for (folderNode in foldersToSort) {
        val children = ArrayList<DefaultMutableTreeNode>()
        for (i in 0 until folderNode.childCount) {
          val child = folderNode.getChildAt(i) as DefaultMutableTreeNode
          children.add(child)
        }
        children.sortWith(this)
        for (child in children) {
          folderNode.add(child)
        }
        treeModel.nodeStructureChanged(folderNode)
      }
    }

    override fun update(e: AnActionEvent) {
      val selectedNodes = selectedNodes
      for (node in selectedNodes) {
        val kind = getKind(node)
        if (kind == CONFIGURATION_TYPE || kind == FOLDER) {
          e.presentation.isEnabled = true
          return
        }
      }
      e.presentation.isEnabled = false
    }
  }

  private val selectedNodes: Array<DefaultMutableTreeNode>
    get() = tree.getSelectedNodes(DefaultMutableTreeNode::class.java, null)

  private val selectedNode: DefaultMutableTreeNode?
    get() = tree.getSelectedNodes(DefaultMutableTreeNode::class.java, null).firstOrNull()

  private val selectedSettings: RunnerAndConfigurationSettings?
    get() {
      val selectionPath = tree.selectionPath ?: return null
      return getSettings(selectionPath.lastPathComponent as DefaultMutableTreeNode)
    }

  inner class MyTreeModel(root: TreeNode) : DefaultTreeModel(root), EditableModel, RowsDnDSupport.RefinedDropSupport {
    override fun addRow() {}

    override fun removeRow(index: Int) {}

    override fun exchangeRows(oldIndex: Int, newIndex: Int) {
      //Do nothing, use drop() instead
    }

    //Legacy, use canDrop() instead
    override fun canExchangeRows(oldIndex: Int, newIndex: Int) = false

    override fun canDrop(oldIndex: Int, newIndex: Int, position: RowsDnDSupport.RefinedDropSupport.Position): Boolean {
      if (tree.rowCount <= oldIndex || tree.rowCount <= newIndex || oldIndex < 0 || newIndex < 0) {
        return false
      }

      val oldNode = tree.getPathForRow(oldIndex).lastPathComponent as DefaultMutableTreeNode
      val newNode = tree.getPathForRow(newIndex).lastPathComponent as DefaultMutableTreeNode
      val oldParent = oldNode.parent as DefaultMutableTreeNode
      val newParent = newNode.parent as DefaultMutableTreeNode
      val oldKind = getKind(oldNode)
      val newKind = getKind(newNode)
      val oldType = getType(oldNode)
      val newType = getType(newNode)
      if (oldParent === newParent) {
        if (oldNode.previousSibling === newNode && position == BELOW) {
          return false
        }
        if (oldNode.nextSibling === newNode && position == ABOVE) {
          return false
        }
      }

      when {
        oldType == null -> return false
        oldType !== newType -> {
          val typeNode = getConfigurationTypeNode(oldType)
          if (getKind(oldParent) == FOLDER && typeNode != null && typeNode.nextSibling === newNode && position == ABOVE) {
            return true
          }
          return getKind(oldParent) == CONFIGURATION_TYPE &&
                 oldKind == FOLDER &&
                 typeNode != null &&
                 typeNode.nextSibling === newNode &&
                 position == ABOVE &&
                 oldParent.lastChild !== oldNode &&
                 getKind(oldParent.lastChild as DefaultMutableTreeNode) == FOLDER
        }

        newParent === oldNode || oldParent === newNode -> return false
        oldKind == FOLDER && newKind != FOLDER -> return newKind.isConfiguration &&
                                                         position == ABOVE &&
                                                         getKind(newParent) == CONFIGURATION_TYPE &&
                                                         newIndex > 1 &&
                                                         getKind(tree.getPathForRow(newIndex - 1).parentPath.lastPathComponent as DefaultMutableTreeNode) == FOLDER
        !oldKind.supportsDnD() || !newKind.supportsDnD() -> return false
        oldKind.isConfiguration && newKind == FOLDER && position == ABOVE -> return false
        oldKind == TEMPORARY_CONFIGURATION && newKind == CONFIGURATION && position == ABOVE -> return false
        oldKind == CONFIGURATION && newKind == TEMPORARY_CONFIGURATION && position == BELOW -> return false
        oldKind == CONFIGURATION && newKind == TEMPORARY_CONFIGURATION && position == ABOVE -> return newNode.previousSibling == null ||
                                                                                                      getKind(newNode.previousSibling) == CONFIGURATION ||
                                                                                                      getKind(newNode.previousSibling) == FOLDER
        oldKind == TEMPORARY_CONFIGURATION && newKind == CONFIGURATION && position == BELOW -> return newNode.nextSibling == null || getKind(newNode.nextSibling) == TEMPORARY_CONFIGURATION
        oldParent === newParent -> //Same parent
          if (oldKind.isConfiguration && newKind.isConfiguration) {
            return oldKind == newKind//both are temporary or saved
          }
          else if (oldKind == FOLDER) {
            return !tree.isExpanded(newIndex) || position == ABOVE
          }
      }
      return true
    }

    override fun isDropInto(component: JComponent, oldIndex: Int, newIndex: Int): Boolean {
      val oldPath = tree.getPathForRow(oldIndex)
      val newPath = tree.getPathForRow(newIndex)
      if (oldPath == null || newPath == null) {
        return false
      }
      val oldNode = oldPath.lastPathComponent as DefaultMutableTreeNode
      val newNode = newPath.lastPathComponent as DefaultMutableTreeNode
      return getKind(oldNode).isConfiguration && getKind(newNode) == FOLDER
    }

    override fun drop(oldIndex: Int, newIndex: Int, position: RowsDnDSupport.RefinedDropSupport.Position) {
      val oldNode = tree.getPathForRow(oldIndex).lastPathComponent as DefaultMutableTreeNode
      val newNode = tree.getPathForRow(newIndex).lastPathComponent as DefaultMutableTreeNode
      var newParent = newNode.parent as DefaultMutableTreeNode
      val oldKind = getKind(oldNode)
      val wasExpanded = tree.isExpanded(TreePath(oldNode.path))
      if (isDropInto(tree, oldIndex, newIndex)) { //Drop in folder
        removeNodeFromParent(oldNode)
        var index = newNode.childCount
        if (oldKind.isConfiguration) {
          var middleIndex = newNode.childCount
          for (i in 0 until newNode.childCount) {
            if (getKind(newNode.getChildAt(i) as DefaultMutableTreeNode) == TEMPORARY_CONFIGURATION) {
              middleIndex = i//index of first temporary configuration in target folder
              break
            }
          }
          if (position != INTO) {
            if (oldIndex < newIndex) {
              index = if (oldKind == CONFIGURATION) 0 else middleIndex
            }
            else {
              index = if (oldKind == CONFIGURATION) middleIndex else newNode.childCount
            }
          }
          else {
            index = if (oldKind == TEMPORARY_CONFIGURATION) newNode.childCount else middleIndex
          }
        }
        insertNodeInto(oldNode, newNode, index)
        tree.expandPath(TreePath(newNode.path))
      }
      else {
        val type = getType(oldNode)!!
        removeNodeFromParent(oldNode)
        var index: Int
        if (type !== getType(newNode)) {
          val typeNode = getConfigurationTypeNode(type)!!
          newParent = typeNode
          index = newParent.childCount
        }
        else {
          index = newParent.getIndex(newNode)
          if (position == BELOW) {
            index++
          }
        }
        insertNodeInto(oldNode, newParent, index)
      }
      val treePath = TreePath(oldNode.path)
      tree.selectionPath = treePath
      if (wasExpanded) {
        tree.expandPath(treePath)
      }
    }

    override fun insertNodeInto(newChild: MutableTreeNode, parent: MutableTreeNode, index: Int) {
      super.insertNodeInto(newChild, parent, index)
      if (!getKind(newChild as DefaultMutableTreeNode).isConfiguration) {
        return
      }

      val userObject = getSafeUserObject(newChild)
      val newFolderName = if (getKind(parent as DefaultMutableTreeNode) == FOLDER) parent.userObject as String else null
      if (userObject is SingleConfigurationConfigurable<*>) {
        userObject.folderName = newFolderName
      }
    }

    override fun reload(node: TreeNode?) {
      super.reload(node)
      val userObject = (node as DefaultMutableTreeNode).userObject
      if (userObject is String) {
        for (i in 0 until node.childCount) {
          val safeUserObject = getSafeUserObject(node.getChildAt(i) as DefaultMutableTreeNode)
          if (safeUserObject is SingleConfigurationConfigurable<*>) {
            safeUserObject.folderName = userObject
          }
        }
      }
    }

    private fun getType(treeNode: DefaultMutableTreeNode?): ConfigurationType? {
      val userObject = treeNode?.userObject ?: return null
      return when (userObject) {
        is SingleConfigurationConfigurable<*> -> userObject.configuration.type
        is RunnerAndConfigurationSettings -> userObject.type
        is ConfigurationType -> userObject
        else -> if (treeNode.parent is DefaultMutableTreeNode) getType(treeNode.parent as DefaultMutableTreeNode) else null
      }
    }
  }
}

private fun canRunConfiguration(configuration: SingleConfigurationConfigurable<RunConfiguration>?, executor: Executor): Boolean {
  try {
    return configuration != null && RunManagerImpl.canRunConfiguration(configuration.snapshot!!, executor)
  }
  catch (e: ConfigurationException) {
    return false
  }
}

private fun createUniqueName(typeNode: DefaultMutableTreeNode, baseName: String?, vararg kinds: RunConfigurableNodeKind): String {
  val str = baseName ?: ExecutionBundle.message("run.configuration.unnamed.name.prefix")
  val configurationNodes = ArrayList<DefaultMutableTreeNode>()
  collectNodesRecursively(typeNode, configurationNodes, *kinds)
  val currentNames = ArrayList<String>()
  for (node in configurationNodes) {
    val userObject = node.userObject
    when (userObject) {
      is SingleConfigurationConfigurable<*> -> currentNames.add(userObject.nameText)
      is RunnerAndConfigurationSettingsImpl -> currentNames.add((userObject as RunnerAndConfigurationSettings).name)
      is String -> currentNames.add(userObject)
    }
  }
  return RunManager.suggestUniqueName(str, currentNames)
}

private fun getType(_node: DefaultMutableTreeNode?): ConfigurationType? {
  var node = _node
  while (node != null) {
    (node.userObject as? ConfigurationType)?.let {
      return it
    }
    node = node.parent as DefaultMutableTreeNode
  }
  return null
}

private fun getSettings(treeNode: DefaultMutableTreeNode?): RunnerAndConfigurationSettings? {
  if (treeNode == null) {
    return null
  }

  val settings: RunnerAndConfigurationSettings? = null
  if (treeNode.userObject is SingleConfigurationConfigurable<*>) {
    return (treeNode.userObject as SingleConfigurationConfigurable<*>).settings as RunnerAndConfigurationSettings
  }
  if (treeNode.userObject is RunnerAndConfigurationSettings) {
    return treeNode.userObject as RunnerAndConfigurationSettings
  }
  return settings
}
