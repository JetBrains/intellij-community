// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.starters.local.wizard

import com.intellij.icons.AllIcons
import com.intellij.ide.starters.JavaStartersBundle
import com.intellij.ide.starters.local.*
import com.intellij.ide.starters.shared.*
import com.intellij.ide.starters.shared.ui.LibraryDescriptionPanel
import com.intellij.ide.starters.shared.ui.SelectedLibrariesPanel
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.Version
import com.intellij.ui.*
import com.intellij.ui.components.JBLabel
import com.intellij.ui.layout.*
import com.intellij.util.containers.Convertor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.tree.TreeUtil
import java.awt.Dimension
import java.awt.GridBagLayout
import java.awt.event.ItemEvent
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

open class StarterLibrariesStep(contextProvider: StarterContextProvider) : ModuleWizardStep() {
  protected val starterContext = contextProvider.starterContext
  protected val starterSettings: StarterWizardSettings = contextProvider.settings
  protected val moduleBuilder: StarterModuleBuilder = contextProvider.moduleBuilder

  private val topLevelPanel: BorderLayoutPanel = BorderLayoutPanel()
  private val contentPanel: DialogPanel by lazy { createComponent() }
  private val libraryDescriptionPanel: LibraryDescriptionPanel by lazy { LibraryDescriptionPanel() }
  private val selectedLibrariesPanel: SelectedLibrariesPanel by lazy { createSelectedLibrariesPanel() }

  private val dependencyConfig: Map<String, DependencyConfig> by lazy { moduleBuilder.loadDependencyConfigInternal() }

  private val selectedLibraryIds: MutableSet<String> = mutableSetOf()
  private var selectedStarterId: String? = null

  private val startersComboBox: ComboBox<Starter> by lazy { ComboBox<Starter>() }
  private val librariesList: CheckboxTreeBase by lazy { createLibrariesList() }

  override fun updateDataModel() {
    starterContext.starter = startersComboBox.selectedItem as? Starter
    starterContext.libraryIds.clear()
    starterContext.libraryIds.addAll(selectedLibraryIds)
    starterContext.starterDependencyConfig = dependencyConfig[starterContext.starter?.id]
  }

  override fun onStepLeaving() {
    super.onStepLeaving()

    updateDataModel()
  }

  override fun getComponent(): JComponent {
    return topLevelPanel
  }

  @NlsSafe
  private fun getLibraryVersion(library: Library): String? {
    if (library.group == null || library.artifact == null) return null
    val selectedStarter = startersComboBox.selectedItem as? Starter ?: return null

    return dependencyConfig[selectedStarter.id]?.getVersion(library.group, library.artifact)
  }

  override fun getPreferredFocusedComponent(): JComponent {
    return librariesList
  }

