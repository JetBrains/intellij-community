// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.dashboard.RunDashboardManager
import com.intellij.execution.services.ServiceViewContributor
import com.intellij.execution.services.ServiceViewManager
import com.intellij.execution.services.ServiceViewToolWindowDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.ui.*
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.xml.util.XmlStringUtil
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.MutableTreeNode

internal class ConfigureServicesDialog(private val project: Project) : DialogWrapper(project) {
  private val includedServicesTree = ServicesTree(project, ExecutionBundle.message("service.view.configure.run.configuration.types"))
  private val excludedServicesTree = ServicesTree(project, ExecutionBundle.message("service.view.configure.run.tool.windows"))
  private val initiallyFocusedTree: ServicesTree

  companion object {
    internal fun collectServices(project: Project): Pair<List<ServiceViewContributor<*>>, List<ServiceViewContributor<*>>> {
      val included = ArrayList<ServiceViewContributor<*>>()
      val excluded = ArrayList<ServiceViewContributor<*>>()
      val serviceViewManager = ServiceViewManager.getInstance(project)
      for (contributor in ServiceViewContributor.CONTRIBUTOR_EP_NAME.extensionList) {
        if ((contributor.getViewDescriptor(project) as? ServiceViewToolWindowDescriptor)?.isExclusionAllowed != false) {
          val toolWindowId = serviceViewManager.getToolWindowId(contributor::class.java) ?: ToolWindowId.SERVICES
          if (toolWindowId == ToolWindowId.SERVICES) {
            included.add(contributor)
          }
          else {
            excluded.add(contributor)
          }
        }
      }
      return Pair(included, excluded)
    }

    private fun collectTypes(project: Project): Pair<List<ConfigurationType>, List<ConfigurationType>> {
      val includedTypes = ArrayList<ConfigurationType>()
      val excludedTypes = ArrayList<ConfigurationType>()
      val types = RunDashboardManager.getInstance(project).types
      for (type in ConfigurationType.CONFIGURATION_TYPE_EP.extensionList) {
        if (types.contains(type.id)) {
          includedTypes.add(type)
        }
        else {
          excludedTypes.add(type)
        }
      }
      return Pair(includedTypes, excludedTypes)
    }
  }

  init {
    val baseText = ExecutionBundle.message("service.view.configure.dialog.title")
    if (project.isDefault) {
      title = IdeUICustomization.getInstance().projectMessage("title.for.new.projects", baseText)
    }
    else {
      title = baseText
    }

    val services = collectServices(project)
    val types = collectTypes(project)

    includedServicesTree.initTree(services.first, types.first, true)
    excludedServicesTree.initTree(services.second, types.second, false)

    initiallyFocusedTree = includedServicesTree
    init()
  }

  override fun createCenterPanel(): JComponent {
    val buttonsPanel = JPanel(VerticalFlowLayout())
    val moveToExcludedButton = JButton(ExecutionBundle.message("service.view.configure.exclude.button.text"))
    val moveToIncludedButton = JButton(ExecutionBundle.message("service.view.configure.include.button.text"))
    moveToExcludedButton.addActionListener {
      moveToExcluded()
    }
    moveToIncludedButton.addActionListener {
      moveToIncluded()
    }
    buttonsPanel.add(moveToExcludedButton)
    buttonsPanel.add(moveToIncludedButton)
    includedServicesTree.installDoubleClickListener(this::moveToExcluded)
    excludedServicesTree.installDoubleClickListener(this::moveToIncluded)

    val mainPanel = JPanel(BorderLayout())
    val gridBag = GridBag().setDefaultWeightX(0, 0.5).setDefaultWeightX(1, 0.0).setDefaultWeightX(2, 0.5)
    val treesPanel = JPanel(GridBagLayout())
    treesPanel.add(JBLabel(ExecutionBundle.message("service.view.configure.included.label.text")),
                   gridBag.nextLine().next().anchor(GridBagConstraints.WEST))
    treesPanel.add(JBLabel(ExecutionBundle.message("service.view.configure.excluded.label.text")),
                   gridBag.next().next().anchor(GridBagConstraints.WEST))

    val includedPane = JBScrollPane(includedServicesTree.tree)
    treesPanel.add(includedPane, gridBag.nextLine().next().weighty(1.0).fillCell())
    treesPanel.add(buttonsPanel, gridBag.next().anchor(GridBagConstraints.NORTH))
    val excludedPane = JBScrollPane(excludedServicesTree.tree)
    excludedPane.preferredSize = includedPane.preferredSize
    treesPanel.add(excludedPane, gridBag.next().weighty(1.0).fillCell())
    mainPanel.add(treesPanel, BorderLayout.CENTER)

    val statusPanel = JPanel(BorderLayout())
    mainPanel.add(statusPanel, BorderLayout.SOUTH)
    val statusLabel = JBLabel()
    statusLabel.text = XmlStringUtil.wrapInHtml(ExecutionBundle.message("service.view.configure.dialog.description"))
    statusLabel.border = JBUI.Borders.emptyTop(5)
    statusPanel.add(statusLabel, BorderLayout.NORTH)

    if (!project.isDefault) {
      val actionLink = ActionLink(ExecutionBundle.message("service.view.configure.dialog.new.project.text")) {
        ConfigureServicesDialog(ProjectManager.getInstance().defaultProject).show()
      }
      actionLink.border = JBUI.Borders.emptyTop(10)
      actionLink.background = JBColor.background()
      statusPanel.add(actionLink, BorderLayout.SOUTH)
    }

    return mainPanel
  }

