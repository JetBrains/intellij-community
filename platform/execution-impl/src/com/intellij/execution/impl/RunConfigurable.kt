// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configuration.ConfigurationFactoryEx
import com.intellij.execution.configurations.*
import com.intellij.execution.configurations.ConfigurationTypeUtil.isEditableInDumbMode
import com.intellij.execution.impl.RunConfigurable.Companion.collectNodesRecursively
import com.intellij.execution.impl.RunConfigurableNodeKind.*
import com.intellij.execution.impl.statistics.RunConfigurationOptionUsagesCollector
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.dnd.TransferableList
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SettingsEditorConfigurable
import com.intellij.openapi.project.*
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.AlignedPopup
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.Trinity
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.IdeFocusManager.getGlobalInstance
import com.intellij.ui.*
import com.intellij.ui.RowsDnDSupport.RefinedDropSupport.Position.*
import com.intellij.ui.SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.mac.touchbar.Touchbar
import com.intellij.ui.mac.touchbar.TouchbarActionCustomizations
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ArrayUtilRt
import com.intellij.util.SingleAlarm
import com.intellij.util.containers.TreeTraversal
import com.intellij.util.ui.EditableModel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.datatransfer.Transferable
import java.awt.event.MouseEvent
import java.util.function.ToIntFunction
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.*
import kotlin.math.max

private val LOG = logger<RunConfigurable>()

@Nls
internal fun getUserObjectName(userObject: Any): String {
  @Suppress("HardCodedStringLiteral")
  return when (userObject) {
    is ConfigurationType -> userObject.displayName
    is ConfigurationFactory -> userObject.name
    is SingleConfigurationConfigurable<*> -> userObject.nameText
    is RunnerAndConfigurationSettingsImpl -> userObject.name
    // Folder objects are strings
    is String -> userObject
    else -> userObject.toString()
  }
}

fun createRunConfigurationConfigurable(project: Project): RunConfigurable {
  return when {
    project.isDefault -> RunConfigurable(project)
    else -> ProjectRunConfigurationConfigurable(project)
  }
}

open class RunConfigurable constructor(protected val project: Project) : Configurable, Disposable, RunConfigurationCreator {
  @Volatile private var isDisposed: Boolean = false

  val root = DefaultMutableTreeNode("Root")
  val treeModel = MyTreeModel(root)
  val tree = Tree(treeModel)
  private val rightPanel = JPanel(BorderLayout())
  private val splitter = JBSplitter("RunConfigurable.dividerProportion", 0.3f)
  private var wholePanel: JPanel? = null
  private var selectedConfigurable: Configurable? = null
  private val storedComponents = HashMap<ConfigurationFactory, Configurable>()
  protected var toolbarDecorator: ToolbarDecorator? = null
  private var isFolderCreating = false
  protected val toolbarAddAction = MyToolbarAddAction()
  private var isModified = false

  private lateinit var changeRunConfigurationNodeAlarm: SingleAlarm
  private val changeRunConfigurationListener = TreeSelectionListener {
    if (changeRunConfigurationNodeAlarm.isDisposed) return@TreeSelectionListener
    changeRunConfigurationNodeAlarm.cancelAndRequest()
  }
  private var dialogUpdateCallback: Runnable? = null

  companion object {
    fun collectNodesRecursively(parentNode: DefaultMutableTreeNode, nodes: MutableList<DefaultMutableTreeNode>, vararg allowed: RunConfigurableNodeKind) {
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

      return when (val userObject = node.userObject) {
        is SingleConfigurationConfigurable<*>, is RunnerAndConfigurationSettings -> {
          val settings = getSettings(node) ?: return UNKNOWN
          if (settings.isTemporary) TEMPORARY_CONFIGURATION else CONFIGURATION
        }
        is String -> FOLDER
        else -> if (userObject is ConfigurationType) CONFIGURATION_TYPE else UNKNOWN
      }
    }

    fun isVirtualConfiguration(node: DefaultMutableTreeNode?) = when (node?.userObject) {
      is SingleConfigurationConfigurable<*>, is RunnerAndConfigurationSettings -> {
        getSettings(node)?.type is VirtualConfigurationType
      }
      is VirtualConfigurationType -> true
      else -> false
    }

    fun configurationTypeSorted(project: Project,
                                showApplicableTypesOnly: Boolean,
                                allTypes: List<ConfigurationType>,
                                hideVirtualConfigurations : Boolean = false): List<ConfigurationType> =
      getTypesToShow(project, showApplicableTypesOnly, allTypes, hideVirtualConfigurations)
        .sortedWith(kotlin.Comparator { type1, type2 -> compareTypesForUi(type1!!, type2!!) })

    fun getTypesToShow(project: Project, showApplicableTypesOnly: Boolean, allTypes: List<ConfigurationType>, hideVirtualConfigurations : Boolean = false): List<ConfigurationType> {
      val allVisibleTypes = if (hideVirtualConfigurations) allTypes.filter { it !is VirtualConfigurationType } else allTypes
      if (showApplicableTypesOnly) {
        val applicableTypes = allVisibleTypes.filter { configurationType -> configurationType.configurationFactories.any { it.isApplicable(project) } }
        if (applicableTypes.isNotEmpty() && applicableTypes.size < (allTypes.size - 3)) {
          return applicableTypes
        }
      }
      return allVisibleTypes
    }
  }

