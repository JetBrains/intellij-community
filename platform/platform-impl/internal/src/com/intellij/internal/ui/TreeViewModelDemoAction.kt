// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ComponentUtil.getScrollPane
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.tree.TreeVisitor
import com.intellij.ui.treeStructure.Tree
import com.intellij.ui.treeStructure.TreeDomainModel
import com.intellij.ui.treeStructure.TreeNodeDomainModel
import com.intellij.ui.treeStructure.TreeNodePresentation
import com.intellij.ui.treeStructure.TreeNodePresentationBuilder
import com.intellij.ui.treeStructure.TreeNodeViewModel
import com.intellij.ui.treeStructure.TreeSwingModel
import com.intellij.ui.treeStructure.TreeViewModel
import com.intellij.ui.treeStructure.TreeViewModelVisitor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.showingScope
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.GroupLayout
import javax.swing.GroupLayout.Alignment.BASELINE
import javax.swing.GroupLayout.Alignment.LEADING
import javax.swing.GroupLayout.DEFAULT_SIZE
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.LayoutStyle
import javax.swing.LayoutStyle.ComponentPlacement.RELATED
import javax.swing.plaf.ScrollPaneUI
import kotlin.io.path.relativeTo

internal class TreeViewModelDemoAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val dialog = MyDialog(e.project)
    dialog.title = "Tree ViewModel Demo"
    dialog.setSize(800, 600)
    dialog.show()
  }
}

class MyDialog(project: Project?) : DialogWrapper(project, false, IdeModalityType.MODELESS) {

  init {
    init()
  }

  override fun createCenterPanel(): JComponent {
    val result = JPanel()

    result.showingScope("TreeViewModel Demo") {
      result.removeAll()
      val layout = GroupLayout(result)
      result.layout = layout
      val hg = layout.createParallelGroup(LEADING)
      val vg = layout.createSequentialGroup()
      layout.setHorizontalGroup(hg)
      layout.setVerticalGroup(vg)

      val tree = createTree(this)
      val controlPanel = createControlPanel(this, tree)

      hg
        .addComponent(tree.component, DEFAULT_SIZE, DEFAULT_SIZE, Short.MAX_VALUE.toInt())
        .addComponent(controlPanel)

      vg
        .addComponent(tree.component, DEFAULT_SIZE, DEFAULT_SIZE, Short.MAX_VALUE.toInt())
        .addComponent(controlPanel)
    }

    return result
  }
}

private class MyTree(
  val component: JScrollPane,
  val viewModel: TreeViewModel,
)

private fun createTree(coroutineScope: CoroutineScope): MyTree {
  val tree = Tree()
  val viewModel = TreeViewModel(coroutineScope, MyTreeDomainModel())
  tree.model = TreeSwingModel(coroutineScope, viewModel)
  return MyTree(ScrollPaneFactory.createScrollPane(tree, true), viewModel)
}

private fun createControlPanel(coroutineScope: CoroutineScope, tree: MyTree): JPanel {
  val result = JPanel()
  val layout = GroupLayout(result)
  result.layout = layout
  val hg = layout.createParallelGroup(LEADING)
  val vg = layout.createSequentialGroup()
  layout.setHorizontalGroup(hg)
  layout.setVerticalGroup(vg)

  val pathLabel = JBLabel("Path:")
  val visitPathField = JBTextField().apply {
    toolTipText = "Type a path relative to the root and press Enter to select it"
  }
  val logTextArea = JBTextArea()
  val logTextScrollPane = ScrollPaneFactory.createScrollPane(logTextArea, true)

  hg
    .addGroup(
      layout.createSequentialGroup()
        .addComponent(pathLabel)
        .addPreferredGap(RELATED)
        .addComponent(visitPathField)
    )
    .addComponent(logTextScrollPane)

  vg
    .addGroup(
      layout.createParallelGroup(BASELINE)
        .addComponent(pathLabel)
        .addComponent(visitPathField)
    )
    .addComponent(logTextScrollPane, JBUI.scale(200), DEFAULT_SIZE, Short.MAX_VALUE.toInt())

  visitPathField.addActionListener {
    val path = visitPathField.text
    coroutineScope.launch(CoroutineName("Visiting $path")) {
      val found = tree.viewModel.accept(PathVisitor(path, logTextArea), allowLoading = true)
      if (found != null) {
        tree.viewModel.setSelection(listOf(found))
      }
    }
  }

  return result
}

private class MyTreeDomainModel : TreeDomainModel {
  override suspend fun computeRoot(): TreeNodeDomainModel? = MyTreeNodeDomainModel(ModelPath())
}

private class MyTreeNodeDomainModel(private val path: ModelPath) : TreeNodeDomainModel {
  override suspend fun computeIsLeaf(): Boolean = !path.isDirectory() || path.isEmptyDirectory()

  override suspend fun computePresentation(builder: TreeNodePresentationBuilder): Flow<TreeNodePresentation> {
    builder.setIcon(if (path.isDirectory()) AllIcons.Nodes.Folder else AllIcons.FileTypes.Any_type)
    val mainText = path.name
    builder.setMainText(mainText)
    builder.appendTextFragment(mainText, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    builder.appendTextFragment(" (${path.fullPath})", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    return flowOf(
      builder.build()
    )
  }

  override suspend fun computeChildren(): List<TreeNodeDomainModel> = path.children.map {
    MyTreeNodeDomainModel(it)
  }

  override fun getUserObject(): ModelPath = path

  override fun toString(): String = "node($path)"
}

private class PathVisitor(path: String, private val logTextArea: JBTextArea) : TreeViewModelVisitor {
  private val path = ModelPath(ROOT.resolve(path))

  override suspend fun visit(node: TreeNodeViewModel): TreeVisitor.Action {
    val nodePath = node.getUserObject() as ModelPath
    return when {
      nodePath == path -> {
        log("found: $nodePath")
        TreeVisitor.Action.INTERRUPT
      }
      path.startsWith(nodePath) -> {
        log("visit: $nodePath")
        TreeVisitor.Action.CONTINUE
      }
      else -> {
        log("no match: $nodePath")
        TreeVisitor.Action.SKIP_CHILDREN
      }
    }
  }

  private fun log(message: String) {
    logTextArea.append(message + "\n")
    val scrollPane = getScrollPane(logTextArea)
    if (scrollPane != null) {
      scrollPane.verticalScrollBar.value = scrollPane.verticalScrollBar.maximum
    }
  }
}

class ModelPath(path: Path = ROOT) {
  private val path: Path = path.toAbsolutePath()

  val name: String
    get() = path.fileName.toString()

  val fullPath: String
    get() = path.toString()

  val relativePath: String
    get() = path.relativeTo(ROOT).toString()

  val children: List<ModelPath>
    get() = Files.newDirectoryStream(path).use {
      it.map { ModelPath(it) }
    }

  fun isDirectory(): Boolean = Files.isDirectory(path)

  fun isEmptyDirectory(): Boolean = runCatching {
    Files.newDirectoryStream(path).use { !it.any() }
  }.getOrDefault(true)

  fun startsWith(path: ModelPath): Boolean = this.relativePath.startsWith(path.relativePath)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ModelPath

    return path == other.path
  }

  override fun hashCode(): Int {
    return path.hashCode()
  }

  override fun toString(): String = "ModelPath(path=$path)"
}

private val ROOT: Path
  get() = Path.of(System.getProperty("user.home"))