  private fun moveToExcluded() {
    move(includedServicesTree, excludedServicesTree)
  }

  private fun moveToIncluded() {
    move(excludedServicesTree, includedServicesTree)
  }

  private fun move(from: ServicesTree, to: ServicesTree) {
    val nodes = from.getSelectedNodes()
    if (nodes.isEmpty()) return

    val oldSelectedRow = from.tree.selectionModel.leadSelectionRow
    from.removeNodes(nodes)
    to.addNodes(nodes)
    to.selectNodes(nodes)
    IdeFocusManager.getInstance(project).requestFocus(from.tree, true).doWhenDone {
      from.tree.selectionModel.selectionPath = from.tree.getPathForRow(oldSelectedRow.coerceAtMost(from.tree.rowCount - 1))
    }
  }

  override fun getPreferredFocusedComponent(): JComponent {
    return initiallyFocusedTree.tree
  }

  override fun doOKAction() {
    (ServiceViewManager.getInstance(project) as ServiceViewManagerImpl).setExcludedContributors(excludedServicesTree.getServices())
    RunDashboardManager.getInstance(project).types = includedServicesTree.getTypes()
    super.doOKAction()
  }
}

private class ServicesTree(private val project: Project,
                           private val runDashboardNodeName: @NlsContexts.TabTitle String) {
  private val root = RootNode()
  private val model = DefaultTreeModel(root)
  private var runDashboardNode: RunDashboardNode? = null
  val tree = Tree(model)

  init {
    tree.isRootVisible = false
    tree.showsRootHandles = true
    TreeSpeedSearch.installOn(tree, true) { treePath -> (treePath.lastPathComponent as? ServiceTreeNode)?.text ?: "" }
    tree.cellRenderer = ServicesTreeRenderer()
  }

  fun installDoubleClickListener(action: () -> Unit) {
    object : DoubleClickListener() {
      override fun onDoubleClick(event: MouseEvent): Boolean {
        if (tree.selectionPaths?.all { (it?.lastPathComponent as? ServiceTreeNode)?.isLeaf == true } == true) {
          action()
          return true
        }
        return false
      }
    }.installOn(tree)
  }

  fun initTree(services: Collection<ServiceViewContributor<*>>, types: Collection<ConfigurationType>, expandTypes: Boolean) {
    addNodes(services.map { ServiceViewContributorNode(it, project) } + types.map { RunConfigurationTypeNode(it) })
    if (expandTypes && runDashboardNode != null) {
      tree.expandPath(TreeUtil.getPathFromRoot(runDashboardNode!!))
    }
  }

  fun addNodes(nodes: Collection<ServiceTreeNode>) {
    val typesNode = nodes.find { it is RunDashboardNode }
    val types = typesNode?.children()?.toList() ?: nodes.filterIsInstance<RunConfigurationTypeNode>()
    if (types.isNotEmpty()) {
      if (runDashboardNode == null) {
        runDashboardNode = RunDashboardNode(runDashboardNodeName)
        root.add(runDashboardNode)
      }
      for (type in types) {
        runDashboardNode!!.add(type as MutableTreeNode)
      }
      TreeUtil.sort(runDashboardNode!!, nodeComparator)
    }

    val services = nodes.filterIsInstance<ServiceViewContributorNode>()
    if (services.isNotEmpty()) {
      for (service in services) {
        root.add(service)
      }
      TreeUtil.sort(root, nodeComparator)
    }

    updateModel()
  }

  fun removeNodes(nodes: Collection<ServiceTreeNode>) {
    for (node in nodes) {
      when (node) {
        is ServiceViewContributorNode -> {
          root.remove(node)
        }
        is RunDashboardNode -> {
          root.remove(node)
          runDashboardNode = null
        }
        is RunConfigurationTypeNode -> {
          runDashboardNode?.remove(node)
        }
      }
    }

    updateModel()
  }

  private fun updateModel() {
    val expanded = runDashboardNode?.let { tree.isExpanded(TreeUtil.getPathFromRoot(runDashboardNode!!)) } ?: false
    model.nodeStructureChanged(root)
    if (runDashboardNode != null) {
      model.nodeStructureChanged(runDashboardNode)
      if (expanded) {
        tree.expandPath(TreeUtil.getPathFromRoot(runDashboardNode!!))
      }
    }
  }

  fun getSelectedNodes(): List<ServiceTreeNode> =
    tree.selectionPaths
      ?.mapNotNull { it.lastPathComponent }
      ?.filterIsInstance<ServiceTreeNode>()
    ?: emptyList()

  fun selectNodes(nodes: Collection<ServiceTreeNode>) {
    val paths = nodes.map { TreeUtil.getPathFromRoot(it) }
    tree.selectionModel.selectionPaths = paths.toTypedArray()
    if (paths.isNotEmpty()) {
      TreeUtil.showRowCentered(tree, tree.getRowForPath(paths.first()), false, true)
    }
  }

  fun getServices(): Collection<ServiceViewContributor<*>> {
    val services = ArrayList<ServiceViewContributor<*>>()
    for (child in root.children()) {
      if (child is ServiceViewContributorNode) {
        services.add(child.contributor)
      }
    }
    return services
  }

  fun getTypes(): Set<String> {
    if (runDashboardNode == null) {
      return emptySet()
    }
    val types = HashSet<String>()
    for (child in runDashboardNode!!.children()) {
      types.add((child as RunConfigurationTypeNode).type.id)
    }
    return types
  }
}