  // https://youtrack.jetbrains.com/issue/TW-61353
  fun getSelectedConfigurable() = selectedConfigurable

  fun setDialogUpdateCallback(callback: Runnable) {
    dialogUpdateCallback = callback
  }

  override fun getDisplayName() = ExecutionBundle.message("run.configurable.display.name")

  protected fun initTree() {
    tree.isRootVisible = false
    tree.showsRootHandles = true
    tree.transferHandler = object : TransferHandler() {
      override fun createTransferable(component: JComponent): Transferable? {
        val tree = component as? JTree ?: return null
        val selection = tree.selectionPaths ?: return null
        if (selection.size <= 1) return null
        return object : TransferableList<TreePath>(*selection) {
          override fun toString(path: TreePath): String {
            return path.lastPathComponent.toString()
          }
        }
      }

      override fun getSourceActions(c: JComponent) = COPY_OR_MOVE
    }
    TreeUtil.installActions(tree)
    TreeSpeedSearch.installOn(tree, false) { o ->
      val node = o.lastPathComponent as DefaultMutableTreeNode
      when (val userObject = node.userObject) {
        is RunnerAndConfigurationSettingsImpl -> return@installOn userObject.name
        is SingleConfigurationConfigurable<*> -> return@installOn userObject.nameText
        else -> if (userObject is ConfigurationType) {
          return@installOn userObject.displayName
        }
        else if (userObject is String) {
          return@installOn userObject
        }
      }
      o.toString()
    }

    tree.cellRenderer = RunConfigurableTreeRenderer(runManager)

    addRunConfigurationsToModel(root)

    if (ApplicationManager.getApplication().isUnitTestMode) {
      tree.addTreeSelectionListener { selectRunConfiguration() }
    }

    sortTopLevelBranches()
    tree.emptyText.appendText(ExecutionBundle.message("status.text.no.run.configurations.added")).appendLine(
      ExecutionBundle.message("status.text.add.new"), LINK_PLAIN_ATTRIBUTES) {
      toolbarAddAction.showAddPopup(true, it.source as MouseEvent)}
    val shortcut = KeymapUtil.getShortcutsText(toolbarAddAction.shortcutSet.shortcuts)
    if (shortcut.isNotEmpty()) tree.emptyText.appendText(" $shortcut")
    (tree.model as DefaultTreeModel).reload()
  }

  protected fun initTreeSelectionListener(parentDisposable: Disposable) {
    if (tree.treeSelectionListeners.any { it == changeRunConfigurationListener }) return

    val modalityState = ModalityState.stateForComponent(tree)

    // The listener is supposed to be registered for a dialog, so the modality state cannot be NON_MODAL
    if (modalityState == ModalityState.nonModal()) return

    changeRunConfigurationNodeAlarm = SingleAlarm(
      task = ::selectRunConfiguration,
      delay = 300,
      parentDisposable = parentDisposable,
      modalityState = modalityState
    )

    tree.addTreeSelectionListener(changeRunConfigurationListener)
  }

  private fun selectRunConfiguration() {
    val selectionPath = tree.selectionPath
    if (selectionPath != null) {
      val node = selectionPath.lastPathComponent as DefaultMutableTreeNode
      when (val userObject = getSafeUserObject(node)) {
        is SingleConfigurationConfigurable<*> -> {
          @Suppress("UNCHECKED_CAST")
          updateRightPanel(userObject as SingleConfigurationConfigurable<RunConfiguration>)
        }
        is String -> {
          showFolderField(node, userObject)
        }
        is ConfigurationFactory, is ConfigurationType -> {
          typeOrFactorySelected(userObject)
        }
      }
    }
    updateDialog()
  }