  private fun createLibrariesList(): CheckboxTreeBase {
    val list = CheckboxTreeBase(object : CheckboxTree.CheckboxTreeCellRenderer() {
      override fun customizeRenderer(
        tree: JTree?,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
      ) {
        if (value !is DefaultMutableTreeNode) return

        this.border = JBUI.Borders.empty(2)
        val renderer = textRenderer

        when (val item = value.userObject) {
          is LibraryCategory -> {
            renderer.icon = item.icon ?: AllIcons.Nodes.PpLibFolder
            renderer.append(item.title, SimpleTextAttributes.REGULAR_ATTRIBUTES)
          }
          is Library -> {
            renderer.icon = item.icon ?: AllIcons.Nodes.PpLib
            renderer.append(item.title, SimpleTextAttributes.REGULAR_ATTRIBUTES)

            val version = getLibraryVersion(item)
            if (version != null) {
              renderer.append(" ($version)", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
          }
        }
      }
    }, null)

    enableEnterKeyHandling(list)

    list.rowHeight = 0
    list.isRootVisible = false
    list.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
    list.addCheckboxTreeListener(object : CheckboxTreeListener {
      override fun nodeStateChanged(node: CheckedTreeNode) {
        val library = node.userObject as? Library ?: return
        val libraryId = library.id
        if (node.isChecked) {
          selectedLibraryIds.add(libraryId)
          selectedLibraryIds.removeAll(library.includesLibraries)
        }
        else {
          selectedLibraryIds.remove(libraryId)
        }

        updateIncludedLibraries(library, node)
        updateSelectedLibraries()

        list.repaint()
      }
    })

    return list
  }

  private fun createComponent(): DialogPanel {
    startersComboBox.setMinimumAndPreferredWidth(200)
    startersComboBox.renderer = SimpleListCellRenderer.create("", Starter::title)
    startersComboBox.addItemListener { e ->
      if (e.stateChange == ItemEvent.SELECTED) {
        val newValue = e.item as? Starter
        if (newValue != null) {
          selectedStarterId = newValue.id
          updateLibrariesList(newValue, false)
        }
      }
    }

    TreeSpeedSearch(librariesList, Convertor { treePath: TreePath ->
      when (val dataObject = (treePath.lastPathComponent as DefaultMutableTreeNode).userObject) {
        is LibraryCategory -> dataObject.title
        is Library -> dataObject.title
        else -> ""
      }
    })

    librariesList.selectionModel.addTreeSelectionListener(TreeSelectionListener { e ->
      val path = e.path
      if (path != null && e.isAddedPath) {
        when (val item = (path.lastPathComponent as? DefaultMutableTreeNode)?.userObject) {
          is LibraryCategory -> libraryDescriptionPanel.update(item.title, item.description)
          is Library -> libraryDescriptionPanel.update(item, null)
        }
      }
      else {
        libraryDescriptionPanel.reset()
      }
    })
    val messages = starterSettings.customizedMessages

    return panel {
      if (starterContext.starterPack.starters.size > 1) {
        row {
          cell(isFullWidth = true) {
            label(messages?.frameworkVersionLabel ?: JavaStartersBundle.message("title.project.version.label"))

            component(startersComboBox)
          }
        }.largeGapAfter()
      }

      row {
        label(messages?.dependenciesLabel ?: JavaStartersBundle.message("title.project.dependencies.label"))
      }

      row {
        component(JPanel(GridBagLayout()).apply {
          add(ScrollPaneFactory.createScrollPane(librariesList).apply {
            preferredSize = Dimension(0, 0)
          }, gridConstraint(0, 0))

          add(JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.emptyLeft(UIUtil.DEFAULT_HGAP * 2)
            preferredSize = Dimension(0, 0)

            add(libraryDescriptionPanel.apply {
              preferredSize = Dimension(0, 0)
            }, gridConstraint(0, 0))
            add(BorderLayoutPanel().apply {
              preferredSize = Dimension(0, 0)

              addToTop(JBLabel(messages?.selectedDependenciesLabel
                               ?: JavaStartersBundle.message("title.project.dependencies.selected.label")).apply {
                border = JBUI.Borders.empty(0, 0, UIUtil.DEFAULT_VGAP * 2, 0)
              })
              addToCenter(selectedLibrariesPanel)
            }, gridConstraint(0, 1))
          }, gridConstraint(1, 0))
        }).constraints(push, grow)
      }
    }.withVisualPadding()
  }

  private fun createSelectedLibrariesPanel(): SelectedLibrariesPanel {
    val panel = SelectedLibrariesPanel()
    val messages = starterSettings.customizedMessages
    panel.emptyText.text = messages?.noDependenciesSelectedLabel ?: JavaStartersBundle.message("hint.dependencies.not.selected")
    panel.libraryRemoveListener = { libraryInfo ->
      // do not remove from selectedLibraryIds directly, library can be included
      walkCheckedTree(getLibrariesRoot()) {
        if (it.userObject == libraryInfo && it.isEnabled) {
          librariesList.setNodeState(it, false)
        }
      }
      updateSelectedLibraries()
    }
    return panel
  }

  private fun updateSelectedLibraries() {
    val selected = mutableListOf<LibraryInfo>()
    walkCheckedTree(getLibrariesRoot()) {
      val library = (it.userObject as? Library)
      if (library != null && it.isChecked) {
        selected.add(library)
      }
    }
    selectedLibrariesPanel.update(selected)
  }

  private fun getLibrariesRoot(): CheckedTreeNode? {
    return librariesList.model.root as? CheckedTreeNode
  }

  override fun _init() {
    super._init()

    if (topLevelPanel.componentCount == 0) {
      // create UI only on first show
      topLevelPanel.addToCenter(contentPanel)
    }

    // step became visible
    val starterPack = starterContext.starterPack

    startersComboBox.model = DefaultComboBoxModel(starterPack.starters.toTypedArray())

    val initial = selectedStarterId == null

    val selectedStarter = when (val previouslySelectedStarter = starterContext.starter?.id) {
      null -> starterPack.starters.firstOrNull()
      else -> starterPack.starters.find { it.id == previouslySelectedStarter }
    }
    if (selectedStarter != null) {
      startersComboBox.selectedItem = selectedStarter
      selectedStarterId = selectedStarter.id

      selectedLibraryIds.clear()
      for (libraryId in starterContext.libraryIds) {
        val lib = selectedStarter.libraries.find { it.id == libraryId }
        if (lib != null) {
          selectedLibraryIds.add(libraryId)
          selectedLibraryIds.removeAll(lib.includesLibraries)
        }
      }

      updateLibrariesList(selectedStarter, initial)
    }
  }

  private fun updateLibrariesList(starter: Starter, init: Boolean) {
    val previouslyExpandedGroups = getExpandedCategories()
    val librariesRoot = CheckedTreeNode()

    val categoryNodes = mutableMapOf<LibraryCategory, DefaultMutableTreeNode>()
    val selectedLibraries = mutableListOf<Pair<Library, CheckedTreeNode>>()
    val libraryNodes = mutableListOf<CheckedTreeNode>()

    for (library in starter.libraries) {
      if (!moduleBuilder.isDependencyAvailableInternal(starter, library)) continue

      val libraryNode = CheckedTreeNode(library)

      if (library.isRequired || library.isDefault && init) {
        selectedLibraryIds.add(library.id)
      }
      libraryNode.isChecked = selectedLibraryIds.contains(library.id)
      libraryNode.isEnabled = !library.isRequired

      if (library.category == null) {
        librariesRoot.add(libraryNode)
      }
      else {
        val categoryNode = categoryNodes.getOrPut(library.category) {
          val newCategoryNode = DefaultMutableTreeNode(library.category, true)
          librariesRoot.add(newCategoryNode)
          newCategoryNode
        }
        categoryNode.add(libraryNode)
      }

      libraryNodes.add(libraryNode)
      if (libraryNode.isChecked) {
        selectedLibraries.add(library to libraryNode)
      }
    }

    val starterLibraryIds = starter.libraries.map { it.id }.toSet()
    selectedLibraryIds.removeIf { !starterLibraryIds.contains(it) }

    librariesList.model = DefaultTreeModel(librariesRoot)
    expandCategories(categoryNodes, librariesRoot, previouslyExpandedGroups, init)

    if (libraryNodes.isNotEmpty()) {
      val toExpand = libraryNodes.find { librariesList.isExpanded(TreeUtil.getPath(librariesRoot, it.parent)) }
      if (toExpand != null) {
        librariesList.selectionModel.addSelectionPath(TreeUtil.getPath(librariesRoot, toExpand))
      }
    }

    for ((library, node) in selectedLibraries) {
      updateIncludedLibraries(library, node)
    }
    updateSelectedLibraries()
  }

  private fun expandCategories(categoryNodes: MutableMap<LibraryCategory, DefaultMutableTreeNode>,
                               librariesRoot: CheckedTreeNode,
                               expandedCategories: List<LibraryCategory>,
                               isInitial: Boolean) {
    if (isInitial) {
      val collapsedCategories = moduleBuilder.getCollapsedDependencyCategoriesInternal()
      for ((_, node) in categoryNodes) {
        val categoryId = (node.userObject as? LibraryCategory)?.id
        val path = TreeUtil.getPath(librariesRoot, node)
        if (!collapsedCategories.contains(categoryId)) {
          librariesList.expandPath(path)
        }
        else {
          librariesList.collapsePath(path)
        }
      }
    }
    else {
      for (group in expandedCategories) {
        val newGroup = categoryNodes.entries.find { it.key == group }
        if (newGroup != null) {
          val path = TreeUtil.getPath(librariesRoot, newGroup.value)
          librariesList.expandPath(path)
        }
      }
    }
  }

  private fun getExpandedCategories(): List<LibraryCategory> {
    val librariesRoot = getLibrariesRoot() ?: return emptyList()
    return librariesRoot.children().toList()
      .filter { librariesList.isExpanded(TreeUtil.getPath(librariesRoot, it)) }
      .mapNotNull { (it as? DefaultMutableTreeNode)?.userObject as? LibraryCategory }
  }

  private fun updateIncludedLibraries(library: Library, node: CheckedTreeNode) {
    if (library.includesLibraries.isNotEmpty()) {
      val rootNode = librariesList.model.root as? CheckedTreeNode
      if (rootNode != null) {
        for (child in rootNode.children()) {
          if (child is CheckedTreeNode) {
            updateNodeIncluded(child, library, node)
          }
          else if (child is DefaultMutableTreeNode) {
            for (groupChild in child.children()) {
              updateNodeIncluded(groupChild, library, node)
            }
          }
        }
      }
    }
  }

  private fun updateNodeIncluded(child: Any?, library: Library, node: CheckedTreeNode) {
    val checkedNode = child as? CheckedTreeNode
    val nodeLibrary = checkedNode?.userObject as? Library ?: return

    if (library.includesLibraries.contains(nodeLibrary.id)) {
      checkedNode.isChecked = node.isChecked
      checkedNode.isEnabled = !node.isChecked
    }
  }
}