private class ServicesTreeRenderer : ColoredTreeCellRenderer() {
  override fun customizeCellRenderer(tree: JTree,
                                     value: Any?,
                                     selected: Boolean,
                                     expanded: Boolean,
                                     leaf: Boolean,
                                     row: Int,
                                     hasFocus: Boolean) {
    if (value is ServiceTreeNode) {
      icon = value.icon
      append(value.text)
    }
  }
}

private val nodeComparator = Comparator<ServiceTreeNode> { node1, node2 ->
  if (node1 is RunDashboardNode) {
    return@Comparator if (node2 is RunDashboardNode) 0 else 1
  }
  else if (node2 is RunDashboardNode) {
    return@Comparator -1
  }
  return@Comparator NaturalComparator.INSTANCE.compare(node1.text, node2.text)
}

private abstract class ServiceTreeNode(val text: @NlsSafe String, val icon: Icon) : DefaultMutableTreeNode()

private class RootNode : ServiceTreeNode("<root>", AllIcons.Nodes.EmptyNode)

private class ServiceViewContributorNode(val contributor: ServiceViewContributor<*>, project: Project) :
  ServiceTreeNode(contributor.getViewDescriptor(project).presentation.presentableText ?: "",
                  contributor.getViewDescriptor(project).presentation.getIcon(false) ?: AllIcons.Nodes.EmptyNode)

private class RunDashboardNode(nodeText: String) : ServiceTreeNode(nodeText, AllIcons.Actions.Execute)

private class RunConfigurationTypeNode(val type: ConfigurationType) : ServiceTreeNode(type.displayName, type.icon)