  protected open fun typeOrFactorySelected(userObject: Any) {
    if (userObject is ConfigurationType && userObject.configurationFactories.size == 1) {
      showTemplateConfigurable(userObject.configurationFactories.first())
    }
    else if (userObject is ConfigurationFactory) {
      showTemplateConfigurable(userObject)
    }
    else {
      updateRightPanel(null)
    }
  }

  protected open fun addRunConfigurationsToModel(model: DefaultMutableTreeNode) {
  }

  fun selectConfigurableOnShow() {
    ApplicationManager.getApplication().invokeLater({
      if (isDisposed) {
        return@invokeLater
      }

      tree.requestFocusInWindow()
      val settings = getInitialSelectedConfiguration()
      if (settings != null) {
        if (selectConfiguration(settings.configuration)) {
          return@invokeLater
        }
      }
      else {
        selectedConfigurable = null
      }
      drawPressAddButtonMessage(null)
    }, ModalityState.stateForComponent(wholePanel!!))
  }

  protected open fun getInitialSelectedConfiguration(): RunnerAndConfigurationSettings? {
    return runManager.selectedConfiguration
  }

  private fun selectConfiguration(configuration: RunConfiguration): Boolean {
    val node = findNode(configuration) ?: return false
    TreeUtil.selectInTree(node, true, tree)
    return true
  }

  private fun findNode(configuration: RunConfiguration): DefaultMutableTreeNode? {
    val enumeration = root.breadthFirstEnumeration()
    while (enumeration.hasMoreElements()) {
      val node = enumeration.nextElement() as DefaultMutableTreeNode
      var userObject = node.userObject
      if (userObject is SettingsEditorConfigurable<*>) {
        userObject = userObject.settings
      }
      if (userObject is RunnerAndConfigurationSettingsImpl) {
        val otherConfiguration = (userObject as RunnerAndConfigurationSettings).configuration
        if (otherConfiguration.factory?.type?.id == configuration.factory?.type?.id && otherConfiguration.name == configuration.name) {
          return node
        }
      }
    }
    return null
  }

  private fun showTemplateConfigurable(factory: ConfigurationFactory) {
    var configurable: Configurable? = storedComponents[factory]
    if (configurable == null) {
      configurable = TemplateConfigurable(runManager.getConfigurationTemplate(factory))
      storedComponents[factory] = configurable
      configurable.reset()
    }
    updateRightPanel(configurable)
  }

