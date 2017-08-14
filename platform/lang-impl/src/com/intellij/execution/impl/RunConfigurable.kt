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
import com.intellij.execution.impl.RunConfigurable.NodeKind.*
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
  val myRoot = DefaultMutableTreeNode("Root")
  val myTreeModel = MyTreeModel(myRoot)
  val myTree = Tree(myTreeModel)
  private val myRightPanel = JPanel(BorderLayout())
  private val mySplitter = JBSplitter("RunConfigurable.dividerProportion", 0.3f)
  private var myWholePanel: JPanel? = null
  private var mySelectedConfigurable: Configurable? = null
  private val myRecentsLimit = JTextField("5", 2)
  private val myConfirmation = JCheckBox(ExecutionBundle.message("rerun.confirmation.checkbox"), true)
  private val myAdditionalSettings = ArrayList<Pair<UnnamedConfigurable, JComponent>>()
  private val myStoredComponents = HashMap<ConfigurationFactory, Configurable>()
  private var myToolbarDecorator: ToolbarDecorator? = null
  private var isFolderCreating: Boolean = false
  private val myAddAction = MyToolbarAddAction()

  override fun getDisplayName(): String {
    return ExecutionBundle.message("run.configurable.display.name")
  }

  private fun initTree() {
    myTree.isRootVisible = false
    myTree.showsRootHandles = true
    UIUtil.setLineStyleAngled(myTree)
    TreeUtil.installActions(myTree)
    TreeSpeedSearch(myTree) { o ->
      val node = o.lastPathComponent as DefaultMutableTreeNode
      val userObject = node.userObject
      if (userObject is RunnerAndConfigurationSettingsImpl) {
        return@TreeSpeedSearch userObject.name
      }
      else if (userObject is SingleConfigurationConfigurable<*>) {
        return@TreeSpeedSearch userObject.nameText
      }
      else {
        if (userObject is ConfigurationType) {
          return@TreeSpeedSearch userObject.displayName
        }
        else if (userObject is String) {
          return@TreeSpeedSearch userObject
        }
      }
      o.toString()
    }
    myTree.cellRenderer = object : ColoredTreeCellRenderer() {
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
      val configurations = manager.getConfigurationSettingsList(type)
      if (!configurations.isEmpty()) {
        val typeNode = DefaultMutableTreeNode(type)
        myRoot.add(typeNode)
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
    if (defaults.childCount > 0) myRoot.add(defaults)

    myTree.addTreeSelectionListener {
      val selectionPath = myTree.selectionPath
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
    myTree.registerKeyboardAction({ clickDefaultButton() }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED)
    sortTopLevelBranches()
    (myTree.model as DefaultTreeModel).reload()
  }

  fun selectConfigurableOnShow(option: Boolean): RunConfigurable {
    if (!option) return this
    SwingUtilities.invokeLater {
      if (isDisposed) return@invokeLater

      myTree.requestFocusInWindow()
      val settings = runManager.selectedConfiguration
      if (settings != null) {
        if (selectConfiguration(settings.configuration)) {
          return@invokeLater
        }
      }
      else {
        mySelectedConfigurable = null
      }
      //TreeUtil.selectInTree(defaults, true, myTree);
      drawPressAddButtonMessage(null)
    }
    return this
  }

  private fun selectConfiguration(configuration: RunConfiguration): Boolean {
    val enumeration = myRoot.breadthFirstEnumeration()
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
          TreeUtil.selectInTree(node, true, myTree)
          return true
        }
      }
    }
    return false
  }

  private fun showTemplateConfigurable(factory: ConfigurationFactory) {
    var configurable: Configurable? = myStoredComponents[factory]
    if (configurable == null) {
      configurable = TemplateConfigurable(RunManagerImpl.getInstanceImpl(myProject).getConfigurationTemplate(factory))
      myStoredComponents.put(factory, configurable)
      configurable.reset()
    }
    updateRightPanel(configurable)
  }

  private fun showFolderField(node: DefaultMutableTreeNode, folderName: String) {
    myRightPanel.removeAll()
    val p = JPanel(MigLayout("ins ${myToolbarDecorator!!.actionsPanel.height} 5 0 0, flowx"))
    val textField = JTextField(folderName)
    textField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        node.userObject = textField.text
        myTreeModel.reload(node)
      }
    })
    textField.addActionListener { getGlobalInstance().doWhenFocusSettlesDown { getGlobalInstance().requestFocus(myTree, true) } }
    p.add(JLabel("Folder name:"), "gapright 5")
    p.add(textField, "pushx, growx, wrap")
    p.add(JLabel(ExecutionBundle.message("run.configuration.rename.folder.disclaimer")), "gaptop 5, spanx 2")

    myRightPanel.add(p)
    myRightPanel.revalidate()
    myRightPanel.repaint()
    if (isFolderCreating) {
      textField.selectAll()
      getGlobalInstance().doWhenFocusSettlesDown { getGlobalInstance().requestFocus(textField, true) }
    }
  }

  private fun getSafeUserObject(node: DefaultMutableTreeNode): Any {
    val userObject = node.userObject
    if (userObject is RunnerAndConfigurationSettingsImpl) {
      val configurationConfigurable = SingleConfigurationConfigurable.editSettings<RunConfiguration>(
        userObject as RunnerAndConfigurationSettings, null)
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
    myRightPanel.removeAll()
    mySelectedConfigurable = configurable

    val configurableComponent = configurable.createComponent()
    myRightPanel.add(BorderLayout.CENTER, configurableComponent)
    if (configurable is SingleConfigurationConfigurable<*>) {
      myRightPanel.add(configurable.validationComponent, BorderLayout.SOUTH)
      ApplicationManager.getApplication().invokeLater { configurable.updateWarning() }
      if (configurableComponent != null) {
        val dataProvider = DataManager.getDataProvider(configurableComponent)
        if (dataProvider != null) {
          DataManager.registerDataProvider(myRightPanel, dataProvider)
        }
      }
    }

    setupDialogBounds()
  }

  private fun sortTopLevelBranches() {
    val expandedPaths = TreeUtil.collectExpandedPaths(myTree)
    TreeUtil.sortRecursively(myRoot) { o1, o2 ->
      val userObject1 = o1.userObject
      val userObject2 = o2.userObject
      if (userObject1 is ConfigurationType && userObject2 is ConfigurationType) {
        return@sortRecursively(userObject1).displayName.compareTo(
          userObject2.displayName)
      }
      else if (userObject1 === DEFAULTS && userObject2 is ConfigurationType) {
        return@sortRecursively 1
      }
      else if (userObject2 === DEFAULTS && userObject1 is ConfigurationType) {
        return@sortRecursively - 1
      }

      0
    }
    TreeUtil.restoreExpandedPaths(myTree, expandedPaths)
  }

  private fun update() {
    updateDialog()
    val selectionPath = myTree.selectionPath
    if (selectionPath != null) {
      val node = selectionPath.lastPathComponent as DefaultMutableTreeNode
      myTreeModel.reload(node)
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

    val addIcon = ActionLink("", IconUtil.getAddIcon(), myAddAction)
    addIcon.border = EmptyBorder(0, 0, 0, 5)
    panel.add(addIcon)

    val configurationTypeDescription = if (configurationType != null)
      configurationType.configurationTypeDescription
    else
      ExecutionBundle.message("run.configuration.default.type.description")
    panel.add(JLabel(ExecutionBundle.message("empty.run.configuration.panel.text.label3", configurationTypeDescription)))
    val scrollPane = ScrollPaneFactory.createScrollPane(panel, true)

    myRightPanel.removeAll()
    myRightPanel.add(scrollPane, BorderLayout.CENTER)
    if (configurationType == null) {
      val settingsPanel = JPanel(GridBagLayout())
      val grid = GridBag().setDefaultAnchor(GridBagConstraints.NORTHWEST)

      for (each in myAdditionalSettings) {
        settingsPanel.add(each.second, grid.nextLine().next())
      }
      settingsPanel.add(createSettingsPanel(), grid.nextLine().next())

      val wrapper = JPanel(BorderLayout())
      wrapper.add(settingsPanel, BorderLayout.WEST)
      wrapper.add(Box.createGlue(), BorderLayout.CENTER)

      myRightPanel.add(wrapper, BorderLayout.SOUTH)
    }
    myRightPanel.revalidate()
    myRightPanel.repaint()
  }

  private fun createLeftPanel(): JPanel {
    initTree()
    val removeAction = MyRemoveAction()
    val moveUpAction = MyMoveAction(ExecutionBundle.message("move.up.action.name"), null, IconUtil.getMoveUpIcon(), -1)
    val moveDownAction = MyMoveAction(ExecutionBundle.message("move.down.action.name"), null, IconUtil.getMoveDownIcon(), 1)
    myToolbarDecorator = ToolbarDecorator.createDecorator(myTree).setAsUsualTopToolbar()
      .setAddAction(myAddAction).setAddActionName(ExecutionBundle.message("add.new.run.configuration.action2.name"))
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
    return myToolbarDecorator!!.createPanel()
  }

  private fun createSettingsPanel(): JPanel {
    val bottomPanel = JPanel(GridBagLayout())
    val g = GridBag()

    bottomPanel.add(myConfirmation, g.nextLine().coverLine())
    bottomPanel.add(create(myRecentsLimit, ExecutionBundle.message("temporary.configurations.limit"), BorderLayout.WEST),
                    g.nextLine().insets(JBUI.insets(10, 0, 0, 0)).anchor(GridBagConstraints.WEST))

    myRecentsLimit.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        isModified = !Comparing.equal(myRecentsLimit.text, myRecentsLimit.getClientProperty(INITIAL_VALUE_KEY))
      }
    })
    myConfirmation.addChangeListener {
      isModified = !Comparing.equal(myConfirmation.isSelected, myConfirmation.getClientProperty(INITIAL_VALUE_KEY))
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
      myAdditionalSettings.add(Pair.create(configurable, configurable.createComponent()))
    }

    myWholePanel = JPanel(BorderLayout())
    DataManager.registerDataProvider(myWholePanel!!) { dataId ->
      if (RunConfigurationSelector.KEY.name == dataId)
        RunConfigurationSelector { configuration -> selectConfiguration(configuration) }
      else
        null
    }

    mySplitter.firstComponent = createLeftPanel()
    mySplitter.setHonorComponentsMinimumSize(true)
    mySplitter.secondComponent = myRightPanel
    myWholePanel!!.add(mySplitter, BorderLayout.CENTER)

    updateDialog()

    val d = myWholePanel!!.preferredSize
    d.width = Math.max(d.width, 800)
    d.height = Math.max(d.height, 600)
    myWholePanel!!.preferredSize = d

    return myWholePanel
  }

  override fun reset() {
    val manager = runManager
    val config = manager.config
    myRecentsLimit.text = Integer.toString(config.recentsLimit)
    myRecentsLimit.putClientProperty(INITIAL_VALUE_KEY, myRecentsLimit.text)
    myConfirmation.isSelected = config.isRestartRequiresConfirmation
    myConfirmation.putClientProperty(INITIAL_VALUE_KEY, myConfirmation.isSelected)

    for (each in myAdditionalSettings) {
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
      for (i in 0 until myRoot.childCount) {
        val node = myRoot.getChildAt(i) as DefaultMutableTreeNode
        val userObject = node.userObject
        if (userObject is ConfigurationType) {
          for (bean in applyByType(node, userObject, selectedSettings)) {
            settingsToOrder.put(bean.settings, order++)
            toDeleteSettings.remove(bean.settings)
          }
        }
      }
      manager.removeConfigurations(toDeleteSettings)

      val recentLimit = Math.max(RunManagerConfig.MIN_RECENT_LIMIT, StringUtil.parseInt(myRecentsLimit.text, 0))
      if (manager.config.recentsLimit != recentLimit) {
        manager.config.recentsLimit = recentLimit
        manager.checkRecentsLimit()
      }
      manager.config.isRestartRequiresConfirmation = myConfirmation.isSelected

      for (configurable in myStoredComponents.values) {
        if (configurable.isModified) {
          configurable.apply()
        }
      }

      for (each in myAdditionalSettings) {
        each.first.apply()
      }

      manager.setOrder(Comparator.comparingInt(ToIntFunction<RunnerAndConfigurationSettings> { settingsToOrder.get(it) }))
    }
    finally {
      manager.fireEndUpdate()
    }
    updateActiveConfigurationFromSelected()
    isModified = false
    myTree.repaint()
  }

  fun updateActiveConfigurationFromSelected() {
    if (mySelectedConfigurable != null && mySelectedConfigurable is SingleConfigurationConfigurable<*>) {
      runManager.selectedConfiguration = (mySelectedConfigurable as SingleConfigurationConfigurable<*>).settings as RunnerAndConfigurationSettings
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
          TreeUtil.selectNode(myTree, node)
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
        TreeUtil.selectNode(myTree, node)
        throw ConfigurationException("Folder name shouldn't be empty")
      }
      if (!names.add(folderName)) {
        TreeUtil.selectNode(myTree, node)
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
    for (i in 0 until myRoot.childCount) {
      val node = myRoot.getChildAt(i) as DefaultMutableTreeNode
      if (node.userObject === type) return node
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
          TreeUtil.selectNode(myTree, node)
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
    for (i in 0 until myRoot.childCount) {
      val typeNode = myRoot.getChildAt(i) as DefaultMutableTreeNode
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

    for (configurable in myStoredComponents.values) {
      if (configurable.isModified) return true
    }

    for (each in myAdditionalSettings) {
      if (each.first.isModified) return true
    }

    return false
  }

  override fun disposeUIResources() {
    Disposer.dispose(this)
  }

  override fun dispose() {
    isDisposed = true
    for (configurable in myStoredComponents.values) {
      configurable.disposeUIResources()
    }
    myStoredComponents.clear()

    for (each in myAdditionalSettings) {
      each.first.disposeUIResources()
    }

    TreeUtil.traverseDepth(myRoot) { node ->
      if (node is DefaultMutableTreeNode) {
        val userObject = node.userObject
        (userObject as? SingleConfigurationConfigurable<*>)?.disposeUIResources()
      }
      true
    }
    myRightPanel.removeAll()
    mySplitter.dispose()
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
    SwingUtilities.invokeLater { UIUtil.setupEnclosingDialogBounds(myWholePanel!!) }
  }

  private val selectedConfiguration: SingleConfigurationConfigurable<RunConfiguration>?
    get() {
      val selectionPath = myTree.selectionPath
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
      val selectionPath = myTree.selectionPath
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

  private fun getNode(row: Int): DefaultMutableTreeNode {
    return myTree.getPathForRow(row).lastPathComponent as DefaultMutableTreeNode
  }

  fun getAvailableDropPosition(direction: Int): Trinity<Int, Int, RowsDnDSupport.RefinedDropSupport.Position>? {
    val rows = myTree.selectionRows
    if (rows == null || rows.size != 1) {
      return null
    }
    val oldIndex = rows[0]
    var newIndex = oldIndex + direction

    if (!getKind(myTree.getPathForRow(oldIndex).lastPathComponent as DefaultMutableTreeNode).supportsDnD())
      return null

    while (newIndex > 0 && newIndex < myTree.rowCount) {
      val targetPath = myTree.getPathForRow(newIndex)
      val allowInto = getKind(targetPath.lastPathComponent as DefaultMutableTreeNode) == FOLDER && !myTree.isExpanded(targetPath)
      val position = if (allowInto && myTreeModel.isDropInto(myTree, oldIndex, newIndex))
        INTO
      else if (direction > 0) BELOW else ABOVE
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
        if (myTreeModel.canDrop(oldIndex, newIndex, copy)) {
          return Trinity.create(oldIndex, newIndex, copy)
        }
      }
      if (myTreeModel.canDrop(oldIndex, newIndex, position)) {
        return Trinity.create(oldIndex, newIndex, position)
      }

      if (position == BELOW && newIndex < myTree.rowCount - 1 && myTreeModel.canDrop(oldIndex, newIndex + 1, ABOVE)) {
        return Trinity.create(oldIndex, newIndex + 1, ABOVE)
      }
      if (position == ABOVE && newIndex > 1 && myTreeModel.canDrop(oldIndex, newIndex - 1, BELOW)) {
        return Trinity.create(oldIndex, newIndex - 1, BELOW)
      }
      if (position == BELOW && myTreeModel.canDrop(oldIndex, newIndex, ABOVE)) {
        return Trinity.create(oldIndex, newIndex, ABOVE)
      }
      if (position == ABOVE && myTreeModel.canDrop(oldIndex, newIndex, BELOW)) {
        return Trinity.create(oldIndex, newIndex, BELOW)
      }
      newIndex += direction
    }
    return null
  }

  private fun createNewConfiguration(settings: RunnerAndConfigurationSettings,
                                     node: DefaultMutableTreeNode?,
                                     selectedNode: DefaultMutableTreeNode?): SingleConfigurationConfigurable<RunConfiguration> {
    val configurationConfigurable = SingleConfigurationConfigurable.editSettings<RunConfiguration>(settings, null)
    installUpdateListeners(configurationConfigurable)
    val nodeToAdd = DefaultMutableTreeNode(configurationConfigurable)
    myTreeModel.insertNodeInto(nodeToAdd, node!!, if (selectedNode != null) node.getIndex(selectedNode) + 1 else node.childCount)
    TreeUtil.selectNode(myTree, nodeToAdd)
    return configurationConfigurable
  }

  fun createNewConfiguration(factory: ConfigurationFactory): SingleConfigurationConfigurable<RunConfiguration> {
    var node: DefaultMutableTreeNode
    var selectedNode: DefaultMutableTreeNode? = null
    val selectionPath = myTree.selectionPath
    if (selectionPath != null) {
      selectedNode = selectionPath.lastPathComponent as DefaultMutableTreeNode
    }
    var typeNode = getConfigurationTypeNode(factory.type)
    if (typeNode == null) {
      typeNode = DefaultMutableTreeNode(factory.type)
      myRoot.add(typeNode)
      sortTopLevelBranches()
      (myTree.model as DefaultTreeModel).reload()
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
      registerCustomShortcutSet(CommonShortcuts.INSERT, myTree)
    }

    override fun actionPerformed(e: AnActionEvent) {
      showAddPopup(true)
    }

    override fun run(button: AnActionButton) {
      showAddPopup(true)
    }

    private fun showAddPopup(showApplicableTypesOnly: Boolean) {
      val allTypes = runManager.configurationFactoriesWithoutUnknown
      var configurationTypes: List<ConfigurationType?> = getTypesToShow(showApplicableTypesOnly, allTypes)
      Collections.sort(configurationTypes) { type1, type2 -> type1!!.displayName.compareTo(type2!!.displayName, ignoreCase = true) }
      val hiddenCount = allTypes.size - configurationTypes.size
      if (hiddenCount > 0) {
        val list = configurationTypes.toMutableList()
        list.add(null)
        configurationTypes = list
      }

      val popup = NewRunConfigurationPopup.createAddPopup(configurationTypes, hiddenCount.toString() + " items more (irrelevant)...",
                                                          { factory -> createNewConfiguration(factory) }, selectedConfigurationType,
                                                          { showAddPopup(false) }, true)
      //new TreeSpeedSearch(myTree);
      popup.showUnderneathOf(myToolbarDecorator!!.actionsPanel)
    }

    private fun getTypesToShow(showApplicableTypesOnly: Boolean, allTypes: List<ConfigurationType>): List<ConfigurationType> {
      if (showApplicableTypesOnly) {
        val applicableTypes = ArrayList<ConfigurationType>()
        for (type in allTypes) {
          if (isApplicable(type)) {
            applicableTypes.add(type)
          }
        }
        if (applicableTypes.size < allTypes.size - 3) {
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
      registerCustomShortcutSet(CommonShortcuts.getDelete(), myTree)
    }

    override fun actionPerformed(e: AnActionEvent) {
      doRemove()
    }

    override fun run(button: AnActionButton) {
      doRemove()
    }

    private fun doRemove() {
      val selections = myTree.selectionPaths
      myTree.clearSelection()

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
        myTreeModel.removeNodeFromParent(node)
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
              myTreeModel.insertNodeInto(child, parent, confIndex)
          }
          confIndex = parent.childCount
          for (i in 0 until parent.childCount) {
            if (getKind(parent.getChildAt(i) as DefaultMutableTreeNode) == TEMPORARY_CONFIGURATION) {
              confIndex = i
              break
            }
          }
          for (child in children) {
            if (getKind(child) == TEMPORARY_CONFIGURATION)
              myTreeModel.insertNodeInto(child, parent, confIndex)
          }
        }

        if (parent.childCount == 0 && parent.userObject is ConfigurationType) {
          changedParents.remove(parent)
          wasRootChanged = true

          nodeIndexToSelect = myRoot.getIndex(parent)
          nodeIndexToSelect = Math.max(0, nodeIndexToSelect - 1)
          parentToSelect = myRoot
          parent.removeFromParent()
        }
      }

      if (wasRootChanged) {
        (myTree.model as DefaultTreeModel).reload()
      }
      else {
        for (each in changedParents) {
          myTreeModel.reload(each)
          myTree.expandPath(TreePath(each))
        }
      }

      mySelectedConfigurable = null
      if (myRoot.childCount == 0) {
        drawPressAddButtonMessage(null)
      }
      else {
        if (parentToSelect!!.childCount > 0) {
          val nodeToSelect = if (nodeIndexToSelect < parentToSelect.childCount)
            parentToSelect.getChildAt(nodeIndexToSelect)
          else
            parentToSelect.getChildAt(nodeIndexToSelect - 1)
          TreeUtil.selectInTree(nodeToSelect as DefaultMutableTreeNode, true, myTree)
        }
      }
    }


    override fun update(e: AnActionEvent) {
      val enabled = isEnabled(e)
      e.presentation.isEnabled = enabled
    }

    override fun isEnabled(e: AnActionEvent): Boolean {
      var enabled = false
      val selections = myTree.selectionPaths
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
      registerCustomShortcutSet(action.shortcutSet, myTree)
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
        Messages.showErrorDialog(myToolbarDecorator!!.actionsPanel, e1.message, e1.title)
      }

    }

    override fun update(e: AnActionEvent) {
      val configuration = selectedConfiguration
      e.presentation.isEnabled = configuration != null && configuration.configuration !is UnknownRunConfiguration
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
      myTree.repaint()
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
    val selectionPath = myTree.selectionPath ?: return 0
    val treeNode = selectionPath.lastPathComponent as DefaultMutableTreeNode
    val selectedSettings = getSettings(treeNode)
    if (selectedSettings == null || selectedSettings.isTemporary)
      return 0
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
      TreeUtil.moveSelectedRow(myTree, -1)
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
      val dropPosition = getAvailableDropPosition(myDirection)
      if (dropPosition != null) {
        myTreeModel.drop(dropPosition.first, dropPosition.second, dropPosition.third)
      }
    }

    override fun run(button: AnActionButton) {
      doMove()
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = isEnabled(e)
    }

    override fun isEnabled(e: AnActionEvent): Boolean {
      return getAvailableDropPosition(myDirection) != null
    }
  }

  private inner class MyEditDefaultsAction : AnAction(ExecutionBundle.message("run.configuration.edit.default.configuration.settings.text"),
                                                      ExecutionBundle.message(
                                                        "run.configuration.edit.default.configuration.settings.description"),
                                                      AllIcons.General.Settings) {

    override fun actionPerformed(e: AnActionEvent) {
      var defaults = TreeUtil.findNodeWithObject(DEFAULTS, myTree.model, myRoot)
      if (defaults != null) {
        val configurationType = selectedConfigurationType
        if (configurationType != null) {
          defaults = TreeUtil.findNodeWithObject(configurationType, myTree.model, defaults)
        }
        val defaultsNode = defaults as DefaultMutableTreeNode? ?: return
        val path = TreeUtil.getPath(myRoot, defaultsNode)
        myTree.expandPath(path)
        TreeUtil.selectInTree(defaultsNode, true, myTree)
        myTree.scrollPathToVisible(path)
      }
    }

    override fun update(e: AnActionEvent) {
      var isEnabled = TreeUtil.findNodeWithObject(DEFAULTS, myTree.model, myRoot) != null
      val path = myTree.selectionPath
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
                                                      ExecutionBundle.message(
                                                                              "run.configuration.create.folder.description"),
                                                      AllIcons.Nodes.Folder) {

    override fun actionPerformed(e: AnActionEvent) {
      val type = selectedConfigurationType ?: return
      val selectedNodes = selectedNodes
      val typeNode = getConfigurationTypeNode(type) ?: return
      val folderName = createUniqueName(typeNode, "New Folder", FOLDER)
      val folders = ArrayList<DefaultMutableTreeNode>()
      collectNodesRecursively(getConfigurationTypeNode(type)!!, folders, FOLDER)
      val folderNode = DefaultMutableTreeNode(folderName)
      myTreeModel.insertNodeInto(folderNode, typeNode, folders.size)
      isFolderCreating = true
      try {
        for (node in selectedNodes) {
          val folderRow = myTree.getRowForPath(TreePath(folderNode.path))
          val rowForPath = myTree.getRowForPath(TreePath(node.path))
          if (getKind(node).isConfiguration && myTreeModel.canDrop(rowForPath, folderRow, INTO)) {
            myTreeModel.drop(rowForPath, folderRow, INTO)
          }
        }
        myTree.selectionPath = TreePath(folderNode.path)
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
        if (kind.isConfiguration || kind == CONFIGURATION_TYPE && node.parent === myRoot || kind == FOLDER) {
          isEnabled = true
        }
        if (kind.isConfiguration) {
          toMove = true
        }
      }
      e.presentation.text = ExecutionBundle.message("run.configuration.create.folder.description" + if (toMove) ".move" else "")
      e.presentation.isEnabled = isEnabled
    }
  }

  private inner class MySortFolderAction : AnAction(ExecutionBundle.message("run.configuration.sort.folder.text"),
                                                                          ExecutionBundle.message(
                                                                            "run.configuration.sort.folder.description"),
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
      if (kind1 == TEMPORARY_CONFIGURATION) {
        return if (kind2 == TEMPORARY_CONFIGURATION) name1.compareTo(name2) else 1
      }
      return if (kind2 == TEMPORARY_CONFIGURATION) {
        -1
      }
      else name1.compareTo(name2)
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
        Collections.sort(children, this)
        for (child in children) {
          folderNode.add(child)
        }
        myTreeModel.nodeStructureChanged(folderNode)
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
    get() = myTree.getSelectedNodes(DefaultMutableTreeNode::class.java, null)

  private val selectedNode: DefaultMutableTreeNode?
    get() = myTree.getSelectedNodes(DefaultMutableTreeNode::class.java, null).firstOrNull()

  private val selectedSettings: RunnerAndConfigurationSettings?
    get() {
      val selectionPath = myTree.selectionPath ?: return null
      return getSettings(selectionPath.lastPathComponent as DefaultMutableTreeNode)
    }

  interface RunDialogBase {
    fun setOKActionEnabled(isEnabled: Boolean)

    val executor: Executor?

    fun setTitle(title: String)

    fun clickDefaultButton()
  }

  enum class NodeKind {
    CONFIGURATION_TYPE, FOLDER, CONFIGURATION, TEMPORARY_CONFIGURATION, UNKNOWN;

    fun supportsDnD(): Boolean {
      return this == FOLDER || this == CONFIGURATION || this == TEMPORARY_CONFIGURATION
    }

    val isConfiguration: Boolean
      get() = (this == CONFIGURATION) or (this == TEMPORARY_CONFIGURATION)
  }

  inner class MyTreeModel constructor(root: TreeNode) : DefaultTreeModel(
    root), EditableModel, RowsDnDSupport.RefinedDropSupport {

    override fun addRow() {}

    override fun removeRow(index: Int) {}

    override fun exchangeRows(oldIndex: Int, newIndex: Int) {
      //Do nothing, use drop() instead
    }

    override fun canExchangeRows(oldIndex: Int, newIndex: Int): Boolean {
      return false//Legacy, use canDrop() instead
    }

    override fun canDrop(oldIndex: Int, newIndex: Int, position: RowsDnDSupport.RefinedDropSupport.Position): Boolean {
      if (myTree.rowCount <= oldIndex || myTree.rowCount <= newIndex || oldIndex < 0 || newIndex < 0) {
        return false
      }
      val oldNode = myTree.getPathForRow(oldIndex).lastPathComponent as DefaultMutableTreeNode
      val newNode = myTree.getPathForRow(newIndex).lastPathComponent as DefaultMutableTreeNode
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
      if (oldType == null)
        return false
      if (oldType !== newType) {
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
      if (newParent === oldNode || oldParent === newNode)
        return false
      if (oldKind == FOLDER && newKind != FOLDER) {
        return newKind.isConfiguration &&
               position == ABOVE &&
               getKind(newParent) == CONFIGURATION_TYPE &&
               newIndex > 1 &&
               getKind(myTree.getPathForRow(newIndex - 1).parentPath.lastPathComponent as DefaultMutableTreeNode) == FOLDER
      }
      if (!oldKind.supportsDnD() || !newKind.supportsDnD()) {
        return false
      }
      if (oldKind.isConfiguration && newKind == FOLDER && position == ABOVE)
        return false
      if (oldKind == TEMPORARY_CONFIGURATION && newKind == CONFIGURATION && position == ABOVE)
        return false
      if (oldKind == CONFIGURATION && newKind == TEMPORARY_CONFIGURATION && position == BELOW)
        return false
      if (oldKind == CONFIGURATION && newKind == TEMPORARY_CONFIGURATION && position == ABOVE) {
        return newNode.previousSibling == null ||
               getKind(newNode.previousSibling) == CONFIGURATION ||
               getKind(newNode.previousSibling) == FOLDER
      }
      if (oldKind == TEMPORARY_CONFIGURATION && newKind == CONFIGURATION && position == BELOW)
        return newNode.nextSibling == null || getKind(newNode.nextSibling) == TEMPORARY_CONFIGURATION
      if (oldParent === newParent) { //Same parent
        if (oldKind.isConfiguration && newKind.isConfiguration) {
          return oldKind == newKind//both are temporary or saved
        }
        else if (oldKind == FOLDER) {
          return !myTree.isExpanded(newIndex) || position == ABOVE
        }
      }
      return true
    }

    override fun isDropInto(component: JComponent, oldIndex: Int, newIndex: Int): Boolean {
      val oldPath = myTree.getPathForRow(oldIndex)
      val newPath = myTree.getPathForRow(newIndex)
      if (oldPath == null || newPath == null) {
        return false
      }
      val oldNode = oldPath.lastPathComponent as DefaultMutableTreeNode
      val newNode = newPath.lastPathComponent as DefaultMutableTreeNode
      return getKind(oldNode).isConfiguration && getKind(newNode) == FOLDER
    }

    override fun drop(oldIndex: Int, newIndex: Int, position: RowsDnDSupport.RefinedDropSupport.Position) {
      val oldNode = myTree.getPathForRow(oldIndex).lastPathComponent as DefaultMutableTreeNode
      val newNode = myTree.getPathForRow(newIndex).lastPathComponent as DefaultMutableTreeNode
      var newParent = newNode.parent as DefaultMutableTreeNode
      val oldKind = getKind(oldNode)
      val wasExpanded = myTree.isExpanded(TreePath(oldNode.path))
      if (isDropInto(myTree, oldIndex, newIndex)) { //Drop in folder
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
        myTree.expandPath(TreePath(newNode.path))
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
          if (position == BELOW)
            index++
        }
        insertNodeInto(oldNode, newParent, index)
      }
      val treePath = TreePath(oldNode.path)
      myTree.selectionPath = treePath
      if (wasExpanded) {
        myTree.expandPath(treePath)
      }
    }

    override fun insertNodeInto(newChild: MutableTreeNode, parent: MutableTreeNode, index: Int) {
      super.insertNodeInto(newChild, parent, index)
      if (!getKind(newChild as DefaultMutableTreeNode).isConfiguration) {
        return
      }
      val userObject = getSafeUserObject(newChild)
      val newFolderName = if (getKind(parent as DefaultMutableTreeNode) == FOLDER)
        parent.userObject as String
      else
        null
      if (userObject is SingleConfigurationConfigurable<*>) {
        userObject.folderName = newFolderName
      }
    }

    override fun reload(node: TreeNode?) {
      super.reload(node)
      val userObject = (node as DefaultMutableTreeNode).userObject
      if (userObject is String) {
        for (i in 0 until node.childCount) {
          val child = node.getChildAt(i) as DefaultMutableTreeNode
          val safeUserObject = getSafeUserObject(child)
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

  companion object {
    fun collectNodesRecursively(parentNode: DefaultMutableTreeNode, nodes: MutableList<DefaultMutableTreeNode>, vararg allowed: NodeKind) {
      for (i in 0 until parentNode.childCount) {
        val child = parentNode.getChildAt(i) as DefaultMutableTreeNode
        if (ArrayUtilRt.find(allowed, getKind(child)) != -1) {
          nodes.add(child)
        }
        collectNodesRecursively(child, nodes, *allowed)
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


    private fun createUniqueName(typeNode: DefaultMutableTreeNode, baseName: String?, vararg kinds: NodeKind): String {
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
      if (treeNode == null)
        return null
      var settings: RunnerAndConfigurationSettings? = null
      if (treeNode.userObject is SingleConfigurationConfigurable<*>) {
        settings = (treeNode.userObject as SingleConfigurationConfigurable<*>).settings as RunnerAndConfigurationSettings
      }
      if (treeNode.userObject is RunnerAndConfigurationSettings) {
        settings = treeNode.userObject as RunnerAndConfigurationSettings
      }
      return settings
    }

    fun getKind(node: DefaultMutableTreeNode?): NodeKind {
      if (node == null)
        return UNKNOWN
      val userObject = node.userObject
      if (userObject is SingleConfigurationConfigurable<*> || userObject is RunnerAndConfigurationSettings) {
        val settings = getSettings(node) ?: return UNKNOWN
        return if (settings.isTemporary) TEMPORARY_CONFIGURATION else CONFIGURATION
      }
      if (userObject is String) {
        return FOLDER
      }
      return if (userObject is ConfigurationType) {
        CONFIGURATION_TYPE
      }
      else UNKNOWN
    }
  }

  //private class DataContextPanel extends JPanel implements DataProvider {
  //  public DataContextPanel(LayoutManager layout) {
  //    super(layout);
  //  }
  //
  //  @Nullable
  //  @Override
  //  public Object getData(@NonNls String dataId) {
  //    return RunConfigurationSelector.KEY.getName().equals(dataId) ? new RunConfigurationSelector() {
  //      @Override
  //      public void select(@NotNull RunConfiguration configuration) {
  //        selectConfiguration(configuration);
  //      }
  //    } : null;
  //  }
  //}
}
