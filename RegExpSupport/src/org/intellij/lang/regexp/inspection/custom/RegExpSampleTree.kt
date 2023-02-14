// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.inspection.custom

import com.intellij.ide.DefaultTreeExpander
import com.intellij.ide.ui.search.SearchUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.*
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.tree.TreePathUtil
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.event.MouseEvent
import java.util.function.Function
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.tree.*

data class RegExpSample(
  @NlsSafe val name: String,
  @NlsSafe val sample: String,
  val caretOffset: Int,
  @NlsSafe val category: String,
  val userDefined: Boolean,
)

class RegExpSampleTree(val doubleClickConsumer: (RegExpSample) -> Unit) {
  val treeModel: TreeModel
  val tree: Tree

  val panel: JComponent
    get() = JBScrollPane(tree).apply { border = JBUI.Borders.empty() }

  init {
    val root = DefaultMutableTreeNode(null)
    treeModel = DefaultTreeModel(root)
    tree = Tree(treeModel).apply {
      isRootVisible = false
      showsRootHandles = true
      dragEnabled = false
      isEditable = false
      selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

      val speedSearch = TreeSpeedSearch(
        this,
        false,
        Function { treePath: TreePath ->
          val treeNode = treePath.lastPathComponent as DefaultMutableTreeNode
          val userObject = treeNode.userObject
          if (userObject is RegExpSample) userObject.name else userObject.toString()
        }
      )
      cellRenderer = MyTreeCellRenderer(speedSearch)
    }

    val groups = DefaultMutableTreeNode("Groups")
    groups.add(DefaultMutableTreeNode(RegExpSample("Capturing", "()", 1, "Groups", false)))
    groups.add(DefaultMutableTreeNode(RegExpSample("Named", "(?<name>)", 8, "Groups", false)))
    groups.add(DefaultMutableTreeNode(RegExpSample("Non-capturing", "(?:)", 3, "Groups", false)))
    groups.add(DefaultMutableTreeNode(RegExpSample("Lookahead", "(?=)", 3, "Groups", false)))
    groups.add(DefaultMutableTreeNode(RegExpSample("Negative lookahead", "(?!)", 3, "Groups", false)))
    groups.add(DefaultMutableTreeNode(RegExpSample("Lookbehind", "(?<=)", 4, "Groups", false)))
    groups.add(DefaultMutableTreeNode(RegExpSample("Negative lookbehind", "(?<!)", 4, "Groups", false)))
    groups.add(DefaultMutableTreeNode(RegExpSample("Atomic", "(?>)", 3, "Groups", false)))
    groups.add(DefaultMutableTreeNode(RegExpSample("Comment", "(?#)", 3, "Groups", false)))
    root.add(groups)

    val anchors = DefaultMutableTreeNode("Anchors")
    anchors.add(DefaultMutableTreeNode(RegExpSample("String or line start", "^", -1, "Anchors", false)))
    anchors.add(DefaultMutableTreeNode(RegExpSample("String start", "\\A", -1, "Anchors", false)))
    anchors.add(DefaultMutableTreeNode(RegExpSample("String or line end", "$", -1, "Anchors", false)))
    anchors.add(DefaultMutableTreeNode(RegExpSample("String end", "\\Z", -1, "Anchors", false)))
    anchors.add(DefaultMutableTreeNode(RegExpSample("Word boundary", "\\b", -1, "Anchors", false)))
    anchors.add(DefaultMutableTreeNode(RegExpSample("Non-word boundary", "\\B", -1, "Anchors", false)))
    anchors.add(DefaultMutableTreeNode(RegExpSample("Word start", "\\<", -1, "Anchors", false)))
    anchors.add(DefaultMutableTreeNode(RegExpSample("Word end", "\\>", -1, "Anchors", false)))
    root.add(anchors)

    val classes = DefaultMutableTreeNode("Character Classes")
    classes.add(DefaultMutableTreeNode(RegExpSample("Control character", "\\c", -1, "Classes", false)))
    classes.add(DefaultMutableTreeNode(RegExpSample("Whitespace", "\\s", -1, "Classes", false)))
    classes.add(DefaultMutableTreeNode(RegExpSample("Non-whitespace", "\\S", -1, "Classes", false)))
    classes.add(DefaultMutableTreeNode(RegExpSample("Digit", "\\d", -1, "Classes", false)))
    classes.add(DefaultMutableTreeNode(RegExpSample("Non-digit", "\\D", -1, "Classes", false)))
    classes.add(DefaultMutableTreeNode(RegExpSample("Word", "\\w", -1, "Classes", false)))
    classes.add(DefaultMutableTreeNode(RegExpSample("Non-word", "\\W", -1, "Classes", false)))
    classes.add(DefaultMutableTreeNode(RegExpSample("Hexadecimal digit", "\\x", -1, "Classes", false)))
    classes.add(DefaultMutableTreeNode(RegExpSample("Octal digit", "\\O", -1, "Classes", false)))
    root.add(classes)

    treeModel.reload()
    DefaultTreeExpander(tree).expandAll()

    object: DoubleClickListener() {
      override fun onDoubleClick(event: MouseEvent): Boolean {
        val path = tree.selectionPath ?: return false
        val node = TreePathUtil.toTreeNode(path) as? DefaultMutableTreeNode
        val sample = node?.userObject as? RegExpSample ?: return false
        doubleClickConsumer(sample)
        return true
      }
    }.installOn(tree)
  }

  private inner class MyTreeCellRenderer(private val mySpeedSearch: TreeSpeedSearch) : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(tree: JTree, value: Any, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean) {
      val treeNode = value as DefaultMutableTreeNode
      val userObject = treeNode.userObject ?: return
      val background = UIUtil.getTreeBackground(selected, hasFocus)
      val foreground = UIUtil.getTreeForeground(selected, hasFocus)

      val text: String
      val style: Int
      when (userObject) {
        is RegExpSample -> {
          text = userObject.name
          style = SimpleTextAttributes.STYLE_PLAIN
        }
        else -> {
          text = userObject.toString()
          style = SimpleTextAttributes.STYLE_BOLD
        }
      }
      SearchUtil.appendFragments(mySpeedSearch.enteredPrefix, text, style, foreground, background, this)
      if (userObject is RegExpSample) {
        append(ColoredText.singleFragment(" ${userObject.sample}", SimpleTextAttributes.GRAYED_ATTRIBUTES))
      }
    }
  }
}