  private fun showFolderField(node: DefaultMutableTreeNode, @Nls folderName: String) {
    rightPanel.removeAll()
    val p = JPanel(MigLayout("ins ${toolbarDecorator!!.actionsPanel.height}px 5 0 0, flowx"))
    val textField = JTextField(folderName)
    textField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        node.userObject = textField.text
        treeModel.reload(node)
      }
    })
    textField.addActionListener { getGlobalInstance().doWhenFocusSettlesDown { getGlobalInstance().requestFocus(tree, true) } }
    p.add(JLabel(ExecutionBundle.message("run.configuration.folder.name")), "gapright 5")
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

  fun updateRightPanel(configurable: Configurable?) {
    rightPanel.removeAll()
    selectedConfigurable = configurable
    if (configurable == null) {
      rightPanel.repaint()
      return
    }

    val configurableComponent = if (!project.isDefault && DumbService.getInstance(project).isDumb && !mayBeEditedInDumbMode(configurable)) {
      JBPanelWithEmptyText().withEmptyText(IdeBundle.message("empty.text.this.view.is.not.available.until.indices.are.built"))
    }
    else {
      configurable.createComponent()
    }
    rightPanel.add(BorderLayout.CENTER, configurableComponent)
    if (configurable is SingleConfigurationConfigurable<*>) {
      rightPanel.add(configurable.validationComponent, BorderLayout.SOUTH)
      ApplicationManager.getApplication().invokeLater({ configurable.requestToUpdateWarning() }) { isDisposed }
    }

    setupDialogBounds()
  }

  private fun mayBeEditedInDumbMode(configurable: Configurable): Boolean = when (configurable) {
    is SingleConfigurationConfigurable<*> -> isEditableInDumbMode(configurable.configuration)
    is TemplateConfigurable -> isEditableInDumbMode(configurable.settings)
    else -> false
  }

  private fun sortTopLevelBranches() {
    val expandedPaths = TreeUtil.collectExpandedPaths(tree)
    TreeUtil.sortRecursively(root) { o1, o2 ->
      val userObject1 = o1.userObject
      val userObject2 = o2.userObject
      when {
        userObject1 is ConfigurationType && userObject2 is ConfigurationType -> (userObject1).displayName.compareTo(userObject2.displayName, true)
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
    var changed = false
    info.editor.addSettingsEditorListener { editor ->
      ApplicationManager.getApplication().invokeLater(
        {
          update()
          val configuration = info.configuration
          if (configuration is LocatableConfiguration) {
            if (configuration.isGeneratedName && !changed) {
              try {
                val snapshot = editor.snapshot.configuration as LocatableConfiguration
                val generatedName = snapshot.suggestedName()
                if (!generatedName.isNullOrEmpty()) {
                  info.nameText = generatedName
                  changed = false
                }
              }
              catch (ignore: ConfigurationException) {
              }
            }
          }
          setupDialogBounds()
        }, { isDisposed })
    }

    info.addNameListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        changed = true
        update()
      }
    })

    info.addSharedListener {
      changed = true
      update()
    }

    info.addValidationListener {
      val node = findNode(info.configuration) ?: return@addValidationListener
      treeModel.nodeChanged(node)
    }
  }

  protected fun drawPressAddButtonMessage(configurationType: ConfigurationType?) {
    val panel = JPanel(BorderLayout())
    if (configurationType !is UnknownConfigurationType) {
      createTipPanelAboutAddingNewRunConfiguration(configurationType)?.let {
        panel.add(it, BorderLayout.CENTER)
      }
    }

    rightPanel.removeAll()
    rightPanel.add(ScrollPaneFactory.createScrollPane(panel, true), BorderLayout.CENTER)
    rightPanel.revalidate()
    rightPanel.repaint()
  }

  protected open fun createTipPanelAboutAddingNewRunConfiguration(configurationType: ConfigurationType?): JComponent? = null

  protected open fun createLeftPanel(): JComponent {
    initTree()
    return ScrollPaneFactory.createScrollPane(tree)
  }

  protected val selectedConfigurationType: ConfigurationType?
    get() {
      val configurationTypeNode = selectedConfigurationTypeNode
      return if (configurationTypeNode != null) configurationTypeNode.userObject as ConfigurationType else null
    }

  override fun createComponent(): JComponent? {
    wholePanel = object : JPanel(BorderLayout()), UiDataProvider {
      override fun uiDataSnapshot(sink: DataSink) {
        sink[RunConfigurationSelector.KEY] = RunConfigurationSelector { selectConfiguration(it) }
        sink[CommonDataKeys.PROJECT] = project
        sink[RunConfigurationCreator.KEY] = this@RunConfigurable
      }
    }
    if (SystemInfo.isMac) {
      val touchbarActions = DefaultActionGroup(toolbarAddAction)
      TouchbarActionCustomizations.setShowText(touchbarActions, true)
      Touchbar.setActions(wholePanel!!, touchbarActions)
    }

    val leftPanel = createLeftPanel()
    leftPanel.border = IdeBorderFactory.createBorder(SideBorder.RIGHT)
    splitter.firstComponent = leftPanel
    splitter.setHonorComponentsMinimumSize(true)
    rightPanel.border = JBUI.Borders.empty(15, 5, 0, 15)
    splitter.secondComponent = rightPanel
    wholePanel!!.add(splitter, BorderLayout.CENTER)

    updateDialog()

    val d = wholePanel!!.preferredSize
    d.width = max(d.width, 800)
    d.height = max(d.height, 600)
    wholePanel!!.preferredSize = d

    return wholePanel
  }

  override fun reset() {
    isModified = false
  }

  @Throws(ConfigurationException::class)
  override fun apply() {
    val manager = runManager
    manager.fireBeginUpdate()
    try {
      val settingsToOrder = Object2IntOpenHashMap<RunnerAndConfigurationSettings>()
      var order = 0
      val toDeleteSettings = HashSet(manager.allSettings)
      val selectedSettings = selectedSettings
      for (i in 0 until root.childCount) {
        val node = root.getChildAt(i) as DefaultMutableTreeNode
        val userObject = node.userObject
        if (userObject is ConfigurationType) {
          for (bean in applyByType(node, userObject, selectedSettings)) {
            settingsToOrder[bean.settings] = order++
            toDeleteSettings.remove(bean.settings)
          }
        }
      }
      manager.removeConfigurations(toDeleteSettings)
      manager.setOrder(Comparator.comparingInt(ToIntFunction { settingsToOrder.getInt(it) }), isApplyAdditionalSortByTypeAndGroup = false)
    }
    finally {
      manager.fireEndUpdate()
    }
    updateActiveConfigurationFromSelected()
    isModified = false
    tree.repaint()
  }

  protected fun applyTemplates() {
    for (configurable in storedComponents.values) {
      if (configurable.isModified) {
        configurable.apply()
      }
    }
  }

  open fun updateActiveConfigurationFromSelected() {
    val selectedConfigurable = selectedConfigurable
    if (selectedConfigurable is SingleConfigurationConfigurable<*>) {
      runManager.selectedConfiguration = selectedConfigurable.settings
    }
  }

  @Throws(ConfigurationException::class)
  private fun applyByType(typeNode: DefaultMutableTreeNode,
                          type: ConfigurationType,
                          selectedSettings: RunnerAndConfigurationSettings?): List<RunConfigurationBean> {
    var indexToMove = -1

    val configurationBeans = ArrayList<RunConfigurationBean>()
    val names = HashSet<String>()
    val configurationNodes = ArrayList<DefaultMutableTreeNode>()
    collectNodesRecursively(typeNode, configurationNodes, CONFIGURATION, TEMPORARY_CONFIGURATION)
    for (node in configurationNodes) {
      val userObject = node.userObject
      var configurationBean: RunConfigurationBean? = null
      var settings: RunnerAndConfigurationSettings? = null
      if (userObject is SingleConfigurationConfigurable<*>) {
        settings = userObject.settings
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
          throw ConfigurationException(
            ExecutionBundle.message("dialog.message.run.configuration.already.exists", type.displayName, nameText))
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
        throw ConfigurationException(ExecutionBundle.message("dialog.message.folder.name.should.not.be.empty"))
      }
      if (!names.add(folderName)) {
        TreeUtil.selectNode(tree, node)
        throw ConfigurationException(ExecutionBundle.message("dialog.message.folders.have.same.name", folderName))
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
        if (configurable == node.userObject) {
          TreeUtil.selectNode(tree, node)
          break
        }
      }
      throw e
    }
  }

  override fun isModified(): Boolean {
    if (isModified) {
      return true
    }

    val runManager = runManager
    val allSettings = runManager.allSettings
    var currentSettingCount = 0
    for (i in 0 until root.childCount) {
      val typeNode = root.getChildAt(i) as DefaultMutableTreeNode
      val configurationType = typeNode.userObject as? ConfigurationType ?: continue

      val configurationNodes = ArrayList<DefaultMutableTreeNode>()
      collectNodesRecursively(typeNode, configurationNodes, CONFIGURATION, TEMPORARY_CONFIGURATION)
      val allTypeSettings = allSettings.filter { it.type == configurationType }
      if (allTypeSettings.size != configurationNodes.size) {
        return true
      }

      var currentTypeSettingsCount = 0
      for (configurationNode in configurationNodes) {
        val userObject = configurationNode.userObject
        val settings: RunnerAndConfigurationSettings
        if (userObject is SingleConfigurationConfigurable<*>) {
          if (userObject.isModified) {
            return true
          }
          settings = userObject.settings
        }
        else if (userObject is RunnerAndConfigurationSettings) {
          settings = userObject
        }
        else {
          continue
        }

        currentSettingCount++
        val index = currentTypeSettingsCount++
        // we compare by instance, equals is not implemented and in any case object modification is checked by other logic
        // we compare by index among current types settings because indexes among all configurations may differ
        // since temporary configurations are stored in the end
        if (allTypeSettings.size <= index || allTypeSettings[index] !== settings) {
          return true
        }
      }
    }

    return allSettings.size != currentSettingCount || isConfigurableModified()
  }

  protected fun isConfigurableModified() = storedComponents.values.any { it.isModified }

  override fun disposeUIResources() {
    Disposer.dispose(this)
  }

  override fun dispose() {
    isDisposed = true
    storedComponents.values.forEach { it.disposeUIResources() }
    storedComponents.clear()

    TreeUtil.treeNodeTraverser(root)
      .traverse(TreeTraversal.PRE_ORDER_DFS)
      .processEach { node ->
        ((node as? DefaultMutableTreeNode)?.userObject as? SingleConfigurationConfigurable<*>)?.disposeUIResources()
        true
      }
    rightPanel.removeAll()
    splitter.dispose()
  }

  private fun updateDialog() {
    dialogUpdateCallback?.run()
  }

  private fun setupDialogBounds() {
    SwingUtilities.invokeLater { UIUtil.setupEnclosingDialogBounds(wholePanel!!) }
  }

  val selectedConfiguration: SingleConfigurationConfigurable<RunConfiguration>?
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
    get() = RunManagerImpl.getInstanceImpl(project)

  override fun getHelpTopic(): String? {
    return selectedConfigurationType?.helpTopic ?: "reference.dialogs.rundebug"
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
        node = node.parent as? DefaultMutableTreeNode
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
    IdeFocusManager.getInstance(project).requestFocus(configurationConfigurable.nameTextField, true)
    configurationConfigurable.nameTextField.selectionStart = 0
    configurationConfigurable.nameTextField.selectionEnd = settings.name.length
    return configurationConfigurable
  }

  override fun createNewConfiguration(factory: ConfigurationFactory): SingleConfigurationConfigurable<RunConfiguration> {
    var typeNode = getConfigurationTypeNode(factory.type)
    if (typeNode == null) {
      typeNode = DefaultMutableTreeNode(factory.type)
      root.add(typeNode)
      sortTopLevelBranches()
      (tree.model as DefaultTreeModel).reload()
    }

    val selectedNode = tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode
    var node = typeNode
    if (selectedNode != null && typeNode.isNodeDescendant(selectedNode)) {
      node = selectedNode
      if (getKind(node).isConfiguration) {
        node = node.parent as DefaultMutableTreeNode
      }
    }

    val settings = runManager.createConfiguration("", factory)
    val configuration = settings.configuration
    configuration.name = createUniqueName(typeNode, suggestName(configuration), CONFIGURATION, TEMPORARY_CONFIGURATION)
    (configuration as? LocatableConfigurationBase<*>)?.setNameChangedByUser(false)
    callNewConfigurationCreated(factory, configuration)
    RunConfigurationOptionUsagesCollector.logAddNew(project, factory.type.id, ActionPlaces.RUN_CONFIGURATION_EDITOR)
    return createNewConfiguration(settings, node, selectedNode)
  }

  @Nls
  private fun suggestName(configuration: RunConfiguration): String? {
    if (configuration is LocatableConfiguration) {
      val name = configuration.suggestedName()
      if (!name.isNullOrEmpty()) {
        return name
      }
    }
    return null
  }

  protected inner class MyToolbarAddAction : AnAction(ExecutionBundle.message("add.new.run.configuration.action2.name"),
                                                      ExecutionBundle.message("add.new.run.configuration.action2.name"),
                                                      AllIcons.General.Add), AnActionButtonRunnable {
    init {
      registerCustomShortcutSet(CommonShortcuts.getInsert(), tree)
    }

    override fun actionPerformed(e: AnActionEvent) {
      showAddPopup(true, null)
    }

    override fun run(button: AnActionButton) {
      showAddPopup(true, null)
    }

    fun showAddPopup(showApplicableTypesOnly: Boolean, clickEvent: MouseEvent?) {
      val allTypes = ConfigurationType.CONFIGURATION_TYPE_EP.extensionList
      val configurationTypes: MutableList<ConfigurationType?> = configurationTypeSorted(project, showApplicableTypesOnly, allTypes, true).toMutableList()
      val hiddenCount = allTypes.size - configurationTypes.size
      if (hiddenCount > 0) {
        configurationTypes.add(NewRunConfigurationPopup.HIDDEN_ITEMS_STUB)
      }

      val popup = NewRunConfigurationPopup.createAddPopup(project, configurationTypes,
                                                          ExecutionBundle.message("show.irrelevant.configurations.action.name",
                                                                                  hiddenCount),
                                                          { factory -> createNewConfiguration(factory) }, selectedConfigurationType,
                                                          { showAddPopup(false, null) }, true)
      if (clickEvent == null) AlignedPopup.showUnderneathWithoutAlignment(popup, toolbarDecorator!!.actionsPanel)
      else {
        // it seems this method can be called asynchronously with input event, so need store the source component manually
        PopupUtil.setPopupToggleComponent(popup, clickEvent.component)
        popup.show(RelativePoint(clickEvent))
      }
    }
  }

  protected inner class MyRemoveAction : AnAction(ExecutionBundle.message("remove.run.configuration.action.name"),
                                                  ExecutionBundle.message("remove.run.configuration.action.name"),
                                                  AllIcons.General.Remove), AnActionButtonRunnable, AnActionButtonUpdater {
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
        if (!kind.isConfiguration && kind != FOLDER || isVirtualConfiguration(node))
          continue

        if (node.userObject is SingleConfigurationConfigurable<*>) {
          val configurable = node.userObject as SingleConfigurationConfigurable<*>
          RunConfigurationOptionUsagesCollector.logRemove(project, configurable.configuration.type.id, ActionPlaces.RUN_CONFIGURATION_EDITOR)
          configurable.disposeUIResources()
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
          nodeIndexToSelect = max(0, nodeIndexToSelect - 1)
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

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun isEnabled(e: AnActionEvent): Boolean {
      var enabled = false
      val selections = tree.selectionPaths
      if (selections != null) {
        for (each in selections) {
          val node = each.lastPathComponent as DefaultMutableTreeNode
          val kind = getKind(node)
          if ((kind.isConfiguration || kind == FOLDER) && !isVirtualConfiguration(node)) {
            enabled = true
            break
          }
        }
      }
      return enabled
    }
  }

  protected inner class MyCopyAction : AnAction(ExecutionBundle.message("copy.configuration.action.name"),
                                                ExecutionBundle.message("copy.configuration.action.name"),
                                                IconManager.getInstance().getPlatformIcon(PlatformIcons.Copy)), PossiblyDumbAware {
    init {
      val action = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_DUPLICATE)
      registerCustomShortcutSet(action.shortcutSet, tree)
    }

    override fun actionPerformed(e: AnActionEvent) {
      val configuration = selectedConfiguration!!
      try {
        val typeNode = selectedConfigurationTypeNode!!
        val settings = configuration.createSnapshot(true)
        val copyName = createUniqueName(typeNode, configuration.nameText, CONFIGURATION, TEMPORARY_CONFIGURATION)
        (settings.configuration as? LocatableConfigurationBase<*>)?.setNameChangedByUser(true)
        settings.name = copyName
        val factory = settings.factory
        @Suppress("UNCHECKED_CAST", "DEPRECATION")
        (factory as? ConfigurationFactoryEx<RunConfiguration>)?.onConfigurationCopied(settings.configuration)
        (settings.configuration as? ConfigurationCreationListener)?.onConfigurationCopied()
        val parentNode = selectedNode?.parent
        val node = (if ((parentNode as? DefaultMutableTreeNode)?.userObject is String) parentNode else typeNode) as DefaultMutableTreeNode
        val configurable = createNewConfiguration(settings, node, selectedNode)
        configurable.nameTextField.selectionStart = 0
        configurable.nameTextField.selectionEnd = copyName.length
        RunConfigurationOptionUsagesCollector.logCopy(project, configurable.configuration.type.id, ActionPlaces.RUN_CONFIGURATION_EDITOR)
      }
      catch (e: ConfigurationException) {
        Messages.showErrorDialog(toolbarDecorator!!.actionsPanel, e.message, e.title)
      }
    }

    override fun update(e: AnActionEvent) {
      val configuration = selectedConfiguration
      e.presentation.isEnabled = configuration != null && configuration.configuration.type.isManaged
    }
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun isDumbAware(): Boolean {
      val configuration = selectedConfiguration
      return configuration != null && isEditableInDumbMode(configuration.configuration)
    }
  }

  protected inner class MySaveAction : DumbAwareAction(ExecutionBundle.message("action.name.save.configuration"), null,
                                                AllIcons.Actions.MenuSaveall) {
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
      e.presentation.isEnabledAndVisible = when (configuration) {
        null -> false
        else -> configuration.settings.isTemporary
      }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.EDT
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

  protected inner class MyCreateFolderAction : DumbAwareAction(ExecutionBundle.message("run.configuration.create.folder.text"),
                                                               ExecutionBundle.message("run.configuration.create.folder.description"),
                                                               AllIcons.Actions.NewFolder) {

    override fun actionPerformed(e: AnActionEvent) {
      val type = selectedConfigurationType ?: return
      val selectedNodes = selectedNodes
      val typeNode = getConfigurationTypeNode(type) ?: return
      val folderName = createUniqueName(typeNode, ExecutionBundle.message("new.folder"), FOLDER)
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
          if (type != selectedType) {
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
      e.presentation.text = if (toMove) ExecutionBundle.message("run.configuration.create.folder.description.move")
                            else ExecutionBundle.message("run.configuration.create.folder.text")
      e.presentation.isEnabled = isEnabled
    }
    override fun getActionUpdateThread() = ActionUpdateThread.EDT
  }

  protected inner class MySortFolderAction : AnAction(ExecutionBundle.message("run.configuration.sort.folder.text"),
                                                      ExecutionBundle.message("run.configuration.sort.folder.description"),
                                                      AllIcons.ObjectBrowser.Sorted), Comparator<DefaultMutableTreeNode>, DumbAware {
    override fun compare(node1: DefaultMutableTreeNode, node2: DefaultMutableTreeNode): Int {
      val kind1 = getKind(node1)
      val kind2 = getKind(node2)
      if (kind1 == FOLDER) {
        return if (kind2 == FOLDER) node1.parent.getIndex(node1) - node2.parent.getIndex(node2) else -1
      }
      if (kind2 == FOLDER) {
        return 1
      }
      val name1 = getUserObjectName(node1.userObject)
      val name2 = getUserObjectName(node2.userObject)
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
          children.add(folderNode.getChildAt(i) as DefaultMutableTreeNode)
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

    override fun getActionUpdateThread() = ActionUpdateThread.EDT
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

      val oldPaths = tree.selectionPaths ?: return false
      val newNode = tree.getPathForRow(newIndex).lastPathComponent as DefaultMutableTreeNode
      for (oldPath in oldPaths) {
        val oldNode = oldPath.lastPathComponent as DefaultMutableTreeNode
        if (oldNode === newNode || !canDrop(oldNode, newNode, newIndex, position)) return false
      }
      return true
    }

    fun canDrop(oldNode: DefaultMutableTreeNode, newNode: DefaultMutableTreeNode, newIndex: Int,
                position: RowsDnDSupport.RefinedDropSupport.Position): Boolean {
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
      val oldNode = (tree.getPathForRow(oldIndex) ?: return false).lastPathComponent as DefaultMutableTreeNode
      val newNode = (tree.getPathForRow(newIndex) ?: return false).lastPathComponent as DefaultMutableTreeNode
      return getKind(oldNode).isConfiguration && getKind(newNode) == FOLDER
    }

    override fun drop(oldIndex: Int, newIndex: Int, position: RowsDnDSupport.RefinedDropSupport.Position) {
      val oldPaths = tree.selectionPaths ?: return
      val newNode = tree.getPathForRow(newIndex).lastPathComponent as DefaultMutableTreeNode
      val newKind = getKind(newNode)
      for (oldPath in oldPaths) {
        val oldNode = oldPath.lastPathComponent as DefaultMutableTreeNode
        var newParent = newNode.parent as DefaultMutableTreeNode
        val oldKind = getKind(oldNode)
        val wasExpanded = tree.isExpanded(TreePath(oldNode.path))
        // drop in folder
        if (oldKind.isConfiguration && newKind == FOLDER) {
          removeNodeFromParent(oldNode)
          var index = newNode.childCount
          if (oldKind.isConfiguration) {
            var middleIndex = newNode.childCount
            for (i in 0 until newNode.childCount) {
              if (getKind(newNode.getChildAt(i) as DefaultMutableTreeNode) == TEMPORARY_CONFIGURATION) {
                //index of first temporary configuration in target folder
                middleIndex = i
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
          if (type !== getType(newParent)) {
            newParent = getConfigurationTypeNode(type)!!
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

private fun createUniqueName(typeNode: DefaultMutableTreeNode, @Nls baseName: String?, vararg kinds: RunConfigurableNodeKind): String {
  val str = baseName ?: ExecutionBundle.message("run.configuration.unnamed.name.prefix")
  val configurationNodes = ArrayList<DefaultMutableTreeNode>()
  collectNodesRecursively(typeNode, configurationNodes, *kinds)
  val currentNames = ArrayList<String>()
  for (node in configurationNodes) {
    when (val userObject = node.userObject) {
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
    node = node.parent as? DefaultMutableTreeNode
  }
  return null
}

private fun getSettings(treeNode: DefaultMutableTreeNode?): RunnerAndConfigurationSettings? {
  if (treeNode == null) {
    return null
  }

  val settings: RunnerAndConfigurationSettings? = null
  return when (treeNode.userObject) {
    is SingleConfigurationConfigurable<*> -> (treeNode.userObject as SingleConfigurationConfigurable<*>).settings
    is RunnerAndConfigurationSettings -> treeNode.userObject as RunnerAndConfigurationSettings
    else -> settings
  }
}
